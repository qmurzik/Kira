package app.pulse.client

import app.pulse.client.data.api.dto.ConversationsResponse
import app.pulse.client.data.api.dto.PhotoSizeDto
import app.pulse.client.data.api.dto.VideoDto
import app.pulse.client.data.mapper.Owners
import app.pulse.client.data.mapper.best
import app.pulse.client.data.mapper.toConversation
import app.pulse.client.data.mapper.toVideoItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun bestPhotoSizePicksSmallestAboveTarget() {
        val sizes = listOf(
            PhotoSizeDto("s", "s", null, 75, 50),
            PhotoSizeDto("x", "x", null, 604, 400),
            PhotoSizeDto("z", "z", null, 1280, 853),
        )
        assertEquals("x", sizes.best(320)?.url)
        assertEquals("z", sizes.best(2000)?.url)
    }

    @Test
    fun videoQualitiesAreOrderedAndLabelled() {
        val v = json.decodeFromString(
            VideoDto.serializer(),
            """{"id":1,"owner_id":2,"title":"t","files":{"mp4_360":"a","mp4_720":"b","hls":"h","failover_host":"x"}}""",
        ).toVideoItem()
        assertEquals(listOf("Авто (HLS)", "720p", "360p"), v.files.keys.toList())
        assertEquals("2_1", v.apiId)
    }

    @Test
    fun conversationParsesPinnedAndSender() {
        val r = json.decodeFromString(
            ConversationsResponse.serializer(),
            """
            {"count":1,"unread_count":2,"items":[{"conversation":{"peer":{"id":2000000001,"type":"chat","local_id":1},
              "in_read":5,"out_read":5,"unread_count":2,"sort_id":{"major_id":16,"minor_id":10},
              "chat_settings":{"title":"Беседа","members_count":3}},
              "last_message":{"id":10,"date":1,"peer_id":2000000001,"from_id":7,"text":"привет","out":0}}],
             "profiles":[{"id":7,"first_name":"Анна","last_name":"К","online":1}]}
            """.trimIndent(),
        )
        val c = r.items.first().toConversation(Owners(r.profiles, r.groups))
        assertTrue(c.pinned)
        assertTrue(c.isChat)
        assertEquals("Беседа", c.title)
        assertEquals("Анна", c.lastSenderName)
        assertEquals(2, c.unreadCount)
    }
}
