package com.example.digitalcollectionmanager.ui.components

import android.annotation.SuppressLint
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.nio.charset.Charset

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UbisoftAuthDialog(
    onSessionCaptured: (ticket: String, sessionId: String) -> Unit,
    onDismiss: () -> Unit
) {
    // The AppId observed in successful background requests
    val appId = "f35adcb5-1911-440c-b1c9-48fdc1701c68"
    val loginUrl = "https://connect.ubisoft.com/login?appId=$appId&lang=en-US&nextUrl=https%3A%2F%2Fconnect.ubisoft.com%2Fready"
    val ua = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

    var capturedTicket by remember { mutableStateOf<String?>(null) }
    var capturedSessionId by remember { mutableStateOf<String?>(null) }

    fun checkAndNotify() {
        if (capturedTicket != null && capturedSessionId != null) {
            Log.d("UBI_AUTH", "All tokens captured! Notifying listener...")
            onSessionCaptured(capturedTicket!!, capturedSessionId!!)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Login to Ubisoft Connect") },
        text = {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp),
                factory = { context ->
                    WebView(context).apply {
                        // Log WebView Version for diagnostics
                        val webViewPackage = WebViewCompat.getCurrentWebViewPackage(context)
                        Log.d("UBI_AUTH", "WebView Version: ${webViewPackage?.versionName ?: "Unknown"}")

                        // Clear state for a fresh login
                        clearCache(true)
                        clearHistory()
                        clearFormData()
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()

                        applyStealthSettings(this, ua)
                        Log.d("UBI_AUTH", "Initializing WebView with Stealth Settings")

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                Log.d("UBI_WEB_CONSOLE", "${consoleMessage?.messageLevel()}: ${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                                return true
                            }

                            override fun onPermissionRequest(request: PermissionRequest?) {
                                Log.d("UBI_AUTH", "Permission requested: ${request?.resources?.joinToString()}")
                                request?.grant(request.resources)
                            }

                            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                                Log.d("UBI_AUTH", "Create window requested (isDialog: $isDialog, isUserGesture: $isUserGesture)")
                                val parentChromeClient = this@apply.webChromeClient
                                val newWebView = WebView(context).apply {
                                    applyStealthSettings(this, ua)
                                    webViewClient = view?.webViewClient ?: object : WebViewClient() {}
                                    webChromeClient = parentChromeClient
                                }
                                
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = newWebView
                                resultMsg?.sendToTarget()
                                return true
                            }
                        }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                Log.d("UBI_AUTH", "Page started: $url")
                            }

                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                super.onReceivedError(view, request, error)
                                Log.e("UBI_AUTH", "Network Error: ${error?.errorCode} - ${error?.description} for URL: ${request?.url}")
                            }

                            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                Log.e("UBI_AUTH", "HTTP Error: ${errorResponse?.statusCode} for URL: ${request?.url}")
                            }

                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val url = request?.url?.toString() ?: ""
                                
                                if (url.contains("public-ubiservices.ubi.com")) {
                                    val authHeader = request?.requestHeaders?.get("Authorization")
                                    if (authHeader != null && authHeader.startsWith("ubi_v1 t=")) {
                                        val ticket = authHeader.substringAfter("t=")
                                        if (ticket != capturedTicket) {
                                            capturedTicket = ticket
                                            Log.d("UBI_AUTH", "Captured Ticket from headers!")
                                            
                                            // Extract SessionID from Ticket (sid field)
                                            try {
                                                val jsonPart = ticket.split(".")[0]
                                                val decoded = String(Base64.decode(jsonPart, Base64.DEFAULT), Charset.forName("UTF-8"))
                                                val sid = JSONObject(decoded).optString("sid")
                                                if (sid.isNotBlank()) {
                                                    capturedSessionId = sid
                                                    Log.d("UBI_AUTH", "Extracted SessionID: $sid")
                                                }
                                            } catch (e: Exception) {
                                                Log.e("UBI_AUTH", "Failed to parse session from ticket: ${e.message}")
                                            }
                                            checkAndNotify()
                                        }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                Log.d("UBI_AUTH", "Loading URL: $url")
                                
                                if (url.contains("connect.ubisoft.com/ready") || url.contains("uplay://")) {
                                    captureSession()
                                    return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Log.d("UBI_AUTH", "Page finished: $url")
                                
                                // Inject stealth scripts to hide WebView signature
                                view?.evaluateJavascript("""
                                    (function() {
                                        Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                                        window.chrome = { runtime: {} };
                                        Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
                                        Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
                                    })();
                                """.trimIndent(), null)

                                if (url?.contains("connect.ubisoft.com/ready") == true) {
                                    captureSessionFromCookies()
                                }
                            }

                            private fun captureSessionFromCookies() {
                                val cookieManager = CookieManager.getInstance()
                                
                                // Check both common Ubisoft domains for session data
                                val domains = listOf("https://public-ubiservices.ubi.com", "https://connect.ubisoft.com")
                                
                                var ticket: String? = null
                                var sessionId: String? = null

                                for (domain in domains) {
                                    val allCookies = cookieManager.getCookie(domain) ?: ""
                                    Log.d("UBI_AUTH", "Checking cookies for domain $domain...")

                                    allCookies.split(";").forEach {
                                        val pair = it.trim().split("=")
                                        if (pair.size == 2) {
                                            val key = pair[0].trim()
                                            val value = pair[1].trim()
                                            when (key) {
                                                "ticket" -> ticket = value
                                                "sessionId" -> sessionId = value
                                            }
                                        }
                                    }
                                    if (ticket != null && sessionId != null) break
                                }

                                // Fallback: If ticket is found but sessionId isn't in cookies, 
                                // try to extract it from the JWT-like ticket structure
                                if (ticket != null && sessionId == null) {
                                    try {
                                        val jsonPart = ticket.split(".")[0]
                                        val decoded = String(Base64.decode(jsonPart, Base64.DEFAULT), Charset.forName("UTF-8"))
                                        val sid = JSONObject(decoded).optString("sid")
                                        if (sid.isNotBlank()) {
                                            sessionId = sid
                                            Log.d("UBI_AUTH", "Extracted SessionID from Ticket fallback: $sid")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("UBI_AUTH", "Fallback extraction failed: ${e.message}")
                                    }
                                }

                                if (ticket != null && sessionId != null) {
                                    Log.d("UBI_AUTH", "Session captured! Ticket: ${ticket.take(10)}..., SID: ${sessionId.take(10)}...")
                                    onSessionCaptured(ticket, sessionId)
                                }
                            }
                            
                            private fun captureSession() {
                                // Sometimes the ticket is in the URL hash or parameters
                                // But usually it's in cookies after the 'ready' redirect.
                                captureSessionFromCookies()
                            }
                        }
                        // Attempt to mask X-Requested-With to avoid 403
                        loadUrl(loginUrl, mapOf("X-Requested-With" to ""))
                    }
                }
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun applyStealthSettings(webView: WebView, ua: String) {
    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    @Suppress("DEPRECATION")
    settings.databaseEnabled = true
    settings.allowFileAccess = true
    settings.allowContentAccess = true
    settings.userAgentString = ua
    
    // Advanced settings for ReCaptcha & Ubisoft security
    settings.javaScriptCanOpenWindowsAutomatically = true
    @Suppress("DEPRECATION")
    settings.setSupportMultipleWindows(true)
    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    settings.setSupportZoom(true)
    
    // Fix for 403 Forbidden & ReCaptcha: Disable X-Requested-With header
    @Suppress("RestrictedApi")
    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
        // Use explicit origins instead of emptySet for better compatibility
        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(
            settings, 
            setOf(
                "https://connect.ubisoft.com", 
                "https://public-ubiservices.ubi.com", 
                "https://google.com", 
                "https://www.google.com", 
                "https://www.gstatic.com",
                "https://gstatic.com"
            )
        )
    }

    // Disable Safe Browsing to avoid blocking auth scripts
    if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
        WebSettingsCompat.setSafeBrowsingEnabled(settings, false)
    }

    // Override Client Hints to hide "Android WebView" brand
    if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
        val metadata = UserAgentMetadata.Builder()
            .setBrandVersionList(listOf(
                UserAgentMetadata.BrandVersion.Builder().setBrand("Not;A=Brand").setMajorVersion("8").setFullVersion("8.0.0.0").build(),
                UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium").setMajorVersion("116").setFullVersion("116.0.0.0").build(),
                UserAgentMetadata.BrandVersion.Builder().setBrand("Google Chrome").setMajorVersion("116").setFullVersion("116.0.0.0").build()
            ))
            .setMobile(true)
            .setPlatform("Android")
            .setPlatformVersion("13.0.0")
            .setArchitecture("arm")
            .setModel("Pixel 7")
            .build()
        WebSettingsCompat.setUserAgentMetadata(settings, metadata)
    }
    
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
}
