package com.hashmusic.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to YouTube Music") },
                actions = {
                    TextButton(onClick = { onLoginSuccess("") }) {
                        Text("Skip / Guest", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.javaScriptCanOpenWindowsAutomatically = true

                        // Use modern Chrome desktop user agent to enable Google Account Chooser & modern passkey support
                        settings.userAgentString = 
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                // Check if we're on YouTube Music after login
                                if (url?.contains("music.youtube.com") == true) {
                                    val cookies = CookieManager.getInstance()
                                        .getCookie("https://music.youtube.com")
                                    if (cookies != null && (cookies.contains("SAPISID") || cookies.contains("HSID") || cookies.contains("SID"))) {
                                        onLoginSuccess(cookies)
                                    }
                                }
                            }
                        }
                        
                        loadUrl("https://accounts.google.com/AccountChooser?service=youtube&continue=https://music.youtube.com")
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}