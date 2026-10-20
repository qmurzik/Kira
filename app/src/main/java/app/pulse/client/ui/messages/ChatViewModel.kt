package app.pulse.client.ui.messages

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pulse.client.core.media.VoicePlayer
import app.pulse.client.core.media.VoiceRecorder
import app.pulse.client.data.api.dto.StickerProductDto
import app.pulse.client.data.api.userMessage
import app.pulse.client.data.settings.AppSettings
import app.pulse.client.data.settings.SettingsRepository
import app.pulse.client.di.UserSession
import app.pulse.client.domain.model.Attachment
import app.pulse.client.domain.model.Conversation
import app.pulse.client.domain.model.Message
import app.pulse.client.domain.model.Owner
import app.pulse.client.notifications.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class PendingAttachment(val uri: Uri, val mime: String, val isPhoto: Boolean, val name: String)

@Immutable
data class Composer(
    val replyTo: Message? = null,
    val editing: Message? = null,
    val forward: List<Message> = emptyList(),
    val attachments: List<PendingAttachment> = emptyList(),
    val sending: Boolean = false,
    val recording: Boolean = false,
)

sealed interface StickersState {
    data object Idle : StickersState
    data object Loading : StickersState
    data class Ready(val packs: List<StickerProductDto>) : StickersState
    data class Unavailable(val reason: String) : StickersState
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val app: Application,
    private val session: UserSession,
    private val settingsRepo: SettingsRepository,
    val peerId: Long,
) : ViewModel() {

    private val repo = session.messages
    private val limit = MutableStateFlow(60)

    val conversation: StateFlow<Conversation?> = repo.observeConversation(peerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<Message>> = limit
        .flatMapLatest { repo.observeMessages(peerId, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _owners = MutableStateFlow<Map<Long, Owner>>(emptyMap())
    val owners: StateFlow<Map<Long, Owner>> = _owners.asStateFlow()

    val typingNames: StateFlow<List<String>> = combine(repo.typing, _owners) { typing, owners ->
        typing[peerId].orEmpty().map { id -> owners[id]?.name?.substringBefore(' ') ?: "Кто-то" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _composer = MutableStateFlow(Composer())
    val composer: StateFlow<Composer> = _composer.asStateFlow()

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()
    private var endReached = false

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _stickers = MutableStateFlow<StickersState>(StickersState.Idle)
    val stickers: StateFlow<StickersState> = _stickers.asStateFlow()

    private val voicePlayer = VoicePlayer(app, viewModelScope)
    val voiceState: StateFlow<VoicePlayer.State> = voicePlayer.state
    private val recorder = VoiceRecorder(app)

    private var lastTypingSent = 0L
    private var lastReadSent = 0L

    init {
        // Take messages chosen for forwarding in another chat.
        val fwd = session.forwardBuffer.value
        if (fwd.isNotEmpty()) {
            _composer.value = _composer.value.copy(forward = fwd)
            session.forwardBuffer.value = emptyList()
        }
        Notifier.cancel(app, session.userId, peerId)
        viewModelScope.launch {
            try {
                repo.loadHistory(peerId, fromStart = true)
            } catch (e: Exception) {
                _error.value = e.userMessage()
            }
        }
        // Resolve sender names/avatars for group chats.
        viewModelScope.launch {
            messages.map { list -> list.map { it.fromId }.toSet() + list.flatMap { m -> m.forwarded.map { it.fromId } } }
                .collect { ids ->
                    val missing = ids.filter { it !in _owners.value }
                    if (missing.isNotEmpty()) _owners.value = _owners.value + repo.owners(missing)
                }
        }
        // Read receipts: mark incoming as read while the chat is open.
        viewModelScope.launch {
            combine(messages, conversation) { list, conv -> list to conv }.collect { (list, conv) ->
                val maxIncoming = list.filter { !it.out && it.id > 0 }.maxOfOrNull { it.id } ?: return@collect
                if (conv != null && maxIncoming > conv.inRead && maxIncoming > lastReadSent && settings.value.sendReadReceipts) {
                    lastReadSent = maxIncoming
                    runCatching { repo.markAsRead(peerId, maxIncoming) }
                }
            }
        }
    }

    fun loadOlder() {
        if (_loadingOlder.value || endReached) return
        viewModelScope.launch {
            _loadingOlder.value = true
            try {
                val n = repo.loadHistory(peerId, fromStart = false)
                if (n == 0) endReached = true
                limit.value = limit.value + n.coerceAtLeast(0)
            } catch (e: Exception) {
                _error.value = e.userMessage()
            } finally {
                _loadingOlder.value = false
            }
        }
    }

    /** Ensures the visible window covers what is already cached when scrolling up. */
    fun onScrolledNearTop(visibleCount: Int) {
        if (visibleCount >= limit.value - 10) {
            limit.value = limit.value + 50
            loadOlder()
        }
    }

    fun onTextChanged(text: String) {
        if (text.isEmpty() || !settings.value.sendTyping) return
        val now = System.currentTimeMillis()
        if (now - lastTypingSent > 5_000) {
            lastTypingSent = now
            viewModelScope.launch { repo.sendTyping(peerId) }
        }
    }

    fun reply(m: Message) {
        _composer.value = _composer.value.copy(replyTo = m, editing = null)
    }

    fun startEdit(m: Message) {
        _composer.value = _composer.value.copy(editing = m, replyTo = null)
    }

    fun cancelContext() {
        _composer.value = _composer.value.copy(replyTo = null, editing = null, forward = emptyList())
    }

    fun forward(messages: List<Message>) {
        session.forwardBuffer.value = messages
    }

    fun addAttachment(a: PendingAttachment) {
        _composer.value = _composer.value.copy(attachments = (_composer.value.attachments + a).take(10))
    }

    fun removeAttachment(a: PendingAttachment) {
        _composer.value = _composer.value.copy(attachments = _composer.value.attachments - a)
    }

    fun dismissError() {
        _error.value = null
    }

    /** Returns true if the input should be cleared. */
    fun send(text: String): Boolean {
        val c = _composer.value
        val trimmed = text.trim()
        if (c.sending) return false
        c.editing?.let { m ->
            if (trimmed.isEmpty() && m.attachments.isEmpty()) return false
            viewModelScope.launch {
                runCatching { repo.edit(m, trimmed) }.onFailure { _error.value = it.userMessage() }
            }
            _composer.value = c.copy(editing = null)
            return true
        }
        if (trimmed.isEmpty() && c.attachments.isEmpty() && c.forward.isEmpty()) return false
        _composer.value = c.copy(sending = c.attachments.isNotEmpty())
        viewModelScope.launch {
            try {
                val uploaded = c.attachments.map { upload(it) }
                _composer.value = _composer.value.copy(replyTo = null, forward = emptyList(), attachments = emptyList(), sending = false)
                repo.send(
                    peerId = peerId,
                    text = trimmed,
                    replyTo = c.replyTo,
                    forward = c.forward.map { it.id }.filter { it > 0 },
                    attachments = uploaded,
                    localPreview = c.attachments.filter { it.isPhoto }.map {
                        Attachment.Photo(0, 0, null, it.uri.toString(), it.uri.toString(), 0, 0)
                    },
                )
            } catch (e: Exception) {
                _composer.value = _composer.value.copy(sending = false)
                _error.value = e.userMessage()
            }
        }
        return true
    }

    private suspend fun upload(a: PendingAttachment): String {
        val bytes = withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(a.uri)?.use { it.readBytes() } ?: error("Файл недоступен")
        }
        return if (a.isPhoto) {
            repo.uploadPhoto(peerId, bytes, a.mime)
        } else {
            repo.uploadDoc(peerId, bytes, a.name, a.mime, "doc")
        }
    }

    fun sendSticker(id: Long) {
        viewModelScope.launch {
            runCatching { repo.send(peerId, "", stickerId = id) }.onFailure { _error.value = it.userMessage() }
        }
    }

    fun retry(m: Message) {
        viewModelScope.launch {
            repo.discardFailed(m)
            runCatching { repo.send(peerId, m.text, replyTo = m.reply) }.onFailure { _error.value = it.userMessage() }
        }
    }

    fun delete(list: List<Message>, forAll: Boolean) {
        viewModelScope.launch {
            val local = list.filter { it.id < 0 }
            local.forEach { repo.discardFailed(it) }
            val remote = list.filter { it.id > 0 }.map { it.id }
            if (remote.isNotEmpty()) {
                runCatching { repo.delete(peerId, remote, forAll) }.onFailure { _error.value = it.userMessage() }
            }
        }
    }

    fun loadStickers() {
        if (_stickers.value !is StickersState.Idle) return
        _stickers.value = StickersState.Loading
        viewModelScope.launch {
            _stickers.value = try {
                val packs = repo.stickerPacks().filter { it.stickers.isNotEmpty() }
                if (packs.isEmpty()) StickersState.Unavailable("API не вернул ни одного набора стикеров") else StickersState.Ready(packs)
            } catch (e: Exception) {
                StickersState.Unavailable(e.userMessage())
            }
        }
    }

    fun toggleVoice(v: Attachment.Voice) {
        val url = v.url ?: return
        voicePlayer.toggle("${v.ownerId}_${v.id}", url)
    }

    // ---- voice recording ----

    fun startRecording() {
        try {
            recorder.start()
            _composer.value = _composer.value.copy(recording = true)
            viewModelScope.launch { repo.sendTyping(peerId, voice = true) }
        } catch (e: Exception) {
            _error.value = "Не удалось начать запись: ${e.message}"
        }
    }

    fun cancelRecording() {
        recorder.cancel()
        _composer.value = _composer.value.copy(recording = false)
    }

    fun stopRecordingAndSend() {
        val result = recorder.stop()
        _composer.value = _composer.value.copy(recording = false)
        result ?: return
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) { result.file.readBytes() }
                val doc = repo.uploadDoc(peerId, bytes, result.file.name, result.mime, "audio_message")
                repo.send(peerId, "", attachments = listOf(doc))
            } catch (e: Exception) {
                _error.value = e.userMessage()
            } finally {
                result.file.delete()
            }
        }
    }

    override fun onCleared() {
        voicePlayer.release()
        recorder.cancel()
    }
}
