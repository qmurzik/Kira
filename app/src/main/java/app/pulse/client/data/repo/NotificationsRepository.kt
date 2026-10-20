package app.pulse.client.data.repo

import app.pulse.client.data.api.VkApi
import app.pulse.client.data.api.dto.GroupDto
import app.pulse.client.data.api.dto.UserDto
import app.pulse.client.data.mapper.Owners
import app.pulse.client.domain.model.AppNotification
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * notifications.get. The item format differs between API versions, so the parser only relies on
 * `type`, `date`, `feedback` (who) and `parent`/`feedback` text, and degrades gracefully.
 */
class NotificationsRepository(private val api: VkApi) {

    data class Page(val items: List<AppNotification>, val nextFrom: String?)

    suspend fun load(startFrom: String?): Page {
        val root = api.call(
            "notifications.get",
            mapOf("count" to 30, "start_from" to startFrom, "fields" to "photo_100"),
        ) as? JsonObject ?: return Page(emptyList(), null)
        val owners = Owners(
            root["profiles"]?.let { api.json.decodeFromJsonElement(ListSerializer(UserDto.serializer()), it) }.orEmpty(),
            root["groups"]?.let { api.json.decodeFromJsonElement(ListSerializer(GroupDto.serializer()), it) }.orEmpty(),
        )
        val lastViewed = (root["last_viewed"] as? JsonPrimitive)?.longOrNull ?: 0
        val items = (root["items"] as? JsonArray).orEmpty().mapIndexedNotNull { i, el ->
            val o = el as? JsonObject ?: return@mapIndexedNotNull null
            parse(o, owners, lastViewed, i)
        }
        return Page(items, (root["next_from"] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() })
    }

    suspend fun markViewed() {
        runCatching { api.call("notifications.markAsViewed") }
    }

    private fun parse(o: JsonObject, owners: Owners, lastViewed: Long, index: Int): AppNotification {
        val type = o.str("type") ?: "unknown"
        val date = (o["date"] as? JsonPrimitive)?.longOrNull ?: 0
        val feedback = o["feedback"]
        val actors = actorIds(feedback).distinct().take(3).map { owners[it] }
        val feedbackText = (feedback as? JsonObject)?.str("text")
        val parentText = (o["parent"] as? JsonObject)?.let { it.str("text") ?: (it["post"] as? JsonObject)?.str("text") }
        // Newer formats carry ready-made texts; prefer them when present.
        val header = o.str("header")?.replace(Regex("\\[[^|\\]]+\\|([^\\]]+)]"), "$1")
        val text = header ?: describe(type, feedbackText)
        val thumb = (o["icon_url"] as? JsonPrimitive)?.content
        return AppNotification(
            key = "${type}_${date}_$index",
            type = type,
            date = date,
            actors = actors,
            text = text,
            target = parentText?.take(140),
            thumb = thumb,
            unread = date > lastViewed,
        )
    }

    private fun actorIds(feedback: JsonElement?): List<Long> = when (feedback) {
        is JsonObject -> {
            val items = feedback["items"] as? JsonArray
            if (items != null) {
                items.mapNotNull { (it as? JsonObject)?.let { f -> f.long("from_id") ?: f.long("owner_id") } }
            } else {
                listOfNotNull(feedback.long("from_id") ?: feedback.long("owner_id"))
            }
        }
        is JsonArray -> feedback.mapNotNull { (it as? JsonObject)?.let { f -> f.long("from_id") ?: f.long("owner_id") } }
        else -> emptyList()
    }

    private fun describe(type: String, feedbackText: String?): String = when (type) {
        "follow" -> "подписался(ась) на вас"
        "friend_accepted" -> "принял(а) вашу заявку в друзья"
        "mention", "mention_comments" -> "упомянул(а) вас" + (feedbackText?.let { ": $it" } ?: "")
        "wall", "wall_publish" -> "написал(а) на вашей стене" + (feedbackText?.let { ": $it" } ?: "")
        "comment_post" -> "прокомментировал(а) вашу запись" + (feedbackText?.let { ": $it" } ?: "")
        "comment_photo" -> "прокомментировал(а) ваше фото" + (feedbackText?.let { ": $it" } ?: "")
        "comment_video" -> "прокомментировал(а) ваше видео" + (feedbackText?.let { ": $it" } ?: "")
        "reply_comment", "reply_comment_photo", "reply_comment_video" ->
            "ответил(а) на ваш комментарий" + (feedbackText?.let { ": $it" } ?: "")
        "reply_topic" -> "ответил(а) в обсуждении"
        "like_post" -> "оценил(а) вашу запись"
        "like_comment", "like_comment_photo", "like_comment_video" -> "оценил(а) ваш комментарий"
        "like_photo" -> "оценил(а) ваше фото"
        "like_video" -> "оценил(а) ваше видео"
        "copy_post" -> "поделился(ась) вашей записью"
        "copy_photo" -> "поделился(ась) вашим фото"
        "copy_video" -> "поделился(ась) вашим видео"
        "birthday" -> "день рождения"
        else -> "новое событие ($type)"
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
