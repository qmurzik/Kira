package app.pulse.client.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// VK API 5.199 objects. Only fields the client uses; unknown keys are ignored.
// Booleans arrive as 0/1 ints, so they are kept as Int here and mapped later.

@Serializable
data class ItemsResponse<T>(
    val count: Int = 0,
    val items: List<T> = emptyList(),
    val profiles: List<UserDto> = emptyList(),
    val groups: List<GroupDto> = emptyList(),
    @SerialName("next_from") val nextFrom: String? = null,
)

@Serializable
data class LastSeenDto(val time: Long = 0, val platform: Int = 0)

@Serializable
data class TitleDto(val id: Long = 0, val title: String = "")

@Serializable
data class CountersDto(
    val friends: Int? = null,
    val followers: Int? = null,
    val photos: Int? = null,
    val videos: Int? = null,
    val groups: Int? = null,
    val subscriptions: Int? = null,
)

@Serializable
data class UserDto(
    val id: Long = 0,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    @SerialName("photo_50") val photo50: String? = null,
    @SerialName("photo_100") val photo100: String? = null,
    @SerialName("photo_200") val photo200: String? = null,
    @SerialName("photo_max_orig") val photoMaxOrig: String? = null,
    val online: Int = 0,
    @SerialName("online_mobile") val onlineMobile: Int = 0,
    @SerialName("last_seen") val lastSeen: LastSeenDto? = null,
    val status: String? = null,
    val bdate: String? = null,
    val city: TitleDto? = null,
    @SerialName("screen_name") val screenName: String? = null,
    @SerialName("is_closed") val isClosed: Boolean = false,
    @SerialName("can_access_closed") val canAccessClosed: Boolean = true,
    val counters: CountersDto? = null,
    val verified: Int = 0,
    val deactivated: String? = null,
    @SerialName("friend_status") val friendStatus: Int = 0,
    @SerialName("can_write_private_message") val canWrite: Int = 0,
    @SerialName("mutual") val mutual: JsonObject? = null,
)

@Serializable
data class GroupDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("screen_name") val screenName: String? = null,
    @SerialName("photo_50") val photo50: String? = null,
    @SerialName("photo_100") val photo100: String? = null,
    @SerialName("photo_200") val photo200: String? = null,
    @SerialName("is_closed") val isClosed: Int = 0,
    val type: String? = null,
    @SerialName("members_count") val membersCount: Int? = null,
    val description: String? = null,
    val status: String? = null,
    val verified: Int = 0,
    @SerialName("is_member") val isMember: Int = 0,
    val activity: String? = null,
)

// ---- attachments ----

