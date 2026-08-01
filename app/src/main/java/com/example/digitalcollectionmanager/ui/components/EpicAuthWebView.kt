package com.example.digitalcollectionmanager.ui.components

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.digitalcollectionmanager.data.api.EpicClient
import kotlinx.serialization.json.Json

/**
 * A bridge to allow JavaScript code to communicate with Android Logcat.
 */
class EpicAuthBridge(
    private val onMessage: (String) -> Unit,
    private val onCodeCaptured: (String) -> Unit
) {
    @JavascriptInterface
    fun log(msg: String) {
        onMessage("[JS_LOG] $msg")
    }

    @JavascriptInterface
    fun captureCode(code: String) {
        onMessage("[JS_CODE] Code captured via Bridge")
        onCodeCaptured(code)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpicAuthDialog(
    email: String = "",
    password: String = "",
    onCodeCaptured: (String) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(email) {
        Log.d("EPIC_SYNC", "EpicAuthDialog initialized for email: $email")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Login to Epic Games") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            var captured = false
                            val handleCode = { code: String ->
                                if (!captured) {
                                    captured = true
                                    onCodeCaptured(code)
                                }
                            }

                            addJavascriptInterface(EpicAuthBridge({ Log.d("EPIC_BRIDGE", it) }, handleCode), "AndroidBridge")

                            val injectAutoFill = { view: WebView? ->
                                if (email.isNotBlank() && password.isNotBlank()) {
                                    val emailJson = Json.encodeToString(email)
                                    val passwordJson = Json.encodeToString(password)
                                    
                                    val script = """
                                        (function() {
                                            try {
                                                if (window.epicAutomationStarted) return;
                                                window.epicAutomationStarted = true;
                                                
                                                var emailVal = $emailJson;
                                                var passwordVal = $passwordJson;
                                                var emailDone = false;
                                                var passwordDone = false;

                                                AndroidBridge.log("Script starting (trimmed)...");

                                                function isActuallyVisible(el) {
                                                    if (!el) return false;
                                                    var rect = el.getBoundingClientRect();
                                                    var style = window.getComputedStyle(el);
                                                    
                                                    // Strict check: must be visible, have size, and be within the viewport
                                                    var inViewport = rect.left >= 0 && 
                                                                     rect.top >= 0 && 
                                                                     rect.left < window.innerWidth && 
                                                                     rect.top < window.innerHeight;

                                                    return rect.width > 0 && rect.height > 0 && 
                                                           inViewport &&
                                                           style.display !== 'none' && 
                                                           style.visibility !== 'hidden' && 
                                                           style.opacity !== '0';
                                                }

                                                function fill(el, val, name) {
                                                    AndroidBridge.log("Filling " + name);
                                                    try {
                                                        el.focus();
                                                        
                                                        // 1. Native setter bypasses React's value tracking
                                                        var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                                                        nativeSetter.call(el, val);
                                                        
                                                        // 2. Dispatch events to trigger validation
                                                        el.dispatchEvent(new Event('input', { bubbles: true }));
                                                        el.dispatchEvent(new Event('change', { bubbles: true }));
                                                        
                                                        // 3. Fake some typing to enable buttons
                                                        el.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: ' ' }));
                                                        el.dispatchEvent(new KeyboardEvent('keypress', { bubbles: true, key: ' ' }));
                                                        el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: ' ' }));
                                                        
                                                        // 4. Backup: execCommand
                                                        try {
                                                            el.select();
                                                            document.execCommand('insertText', false, val);
                                                        } catch(e) {}
                                                        
                                                        el.blur();
                                                        AndroidBridge.log(name + " injection complete");
                                                    } catch(e) {
                                                        AndroidBridge.log("Advanced fill error: " + e.message);
                                                        el.value = val;
                                                        el.dispatchEvent(new Event('input', { bubbles: true }));
                                                    }
                                                }

                                                function tryFill() {
                                                    try {
                                                        // Check for JSON response / Warning page code
                                                        var bodyText = document.body.innerText;
                                                        if (bodyText && bodyText.includes('authorizationCode')) {
                                                            var match = bodyText.match(/"authorizationCode"\s*:\s*"([a-f0-9]+)"/i);
                                                            if (match && match[1]) {
                                                                AndroidBridge.captureCode(match[1]);
                                                                clearInterval(window.epicInterval);
                                                                return;
                                                            }
                                                        }

                                                        var emailInput = document.querySelector('input#email, input[name="email"]');
                                                    var passwordInput = document.querySelector('input#password, input[name="password"]');

                                                    // 1. Check for Password Screen (Priority)
                                                    // Only fill password if it's visible AND email is NOT visible (prevents false positives)
                                                    if (!passwordDone && isActuallyVisible(passwordInput) && !isActuallyVisible(emailInput)) {
                                                        if (passwordInput.type === 'password') {
                                                            fill(passwordInput, passwordVal, "Password");
                                                            passwordDone = true;
                                                            AndroidBridge.log("Password complete. Automation off.");
                                                            clearInterval(window.epicInterval);
                                                            return;
                                                        }
                                                    }

                                                    // 2. Email Screen
                                                    if (!emailDone && isActuallyVisible(emailInput)) {
                                                        if (emailInput.type !== 'password') {
                                                            fill(emailInput, emailVal, "Email");
                                                            emailDone = true;
                                                            
                                                            setTimeout(function() {
                                                                var btn = document.querySelector('button#login, button#continue, button[type="submit"]');
                                                                if (isActuallyVisible(btn)) {
                                                                    AndroidBridge.log("Clicking Continue");
                                                                    btn.click();
                                                                }
                                                            }, 2000);
                                                        }
                                                    }

                                                    // 3. Selection Page
                                                    var signInBtn = document.getElementById('login-with-epic');
                                                    if (signInBtn && isActuallyVisible(signInBtn)) {
                                                        AndroidBridge.log("Clicking Sign-in button");
                                                        signInBtn.click();
                                                    }
                                                } catch (e) {
                                                    AndroidBridge.log("Loop error: " + e.message);
                                                }
                                            }

                                                window.epicInterval = setInterval(tryFill, 1500);
                                                tryFill();
                                            } catch (e) {
                                                AndroidBridge.log("CRASH: " + e.message);
                                            }
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(script, null)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    consoleMessage?.let { Log.d("EpicAuthJS", "[${it.messageLevel()}] ${it.message()}") }
                                    return true
                                }
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    if (newProgress > 60) injectAutoFill(view)
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (captured) return true
                                    if (url != null && url.startsWith(EpicClient.REDIRECT_URI)) {
                                        val uri = android.net.Uri.parse(url)
                                        val code = uri.getQueryParameter("code")
                                        if (code != null) {
                                            handleCode(code)
                                            return true
                                        }
                                    }
                                    return false
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (!captured) {
                                        injectAutoFill(view)
                                        
                                        // Backup: Scrape for code if automation missed it or page loaded fresh
                                        evaluateJavascript("(function() { return document.body.innerText; })();") { text ->
                                            if (text != null && !captured) {
                                                val codeRegex = Regex("\"authorizationCode\"\\s*:\\s*\"([a-f0-9]+)\"", RegexOption.IGNORE_CASE)
                                                val match = codeRegex.find(text)
                                                match?.groupValues?.get(1)?.let { handleCode(it) }
                                            }
                                        }
                                    }
                                }
                            }

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()

                            val authUrl = "https://www.epicgames.com/id/login" +
                                    "?redirectUrl=" + android.net.Uri.encode(
                                        "${EpicClient.REDIRECT_URI}?clientId=${EpicClient.CLIENT_ID}&responseType=code"
                                    )
                            loadUrl(authUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
