package app.pulse.client.data.mapper

import app.pulse.client.data.api.dto.AttachmentDto
import app.pulse.client.data.api.dto.CommentDto
import app.pulse.client.data.api.dto.ConversationItemDto
import app.pulse.client.data.api.dto.GroupDto
import app.pulse.client.data.api.dto.MessageDto
import app.pulse.client.data.api.dto.PhotoDto
import app.pulse.client.data.api.dto.PhotoSizeDto
import app.pulse.client.data.api.dto.PostDto
import app.pulse.client.data.api.dto.UserDto
import app.pulse.client.data.api.dto.VideoDto
import app.pulse.client.domain.model.Attachment
import app.pulse.client.domain.model.Comment
import app.pulse.client.domain.model.Conversation
import app.pulse.client.domain.model.Message
import app.pulse.client.domain.model.Owner
import app.pulse.client.domain.model.PhotoItem
import app.pulse.client.domain.model.PollAnswer
import app.pulse.client.domain.model.Post
import app.pulse.client.domain.model.Profile
import app.pulse.client.domain.model.VideoItem
import kotlinx.serialization.json.JsonPrimitive

/** Lookup of authors from the `profiles`/`groups` arrays that come with extended responses. */
class Owners(users: List<UserDto> = emptyList(), groups: List<GroupDto> = emptyList()) {
    private val map = HashMap<Long, Owner>(users.size + groups.size)

    init {
        users.forEach { map[it.id] = it.toOwner() }
        groups.forEach { map[-it.id] = it.toOwner() }
    }

    operator fun get(id: Long): Owner = map[id] ?: Owner.unknown(id)
    fun find(id: Long): Owner? = map[id]
    fun all(): Collection<Owner> = map.values
}

fun UserDto.fullName() = "$firstName $lastName".trim()

fun UserDto.toOwner() = Owner(
    id = id,
    name = fullName(),
    photo = photo100 ?: photo50 ?: photo200,
    online = online == 1,
    onlineMobile = onlineMobile == 1,
    verified = verified == 1,
    lastSeen = lastSeen?.time ?: 0,
)

fun GroupDto.toOwner() = Owner(
    id = -id,
    name = name,
    photo = photo100 ?: photo50 ?: photo200,
    verified = verified == 1,
)

private val SIZE_ORDER = listOf("s", "m", "o", "p", "q", "r", "x", "y", "z", "w")

/** Picks the smallest size that is at least [target] px wide, otherwise the largest. */
fun List<PhotoSizeDto>.best(target: Int): PhotoSizeDto? {
    if (isEmpty()) return null
    val withWidth = filter { it.width > 0 }
    if (withWidth.isNotEmpty()) {
        val sorted = withWidth.sortedBy { it.width }
        return sorted.firstOrNull { it.width >= target } ?: sorted.last()
    }
    return maxByOrNull { SIZE_ORDER.indexOf(it.type) }
}

private fun PhotoSizeDto.link(): String? = url ?: src

fun PhotoDto.toAttachment(): Attachment.Photo {
    val big = sizes.best(1080)
    val small = sizes.best(320)
    return Attachment.Photo(
        ownerId = ownerId,
        id = id,
        accessKey = accessKey,
        url = big?.link().orEmpty(),
        thumb = small?.link() ?: big?.link().orEmpty(),
        width = big?.width ?: 0,
        height = big?.height ?: 0,
    )
}

fun PhotoDto.toPhotoItem(): PhotoItem {
    val a = toAttachment()
    return PhotoItem(ownerId, id, a.url, a.thumb, a.width, a.height, date)
}

fun VideoDto.thumb(): String? = image.filter { it.width <= 800 }.maxByOrNull { it.width }?.url ?: image.lastOrNull()?.url

fun VideoDto.toAttachment() = Attachment.Video(
    ownerId = ownerId,
    id = id,
    accessKey = accessKey,
    title = title,
    duration = duration,
    thumb = thumb(),
    width = width,
    height = height,
    views = views,
)

