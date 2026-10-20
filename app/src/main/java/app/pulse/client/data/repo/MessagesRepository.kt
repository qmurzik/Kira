package app.pulse.client.data.repo

import app.pulse.client.data.api.VkApi
import app.pulse.client.data.api.dto.ConversationDto
import app.pulse.client.data.api.dto.ConversationItemDto
import app.pulse.client.data.api.dto.ConversationsResponse
import app.pulse.client.data.api.dto.HistoryResponse
import app.pulse.client.data.api.dto.ItemsResponse
import app.pulse.client.data.api.dto.MessageDto
import app.pulse.client.data.api.dto.PhotoDto
import app.pulse.client.data.api.dto.SavedDocDto
import app.pulse.client.data.api.dto.StickerProductDto
import app.pulse.client.data.api.dto.UploadServerDto
import app.pulse.client.data.db.AppDatabase
import app.pulse.client.data.db.ConversationEntity
import app.pulse.client.data.db.MessageEntity
import app.pulse.client.data.db.OwnerEntity
import app.pulse.client.data.mapper.Owners
import app.pulse.client.data.mapper.toConversation
import app.pulse.client.data.mapper.toMessage
import app.pulse.client.domain.model.Attachment
import app.pulse.client.domain.model.Conversation
import app.pulse.client.domain.model.Message
import app.pulse.client.domain.model.Owner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

const val USER_FIELDS = "photo_100,photo_50,online,online_mobile,last_seen,verified"

