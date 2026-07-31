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
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (url != null && url.startsWith(EpicClient.REDIRECT_URI)) {
                                        val uri = android.net.Uri.parse(url)
                                        val code = uri.getQueryParameter("code")
                                        if (code != null) {
                                            onCodeCaptured(code)
                                            return true
                                        }
                                    }
                                    return false
                                }
                                
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Some flows redirect to a page that has the code in the URL but doesn't trigger shouldOverrideUrlLoading
                                    if (url != null && url.startsWith(EpicClient.REDIRECT_URI)) {
                                        val uri = android.net.Uri.parse(url)
                                        val code = uri.getQueryParameter("code")
                                        if (code != null) {
                                            onCodeCaptured(code)
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
