package app.pulse.client.data.repo

import app.pulse.client.data.api.VkApi
import app.pulse.client.data.api.dto.LongPollServerDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * User Long Poll (messages.getLongPollServer, lp_version=3).
 * Runs only while the app is in the foreground; in the background new messages are picked up
 * by MessagesSyncWorker (no push: VK push registration is not available to third-party apps).
 */
class LongPollClient(
    private val api: VkApi,
    private val http: OkHttpClient,
    private val json: Json,
    private val repo: MessagesRepository,
) {
    private var job: Job? = null
    private val typingJobs = HashMap<Long, Job>()

    @Synchronized
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch { loop(scope) }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun loop(scope: CoroutineScope) {
        var server: LongPollServerDto? = null
        var backoff = 1_000L
        // Catch up with anything missed while we were stopped.
        runCatching { repo.loadConversations(0) }
        while (coroutineContext.isActive) {
            try {
                val s = server ?: api.request<LongPollServerDto>(
                    "messages.getLongPollServer",
                    mapOf("need_pts" to 1, "lp_version" to 3),
                ).also { server = it }
                val url = "https://${s.server}".toHttpUrl().newBuilder()
                    .addQueryParameter("act", "a_check")
                    .addQueryParameter("key", s.key)
                    .addQueryParameter("ts", s.ts.toString())
                    .addQueryParameter("wait", "25")
                    .addQueryParameter("mode", (2 or 8 or 64 or 128).toString())
                    .addQueryParameter("version", "3")
                    .build()
                val body = withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) throw IOException("Long Poll HTTP ${resp.code}")
                        resp.body?.string().orEmpty()
                    }
                }
                val obj = json.parseToJsonElement(body).jsonObject
                when (obj["failed"]?.jsonPrimitive?.intOrNull) {
                    null -> {
                        server = s.copy(ts = obj["ts"]?.jsonPrimitive?.longOrNull ?: s.ts)
                        obj["updates"]?.jsonArray?.let { handle(it, scope) }
                        backoff = 1_000L
                    }
                    1 -> server = s.copy(ts = obj["ts"]?.jsonPrimitive?.longOrNull ?: s.ts)
                    3 -> {
                        server = null
                        runCatching { repo.loadConversations(0) }
                    }
                    else -> server = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
                if (e !is IOException) server = null
            }
        }
    }

    private suspend fun handle(updates: JsonArray, scope: CoroutineScope) {
        val newIds = LinkedHashSet<Long>()
        val editedIds = LinkedHashSet<Long>()
        val deleted = HashSet<Long>()
        for (u in updates) {
            val a = (u as? JsonArray) ?: continue
            val code = a.int(0) ?: continue
            when (code) {
                4 -> a.long(1)?.let { newIds += it }
                5 -> a.long(1)?.let { editedIds += it }
                2 -> {
                    val flags = a.long(2) ?: 0L
                    // 128 = deleted, 131072 = deleted for everyone
                    if ((flags and 128L) != 0L || (flags and 131072L) != 0L) a.long(1)?.let { deleted += it }
                }
                3 -> a.long(1)?.let { editedIds += it } // flags reset (e.g. restored), refetch
                6 -> {
                    val peer = a.long(1) ?: continue
                    repo.updateReadState(peer, inRead = a.long(2))
                }
                7 -> {
                    val peer = a.long(1) ?: continue
                    repo.updateReadState(peer, outRead = a.long(2))
                }
                63, 64 -> {
                    val peer = a.long(1) ?: continue
                    val users = (a.getOrNull(2) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }.orEmpty()
                    showTyping(peer, users, scope)
                }
                80 -> a.int(1)?.let { repo.setUnread(it) }
            }
        }
        if (deleted.isNotEmpty()) repo.removeLocal(deleted)
        val toFetch = (newIds + editedIds) - deleted
        if (toFetch.isNotEmpty()) {
            val messages = repo.fetchByIds(toFetch)
            for (m in messages) {
                if (m.id in newIds) {
                    repo.setTyping(m.peerId, emptyList())
                    repo.touchConversation(m)
                }
            }
        }
    }

    private fun showTyping(peer: Long, users: List<Long>, scope: CoroutineScope) {
        repo.setTyping(peer, users)
        synchronized(typingJobs) {
            typingJobs.remove(peer)?.cancel()
            if (users.isNotEmpty()) {
                typingJobs[peer] = scope.launch {
                    delay(6_000)
                    repo.setTyping(peer, emptyList())
                }
            }
        }
    }

    private fun JsonArray.int(i: Int): Int? = (getOrNull(i) as? JsonPrimitive)?.intOrNull
    private fun JsonArray.long(i: Int): Long? = (getOrNull(i) as? JsonPrimitive)?.longOrNull
}
