package com.example.digitalcollectionmanager.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BattleNetAuthDialog(
    onCookiesCaptured: (cookies: String) -> Unit,
    onDismiss: () -> Unit
) {
    val loginUrl = "https://account.battle.net/login"
    val ua = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

    var isCaptured by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Login to Battle.net") },
        text = {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        settings.databaseEnabled = true
                        settings.userAgentString = ua
                        
                        // Clear old session
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Log.d("BNET_AUTH", "Page finished: $url")
                                
                                // Inject stealth script
                                view?.evaluateJavascript("""
                                    (function() {
                                        Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                                    })();
                                """.trimIndent(), null)

                                // Detect login success: reaching overview or games page
                                if (url != null && url.contains("account.battle.net") && 
                                    (url.contains("/overview") || url.contains("/games") || url == "https://account.battle.net/")) {
                                    
                                    val cookies = CookieManager.getInstance().getCookie(url)
                                    if (!isCaptured && cookies != null && (cookies.contains("BAID") || cookies.contains("ac") || cookies.contains("BNetSession"))) {
                                        Log.d("BNET_AUTH", "Captured login cookies!")
                                        isCaptured = true
                                        onCookiesCaptured(cookies)
                                    } else if (!isCaptured && cookies != null && url.contains("/overview")) {
                                        // Fallback: If we are on overview, we MUST be logged in
                                        Log.d("BNET_AUTH", "Captured cookies via fallback (overview page)")
                                        isCaptured = true
                                        onCookiesCaptured(cookies)
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: ""
                                Log.d("BNET_AUTH", "Loading URL: $url")
                                return false
                            }
                        }
                        // Mask X-Requested-With
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
