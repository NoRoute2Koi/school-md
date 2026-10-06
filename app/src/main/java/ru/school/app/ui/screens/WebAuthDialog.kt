package ru.school.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject
import ru.school.app.data.JwtDecoder
import ru.school.app.data.Region

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebAuthDialog(
    region: Region,
    onDismiss: () -> Unit,
    onTokenExtracted: (String) -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf(region.title) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Авторизация: ${region.title}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = region.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    },
                    actions = {
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewInstance = this
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            }

                            val webView = this
                            CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setAcceptThirdPartyCookies(webView, true)
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    url?.let { checkForTokenInUrl(it, onTokenExtracted) }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    return if (checkForTokenInUrl(url, onTokenExtracted)) {
                                        true
                                    } else {
                                        false
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    pageTitle = view?.title ?: region.title

                                    url?.let { currentUrl ->
                                        // If we reached token endpoint or any page with potential JSON
                                        if (currentUrl.contains("token") || currentUrl.contains("refresh")) {
                                            view?.evaluateJavascript(
                                                "(function() { return document.body ? document.body.innerText : ''; })();"
                                            ) { text ->
                                                val cleanText = text?.trim()?.removeSurrounding("\"")
                                                    ?.replace("\\\"", "\"")
                                                    ?.replace("\\n", "\n") ?: return@evaluateJavascript
                                                parseTokenFromText(cleanText)?.let { token ->
                                                    onTokenExtracted(token)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            loadUrl(region.authUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun checkForTokenInUrl(url: String, onTokenFound: (String) -> Unit): Boolean {
    // Check if token is in query params or fragment
    if (url.contains("token=")) {
        val queryToken = url.substringAfter("token=").substringBefore("&").substringBefore("#")
        if (JwtDecoder.isValidFormat(queryToken)) {
            onTokenFound(queryToken)
            return true
        }
    }
    if (url.contains("access_token=")) {
        val queryToken = url.substringAfter("access_token=").substringBefore("&").substringBefore("#")
        if (JwtDecoder.isValidFormat(queryToken)) {
            onTokenFound(queryToken)
            return true
        }
    }
    return false
}

private fun parseTokenFromText(text: String): String? {
    if (JwtDecoder.isValidFormat(text)) return text

    return runCatching {
        val json = JSONObject(text)
        when {
            json.has("token") -> json.getString("token")
            json.has("access_token") -> json.getString("access_token")
            json.has("data") && json.getJSONObject("data").has("token") ->
                json.getJSONObject("data").getString("token")
            else -> null
        }
    }.getOrNull()?.takeIf { JwtDecoder.isValidFormat(it) }
}
