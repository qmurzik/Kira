package app.pulse.client.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Immutable UI/domain models. They are also @Serializable so the Room cache can
// store them as compact JSON rows without a separate entity per nested type.

@Immutable
@Serializable
data class Owner(
    val id: Long,
    val name: String,
    val photo: String? = null,
    val online: Boolean = false,
    val onlineMobile: Boolean = false,
    val verified: Boolean = false,
    val lastSeen: Long = 0,
) {
    val isGroup: Boolean get() = id < 0

    companion object {
        fun unknown(id: Long) = Owner(id, if (id < 0) "Сообщество" else "Пользователь")
    }
}

@Immutable
@Serializable
sealed interface Attachment {
    @Serializable
    @SerialName("photo")
    data class Photo(
        val ownerId: Long,
        val id: Long,
        val accessKey: String? = null,
        val url: String,
        val thumb: String,
        val width: Int,
        val height: Int,
    ) : Attachment

    @Serializable
    @SerialName("video")
    data class Video(
        val ownerId: Long,
        val id: Long,
        val accessKey: String? = null,
        val title: String,
        val duration: Int,
        val thumb: String?,
        val width: Int,
        val height: Int,
        val views: Int = 0,
    ) : Attachment {
        val apiId: String get() = "${ownerId}_$id" + (accessKey?.let { "_$it" } ?: "")
    }

    @Serializable
    @SerialName("doc")
    data class Doc(
        val title: String,
        val ext: String,
        val size: Long,
        val url: String?,
        val preview: String? = null,
    ) : Attachment

    @Serializable
    @SerialName("link")
    data class Link(
        val url: String,
        val title: String?,
        val caption: String?,
        val photo: String?,
    ) : Attachment

    @Serializable
    @SerialName("sticker")
    data class Sticker(val id: Long, val url: String?) : Attachment

    @Serializable
    @SerialName("voice")
    data class Voice(
        val ownerId: Long,
        val id: Long,
        val duration: Int,
        val waveform: List<Int>,
        val url: String?,
        val transcript: String? = null,
    ) : Attachment

    @Serializable
    @SerialName("audio")
    data class Audio(val artist: String, val title: String, val duration: Int) : Attachment

    @Serializable
    @SerialName("poll")
    data class Poll(val question: String, val votes: Int, val answers: List<PollAnswer>) : Attachment

    @Serializable
    @SerialName("wall")
    data class WallPost(val post: Post) : Attachment

    @Serializable
    @SerialName("unknown")
    data class Unknown(val type: String) : Attachment
}

@Immutable
@Serializable
data class PollAnswer(val text: String, val votes: Int, val rate: Double)

@Immutable
@Serializable
data class Message(
    val id: Long,
    val cmid: Long,
    val peerId: Long,
    val fromId: Long,
    val date: Long,
    val text: String,
    val out: Boolean,
    val attachments: List<Attachment> = emptyList(),
    val forwarded: List<Message> = emptyList(),
    val reply: Message? = null,
    val edited: Boolean = false,
    val action: String? = null,
    val important: Boolean = false,
    /** Local-only state for optimistic sending. */
    val state: SendState = SendState.Sent,
) {
    @Serializable
    enum class SendState { Sent, Sending, Failed }
}

@Immutable
@Serializable
data class Conversation(
    val peerId: Long,
    val type: String,
    val title: String,
    val photo: String?,
    val unreadCount: Int,
    val inRead: Long,
    val outRead: Long,
    val lastMessage: Message?,
    val lastSenderName: String? = null,
    val pinned: Boolean = false,
    val important: Boolean = false,
    val markedUnread: Boolean = false,
    val canWrite: Boolean = true,
    val membersCount: Int = 0,
    val online: Boolean = false,
    val onlineMobile: Boolean = false,
    val sortMajor: Long = 0,
    val sortMinor: Long = 0,
) {
    val isChat: Boolean get() = type == "chat"
    val isGroup: Boolean get() = type == "group"
}

@Immutable
@Serializable
data class Post(
    val ownerId: Long,
    val id: Long,
    val author: Owner,
    val date: Long,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
    val copyHistory: List<Post> = emptyList(),
    val likes: Int = 0,
    val liked: Boolean = false,
    val canLike: Boolean = true,
    val reposts: Int = 0,
    val reposted: Boolean = false,
    val comments: Int = 0,
    val canComment: Boolean = false,
    val views: Int = 0,
    val isAd: Boolean = false,
    val pinned: Boolean = false,
) {
    val key: String get() = "${ownerId}_$id"
}

@Immutable
@Serializable
data class Comment(
    val id: Long,
    val author: Owner,
    val date: Long,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
    val likes: Int = 0,
    val liked: Boolean = false,
    val threadCount: Int = 0,
    val thread: List<Comment> = emptyList(),
    val replyToName: String? = null,
)

@Immutable
data class Profile(
    val owner: Owner,
    val status: String?,
    val city: String?,
    val bdate: String?,
    val screenName: String?,
    val cover: String?,
    val isClosed: Boolean,
    val canAccess: Boolean,
    val friendStatus: Int,
    val canWrite: Boolean,
    val friends: Int?,
    val followers: Int?,
    val photos: Int?,
    val videos: Int?,
    val groups: Int?,
    val description: String? = null,
    val membersCount: Int? = null,
    val isMember: Boolean = false,
    val deactivated: String? = null,
)

@Immutable
data class VideoItem(
    val ownerId: Long,
    val id: Long,
    val accessKey: String?,
    val title: String,
    val description: String?,
    val duration: Int,
    val thumb: String?,
    val views: Int,
    val date: Long,
    val width: Int,
    val height: Int,
    val likes: Int,
    val liked: Boolean,
    val comments: Int,
    val isLive: Boolean,
    /** Quality label (e.g. "720p", "Авто (HLS)") -> direct URL. Empty if the API did not expose files. */
    val files: Map<String, String>,
    /** Embed player URL from the API, used when [files] is empty. */
    val playerUrl: String?,
    val external: String?,
) {
    val apiId: String get() = "${ownerId}_$id" + (accessKey?.let { "_$it" } ?: "")
    val isVertical: Boolean get() = height > width && width > 0
}

@Immutable
data class Story(
    val ownerId: Long,
    val id: Long,
    val owner: Owner,
    val date: Long,
    val image: String?,
    val videoUrl: String?,
    val seen: Boolean,
)

@Immutable
data class StoryGroup(val owner: Owner, val stories: List<Story>) {
    val allSeen: Boolean get() = stories.all { it.seen }
}

@Immutable
data class PhotoItem(val ownerId: Long, val id: Long, val url: String, val thumb: String, val width: Int, val height: Int, val date: Long)

@Immutable
data class AppNotification(
    val key: String,
    val type: String,
    val date: Long,
    val actors: List<Owner>,
    val text: String,
    val target: String?,
    val thumb: String?,
    val unread: Boolean,
)