private val QUALITY_KEYS = listOf(
    "mp4_2160" to "2160p", "mp4_1440" to "1440p", "mp4_1080" to "1080p", "mp4_720" to "720p",
    "mp4_480" to "480p", "mp4_360" to "360p", "mp4_240" to "240p", "mp4_144" to "144p",
)

fun VideoDto.toVideoItem(): VideoItem {
    val f = files
    val map = LinkedHashMap<String, String>()
    var external: String? = null
    if (f != null) {
        (f["hls"] as? JsonPrimitive)?.content?.let { map["Авто (HLS)"] = it }
        for ((key, label) in QUALITY_KEYS) {
            (f[key] as? JsonPrimitive)?.content?.let { map[label] = it }
        }
        external = (f["external"] as? JsonPrimitive)?.content
    }
    return VideoItem(
        ownerId = ownerId,
        id = id,
        accessKey = accessKey,
        title = title,
        description = description,
        duration = duration,
        thumb = thumb(),
        views = views,
        date = date,
        width = width,
        height = height,
        likes = likes?.count ?: 0,
        liked = likes?.userLikes == 1,
        comments = comments,
        isLive = live == 1,
        files = map,
        playerUrl = player,
        external = external,
    )
}

fun AttachmentDto.toDomain(owners: Owners): Attachment = when (type) {
    "photo" -> photo?.toAttachment()
    "video" -> video?.toAttachment()
    "doc" -> doc?.let { d ->
        Attachment.Doc(
            title = d.title,
            ext = d.ext,
            size = d.size,
            url = d.url,
            preview = d.preview?.photo?.sizes?.best(320)?.let { it.src ?: it.url },
        )
    }
    "link" -> link?.let { l ->
        Attachment.Link(l.url, l.title, l.caption, l.photo?.sizes?.best(600)?.let { it.url ?: it.src })
    }
    "sticker" -> sticker?.let { s ->
        Attachment.Sticker(s.stickerId, s.images.filter { it.width <= 256 }.maxByOrNull { it.width }?.url ?: s.images.lastOrNull()?.url)
    }
    "audio_message" -> audioMessage?.let { v ->
        Attachment.Voice(v.ownerId, v.id, v.duration, v.waveform, v.linkMp3 ?: v.linkOgg, v.transcript)
    }
    "audio" -> audio?.let { Attachment.Audio(it.artist, it.title, it.duration) }
    "poll" -> poll?.let { p -> Attachment.Poll(p.question, p.votes, p.answers.map { PollAnswer(it.text, it.votes, it.rate) }) }
    "wall" -> wall?.let { Attachment.WallPost(it.toPost(owners)) }
    else -> null
} ?: Attachment.Unknown(type)

fun PostDto.toPost(owners: Owners): Post {
    val owner = when {
        sourceId != 0L -> sourceId
        ownerId != 0L -> ownerId
        else -> toId
    }
    val author = if (fromId != 0L) fromId else owner
    val pid = if (postId != 0L) postId else id
    return Post(
        ownerId = owner,
        id = pid,
        author = owners[author],
        date = date,
        text = text,
        attachments = attachments.map { it.toDomain(owners) },
        copyHistory = copyHistory.map { it.toPost(owners) },
        likes = likes?.count ?: 0,
        liked = likes?.userLikes == 1,
        canLike = (likes?.canLike ?: 1) == 1,
        reposts = reposts?.count ?: 0,
        reposted = reposts?.userReposted == 1,
        comments = comments?.count ?: 0,
        canComment = comments?.canPost == 1,
        views = views?.count ?: 0,
        isAd = markedAsAds == 1,
        pinned = isPinned == 1,
    )
}

fun CommentDto.toComment(owners: Owners): Comment = Comment(
    id = id,
    author = owners[fromId],
    date = date,
    text = if (deleted) "Комментарий удалён" else text,
    attachments = attachments.map { it.toDomain(owners) },
    likes = likes?.count ?: 0,
    liked = likes?.userLikes == 1,
    threadCount = thread?.count ?: 0,
    thread = thread?.items.orEmpty().map { it.toComment(owners) },
    replyToName = if (replyToUser != 0L) owners.find(replyToUser)?.name else null,
)