@Serializable
data class PhotoSizeDto(
    val type: String? = null,
    val url: String? = null,
    val src: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class PhotoDto(
    val id: Long = 0,
    @SerialName("owner_id") val ownerId: Long = 0,
    @SerialName("access_key") val accessKey: String? = null,
    @SerialName("album_id") val albumId: Long = 0,
    val sizes: List<PhotoSizeDto> = emptyList(),
    val text: String? = null,
    val date: Long = 0,
)

@Serializable
data class VideoImageDto(val url: String = "", val width: Int = 0, val height: Int = 0)

@Serializable
data class LikesDto(
    val count: Int = 0,
    @SerialName("user_likes") val userLikes: Int = 0,
    @SerialName("can_like") val canLike: Int = 1,
)

@Serializable
data class CountDto(
    val count: Int = 0,
    @SerialName("user_reposted") val userReposted: Int = 0,
    @SerialName("can_post") val canPost: Int = 0,
)

@Serializable
data class VideoDto(
    val id: Long = 0,
    @SerialName("owner_id") val ownerId: Long = 0,
    @SerialName("access_key") val accessKey: String? = null,
    val title: String = "",
    val description: String? = null,
    val duration: Int = 0,
    val image: List<VideoImageDto> = emptyList(),
    val views: Int = 0,
    val date: Long = 0,
    val player: String? = null,
    val files: JsonObject? = null,
    val width: Int = 0,
    val height: Int = 0,
    val likes: LikesDto? = null,
    val comments: Int = 0,
    val platform: String? = null,
    val live: Int = 0,
    val type: String? = null,
)

@Serializable
data class DocPreviewDto(val photo: DocPreviewPhotoDto? = null)

@Serializable
data class DocPreviewPhotoDto(val sizes: List<PhotoSizeDto> = emptyList())

@Serializable
data class DocDto(
    val id: Long = 0,
    @SerialName("owner_id") val ownerId: Long = 0,
    @SerialName("access_key") val accessKey: String? = null,
    val title: String = "",
    val size: Long = 0,
    val ext: String = "",
    val url: String? = null,
    val type: Int = 0,
    val preview: DocPreviewDto? = null,
)

@Serializable
data class AudioMessageDto(
    val id: Long = 0,
    @SerialName("owner_id") val ownerId: Long = 0,
    @SerialName("access_key") val accessKey: String? = null,
    val duration: Int = 0,
    val waveform: List<Int> = emptyList(),
    @SerialName("link_ogg") val linkOgg: String? = null,
    @SerialName("link_mp3") val linkMp3: String? = null,
    val transcript: String? = null,
)

@Serializable
data class LinkDto(
    val url: String = "",
    val title: String? = null,
    val caption: String? = null,
    val description: String? = null,
    val photo: PhotoDto? = null,
)

@Serializable
data class StickerDto(
    @SerialName("sticker_id") val stickerId: Long = 0,
    @SerialName("product_id") val productId: Long = 0,
    val images: List<VideoImageDto> = emptyList(),
    @SerialName("images_with_background") val imagesWithBackground: List<VideoImageDto> = emptyList(),
)

@Serializable
data class PollAnswerDto(val id: Long = 0, val text: String = "", val votes: Int = 0, val rate: Double = 0.0)

@Serializable
data class PollDto(
    val id: Long = 0,
    val question: String = "",
    val votes: Int = 0,
    val answers: List<PollAnswerDto> = emptyList(),
)

@Serializable
data class AudioDto(
    val id: Long = 0,
    @SerialName("owner_id") val ownerId: Long = 0,
    val artist: String = "",
    val title: String = "",
    val duration: Int = 0,
)

@Serializable
data class AttachmentDto(
    val type: String = "",
    val photo: PhotoDto? = null,
    val video: VideoDto? = null,
    val doc: DocDto? = null,
    val link: LinkDto? = null,
    val sticker: StickerDto? = null,
    @SerialName("audio_message") val audioMessage: AudioMessageDto? = null,
    val audio: AudioDto? = null,
    val poll: PollDto? = null,
    val wall: PostDto? = null,
)

// ---- wall / newsfeed ----

@Serializable
data class ViewsDto(val count: Int = 0)

@Serializable
data class PostDto(
    val id: Long = 0,
    @SerialName("post_id") val postId: Long = 0,
    @SerialName("owner_id") val ownerId: Long = 0,
    @SerialName("source_id") val sourceId: Long = 0,
    @SerialName("from_id") val fromId: Long = 0,
    @SerialName("to_id") val toId: Long = 0,
    val type: String? = null,
    val date: Long = 0,
    val text: String = "",
    val attachments: List<AttachmentDto> = emptyList(),
    @SerialName("copy_history") val copyHistory: List<PostDto> = emptyList(),
    val likes: LikesDto? = null,
    val reposts: CountDto? = null,
    val comments: CountDto? = null,
    val views: ViewsDto? = null,
    @SerialName("marked_as_ads") val markedAsAds: Int = 0,
    @SerialName("is_pinned") val isPinned: Int = 0,
)

@Serializable
data class CommentThreadDto(
    val count: Int = 0,
    val items: List<CommentDto> = emptyList(),
)

@Serializable
data class CommentDto(
    val id: Long = 0,
    @SerialName("from_id") val fromId: Long = 0,
    val date: Long = 0,
    val text: String = "",
    val attachments: List<AttachmentDto> = emptyList(),
    val likes: LikesDto? = null,
    val thread: CommentThreadDto? = null,
    @SerialName("reply_to_user") val replyToUser: Long = 0,
    @SerialName("reply_to_comment") val replyToComment: Long = 0,
    val deleted: Boolean = false,
)

// ---- messages ----

@Serializable
data class ActionDto(
    val type: String = "",
    @SerialName("member_id") val memberId: Long = 0,
    val text: String? = null,
)

@Serializable
data class MessageDto(
    val id: Long = 0,
    val date: Long = 0,
    @SerialName("peer_id") val peerId: Long = 0,
    @SerialName("from_id") val fromId: Long = 0,
    val text: String = "",
    val out: Int = 0,
    val attachments: List<AttachmentDto> = emptyList(),
    @SerialName("fwd_messages") val fwdMessages: List<MessageDto> = emptyList(),
    @SerialName("reply_message") val replyMessage: MessageDto? = null,
    @SerialName("conversation_message_id") val cmid: Long = 0,
    @SerialName("update_time") val updateTime: Long = 0,
    val important: Boolean = false,
    val action: ActionDto? = null,
)

@Serializable
data class PeerDto(val id: Long = 0, val type: String = "user", @SerialName("local_id") val localId: Long = 0)

@Serializable
data class SortIdDto(@SerialName("major_id") val majorId: Long = 0, @SerialName("minor_id") val minorId: Long = 0)

@Serializable
data class ChatPhotoDto(
    @SerialName("photo_50") val photo50: String? = null,
    @SerialName("photo_100") val photo100: String? = null,
    @SerialName("photo_200") val photo200: String? = null,
)

@Serializable
data class ChatSettingsDto(
    val title: String = "",
    val photo: ChatPhotoDto? = null,
    @SerialName("members_count") val membersCount: Int = 0,
)

@Serializable
data class CanWriteDto(val allowed: Boolean = true, val reason: Int = 0)

@Serializable
data class ConversationDto(
    val peer: PeerDto = PeerDto(),
    @SerialName("in_read") val inRead: Long = 0,
    @SerialName("out_read") val outRead: Long = 0,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("is_marked_unread") val isMarkedUnread: Boolean = false,
    val important: Boolean = false,
    @SerialName("sort_id") val sortId: SortIdDto? = null,
    @SerialName("chat_settings") val chatSettings: ChatSettingsDto? = null,
    @SerialName("can_write") val canWrite: CanWriteDto? = null,
)

@Serializable
data class ConversationItemDto(
    val conversation: ConversationDto = ConversationDto(),
    @SerialName("last_message") val lastMessage: MessageDto? = null,
)

@Serializable
data class ConversationsResponse(
    val count: Int = 0,
    @SerialName("unread_count") val unreadCount: Int = 0,
    val items: List<ConversationItemDto> = emptyList(),
    val profiles: List<UserDto> = emptyList(),
    val groups: List<GroupDto> = emptyList(),
)

@Serializable
data class HistoryResponse(
    val count: Int = 0,
    val items: List<MessageDto> = emptyList(),
    val profiles: List<UserDto> = emptyList(),
    val groups: List<GroupDto> = emptyList(),
    val conversations: List<ConversationDto> = emptyList(),
)

@Serializable
data class LongPollServerDto(
    val key: String = "",
    val server: String = "",
    val ts: Long = 0,
    val pts: Long = 0,
)

@Serializable
data class UploadServerDto(@SerialName("upload_url") val uploadUrl: String = "")

@Serializable
data class SavedDocDto(
    val type: String = "",
    @SerialName("audio_message") val audioMessage: AudioMessageDto? = null,
    val doc: DocDto? = null,
)

@Serializable
data class StickerProductDto(
    val id: Long = 0,
    val title: String? = null,
    val stickers: List<StickerDto> = emptyList(),
)
