package com.example.digitalcollectionmanager.ui.components

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EaAuthDialog(
    onTokensCaptured: (remid: String, sid: String) -> Unit,
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
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val cookies = CookieManager.getInstance().getCookie(url)
                                    if (cookies != null) {
                                        val cookieMap = cookies.split("; ").associate {
                                            val parts = it.split("=")
                                            parts[0] to parts.getOrElse(1) { "" }
                                        }
                                        
                                        val remid = cookieMap["remid"]
                                        val sid = cookieMap["sid"]
                                        
                                        if (!remid.isNullOrBlank() && !sid.isNullOrBlank()) {
                                            onTokensCaptured(remid, sid)
                                        }
                                    }
                                }
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // Clear old cookies to force a fresh login
                            CookieManager.getInstance().removeAllCookies(null)
                            
                            loadUrl("https://accounts.ea.com/connect/auth?client_id=ORIGIN_JS_SDK&response_type=code&redirect_uri=nucleus:rest&prompt=login")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
