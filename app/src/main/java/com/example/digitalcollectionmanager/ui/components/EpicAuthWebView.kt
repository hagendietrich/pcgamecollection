package com.example.digitalcollectionmanager.ui.components

import android.webkit.CookieManager
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
import com.example.digitalcollectionmanager.data.api.EpicClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpicAuthDialog(
    onCodeCaptured: (String) -> Unit,
    onDismiss: () -> Unit
) {
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
                            webViewClient = object : WebViewClient() {
                                private var captured = false

                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (captured) return true
                                    if (url != null && url.startsWith(EpicClient.REDIRECT_URI)) {
                                        val uri = android.net.Uri.parse(url)
                                        val code = uri.getQueryParameter("code")
                                        if (code != null) {
                                            captured = true
                                            onCodeCaptured(code)
                                            return true
                                        }
                                    }
                                    return false
                                }
                                
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (captured) return

                                    // Attempt 1: Check the URL parameters
                                    if (url != null && url.startsWith(EpicClient.REDIRECT_URI)) {
                                        val uri = android.net.Uri.parse(url)
                                        val code = uri.getQueryParameter("code")
                                        if (code != null) {
                                            captured = true
                                            onCodeCaptured(code)
                                            return
                                        }
                                    }

                                    // Attempt 2: Scrape the page content for the code (handles the JSON/Security Warning page)
                                    evaluateJavascript("(function() { return document.body.innerText; })();") { text ->
                                        if (text != null && !captured) {
                                            // Matches "authorizationCode": "..." or similar patterns in the warning text
                                            val codeRegex = Regex("\"authorizationCode\"\\s*:\\s*\"([a-f0-9]+)\"", RegexOption.IGNORE_CASE)
                                            val altCodeRegex = Regex("authorizationCode\\s*[:=]\\s*([a-f0-9]+)", RegexOption.IGNORE_CASE)
                                            val urlCodeRegex = Regex("code=([a-f0-9]+)", RegexOption.IGNORE_CASE)
                                            
                                            val match = codeRegex.find(text) ?: altCodeRegex.find(text) ?: urlCodeRegex.find(text)
                                            val code = match?.groupValues?.get(1)
                                            
                                            if (code != null) {
                                                captured = true
                                                onCodeCaptured(code)
                                            }
                                        }
                                    }
                                }
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                            
                            // Deep clean
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
