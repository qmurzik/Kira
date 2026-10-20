package app.pulse.client.data.repo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import androidx.room.withTransaction
import app.pulse.client.data.api.VkApi
import app.pulse.client.data.api.dto.CommentDto
import app.pulse.client.data.api.dto.ItemsResponse
import app.pulse.client.data.api.dto.PhotoDto
import app.pulse.client.data.api.dto.PostDto
import app.pulse.client.data.api.dto.UploadServerDto
import app.pulse.client.data.db.AppDatabase
import app.pulse.client.data.db.FeedEntity
import app.pulse.client.data.db.FeedKeyEntity
import app.pulse.client.data.mapper.Owners
import app.pulse.client.data.mapper.best
import app.pulse.client.data.mapper.toComment
import app.pulse.client.data.mapper.toPost
import app.pulse.client.data.api.dto.GroupDto
import app.pulse.client.data.api.dto.UserDto
import app.pulse.client.domain.model.Comment
import app.pulse.client.domain.model.Owner
import app.pulse.client.domain.model.Post
import app.pulse.client.domain.model.Story
import app.pulse.client.domain.model.StoryGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

enum class FeedKind(val id: String) { News("news"), Recommended("recommended") }

/** Row identity for LazyColumn keys: positions are unique per feed, post keys may repeat. */
data class FeedItem(val position: Int, val post: Post)

const val OWNER_FIELDS = "photo_100,photo_50,verified,online"

