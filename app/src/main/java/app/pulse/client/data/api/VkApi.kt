package app.pulse.client.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Supplies the current access token and refreshes it on VK API error 5. */
interface SessionAuth {
    fun token(): String?

    /** Called once per request when VK answers with error 5. Returns true if a new token is ready. */
    suspend fun refreshAfterExpired(failedToken: String): Boolean
}

class VkApiException(val code: Int, message: String) : Exception(message) {
    /** The method exists but this app/token is not allowed to use it. */
    val isAccessDenied: Boolean get() = code in ACCESS_CODES

    companion object {
        val ACCESS_CODES = setOf(3, 7, 15, 27, 28, 30, 203, 260, 917)
    }
}

/**
 * Thin client for https://api.vk.com/method/<name>.
 * - POST form-encoded (tokens never appear in URLs and thus not in proxies' logs);
 * - ~3 requests/second throttle (VK user-token limit);
 * - error 5 -> one token refresh via [SessionAuth] then retry;
 * - errors 6/9/10 -> short back-off and retry (max 3).
 */
class VkApi(
    private val http: OkHttpClient,
    val json: Json,
    private val host: String,
    private val version: String,
    private val auth: SessionAuth,
) {
    private val gate = Mutex()
    private var lastCallAt = 0L

    suspend fun call(method: String, params: Map<String, Any?> = emptyMap()): JsonElement {
        var attempt = 0
        var refreshed = false
        while (true) {
            val token = auth.token() ?: throw VkApiException(5, "Нет активной сессии")
            throttle()
            val root = withContext(Dispatchers.IO) { execute(method, params, token) }
            val error = root["error"] ?: return root["response"] ?: JsonNull
            val obj = error.jsonObject
            val code = obj["error_code"]?.jsonPrimitive?.intOrNull ?: -1
            val msg = obj["error_msg"]?.jsonPrimitive?.content ?: "Ошибка VK API"
            when {
                code == 5 && !refreshed -> {
                    refreshed = true
                    if (!auth.refreshAfterExpired(token)) throw VkApiException(5, msg)
                }
                (code == 6 || code == 9 || code == 10) && attempt < 3 -> {
                    attempt++
                    delay(400L * attempt)
                }
                else -> throw VkApiException(code, msg)
            }
        }
    }

    suspend inline fun <reified T> request(method: String, params: Map<String, Any?> = emptyMap()): T =
        json.decodeFromJsonElement(serializer<T>(), call(method, params))

    private suspend fun throttle() {
        gate.withLock {
            val wait = lastCallAt + MIN_INTERVAL_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastCallAt = System.currentTimeMillis()
        }
    }

    private fun execute(method: String, params: Map<String, Any?>, token: String): JsonObject {
        val form = FormBody.Builder()
        for ((k, v) in params) {
            val s = when (v) {
                null -> continue
                is Boolean -> if (v) "1" else "0"
                is Collection<*> -> v.joinToString(",")
                else -> v.toString()
            }
            form.add(k, s)
        }
        form.add("access_token", token)
        form.add("v", version)
        form.add("lang", "ru")
        val req = Request.Builder().url("https://$host/method/$method").post(form.build()).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw IOException("Пустой ответ")
            return json.parseToJsonElement(body).jsonObject
        }
    }

    /** Multipart upload to a VK upload server URL obtained from *.get*UploadServer. */
    suspend fun upload(url: String, field: String, fileName: String, mime: String, bytes: ByteArray): JsonObject =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(field, fileName, bytes.toRequestBody(mime.toMediaType()))
                .build()
            http.newCall(Request.Builder().url(url).post(body).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Загрузка: HTTP ${resp.code}")
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                obj["error"]?.let { throw IOException("Загрузка: ${it}") }
                obj
            }
        }

    private companion object {
        const val MIN_INTERVAL_MS = 340L
    }
}

/** Human-readable message for UI. Never contains request params (and thus tokens). */
fun Throwable.userMessage(): String = when (this) {
    is VkApiException -> when {
        isAccessDenied -> "Недоступно через API для этого приложения (код $code)"
        code == 5 -> "Сессия истекла, войдите снова"
        code == 14 -> "VK запросил капчу. Повторите позже"
        else -> "$message (код $code)"
    }
    is IOException -> "Нет соединения: ${message ?: "сеть недоступна"}"
    else -> message ?: "Неизвестная ошибка"
}
