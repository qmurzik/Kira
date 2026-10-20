package app.pulse.client.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.BuildConfig
import app.pulse.client.core.util.Format
import app.pulse.client.data.settings.AppSettings
import app.pulse.client.data.settings.ThemeMode
import app.pulse.client.di.AppContainer
import app.pulse.client.di.UserSession
import app.pulse.client.notifications.Notifier
import app.pulse.client.ui.LocalAppContainer
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.components.ListRow
import app.pulse.client.ui.components.SectionHeader
import app.pulse.client.ui.components.ThinDivider
import app.pulse.client.ui.components.openUrl
import app.pulse.client.ui.navigation.LocalNavigator
import app.pulse.client.ui.navigation.Routes
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(private val container: AppContainer, private val session: UserSession) : ViewModel() {
    val settings = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val cacheBytes = MutableStateFlow(-1L)
    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        measureCache()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun disablePin() {
        container.appLock.clearPin()
        update { it.copy(pinEnabled = false, biometricEnabled = false) }
    }

    private fun measureCache() {
        viewModelScope.launch {
            cacheBytes.value = withContext(Dispatchers.IO) {
                val app = container.app
                dirSize(app.cacheDir) + app.getDatabasePath("cache_${session.userId}.db").length()
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val app = container.app
                app.imageLoader.memoryCache?.clear()
                app.imageLoader.diskCache?.clear()
                app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                session.db.clearAllTables()
            }
            measureCache()
            _events.send("Кэш очищен")
        }
    }

    /** Exports non-secret settings (no PIN, no tokens). */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            try {
                val data = settings.value.copy(pinEnabled = false, biometricEnabled = false)
                val text = container.json.encodeToString(AppSettings.serializer(), data)
                withContext(Dispatchers.IO) {
                    container.app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                        ?: error("Нет доступа к файлу")
                }
                _events.send("Настройки сохранены")
            } catch (e: Exception) {
                _events.send("Не удалось экспортировать: ${e.message}")
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    container.app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("Нет доступа к файлу")
                }
                val imported = container.json.decodeFromString(AppSettings.serializer(), text)
                // Security settings are never taken from a file.
                container.settings.update { imported.copy(pinEnabled = it.pinEnabled, biometricEnabled = it.biometricEnabled) }
                _events.send("Настройки загружены")
            } catch (e: Exception) {
                _events.send("Файл не подходит: ${e.message}")
            }
        }
    }

    private fun dirSize(f: File): Long = if (f.isDirectory) f.listFiles()?.sumOf { dirSize(it) } ?: 0 else f.length()
}