class FeedRepository(
    private val api: VkApi,
    private val db: AppDatabase,
    private val json: Json,
) {
    private val dao = db.feed()

    @OptIn(ExperimentalPagingApi::class)
    fun pager(kind: FeedKind): Flow<PagingData<FeedItem>> = Pager(
        config = PagingConfig(pageSize = 20, prefetchDistance = 8, initialLoadSize = 20, enablePlaceholders = false),
        remoteMediator = Mediator(kind),
        pagingSourceFactory = { dao.paging(kind.id) },
    ).flow.map { data ->
        data.map { e -> FeedItem(e.position, decode(e.data) ?: Post(0, 0, Owner.unknown(0), 0, "")) }
    }

    private data class Page(val posts: List<Post>, val nextFrom: String?)

    private suspend fun fetch(kind: FeedKind, startFrom: String?): Page {
        val method = if (kind == FeedKind.News) "newsfeed.get" else "newsfeed.getRecommended"
        val params = buildMap<String, Any?> {
            put("count", 20)
            put("start_from", startFrom)
            put("fields", OWNER_FIELDS)
            if (kind == FeedKind.News) put("filters", "post")
        }
        val r = api.request<ItemsResponse<PostDto>>(method, params)
        val owners = Owners(r.profiles, r.groups)
        val posts = r.items
            .filter { it.type == null || it.type == "post" }
            .map { it.toPost(owners) }
            .filter { it.id != 0L }
        return Page(posts, r.nextFrom)
    }

    @OptIn(ExperimentalPagingApi::class)
    private inner class Mediator(private val kind: FeedKind) : RemoteMediator<Int, FeedEntity>() {

        override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

        override suspend fun load(loadType: LoadType, state: PagingState<Int, FeedEntity>): MediatorResult {
            return try {
                val startFrom = when (loadType) {
                    LoadType.REFRESH -> null
                    LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                    LoadType.APPEND -> {
                        val key = dao.key(kind.id)?.nextFrom
                        if (key.isNullOrEmpty()) return MediatorResult.Success(endOfPaginationReached = true)
                        key
                    }
                }
                val page = fetch(kind, startFrom)
                db.withTransaction {
                    if (loadType == LoadType.REFRESH) dao.clear(kind.id)
                    val base = (dao.maxPosition(kind.id) ?: -1) + 1
                    val fresh = page.posts.distinctBy { it.key }.filter { dao.find(kind.id, it.key) == null }
                    dao.insert(fresh.mapIndexed { i, p -> FeedEntity(kind.id, base + i, p.key, encode(p)) })
                    dao.setKey(FeedKeyEntity(kind.id, page.nextFrom))
                }
                MediatorResult.Success(endOfPaginationReached = page.nextFrom.isNullOrEmpty())
            } catch (e: Exception) {
                MediatorResult.Error(e)
            }
        }
    }

    // ---------- post actions ----------

    /** Optimistically flips the like in every cached copy, then calls likes.add/delete. */
    suspend fun toggleLike(post: Post): Post {
        val liked = !post.liked
        val optimistic = post.copy(liked = liked, likes = (post.likes + if (liked) 1 else -1).coerceAtLeast(0))
        updateCached(optimistic)
        return try {
            val r = api.call(
                if (liked) "likes.add" else "likes.delete",
                mapOf("type" to "post", "owner_id" to post.ownerId, "item_id" to post.id),
            )
            val count = ((r as? JsonObject)?.get("likes") as? JsonPrimitive)?.intOrNull ?: optimistic.likes
            optimistic.copy(likes = count).also { updateCached(it) }
        } catch (e: Exception) {
            updateCached(post)
            throw e
        }
    }

    suspend fun repost(post: Post, message: String?): Post {
        val r = api.call(
            "wall.repost",
            mapOf("object" to "wall${post.ownerId}_${post.id}", "message" to message?.takeIf { it.isNotBlank() }),
        )
        val count = ((r as? JsonObject)?.get("reposts_count") as? JsonPrimitive)?.intOrNull ?: (post.reposts + 1)
        return post.copy(reposted = true, reposts = count).also { updateCached(it) }
    }

    private suspend fun updateCached(post: Post) {
        val rows = dao.findAll(post.key)
        if (rows.isNotEmpty()) dao.insert(rows.map { it.copy(data = encode(post)) })
    }

    suspend fun getPost(ownerId: Long, postId: Long): Post {
        val r = api.request<ItemsResponse<PostDto>>(
            "wall.getById",
            mapOf("posts" to "${ownerId}_$postId", "extended" to 1, "fields" to OWNER_FIELDS),
        )
        val owners = Owners(r.profiles, r.groups)
        return r.items.firstOrNull()?.toPost(owners) ?: error("Запись недоступна")
    }

    suspend fun comments(ownerId: Long, postId: Long, offset: Int): Pair<List<Comment>, Int> {
        val r = api.request<ItemsResponse<CommentDto>>(
            "wall.getComments",
            mapOf(
                "owner_id" to ownerId,
                "post_id" to postId,
                "offset" to offset,
                "count" to 50,
                "sort" to "asc",
                "need_likes" to 1,
                "extended" to 1,
                "thread_items_count" to 3,
                "fields" to OWNER_FIELDS,
            ),
        )
        val owners = Owners(r.profiles, r.groups)
        return r.items.map { it.toComment(owners) } to r.count
    }

    suspend fun addComment(ownerId: Long, postId: Long, text: String, replyTo: Long?) {
        api.call(
            "wall.createComment",
            mapOf("owner_id" to ownerId, "post_id" to postId, "message" to text, "reply_to_comment" to replyTo),
        )
    }

    suspend fun likeComment(ownerId: Long, comment: Comment): Comment {
        val liked = !comment.liked
        api.call(
            if (liked) "likes.add" else "likes.delete",
            mapOf("type" to "comment", "owner_id" to ownerId, "item_id" to comment.id),
        )
        return comment.copy(liked = liked, likes = (comment.likes + if (liked) 1 else -1).coerceAtLeast(0))
    }

    // ---------- create post ----------

    suspend fun uploadWallPhoto(bytes: ByteArray, mime: String): String {
        val server = api.request<UploadServerDto>("photos.getWallUploadServer")
        val up = api.upload(server.uploadUrl, "photo", "photo.jpg", mime, bytes)
        val saved = api.request<List<PhotoDto>>(
            "photos.saveWallPhoto",
            mapOf(
                "photo" to (up["photo"] as? JsonPrimitive)?.content,
                "server" to (up["server"] as? JsonPrimitive)?.content,
                "hash" to (up["hash"] as? JsonPrimitive)?.content,
            ),
        )
        val p = saved.first()
        return "photo${p.ownerId}_${p.id}" + (p.accessKey?.let { "_$it" } ?: "")
    }

    suspend fun publish(ownerId: Long?, text: String, attachments: List<String>, friendsOnly: Boolean): Long {
        val r = api.call(
            "wall.post",
            mapOf(
                "owner_id" to ownerId,
                "message" to text.takeIf { it.isNotBlank() },
                "attachments" to attachments.takeIf { it.isNotEmpty() }?.joinToString(","),
                "friends_only" to if (friendsOnly) 1 else null,
            ),
        )
        return ((r as? JsonObject)?.get("post_id") as? JsonPrimitive)?.longOrNull ?: 0
    }

    // ---------- stories ----------

    /** stories.get; the response shape changed across versions, so it is parsed leniently. */
    suspend fun stories(): List<StoryGroup> {
        val root = api.call("stories.get", mapOf("extended" to 1, "fields" to OWNER_FIELDS)) as? JsonObject
            ?: return emptyList()
        val owners = Owners(
            root["profiles"]?.let { json.decodeFromJsonElement(ListSer.users, it) }.orEmpty(),
            root["groups"]?.let { json.decodeFromJsonElement(ListSer.groups, it) }.orEmpty(),
        )
        val items = (root["items"] as? JsonArray).orEmpty()
        val groups = ArrayList<StoryGroup>()
        for (block in items) {
            val list: List<JsonElement> = when (block) {
                is JsonArray -> block
                is JsonObject -> (block["stories"] as? JsonArray) ?: listOf(block)
                else -> emptyList()
            }
            val stories = list.mapNotNull { parseStory(it as? JsonObject ?: return@mapNotNull null, owners) }
            if (stories.isNotEmpty()) groups += StoryGroup(stories.first().owner, stories)
        }
        return groups
    }

    private fun parseStory(o: JsonObject, owners: Owners): Story? {
        if ((o["is_deleted"] as? JsonPrimitive)?.content == "true") return null
        if ((o["is_expired"] as? JsonPrimitive)?.content == "true") return null
        val ownerId = (o["owner_id"] as? JsonPrimitive)?.longOrNull ?: return null
        val id = (o["id"] as? JsonPrimitive)?.longOrNull ?: return null
        val type = (o["type"] as? JsonPrimitive)?.content
        val photo = o["photo"]?.let { runCatching { json.decodeFromJsonElement(PhotoDto.serializer(), it) }.getOrNull() }
        val video = o["video"] as? JsonObject
        val image = photo?.sizes?.best(1080)?.let { it.url ?: it.src }
            ?: (video?.get("image") as? JsonArray)?.lastOrNull()?.jsonObject?.get("url")?.let { (it as? JsonPrimitive)?.content }
        val files = video?.get("files") as? JsonObject
        val videoUrl = if (type == "video") {
            listOf("mp4_720", "mp4_480", "mp4_360", "mp4_240", "hls").firstNotNullOfOrNull { (files?.get(it) as? JsonPrimitive)?.content }
        } else {
            null
        }
        val seen = (o["seen"] as? JsonPrimitive)?.intOrNull == 1
        return Story(ownerId, id, owners[ownerId], (o["date"] as? JsonPrimitive)?.longOrNull ?: 0, image, videoUrl, seen)
    }

    suspend fun markStorySeen(story: Story) {
        runCatching {
            api.call("stories.markSeen", mapOf("owner_id" to story.ownerId, "story_id" to story.id))
        }
    }

    // ---------- search ----------

    suspend fun searchPosts(query: String, startFrom: String?): Pair<List<Post>, String?> {
        val r = api.request<ItemsResponse<PostDto>>(
            "newsfeed.search",
            mapOf("q" to query, "count" to 30, "start_from" to startFrom, "extended" to 1, "fields" to OWNER_FIELDS),
        )
        val owners = Owners(r.profiles, r.groups)
        return r.items.map { it.toPost(owners) }.distinctBy { it.key } to r.nextFrom
    }

    private fun encode(p: Post) = json.encodeToString(Post.serializer(), p)
    private fun decode(s: String): Post? = runCatching { json.decodeFromString(Post.serializer(), s) }.getOrNull()

    private object ListSer {
        val users = ListSerializer(UserDto.serializer())
        val groups = ListSerializer(GroupDto.serializer())
    }
}
