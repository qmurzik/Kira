package app.pulse.client.data.repo

import app.pulse.client.data.api.VkApi
import app.pulse.client.data.api.dto.CommentDto
import app.pulse.client.data.api.dto.ItemsResponse
import app.pulse.client.data.api.dto.VideoDto
import app.pulse.client.data.mapper.Owners
import app.pulse.client.data.mapper.toComment
import app.pulse.client.data.mapper.toVideoItem
import app.pulse.client.domain.model.Comment
import app.pulse.client.domain.model.VideoItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

class VideoRepository(private val api: VkApi, private val myId: Long) {

    suspend fun my(offset: Int, count: Int = 30): Pair<List<VideoItem>, Int> {
        val r = api.request<ItemsResponse<VideoDto>>(
            "video.get",
            mapOf("owner_id" to myId, "offset" to offset, "count" to count, "extended" to 1),
        )
        return r.items.map { it.toVideoItem() }.distinctBy { it.apiId } to r.count
    }

    suspend fun search(q: String, offset: Int, count: Int = 30): List<VideoItem> {
        val r = api.request<ItemsResponse<VideoDto>>(
            "video.search",
            mapOf("q" to q, "sort" to 2, "adult" to 0, "offset" to offset, "count" to count, "extended" to 1),
        )
        return r.items.map { it.toVideoItem() }.distinctBy { it.apiId }
    }

    /**
     * Videos posted by the user's subscriptions: newsfeed.get with filters=video.
     * Each item is {type:"video", video:{count, items:[...]}}.
     */
    suspend fun fromSubscriptions(startFrom: String?): Pair<List<VideoItem>, String?> {
        val root = api.call(
            "newsfeed.get",
            mapOf("filters" to "video", "count" to 30, "start_from" to startFrom),
        ) as? JsonObject ?: return emptyList<VideoItem>() to null
        val items = (root["items"] as? JsonArray).orEmpty()
        val videos = items.flatMap { item ->
            val list = ((item as? JsonObject)?.get("video") as? JsonObject)?.get("items") as? JsonArray
            list.orEmpty().mapNotNull { v ->
                runCatching { api.json.decodeFromJsonElement(VideoDto.serializer(), v).toVideoItem() }.getOrNull()
            }
        }
        val next = (root["next_from"] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }
        return videos.distinctBy { it.apiId } to next
    }

    /** Full object with `files`/`player` for playback. */
    suspend fun get(apiId: String): VideoItem {
        val r = api.request<ItemsResponse<VideoDto>>("video.get", mapOf("videos" to apiId, "extended" to 1))
        return r.items.firstOrNull()?.toVideoItem() ?: error("Видео недоступно")
    }

    /** Batch variant: one video.get call for up to 200 ids. */
    suspend fun getMany(apiIds: List<String>): List<VideoItem> {
        if (apiIds.isEmpty()) return emptyList()
        val r = api.request<ItemsResponse<VideoDto>>(
            "video.get",
            mapOf("videos" to apiIds.take(200).joinToString(","), "extended" to 1),
        )
        return r.items.map { it.toVideoItem() }
    }

    suspend fun toggleLike(v: VideoItem): VideoItem {
        val liked = !v.liked
        val r = api.call(
            if (liked) "likes.add" else "likes.delete",
            mapOf("type" to "video", "owner_id" to v.ownerId, "item_id" to v.id, "access_key" to v.accessKey),
        )
        val count = ((r as? JsonObject)?.get("likes") as? JsonPrimitive)?.intOrNull
            ?: (v.likes + if (liked) 1 else -1).coerceAtLeast(0)
        return v.copy(liked = liked, likes = count)
    }

    suspend fun comments(v: VideoItem, offset: Int): Pair<List<Comment>, Int> {
        val r = api.request<ItemsResponse<CommentDto>>(
            "video.getComments",
            mapOf(
                "owner_id" to v.ownerId,
                "video_id" to v.id,
                "offset" to offset,
                "count" to 50,
                "need_likes" to 1,
                "extended" to 1,
                "sort" to "asc",
                "fields" to OWNER_FIELDS,
            ),
        )
        val owners = Owners(r.profiles, r.groups)
        return r.items.map { it.toComment(owners) } to r.count
    }

    suspend fun addComment(v: VideoItem, text: String) {
        api.call("video.createComment", mapOf("owner_id" to v.ownerId, "video_id" to v.id, "message" to text))
    }
}