@Composable
fun SettingsScreen() {
    val container = LocalAppContainer.current
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val vm = viewModel { SettingsViewModel(container, session) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val cache by vm.cacheBytes.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var themeDialog by remember { mutableStateOf(false) }
    var intervalDialog by remember { mutableStateOf(false) }
    var lockDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFrom)
    }

    Scaffold(topBar = { AppTopBar("Настройки") }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { SectionHeader("Внешний вид") }
            item {
                ListRow(
                    title = "Тема",
                    subtitle = when (s.theme) {
                        ThemeMode.System -> "Как в системе"
                        ThemeMode.Light -> "Светлая"
                        ThemeMode.Dark -> "Тёмная"
                    },
                    modifier = Modifier.clickable { themeDialog = true },
                )
            }
            item { FontScaleRow(s.fontScale) { v -> vm.update { it.copy(fontScale = v) } } }

            item { SectionHeader("Сообщения и уведомления") }
            item {
                SwitchRow("Уведомления о сообщениях", "Проверка в фоне, когда приложение закрыто", s.messageNotifications) { v ->
                    vm.update { it.copy(messageNotifications = v) }
                }
            }
            item {
                ListRow(
                    title = "Частота фоновой проверки",
                    subtitle = "Раз в ${s.syncIntervalMinutes} мин (минимум Android — 15 минут)",
                    modifier = Modifier.clickable { intervalDialog = true },
                )
            }
            item {
                SwitchRow("Скрывать текст в уведомлениях", null, s.hideNotificationText) { v ->
                    vm.update { it.copy(hideNotificationText = v) }
                }
            }
            item {
                ListRow(
                    title = "Системные настройки уведомлений",
                    subtitle = "Звук, вибрация, каналы",
                    modifier = Modifier.clickable {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        }
                        runCatching { context.startActivity(intent) }
                    },
                )
            }

            item { SectionHeader("Приватность") }
            item {
                SwitchRow("Отправлять «прочитано»", "Если выключить, собеседник не увидит, что вы прочитали", s.sendReadReceipts) { v ->
                    vm.update { it.copy(sendReadReceipts = v) }
                }
            }
            item {
                SwitchRow("Показывать «печатает»", null, s.sendTyping) { v -> vm.update { it.copy(sendTyping = v) } }
            }
            item {
                SwitchRow("Запретить снимки экрана", "И скрыть содержимое в списке приложений", s.secureScreen) { v ->
                    vm.update { it.copy(secureScreen = v) }
                }
            }
            item {
                ListRow(
                    title = "Приватность аккаунта VK",
                    subtitle = "Кто видит страницу, друзей, фото — меняется на сайте VK",
                    modifier = Modifier.clickable { openUrl(context, "https://vk.com/settings?act=privacy") },
                )
            }

            item { SectionHeader("Безопасность") }
            item {
                SwitchRow("PIN-код при входе", null, s.pinEnabled && container.appLock.hasPin()) { v ->
                    if (v) nav.open(Routes.PIN_SETUP) else vm.disablePin()
                }
            }
            if (s.pinEnabled && biometricsAvailable(context)) {
                item {
                    SwitchRow("Разблокировка биометрией", null, s.biometricEnabled) { v -> vm.update { it.copy(biometricEnabled = v) } }
                }
            }
            if (s.pinEnabled) {
                item {
                    ListRow(
                        title = "Блокировать через",
                        subtitle = lockLabel(s.lockTimeoutSeconds),
                        modifier = Modifier.clickable { lockDialog = true },
                    )
                }
            }

            item { SectionHeader("Медиа и трафик") }
            item {
                SwitchRow("Автовоспроизведение видео", "В клипах и историях", s.autoplayVideo) { v -> vm.update { it.copy(autoplayVideo = v) } }
            }
            item {
                SwitchRow("Экономия трафика", "Уменьшенные фото и 360p по умолчанию", s.dataSaver) { v -> vm.update { it.copy(dataSaver = v) } }
            }

            item { SectionHeader("Хранилище") }
            item {
                ListRow(
                    title = "Очистить кэш",
                    subtitle = if (cache < 0) "Подсчёт…" else "Занято: ${Format.fileSize(cache)} (картинки, лента, сообщения)",
                    modifier = Modifier.clickable(onClick = vm::clearCache),
                )
            }
            item {
                ListRow(
                    title = "Экспорт настроек",
                    subtitle = "В файл JSON (без PIN-кода и данных входа)",
                    modifier = Modifier.clickable { exportLauncher.launch("pulse-settings.json") },
                )
            }
            item {
                ListRow(
                    title = "Импорт настроек",
                    modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
            }

            item { SectionHeader("Аккаунт") }
            item { ListRow(title = "Аккаунты", subtitle = "Переключение, добавление, выход", modifier = Modifier.clickable { nav.open(Routes.ACCOUNTS) }) }

            item { ThinDivider() }
            item {
                Text(
                    "Пульс ${BuildConfig.VERSION_NAME} · неофициальный клиент, работает через официальный VK API " +
                        "(версия ${BuildConfig.VK_API_VERSION}). Что недоступно через API — в docs/API-COVERAGE.md.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (themeDialog) {
        ChoiceDialog(
            title = "Тема",
            options = listOf(ThemeMode.System to "Как в системе", ThemeMode.Light to "Светлая", ThemeMode.Dark to "Тёмная"),
            selected = s.theme,
            onSelect = { m -> vm.update { it.copy(theme = m) } },
            onDismiss = { themeDialog = false },
        )
    }
    if (intervalDialog) {
        ChoiceDialog(
            title = "Фоновая проверка",
            options = listOf(15 to "15 минут", 30 to "30 минут", 60 to "1 час", 180 to "3 часа"),
            selected = s.syncIntervalMinutes,
            onSelect = { m -> vm.update { it.copy(syncIntervalMinutes = m) } },
            onDismiss = { intervalDialog = false },
        )
    }
    if (lockDialog) {
        ChoiceDialog(
            title = "Блокировать через",
            options = listOf(0, 30, 60, 300, 1800).map { it to lockLabel(it) },
            selected = s.lockTimeoutSeconds,
            onSelect = { m -> vm.update { it.copy(lockTimeoutSeconds = m) } },
            onDismiss = { lockDialog = false },
        )
    }
    // Keep the channels' descriptions in sync after a theme/locale change (cheap, idempotent).
    LaunchedEffect(Unit) { Notifier.createChannels(context) }
}

private fun lockLabel(seconds: Int) = when (seconds) {
    0 -> "Сразу"
    in 1..59 -> "$seconds секунд"
    in 60..3599 -> "${seconds / 60} мин"
    else -> "${seconds / 3600} ч"
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(
        title = title,
        subtitle = subtitle,
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun FontScaleRow(value: Float, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Размер шрифта: ${(local * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = 0.85f..1.3f,
            steps = 8,
        )
        Text(
            "Так будет выглядеть текст сообщений и записей.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize * (local / value.coerceAtLeast(0.1f))),
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onSelect(value)
                            onDismiss()
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = {
                            onSelect(value)
                            onDismiss()
                        })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
