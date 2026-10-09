package koi.schoolmd.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.net.http.SslError
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import koi.schoolmd.R
import koi.schoolmd.data.JwtData
import koi.schoolmd.data.JwtDecoder
import koi.schoolmd.data.Region
import org.json.JSONObject

@Composable
fun LoginScreen(
    onLoginSuccess: (Region, String) -> Unit,
    onCookiesCaptured: (String) -> Unit = {}
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var selectedRegion by remember { mutableStateOf(Region.MOSCOW_REGION) }
    var tokenInput by remember { mutableStateOf("") }
    var activeWebViewUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val jwtValidation by remember(tokenInput) {
        derivedStateOf {
            if (tokenInput.isBlank()) null
            else JwtDecoder.decode(tokenInput)
        }
    }

    if (activeWebViewUrl != null) {
        InAppAuthWebView(
            initialUrl = activeWebViewUrl!!,
            tokenRefreshUrl = selectedRegion.tokenRefreshUrl,
            onClose = { activeWebViewUrl = null },
            onTokenExtracted = { token ->
                tokenInput = token
                activeWebViewUrl = null
            },
            onCookiesCaptured = onCookiesCaptured
        )
        return
    }

    // Intercept hardware / gesture back button to navigate to previous onboarding step
    BackHandler(enabled = currentStep > 1) {
        currentStep--
    }

    fun openBrowser(url: String) {
        activeWebViewUrl = url
    }

    Scaffold(
        topBar = {
            // MD3 Expressive step indicator dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                (1..3).forEach { stepIndex ->
                    val isActive = stepIndex == currentStep
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(
                                width = if (isActive) 24.dp else 8.dp,
                                height = 8.dp
                            )
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button on steps 2 and 3: darker than the primary forward button
                    if (currentStep > 1) {
                        FilledTonalButton(
                            onClick = { currentStep-- },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Назад")
                        }
                    } else {
                        // Empty space occupying half width on Step 1 so forward button sits on bottom right
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Forward button on steps 1 and 2, Finish button on step 3
                    if (currentStep < 3) {
                        Button(
                            onClick = { currentStep++ },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Вперёд")
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                val token = tokenInput.trim()
                                if (token.isNotBlank()) {
                                    onLoginSuccess(selectedRegion, token)
                                }
                            },
                            enabled = tokenInput.isNotBlank() && jwtValidation?.isSuccess == true,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Завершить")
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut()
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            label = "onboarding_step_animation"
        ) { step ->
            when (step) {
                1 -> WelcomeStep()
                2 -> RegionStep(
                    selectedRegion = selectedRegion,
                    onRegionSelected = { selectedRegion = it }
                )
                3 -> AuthStep(
                    selectedRegion = selectedRegion,
                    tokenInput = tokenInput,
                    onTokenChanged = { tokenInput = it },
                    jwtValidation = jwtValidation,
                    onOpenBrowser = ::openBrowser,
                    context = context
                )
            }
        }
    }
}

/**
 * Screen 1: Welcome / Introduction
 * Centered app name and subtitle, with adaptive app icon.
 */
@Composable
private fun WelcomeStep() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(100.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "SchoolMD",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Добро пожаловать в FOSS клиент для МЭШ",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

/**
 * Screen 2: Region Selection
 * Header "Выберите регион", MD3 list of regions with URLs and single-selection checkmark.
 */
@Composable
private fun RegionStep(
    selectedRegion: Region,
    onRegionSelected: (Region) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Выберите регион",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Выберите систему электронного дневника для вашей школы",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(Region.entries) { region ->
                val isSelected = region == selectedRegion
                Surface(
                    onClick = { onRegionSelected(region) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = region.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Выбрано",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        supportingContent = {
                            Text(
                                text = region.apiHost,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

/**
 * Screen 3: Authorization & Token Retrieval
 * Gosuslugi login in browser, token generation link, and token paste field with validation.
 */
@Composable
private fun AuthStep(
    selectedRegion: Region,
    tokenInput: String,
    onTokenChanged: (String) -> Unit,
    jwtValidation: Result<JwtData>?,
    onOpenBrowser: (String) -> Unit,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Авторизация",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Войдите через Госуслуги на портале ${selectedRegion.title} (${selectedRegion.apiHost}), получите токен по ссылке и вставьте его в поле ниже.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Step 1 & 2 Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Инструкция по входу",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Step 1: Open Gosuslugi portal
                OutlinedButton(
                    onClick = { onOpenBrowser(selectedRegion.loginPortalUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("1. Войти через Госуслуги (${selectedRegion.apiHost})")
                }

                // Step 2: Open token refresh link
                Button(
                    onClick = { onOpenBrowser(selectedRegion.tokenUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("2. Получить токен")
                }

                Text(
                    text = "После входа на сайт нажмите «2. Получить токен», скопируйте открывшийся текст (начинается с eyJhbGci...) и вставьте его в поле ниже.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Step 3 Card: Paste and validate token
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "3. Вставьте токен",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = onTokenChanged,
                    label = { Text("JWT токен") },
                    placeholder = { Text("eyJhbGciOiJSUzI1NiJ9...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 4,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    onTokenChanged(clip.trim().removeSurrounding("\""))
                                }
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Вставить из буфера")
                        }
                    }
                )

                jwtValidation?.let { result ->
                    if (result.isSuccess) {
                        val jwt = result.getOrThrow()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (jwt.isExpired) "Токен истёк!" else "Токен валиден (осталось ${jwt.remainingMinutes} мин)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (jwt.isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Некорректный формат токена",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun InAppAuthWebView(
    initialUrl: String,
    tokenRefreshUrl: String,
    onClose: () -> Unit,
    onTokenExtracted: (String) -> Unit,
    onCookiesCaptured: (String) -> Unit
) {
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("Авторизация") }
    var detectedToken by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    BackHandler(enabled = true) {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onClose()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = pageTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = runCatching { Uri.parse(currentUrl).host ?: currentUrl }.getOrDefault(currentUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { webViewRef?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                }
                IconButton(onClick = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Открыть во внешнем браузере")
                }
                IconButton(onClick = {
                    webViewRef?.loadUrl(tokenRefreshUrl)
                }) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Получить токен",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Quick banner if token detected on the page
            AnimatedVisibility(visible = detectedToken != null) {
                detectedToken?.let { token ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Токен найден!",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Button(onClick = { onTokenExtracted(token) }) {
                                Text("Использовать токен")
                            }
                        }
                    }
                }
            }

            // In-app WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                            // Remove WebView signatures so Gosuslugi anti-bot doesn't block the request
                            val defaultUa = userAgentString
                            userAgentString = defaultUa
                                .replace("; wv", "")
                                .replace(Regex("Version/\\d+\\.\\d+ "), "")
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                if (!title.isNullOrBlank()) {
                                    pageTitle = title
                                }
                            }

                            // Route popups/OAuth redirects into the same WebView
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = view
                                resultMsg?.sendToTarget()
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            // Fallback safety net for Russian National CA (Минцифры) on .ru domains
                            @android.annotation.SuppressLint("WebViewClientOnReceivedSslError")
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                val failingUrl = error?.url.orEmpty()
                                val host = runCatching { Uri.parse(failingUrl).host.orEmpty() }.getOrDefault("")
                                if (host.endsWith(".ru") || host.endsWith(".рф") || host.endsWith(".su") ||
                                    host.contains("gosuslugi") || host.contains("mos.ru") || host.contains("mosreg")
                                ) {
                                    handler?.proceed()
                                } else {
                                    super.onReceivedSslError(view, handler, error)
                                }
                            }

                            // Handle custom schemes (intent://, esia://) gracefully
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    return false
                                }
                                runCatching {
                                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                    view?.context?.startActivity(intent)
                                }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (url != null) {
                                    currentUrl = url
                                    cookieManager.flush()
                                    val cookies = cookieManager.getCookie(url)
                                    if (!cookies.isNullOrBlank()) {
                                        onCookiesCaptured(cookies)
                                    }

                                    // Auto-check for token when navigating to refresh URL
                                    if (url.contains("/v2/token/refresh") || url.contains("token")) {
                                        view?.evaluateJavascript("(function() { return document.body.innerText; })();") { rawText ->
                                            val clean = rawText?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\n", "")
                                            if (!clean.isNullOrBlank()) {
                                                val token = extractTokenFromPage(clean)
                                                if (token != null) {
                                                    detectedToken = token
                                                    onTokenExtracted(token)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        webViewRef = this
                        loadUrl(initialUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            )
        }
    }
}

private fun extractTokenFromPage(text: String): String? {
    if (text.startsWith("eyJ") && text.length > 50) return text
    return runCatching {
        val json = JSONObject(text)
        when {
            json.has("token") -> json.getString("token")
            json.has("access_token") -> json.getString("access_token")
            json.has("data") && json.getJSONObject("data").has("token") ->
                json.getJSONObject("data").getString("token")
            else -> null
        }
    }.getOrNull() ?: if (text.contains("eyJ")) {
        Regex("eyJ[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+").find(text)?.value
    } else null
}
