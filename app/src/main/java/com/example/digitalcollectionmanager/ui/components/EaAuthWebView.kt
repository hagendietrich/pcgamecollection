package com.example.digitalcollectionmanager.ui.components

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.digitalcollectionmanager.data.api.EaClient
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EaAuthDialog(
    lastEmail: String,
    onTokensCaptured: (remid: String, sid: String, code: String?, email: String?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Login to EA") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
            }
        ) { innerPadding ->
            var isCapturing by remember { mutableStateOf(false) }
            var captureStartTime by remember { mutableLongStateOf(0L) }

            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (lastEmail.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Reference email: $lastEmail (Check 'Keep me signed in')",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                                var poller: Runnable? = null
                                
                                webViewClient = object : WebViewClient() {
                                    private var emailCaptured: String? = null
                                    private var codeCaptured: String? = null
                                    private var captured = false

                                    private fun checkStatus(currentUrl: String?, isResource: Boolean = false) {
                                        if (captured) return
                                        
                                        // Use currentUrl if provided, otherwise fallback to main WebView URL
                                        val url = currentUrl ?: this@apply.url
                                        
                                        if (url != null) {
                                            if (url.contains("code=")) {
                                                if (url.startsWith("nucleus:")) {
                                                    // Handle nucleus:rest?code=...
                                                    val query = url.substringAfter("?")
                                                    val params = query.split("&").associate {
                                                        val parts = it.split("=")
                                                        parts[0] to parts.getOrElse(1) { "" }
                                                    }
                                                    codeCaptured = params["code"]
                                                } else {
                                                    val uri = android.net.Uri.parse(url)
                                                    codeCaptured = uri.getQueryParameter("code")
                                                }
                                            } else if (url.contains("access_token=")) {
                                                val fragment = url.substringAfter("#")
                                                val params = fragment.split("&").associate {
                                                    val parts = it.split("=")
                                                    parts[0] to parts.getOrElse(1) { "" }
                                                }
                                                codeCaptured = params["access_token"]
                                            }
                                        }

                                        val cookieManager = CookieManager.getInstance()
                                        
                                        fun getCookieValueFromAll(name: String): String? {
                                            val domains = listOf(
                                                "https://accounts.ea.com", 
                                                "https://ea.com", 
                                                "https://www.ea.com", 
                                                "https://origin.com", 
                                                "https://www.origin.com",
                                                "https://.ea.com",
                                                "https://.origin.com",
                                                "https://signin.ea.com"
                                            )
                                            for (domain in domains) {
                                                val cookies = cookieManager.getCookie(domain) ?: continue
                                                val value = Regex("$name=([^;]+)").find(cookies)?.groupValues?.get(1)
                                                if (!value.isNullOrBlank()) return value
                                            }
                                            return null
                                        }

                                        val remid = getCookieValueFromAll("remid")
                                        val sid = getCookieValueFromAll("sid")
                                        
                                        // IMPORTANT: Success detection only runs on the MAIN page URL, not background resources
                                        val mainUrl = this@apply.url ?: ""
                                        val isLoginServer = mainUrl.contains("accounts.ea.com")
                                        val isEaDomain = mainUrl.contains("ea.com") || mainUrl.contains("origin.com")
                                        val hasTokens = mainUrl.contains("code=") || mainUrl.contains("access_token=")
                                        
                                        val isSuccessUrl = (isEaDomain && !isLoginServer) || hasTokens

                                        // Capture logic:
                                        // 1. If we have cookies, we are definitely good.
                                        // 2. If we have a code but no cookies yet, we can try to proceed anyway.
                                        // 3. If we are on a success URL but have neither, we wait for a bit.
                                        
                                        val canProceedWithCookies = !remid.isNullOrBlank() && !sid.isNullOrBlank()
                                        val canProceedWithCode = !codeCaptured.isNullOrBlank()

                                        if (canProceedWithCookies || canProceedWithCode) {
                                            captured = true
                                            isCapturing = false
                                            handler.removeCallbacks(poller!!)
                                            onTokensCaptured(remid ?: "", sid ?: "", codeCaptured, emailCaptured)
                                        } else if (isSuccessUrl && !isResource) {
                                            // Only enter "Capturing" mode if the main page has redirected
                                            if (captureStartTime == 0L) captureStartTime = System.currentTimeMillis()
                                            isCapturing = true
                                            
                                            // Timeout: If on success page for > 5 seconds, proceed with whatever we have
                                            if (System.currentTimeMillis() - captureStartTime > 5000) {
                                                captured = true
                                                isCapturing = false
                                                handler.removeCallbacks(poller!!)
                                                onTokensCaptured(remid ?: "", sid ?: "", codeCaptured, emailCaptured)
                                            }
                                        }
                                    }

                                    init {
                                        poller = object : Runnable {
                                            override fun run() {
                                                if (!captured) {
                                                    checkStatus(this@apply.url, isResource = false)
                                                    handler.postDelayed(this, 1000)
                                                }
                                            }
                                        }
                                        handler.postDelayed(poller!!, 1000)
                                    }

                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val url = request?.url?.toString() ?: return null
                                        
                                        // Intercept the login pages where SRI errors occur
                                        if (url.contains("signin.ea.com") || url.contains("accounts.ea.com")) {
                                            if (request.method.equals("GET", ignoreCase = true)) {
                                                return try {
                                                    val connection = URL(url).openConnection() as HttpURLConnection
                                                    connection.requestMethod = "GET"
                                                    
                                                    // Copy headers
                                                    request.requestHeaders.forEach { (key, value) ->
                                                        connection.setRequestProperty(key, value)
                                                    }
                                                    
                                                    // Ensure Cookie header is up to date
                                                    val cookies = CookieManager.getInstance().getCookie(url)
                                                    if (!cookies.isNullOrEmpty()) {
                                                        connection.setRequestProperty("Cookie", cookies)
                                                    }
                                                    
                                                    connection.connect()
                                                    
                                                    if (connection.responseCode == 200) {
                                                        val contentType = connection.contentType ?: "text/html"
                                                        if (contentType.contains("text/html")) {
                                                            var html = connection.inputStream.bufferedReader().use { it.readText() }
                                                            
                                                            // Strip integrity attributes to bypass SRI check
                                                            val integrityRegex = Regex("""integrity\s*=\s*["'][^"']*["']""", RegexOption.IGNORE_CASE)
                                                            html = html.replace(integrityRegex, "")
                                                            
                                                            return WebResourceResponse(
                                                                "text/html",
                                                                connection.contentEncoding ?: "UTF-8",
                                                                ByteArrayInputStream(html.toByteArray())
                                                            )
                                                        }
                                                    }
                                                    null
                                                } catch (e: Exception) {
                                                    null
                                                }
                                            }
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                        checkStatus(request?.url?.toString(), isResource = false)
                                        return false
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        checkStatus(url, isResource = false)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        
                                        view?.evaluateJavascript("document.cookie") { cookies ->
                                            if (cookies != null && cookies != "null" && !captured) {
                                                val cleaned = cookies.replace("\"", "")
                                                val cookieMap = cleaned.split("; ").associate {
                                                    val parts = it.split("=")
                                                    parts[0] to parts.getOrElse(1) { "" }
                                                }
                                                val r = cookieMap["remid"]
                                                val s = cookieMap["sid"]
                                                if (!r.isNullOrBlank() && !s.isNullOrBlank()) {
                                                    captured = true
                                                    isCapturing = false
                                                    handler.removeCallbacks(poller!!)
                                                    onTokensCaptured(r, s, codeCaptured, emailCaptured)
                                                }
                                            }
                                        }

                                        view?.evaluateJavascript(
                                            "(function() {" +
                                            "  var input = document.getElementById('email') || document.querySelector('input[type=email]') || document.querySelector('input[name=email]');" +
                                            "  return input ? input.value : null;" +
                                            "})();"
                                        ) { value ->
                                            val cleaned = value?.replace("\"", "")
                                            if (!cleaned.isNullOrBlank() && cleaned != "null") {
                                                emailCaptured = cleaned
                                            }
                                        }
                                        checkStatus(url, isResource = false)
                                    }

                                    override fun onLoadResource(view: WebView?, url: String?) {
                                        super.onLoadResource(view, url)
                                        if (url?.contains("ea.com") == true) {
                                            checkStatus(url, isResource = true)
                                        }
                                    }
                                }
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.safeBrowsingEnabled = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                                
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                // Deep clean to force fresh login
                                clearCache(true)
                                clearFormData()
                                clearHistory()
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                android.webkit.WebStorage.getInstance().deleteAllData()
                                
                                val authUrl = "https://accounts.ea.com/connect/auth" +
                                        "?response_type=code" +
                                        "&client_id=${EaClient.EA_CLIENT_ID}" +
                                        "&display=originXWeb/login" +
                                        "&locale=en_US" +
                                        "&release_type=prod" +
                                        "&redirect_uri=${EaClient.EA_REDIRECT_URI}"
                                
                                loadUrl(authUrl, mapOf("X-Requested-With" to ""))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isCapturing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Capturing session...")
                            }
                        }
                    }
                }
            }
        }
    }
}
