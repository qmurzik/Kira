package app.pulse.client.ui.messages

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shortcut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.core.util.Format
import app.pulse.client.domain.model.Message
import app.pulse.client.domain.model.Owner
import app.pulse.client.notifications.MessagesSyncWorker
import app.pulse.client.ui.LocalAppContainer
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AttachmentActions
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.LoadingRow
import app.pulse.client.ui.components.VoiceState
import app.pulse.client.ui.navigation.LocalNavigator
import app.pulse.client.ui.navigation.Routes
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Immutable
private sealed interface ChatRow {
    val key: String

    data class Msg(val m: Message, val showSender: Boolean) : ChatRow {
        override val key get() = "m${m.id}"
    }

    data class Day(val label: String, val date: Long) : ChatRow {
        override val key get() = "d$date"
    }
}

/** Newest first (the list is reverse-laid-out); a day header follows the oldest message of each day. */
private fun buildRows(list: List<Message>, isChat: Boolean): List<ChatRow> {
    val rows = ArrayList<ChatRow>(list.size + 8)
    for (i in list.indices) {
        val m = list[i]
        val older = list.getOrNull(i + 1)
        val showSender = isChat && !m.out && (older == null || older.fromId != m.fromId || older.action != null)
        rows += ChatRow.Msg(m, showSender)
        if (older == null || !Format.sameDay(older.date, m.date)) {
            rows += ChatRow.Day(Format.dayHeader(m.date), m.date)
        }
    }
    return rows
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(peerId: Long) {
    val container = LocalAppContainer.current
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val vm = viewModel(key = "chat_$peerId") {
        ChatViewModel(container.app, session, container.settings, peerId)
    }
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val owners by vm.owners.collectAsStateWithLifecycle()
    val typing by vm.typingNames.collectAsStateWithLifecycle()
    val composer by vm.composer.collectAsStateWithLifecycle()
    val loadingOlder by vm.loadingOlder.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val stickers by vm.stickers.collectAsStateWithLifecycle()
    val voice by vm.voiceState.collectAsStateWithLifecycle()

    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var panel by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Message?>(null) }
    var confirmDelete by remember { mutableStateOf<Message?>(null) }
    var attachMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.dismissError()
        }
    }
    LaunchedEffect(composer.editing) {
        composer.editing?.let { input = TextFieldValue(it.text, TextRange(it.text.length)) }
    }

    val isChat = conversation?.isChat ?: (peerId > 2_000_000_000L)
    val rows = remember(messages, isChat) { buildRows(messages, isChat) }
    val outRead = conversation?.outRead ?: 0L

    val actions = remember(nav) {
        AttachmentActions(
            onPhoto = nav::photo,
            onVideo = nav::video,
            onWall = { o, p -> nav.post(o, p) },
            onVoice = vm::toggleVoice,
        )
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let { vm.addAttachment(PendingAttachment(it, context.contentResolver.getType(it) ?: "image/jpeg", true, "photo.jpg")) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val name = context.contentResolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "file"
            vm.addAttachment(PendingAttachment(it, context.contentResolver.getType(it) ?: "application/octet-stream", false, name))
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
    }

    BackHandler(enabled = panel) { panel = false }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                title = {
                    val c = conversation
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = c != null && !c.isChat) { nav.profile(peerId) },
                    ) {
                        Avatar(c?.photo, c?.title ?: "", 38.dp, online = c?.online == true)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(c?.title ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = when {
                                typing.isNotEmpty() -> if (isChat) "${typing.joinToString()} печатает…" else "печатает…"
                                c == null -> ""
                                c.isChat -> Format.plural(c.membersCount, "участник", "участника", "участников")
                                c.isGroup -> "сообщество"
                                else -> {
                                    val o = owners[peerId]
                                    Format.lastSeen(c.online, o?.lastSeen ?: 0)
                                }
                            }
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (typing.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            val listState = rememberLazyListState()
            val nearTop by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 8
                }
            }
            LaunchedEffect(listState) {
                snapshotFlow { nearTop }.distinctUntilChanged().filter { it }.collect { vm.onScrolledNearTop(messages.size) }
            }
            // Keep the newest message visible when something new arrives and we are at the bottom.
            LaunchedEffect(messages.firstOrNull()?.id) {
                if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
            }

            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(rows, key = { it.key }, contentType = { if (it is ChatRow.Msg) "msg" else "day" }) { row ->
                    when (row) {
                        is ChatRow.Day -> DayHeader(row.label)
                        is ChatRow.Msg -> MessageBubble(
                            m = row.m,
                            sender = owners[row.m.fromId],
                            showSender = row.showSender,
                            read = row.m.id in 1..outRead,
                            selected = selected?.id == row.m.id,
                            owners = owners,
                            actions = actions,
                            voice = VoiceState(voice.key, voice.progress),
                            onClick = { if (row.m.state == Message.SendState.Failed) selected = row.m },
                            onLongClick = { selected = row.m },
                        )
                    }
                }
                if (loadingOlder) item(key = "older", contentType = "loading") { LoadingRow() }
            }

            if (conversation?.canWrite == false) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Вы не можете писать в этот диалог",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp).navigationBarsPadding(),
                    )
                }
            } else {
            ComposerContext(
                composer = composer,
                owners = owners,
                onCancel = { vm.cancelContext(); input = TextFieldValue("") },
                onRemove = vm::removeAttachment,
            )

            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).let {
                        if (panel) it else it.navigationBarsPadding()
                    },
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (composer.recording) {
                        IconButton(onClick = vm::cancelRecording) { Icon(Icons.Filled.Close, "Отменить запись") }
                        RecordingIndicator(Modifier.weight(1f))
                        IconButton(onClick = vm::stopRecordingAndSend) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Отправить голосовое", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                    Box {
                        IconButton(onClick = { attachMenu = true }) { Icon(Icons.Outlined.AttachFile, "Вложение") }
                        DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Фото") },
                                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                onClick = {
                                    attachMenu = false
                                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Файл") },
                                leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
                                onClick = {
                                    attachMenu = false
                                    pickFile.launch("*/*")
                                },
                            )
                        }
                    }
                    TextField(
                        value = input,
                        onValueChange = {
                            input = it
                            vm.onTextChanged(it.text)
                        },
                        placeholder = { Text("Сообщение") },
                        maxLines = 6,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { panel = !panel }) {
                        Icon(if (panel) Icons.Outlined.Keyboard else Icons.Outlined.EmojiEmotions, "Эмодзи и стикеры")
                    }
                    val canSend = input.text.isNotBlank() || composer.attachments.isNotEmpty() ||
                        composer.forward.isNotEmpty() || composer.editing != null
                    if (canSend) {
                        IconButton(onClick = { if (vm.send(input.text)) input = TextFieldValue("") }, enabled = !composer.sending) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                vm.startRecording()
                            } else {
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) { Icon(Icons.Filled.Mic, "Голосовое сообщение") }
                    }
                    }
                }
            }
            if (panel) {
                Box(Modifier.navigationBarsPadding()) {
                    EmojiStickerPanel(
                        stickers = stickers,
                        onEmoji = { e ->
                            val t = input.text
                            val pos = input.selection.start.coerceIn(0, t.length)
                            val next = t.substring(0, pos) + e + t.substring(pos)
                            input = TextFieldValue(next, TextRange(pos + e.length))
                        },
                        onSticker = { id ->
                            vm.sendSticker(id)
                            panel = false
                        },
                        onNeedStickers = vm::loadStickers,
                    )
                }
            }
            }
        }
    }

    selected?.let { m ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                fun act(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) = Triple(label, icon, onClick)
                val items = buildList {
                    if (m.state == Message.SendState.Failed) {
                        add(act("Повторить", Icons.Outlined.Refresh) { vm.retry(m) })
                    }
                    if (m.id > 0) {
                        add(act("Ответить", Icons.Filled.Reply) { vm.reply(m) })
                        add(act("Переслать", Icons.Outlined.Shortcut) {
                            vm.forward(listOf(m))
                            nav.open(Routes.FORWARD_PICK)
                        })
                    }
                    if (m.text.isNotEmpty()) {
                        add(act("Копировать", Icons.Outlined.ContentCopy) { clipboard.setText(AnnotatedString(m.text)) })
                    }
                    val fresh = System.currentTimeMillis() / 1000 - m.date < 24 * 3600
                    if (m.out && m.id > 0 && fresh) {
                        add(act("Редактировать", Icons.Outlined.Edit) { vm.startEdit(m) })
                    }
                    add(act("Удалить", Icons.Outlined.Delete) { confirmDelete = m })
                }
                items.forEach { (label, icon, onClick) ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = null
                            onClick()
                        }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, null)
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    confirmDelete?.let { m ->
        val canForAll = m.out && m.id > 0 && System.currentTimeMillis() / 1000 - m.date < 24 * 3600
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить сообщение?") },
            confirmButton = {
                Column {
                    if (canForAll) {
                        TextButton(onClick = { vm.delete(listOf(m), forAll = true); confirmDelete = null }) { Text("Удалить для всех") }
                    }
                    TextButton(onClick = { vm.delete(listOf(m), forAll = false); confirmDelete = null }) { Text("Удалить для себя") }
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun DayHeader(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun RecordingIndicator(modifier: Modifier) {
    Row(modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        Spacer(Modifier.width(8.dp))
        Text("Идёт запись…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ComposerContext(
    composer: Composer,
    owners: Map<Long, Owner>,
    onCancel: () -> Unit,
    onRemove: (PendingAttachment) -> Unit,
) {
    val label: String?
    val text: String?
    when {
        composer.editing != null -> {
            label = "Редактирование"
            text = composer.editing.text
        }
        composer.replyTo != null -> {
            label = "Ответ: " + (owners[composer.replyTo.fromId]?.name ?: "")
            text = MessagesSyncWorker.preview(composer.replyTo)
        }
        composer.forward.isNotEmpty() -> {
            label = "Пересылка"
            text = Format.plural(composer.forward.size, "сообщение", "сообщения", "сообщений")
        }
        else -> {
            label = null
            text = null
        }
    }
    if (label != null) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(2.dp).size(2.dp, 32.dp).background(MaterialTheme.colorScheme.primary))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(text.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Отменить") }
            }
        }
    }
    if (composer.attachments.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        ) {
            items(composer.attachments, key = { it.uri.toString() }) { a ->
                Box(Modifier.padding(end = 6.dp).size(64.dp)) {
                    if (a.isPhoto) {
                        AsyncImage(
                            model = a.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) { Text(a.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                    Box(
                        Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Color(0x99000000))
                            .clickable { onRemove(a) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, "Убрать", tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
        }
        if (composer.sending) LoadingRow()
    }
}
