package app.pulse.client.ui.auth

import android.annotation.SuppressLint
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

    when (val s = state) {
        is LoginViewModel.State.Form -> LoginForm(s, onStart = vm::start, onCancel = onCancel)
        is LoginViewModel.State.Web -> {
            BackHandler { vm.backToForm() }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Вход через VK ID") },
                        navigationIcon = {
                            IconButton(onClick = vm::backToForm) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                            }
                        },
                    )
                }
            ) { padding ->
                AuthWebView(
                    url = s.request.url,
                    clearCookies = addAccount,
                    onNavigate = vm::onNavigate,
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
    onStart: (String, String) -> Unit,
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
            Button(onClick = { onStart(clientId, redirect) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Войти через VK ID")
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AuthWebView(
    url: String,
    clearCookies: Boolean,
    onNavigate: (String) -> Boolean,
    modifier: Modifier,
) {
    val loaded = remember { mutableStateOf(false) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            if (clearCookies) {
                CookieManager.getInstance().removeAllCookies(null)
            }
            WebView(ctx).apply {
                settings.javaScriptEnabled = true // the VK ID page requires JS
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        onNavigate(request.url.toString())

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = onNavigate(url)

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        // Some WebView versions skip shouldOverrideUrlLoading for redirects.
                        if (onNavigate(url)) view.stopLoading()
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
}
