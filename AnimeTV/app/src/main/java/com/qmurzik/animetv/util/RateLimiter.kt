package com.qmurzik.animetv.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Simple client-side pacing so we stay comfortably under a public API's published rate limit
 * (Jikan asks third-party clients to stay near ~3 requests/second) instead of hammering it and
 * getting 429s back. Not a full token bucket - just enforces a minimum gap between calls,
 * which is all a single-user TV client needs.
 */
class RateLimiter(private val minIntervalMs: Long) {
    private val mutex = Mutex()
    private var lastCallAt = 0L

    suspend fun <T> throttled(block: suspend () -> T): T {
        mutex.withLock {
            val wait = minIntervalMs - (System.currentTimeMillis() - lastCallAt)
            if (wait > 0) delay(wait)
            lastCallAt = System.currentTimeMillis()
        }
        return block()
    }
}