class MessagesRepository(
    private val api: VkApi,
    private val db: AppDatabase,
    private val json: Json,
    private val myId: Long,
) {
    private val conversationsDao = db.conversations()
    private val messagesDao = db.messages()
    private val ownersDao = db.owners()

    /** peerId -> user ids currently typing (with expiry handled by [LongPollClient]). */
    private val _typing = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())
    val typing: StateFlow<Map<Long, List<Long>>> = _typing.asStateFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    // ---------- conversations ----------

    fun observeConversations(): Flow<List<Conversation>> =
        conversationsDao.observeAll()
            .map { rows -> rows.mapNotNull { decodeConversation(it.data) } }
            .flowOn(Dispatchers.Default)

    fun observeConversation(peerId: Long): Flow<Conversation?> =
        conversationsDao.observe(peerId).map { it?.let { e -> decodeConversation(e.data) } }.flowOn(Dispatchers.Default)

    /** Loads a page of conversations. offset 0 replaces the cache (keeps it consistent with the server). */
    suspend fun loadConversations(offset: Int, count: Int = 40, filter: String = "all"): Int {
        val r = api.request<ConversationsResponse>(
            "messages.getConversations",
            mapOf("offset" to offset, "count" to count, "filter" to filter, "extended" to 1, "fields" to USER_FIELDS),
        )
        val owners = Owners(r.profiles, r.groups)
        saveOwners(owners)
        val items = r.items.map { it.toConversation(owners) }
        if (filter == "all") {
            _unread.value = r.unreadCount
            val entities = items.map { it.toEntity() }
            if (offset == 0) conversationsDao.replaceAll(entities) else conversationsDao.upsert(entities)
        }
        return items.size
    }

    /** Server-side filtered list (e.g. "unread"), not cached. */
    suspend fun loadFiltered(filter: String, offset: Int): List<Conversation> {
        val r = api.request<ConversationsResponse>(
            "messages.getConversations",
            mapOf("offset" to offset, "count" to 40, "filter" to filter, "extended" to 1, "fields" to USER_FIELDS),
        )
        val owners = Owners(r.profiles, r.groups)
        saveOwners(owners)
        return r.items.map { it.toConversation(owners) }
    }

    suspend fun searchConversations(query: String): List<Conversation> {
        // Returns bare conversation objects (no last_message).
        val r = api.request<ItemsResponse<ConversationDto>>(
            "messages.searchConversations",
            mapOf("q" to query, "count" to 30, "extended" to 1, "fields" to USER_FIELDS),
        )
        val owners = Owners(r.profiles, r.groups)
        saveOwners(owners)
        return r.items.map { ConversationItemDto(it, null).toConversation(owners) }
    }

    /** Refreshes the given conversations (after Long Poll events). */
    suspend fun refreshConversations(peerIds: Collection<Long>) {
        if (peerIds.isEmpty()) return
        val r = api.request<ItemsResponse<ConversationDto>>(
            "messages.getConversationsById",
            mapOf("peer_ids" to peerIds.joinToString(","), "extended" to 1, "fields" to USER_FIELDS),
        )
        val owners = Owners(r.profiles, r.groups)
        saveOwners(owners)
        val updated = r.items.map { dto ->
            val cached = conversationsDao.get(dto.peer.id)?.let { decodeConversation(it.data) }
            val last = messagesDao.latest(dto.peer.id)?.let { decodeMessage(it.data) } ?: cached?.lastMessage
            ConversationItemDto(dto, null).toConversation(owners).copy(
                lastMessage = last,
                lastSenderName = when {
                    last == null -> null
                    last.out -> "Вы"
                    dto.peer.type == "chat" -> (owners.find(last.fromId)?.name ?: ownerName(last.fromId))?.substringBefore(' ')
                    else -> null
                },
                sortMinor = maxOf(last?.id ?: 0, dto.sortId?.minorId ?: 0),
            )
        }
        conversationsDao.upsert(updated.map { it.toEntity() })
    }

    fun setUnread(count: Int) {
        _unread.value = count
    }

    fun setTyping(peerId: Long, users: List<Long>) {
        _typing.value = if (users.isEmpty()) _typing.value - peerId else _typing.value + (peerId to users)
    }

    suspend fun updateReadState(peerId: Long, inRead: Long? = null, outRead: Long? = null) {
        val row = conversationsDao.get(peerId) ?: return
        val c = decodeConversation(row.data) ?: return
        val next = c.copy(
            inRead = inRead ?: c.inRead,
            outRead = outRead ?: c.outRead,
            unreadCount = if (inRead != null && inRead >= (c.lastMessage?.id ?: 0)) 0 else c.unreadCount,
        )
        conversationsDao.upsert(listOf(next.toEntity()))
    }

    // ---------- history ----------

    fun observeMessages(peerId: Long, limit: Int): Flow<List<Message>> =
        messagesDao.observe(peerId, limit)
            .map { rows -> rows.mapNotNull { decodeMessage(it.data) } }
            .flowOn(Dispatchers.Default)

    /** Loads [count] messages older than what is cached. Returns number loaded (0 = end). */
    suspend fun loadHistory(peerId: Long, fromStart: Boolean, count: Int = 50): Int {
        val offset = if (fromStart) 0 else messagesDao.countSynced(peerId)
        val r = api.request<HistoryResponse>(
            "messages.getHistory",
            mapOf("peer_id" to peerId, "offset" to offset, "count" to count, "extended" to 1, "fields" to USER_FIELDS),
        )
        val owners = Owners(r.profiles, r.groups)
        saveOwners(owners)
        val items = r.items.map { it.toMessage(owners) }
        if (fromStart) messagesDao.clearSynced(peerId)
        messagesDao.upsert(items.map { it.toEntity() })
        r.conversations.firstOrNull()?.let { conv ->
            updateReadState(peerId, conv.inRead, conv.outRead)
        }
        return items.size
    }

    /** Fetches full objects for ids (used by Long Poll) and stores them. */
    suspend fun fetchByIds(ids: Collection<Long>): List<Message> {
        if (ids.isEmpty()) return emptyList()
        val r = api.request<ItemsResponse<MessageDto>>(
            "messages.getById",
            mapOf("message_ids" to ids.joinToString(","), "extended" to 1, "fields" to USER_FIELDS),
        )
        val owners = Owners(r.profiles, r.groups)
        saveOwners(owners)
        val items = r.items.map { it.toMessage(owners) }
        messagesDao.upsert(items.map { it.toEntity() })
        return items
    }

    suspend fun removeLocal(ids: Collection<Long>) {
        if (ids.isNotEmpty()) messagesDao.deleteAnyPeer(ids.toList())
    }

    // ---------- sending ----------

    /**
     * Optimistic send: a local row with a negative id appears immediately and is replaced by the
     * server copy (messages.getById) once messages.send returns.
     */
    suspend fun send(
        peerId: Long,
        text: String,
        replyTo: Message? = null,
        forward: List<Long> = emptyList(),
        attachments: List<String> = emptyList(),
        stickerId: Long? = null,
        localPreview: List<Attachment> = emptyList(),
    ): Long {
        val randomId = Random.nextInt(1, Int.MAX_VALUE)
        val temp = Message(
            id = -randomId.toLong(),
            cmid = 0,
            peerId = peerId,
            fromId = myId,
            date = System.currentTimeMillis() / 1000,
            text = text,
            out = true,
            attachments = localPreview,
            reply = replyTo,
            state = Message.SendState.Sending,
        )
        messagesDao.upsert(listOf(temp.toEntity()))
        try {
            val params = buildMap<String, Any?> {
                put("peer_id", peerId)
                put("random_id", randomId)
                if (text.isNotEmpty()) put("message", text)
                if (replyTo != null && replyTo.id > 0) put("reply_to", replyTo.id)
                if (forward.isNotEmpty()) put("forward_messages", forward.joinToString(","))
                if (attachments.isNotEmpty()) put("attachment", attachments.joinToString(","))
                if (stickerId != null) put("sticker_id", stickerId)
            }
            val result = api.call("messages.send", params)
            val newId = (result as? JsonPrimitive)?.longOrNull
                ?: ((result as? JsonObject)?.get("message_id") as? JsonPrimitive)?.longOrNull
                ?: 0L
            messagesDao.delete(peerId, listOf(temp.id))
            if (newId > 0) {
                val fetched = fetchByIds(listOf(newId))
                fetched.firstOrNull()?.let { touchConversation(it) }
            }
            return newId
        } catch (e: Exception) {
            messagesDao.upsert(listOf(temp.copy(state = Message.SendState.Failed).toEntity()))
            throw e
        }
    }

    suspend fun discardFailed(message: Message) {
        messagesDao.delete(message.peerId, listOf(message.id))
    }

    suspend fun edit(message: Message, text: String) {
        api.call(
            "messages.edit",
            mapOf(
                "peer_id" to message.peerId,
                "message_id" to message.id,
                "message" to text,
                "keep_forward_messages" to 1,
                "keep_snippets" to 1,
            ),
        )
        messagesDao.upsert(listOf(message.copy(text = text, edited = true).toEntity()))
    }

    suspend fun delete(peerId: Long, ids: List<Long>, forAll: Boolean) {
        api.call(
            "messages.delete",
            mapOf("message_ids" to ids.joinToString(","), "delete_for_all" to if (forAll) 1 else 0, "peer_id" to peerId),
        )
        messagesDao.delete(peerId, ids)
    }

    suspend fun markAsRead(peerId: Long, upToId: Long) {
        api.call("messages.markAsRead", mapOf("peer_id" to peerId, "start_message_id" to upToId))
        updateReadState(peerId, inRead = upToId)
    }

    suspend fun sendTyping(peerId: Long, voice: Boolean = false) {
        runCatching {
            api.call("messages.setActivity", mapOf("peer_id" to peerId, "type" to if (voice) "audiomessage" else "typing"))
        }
    }

    /** Called when a new message arrives or is sent: moves the dialog to the top. */
    suspend fun touchConversation(message: Message) {
        val row = conversationsDao.get(message.peerId)
        val c = row?.let { decodeConversation(it.data) }
        if (c == null) {
            refreshConversations(listOf(message.peerId))
            return
        }
        val next = c.copy(
            lastMessage = message,
            lastSenderName = if (message.out) "Вы" else if (c.isChat) ownerName(message.fromId)?.substringBefore(' ') else null,
            unreadCount = if (message.out) 0 else c.unreadCount + 1,
            sortMinor = maxOf(c.sortMinor, message.id),
        )
        conversationsDao.upsert(listOf(next.toEntity()))
    }

    // ---------- uploads ----------

    suspend fun uploadPhoto(peerId: Long, bytes: ByteArray, mime: String): String {
        val server = api.request<UploadServerDto>("photos.getMessagesUploadServer", mapOf("peer_id" to peerId))
        val up = api.upload(server.uploadUrl, "photo", "photo.jpg", mime, bytes)
        val saved = api.request<List<PhotoDto>>(
            "photos.saveMessagesPhoto",
            mapOf(
                "photo" to (up["photo"] as? JsonPrimitive)?.content,
                "server" to (up["server"] as? JsonPrimitive)?.content,
                "hash" to (up["hash"] as? JsonPrimitive)?.content,
            ),
        )
        val p = saved.first()
        return "photo${p.ownerId}_${p.id}" + (p.accessKey?.let { "_$it" } ?: "")
    }

    /** type = "audio_message" for voice, "doc" for files. */
    suspend fun uploadDoc(peerId: Long, bytes: ByteArray, fileName: String, mime: String, type: String): String {
        val server = api.request<UploadServerDto>(
            "docs.getMessagesUploadServer",
            mapOf("type" to type, "peer_id" to peerId),
        )
        val up = api.upload(server.uploadUrl, "file", fileName, mime, bytes)
        val file = (up["file"] as? JsonPrimitive)?.content ?: error("Сервер не вернул file")
        val saved = api.request<SavedDocDto>("docs.save", mapOf("file" to file, "title" to fileName))
        val (owner, id, key) = saved.audioMessage?.let { Triple(it.ownerId, it.id, it.accessKey) }
            ?: saved.doc?.let { Triple(it.ownerId, it.id, it.accessKey) }
            ?: error("docs.save: пустой ответ")
        return "doc${owner}_$id" + (key?.let { "_$it" } ?: "")
    }

    /** Sticker packs the user owns. May be closed for third-party apps: caller shows a stub. */
    suspend fun stickerPacks(): List<StickerProductDto> {
        val r = api.request<ItemsResponse<StickerProductDto>>(
            "store.getProducts",
            mapOf("type" to "stickers", "filters" to "active", "extended" to 1),
        )
        return r.items
    }

    // ---------- owners ----------

    private val ownerCache = ConcurrentHashMap<Long, Owner>()

    suspend fun owners(ids: Collection<Long>): Map<Long, Owner> {
        val missing = ids.filter { !ownerCache.containsKey(it) }
        if (missing.isNotEmpty()) {
            ownersDao.get(missing).forEach { e ->
                runCatching { json.decodeFromString(Owner.serializer(), e.data) }.getOrNull()?.let { ownerCache[it.id] = it }
            }
        }
        return ids.associateWith { ownerCache[it] ?: Owner.unknown(it) }
    }

    fun ownerName(id: Long): String? = ownerCache[id]?.name

    private suspend fun saveOwners(owners: Owners) {
        val all = owners.all()
        if (all.isEmpty()) return
        all.forEach { ownerCache[it.id] = it }
        ownersDao.upsert(all.map { OwnerEntity(it.id, json.encodeToString(Owner.serializer(), it)) })
    }

    // ---------- json rows ----------

    private fun Conversation.toEntity() =
        ConversationEntity(peerId, sortMajor, sortMinor, json.encodeToString(Conversation.serializer(), this))

    private fun Message.toEntity() =
        MessageEntity(peerId, id, cmid, date, json.encodeToString(Message.serializer(), this))

    private fun decodeConversation(data: String): Conversation? =
        runCatching { json.decodeFromString(Conversation.serializer(), data) }.getOrNull()

    private fun decodeMessage(data: String): Message? =
        runCatching { json.decodeFromString(Message.serializer(), data) }.getOrNull()
}
