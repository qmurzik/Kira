package app.pulse.client.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import app.pulse.client.BuildConfig
import app.pulse.client.ui.components.MessageBox
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.R
import app.pulse.client.ui.LocalAppContainer
import app.pulse.client.ui.components.LoadingBox

/**
 * Sign-in through the VK ID web page inside a WebView (same approach as the original client).
 * [addAccount] wipes WebView cookies first so a different VK account can be used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(addAccount: Boolean, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val container = LocalAppContainer.current
    val vm = viewModel { LoginViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    if (state is LoginViewModel.State.Done) {
        LaunchedEffect(Unit) {
            onDone()
            vm.backToForm()
        }
    }

    // Redirect from the system browser (vk<ID>://vk.com/blank.html?code=...).
    val redirect by container.authRedirect.collectAsStateWithLifecycle()
    LaunchedEffect(redirect) {
        redirect?.let {
            container.authRedirect.value = null
            vm.onBrowserRedirect(it)
        }
    }

    when (val s = state) {
        is LoginViewModel.State.Form -> LoginForm(
            s,
            browserAvailable = vm.browserLoginAvailable,
            onStart = { id, uri, browser -> vm.start(id, uri, browser) },
            onCancel = onCancel,
        )
        is LoginViewModel.State.Browser -> BrowserWait(s.request.url, onCancel = { vm.backToForm() })
        is LoginViewModel.State.Web -> {
            BackHandler { vm.backToForm() }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Вход через VK ID") },
                        navigationIcon = {
                            IconButton(onClick = { vm.backToForm() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                            }
                        },
                        actions = {
                            if (vm.browserLoginAvailable) {
                                TextButton(onClick = vm::switchToBrowser) { Text("В браузере") }
                            }
                        },
                    )
                }
            ) { padding ->
                AuthWebView(
                    url = s.request.url,
                    clearCookies = addAccount,
                    onNavigate = vm::onNavigate,
                    browserAvailable = vm.browserLoginAvailable,
                    onOpenBrowser = vm::switchToBrowser,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            }
        }
        LoginViewModel.State.Exchanging, LoginViewModel.State.Done -> LoadingBox()
    }
}

@Composable
private fun LoginForm(
    form: LoginViewModel.State.Form,
    browserAvailable: Boolean,
    onStart: (String, String, Boolean) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var clientId by rememberSaveable(form.clientId) { mutableStateOf(form.clientId) }
    var redirect by rememberSaveable(form.redirectUri) { mutableStateOf(form.redirectUri) }
    var advanced by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null)
            }
            Text("Пульс", style = MaterialTheme.typography.titleLarge)
            Text(
                "Неофициальный лёгкий клиент. Вход выполняется на странице VK ID, пароль приложению не передаётся.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = clientId,
                onValueChange = { v ->
                    clientId = v.filter { it.isDigit() }
                    if (!advanced) redirect = if (clientId.isEmpty()) "" else "vk$clientId://vk.com/blank.html"
                },
                label = { Text("ID вашего приложения VK ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (advanced) {
                OutlinedTextField(
                    value = redirect,
                    onValueChange = { redirect = it },
                    label = { Text("Доверенный Redirect URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(onClick = { advanced = !advanced }) {
                Text(if (advanced) "Скрыть дополнительно" else "Дополнительно")
            }
            form.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = { onStart(clientId, redirect, false) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Войти через VK ID")
            }
            if (browserAvailable) {
                OutlinedButton(onClick = { onStart(clientId, redirect, true) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Войти через браузер")
                }
            }
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text("Отмена") }
            }
            Text(
                "ID и Redirect URL берутся из кабинета VK ID (id.vk.ru → ваше приложение). " +
                    "Подробнее — в README проекта.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrowserWait(url: String, onCancel: () -> Unit) {
    val context = LocalContext.current
    val open = {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        }
        Unit
    }
    LaunchedEffect(url) { open() }
    BackHandler(onBack = onCancel)
    MessageBox(
        title = "Завершите вход в браузере",
        text = "После входа VK ID вернёт вас в приложение. Если браузер не открылся или вы его закрыли — откройте снова.",
        icon = Icons.Outlined.OpenInBrowser,
        actionLabel = "Открыть снова",
        onAction = open,
        modifier = Modifier.statusBarsPadding(),
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AuthWebView(
    url: String,
    clearCookies: Boolean,
    onNavigate: (String) -> Boolean,
    browserAvailable: Boolean,
    onOpenBrowser: () -> Unit,
    modifier: Modifier,
) {
    val loaded = remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                if (clearCookies) {
                    CookieManager.getInstance().removeAllCookies(null)
                }
                if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true // the VK ID page requires JS
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }

                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            if (BuildConfig.DEBUG) {
                                Log.w(WEB_TAG, "console ${message.messageLevel()}: ${message.message().take(300)} @${message.lineNumber()}")
                            }
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        private fun route(view: WebView, target: String): Boolean {
                            if (onNavigate(target)) return true
                            val scheme = Uri.parse(target).scheme.orEmpty()
                            if (scheme == "http" || scheme == "https") return false
                            // intent://, vkontakte:// etc. cannot load inside a WebView.
                            if (BuildConfig.DEBUG) Log.w(WEB_TAG, "non-web navigation: scheme=$scheme")
                            runCatching {
                                val intent = if (scheme == "intent") {
                                    Intent.parseUri(target, Intent.URI_INTENT_SCHEME)
                                } else {
                                    Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                }
                                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                                intent.component = null
                                intent.selector = null
                                view.context.startActivity(intent)
                            }
                            return true
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            route(view, request.url.toString())

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = route(view, url)

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            // Some WebView versions skip shouldOverrideUrlLoading for redirects.
                            if (onNavigate(url)) view.stopLoading()
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            if (!BuildConfig.DEBUG) return
                            Log.w(WEB_TAG, "finished host=${Uri.parse(url).host} path=${Uri.parse(url).path}")
                            view.evaluateJavascript(
                                "(function(){var b=document.body;return (b?b.innerText.length:-1)+'|'+(b?b.innerText.slice(0,400):'')+'|'+navigator.userAgent})()"
                            ) { Log.w(WEB_TAG, "page text: $it") }
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
                            if (BuildConfig.DEBUG) {
                                Log.w(WEB_TAG, "error ${err.errorCode} ${err.description} main=${request.isForMainFrame} host=${request.url.host}")
                            }
                            if (request.isForMainFrame) error = "${err.description} (${err.errorCode})"
                        }

                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (BuildConfig.DEBUG) {
                                Log.w(WEB_TAG, "http ${response.statusCode} main=${request.isForMainFrame} host=${request.url.host} path=${request.url.path}")
                            }
                            if (request.isForMainFrame) error = "Сервер VK ID ответил ${response.statusCode}"
                        }
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }
            },
            update = { web ->
                if (!loaded.value) {
                    loaded.value = true
                    web.loadUrl(url)
                }
            },
        )
        if (progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
        error?.let { e ->
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                MessageBox(
                    title = "Страница VK ID не загрузилась",
                    text = e + if (browserAvailable) "\nПопробуйте войти через браузер." else "",
                    actionLabel = if (browserAvailable) "Открыть в браузере" else null,
                    onAction = if (browserAvailable) onOpenBrowser else null,
                )
            }
        }
    }
}

private const val WEB_TAG = "PulseWeb"
