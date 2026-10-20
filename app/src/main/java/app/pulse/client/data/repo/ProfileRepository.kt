package app.pulse.client.data.repo

import app.pulse.client.data.api.VkApi
import app.pulse.client.data.api.dto.GroupDto
import app.pulse.client.data.api.dto.ItemsResponse
import app.pulse.client.data.api.dto.PhotoDto
import app.pulse.client.data.api.dto.PostDto
import app.pulse.client.data.api.dto.UserDto
import app.pulse.client.data.mapper.Owners
import app.pulse.client.data.mapper.toOwner
import app.pulse.client.data.mapper.toPhotoItem
import app.pulse.client.data.mapper.toPost
import app.pulse.client.data.mapper.toProfile
import app.pulse.client.domain.model.Owner
import app.pulse.client.domain.model.PhotoItem
import app.pulse.client.domain.model.Post
import app.pulse.client.domain.model.Profile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Calendar

/** Friend with birthday info, for the birthdays screen. */
data class BirthdayFriend(val owner: Owner, val day: Int, val month: Int, val year: Int?, val daysLeft: Int)

class ProfileRepository(private val api: VkApi, val myId: Long) {

    private val profileFields =
        "photo_200,photo_100,photo_max_orig,online,online_mobile,last_seen,status,bdate,city,screen_name," +
            "counters,verified,friend_status,can_write_private_message,is_closed,can_access_closed"

    private val listFields = "photo_100,online,online_mobile,last_seen,verified,city"

    suspend fun profile(id: Long): Profile = if (id >= 0) {
        api.request<List<UserDto>>("users.get", mapOf("user_ids" to id, "fields" to profileFields))
            .firstOrNull()?.toProfile() ?: error("Пользователь не найден")
    } else {
        // groups.getById returns {groups:[...]} in 5.199; older versions returned a bare array.
        val r = api.call(
            "groups.getById",
            mapOf("group_id" to -id, "fields" to "description,members_count,status,activity,verified,is_member,photo_200"),
        )
        val arr = (r as? JsonObject)?.get("groups") as? JsonArray ?: r as? JsonArray ?: JsonArray(emptyList())
        arr.firstOrNull()?.let { api.json.decodeFromJsonElement(GroupDto.serializer(), it).toProfile() }
            ?: error("Сообщество не найдено")
    }

    suspend fun wall(ownerId: Long, offset: Int, count: Int = 20): Pair<List<Post>, Int> {
        val r = api.request<ItemsResponse<PostDto>>(
            "wall.get",
            mapOf("owner_id" to ownerId, "offset" to offset, "count" to count, "extended" to 1, "fields" to OWNER_FIELDS),
        )
        val owners = Owners(r.profiles, r.groups)
        return r.items.map { it.toPost(owners) }.distinctBy { it.key } to r.count
    }

    suspend fun photos(ownerId: Long, offset: Int, count: Int = 60): Pair<List<PhotoItem>, Int> {
        val r = api.request<ItemsResponse<PhotoDto>>(
            "photos.getAll",
            mapOf("owner_id" to ownerId, "offset" to offset, "count" to count, "photo_sizes" to 1),
        )
        return r.items.map { it.toPhotoItem() } to r.count
    }

    suspend fun friends(userId: Long, offset: Int, count: Int = 200): Pair<List<Owner>, Int> {
        val r = api.request<ItemsResponse<UserDto>>(
            "friends.get",
            mapOf(
                "user_id" to userId.takeIf { it != myId },
                "order" to "hints",
                "offset" to offset,
                "count" to count,
                "fields" to listFields,
            ),
        )
        return r.items.map { it.toOwner() } to r.count
    }

    /** Incoming friend requests (ids), then resolved with users.get. */
    suspend fun requests(): List<Owner> {
        val r = api.call("friends.getRequests", mapOf("count" to 100, "need_viewed" to 1)) as? JsonObject
        val ids = (r?.get("items") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }.orEmpty()
        if (ids.isEmpty()) return emptyList()
        return users(ids)
    }

    suspend fun users(ids: List<Long>): List<Owner> =
        api.request<List<UserDto>>("users.get", mapOf("user_ids" to ids.joinToString(","), "fields" to listFields))
            .map { it.toOwner() }

    /** friends.add: accept a request or send one. Returns 1 sent, 2 accepted, 4 resent. */
    suspend fun addFriend(userId: Long): Int =
        (api.call("friends.add", mapOf("user_id" to userId)) as? JsonPrimitive)?.content?.toIntOrNull() ?: 0

    suspend fun deleteFriend(userId: Long) {
        api.call("friends.delete", mapOf("user_id" to userId))
    }

    suspend fun groups(userId: Long, offset: Int, count: Int = 100): Pair<List<Owner>, Int> {
        val r = api.request<ItemsResponse<GroupDto>>(
            "groups.get",
            mapOf(
                "user_id" to userId.takeIf { it != myId },
                "extended" to 1,
                "offset" to offset,
                "count" to count,
                "fields" to "members_count,verified,activity",
            ),
        )
        return r.items.map { it.toOwner() } to r.count
    }

    suspend fun joinGroup(groupId: Long) {
        api.call("groups.join", mapOf("group_id" to groupId))
    }

    suspend fun leaveGroup(groupId: Long) {
        api.call("groups.leave", mapOf("group_id" to groupId))
    }

    suspend fun searchPeople(q: String, offset: Int): List<Owner> {
        val r = api.request<ItemsResponse<UserDto>>(
            "users.search",
            mapOf("q" to q, "offset" to offset, "count" to 50, "fields" to listFields),
        )
        return r.items.map { it.toOwner() }
    }

    suspend fun searchGroups(q: String, offset: Int): List<Owner> {
        val r = api.request<ItemsResponse<GroupDto>>(
            "groups.search",
            mapOf("q" to q, "offset" to offset, "count" to 50),
        )
        return r.items.map { it.toOwner() }
    }

    /** Friends sorted by the nearest upcoming birthday (only those who show day and month). */
    suspend fun birthdays(): List<BirthdayFriend> {
        val r = api.request<ItemsResponse<UserDto>>(
            "friends.get",
            mapOf("fields" to "bdate,photo_100,online", "count" to 5000),
        )
        val today = Calendar.getInstance()
        return r.items.mapNotNull { u ->
            val parts = u.bdate?.split('.') ?: return@mapNotNull null
            val day = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val month = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val year = parts.getOrNull(2)?.toIntOrNull()
            val next = Calendar.getInstance().apply {
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 23)
                if (before(today) && !sameDay(this, today)) add(Calendar.YEAR, 1)
            }
            val days = ((next.timeInMillis - today.timeInMillis) / 86_400_000L).toInt().coerceAtLeast(0)
            BirthdayFriend(u.toOwner(), day, month, year, if (sameDay(next, today)) 0 else days)
        }.sortedBy { it.daysLeft }
    }

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