fun MessageDto.toMessage(owners: Owners): Message = Message(
    id = id,
    cmid = cmid,
    peerId = peerId,
    fromId = fromId,
    date = date,
    text = text,
    out = out == 1,
    attachments = attachments.map { it.toDomain(owners) },
    forwarded = fwdMessages.map { it.toMessage(owners) },
    reply = replyMessage?.toMessage(owners),
    edited = updateTime > 0,
    action = action?.let { actionText(it.type, owners.find(fromId)?.name, it.text) },
    important = important,
)

private fun actionText(type: String, who: String?, text: String?): String {
    val name = who ?: "Участник"
    return when (type) {
        "chat_create" -> "$name создал(а) беседу «${text.orEmpty()}»"
        "chat_title_update" -> "$name изменил(а) название на «${text.orEmpty()}»"
        "chat_photo_update" -> "$name обновил(а) фото беседы"
        "chat_photo_remove" -> "$name удалил(а) фото беседы"
        "chat_invite_user", "chat_invite_user_by_link" -> "$name присоединился(ась) к беседе"
        "chat_kick_user" -> "$name покинул(а) беседу"
        "chat_pin_message" -> "$name закрепил(а) сообщение"
        "chat_unpin_message" -> "$name открепил(а) сообщение"
        else -> "Служебное сообщение"
    }
}

fun ConversationItemDto.toConversation(owners: Owners): Conversation {
    val c = conversation
    val peer = c.peer
    val (title, photo, owner) = when (peer.type) {
        "chat" -> Triple(c.chatSettings?.title ?: "Беседа", c.chatSettings?.photo?.let { it.photo100 ?: it.photo50 }, null)
        else -> owners[peer.id].let { Triple(it.name, it.photo, it) }
    }
    val msg = lastMessage?.toMessage(owners)
    val senderName = msg?.let {
        when {
            it.out -> "Вы"
            peer.type == "chat" -> owners.find(it.fromId)?.name?.substringBefore(' ')
            else -> null
        }
    }
    return Conversation(
        peerId = peer.id,
        type = peer.type,
        title = title,
        photo = photo,
        unreadCount = c.unreadCount,
        inRead = c.inRead,
        outRead = c.outRead,
        lastMessage = msg,
        lastSenderName = senderName,
        pinned = (c.sortId?.majorId ?: 0) > 0,
        important = c.important,
        markedUnread = c.isMarkedUnread,
        canWrite = c.canWrite?.allowed ?: true,
        membersCount = c.chatSettings?.membersCount ?: 0,
        online = owner?.online ?: false,
        onlineMobile = owner?.onlineMobile ?: false,
        sortMajor = c.sortId?.majorId ?: 0,
        sortMinor = c.sortId?.minorId ?: (msg?.id ?: 0),
    )
}

fun UserDto.toProfile() = Profile(
    owner = Owner(id, fullName(), photo200 ?: photo100, online == 1, onlineMobile == 1, verified == 1, lastSeen?.time ?: 0),
    status = status,
    city = city?.title,
    bdate = bdate,
    screenName = screenName,
    cover = photoMaxOrig,
    isClosed = isClosed,
    canAccess = canAccessClosed,
    friendStatus = friendStatus,
    canWrite = canWrite == 1,
    friends = counters?.friends,
    followers = counters?.followers,
    photos = counters?.photos,
    videos = counters?.videos,
    groups = counters?.groups,
    deactivated = deactivated,
)

fun GroupDto.toProfile() = Profile(
    owner = Owner(-id, name, photo200 ?: photo100, verified = verified == 1),
    status = status ?: activity,
    city = null,
    bdate = null,
    screenName = screenName,
    cover = null,
    isClosed = isClosed != 0,
    canAccess = isClosed == 0 || isMember == 1,
    friendStatus = 0,
    canWrite = true,
    friends = null,
    followers = membersCount,
    photos = null,
    videos = null,
    groups = null,
    description = description,
    membersCount = membersCount,
    isMember = isMember == 1,
)
