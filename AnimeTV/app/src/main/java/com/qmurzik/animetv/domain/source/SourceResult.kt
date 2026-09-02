package com.qmurzik.animetv.domain.source

/** Typed failure reasons, so the UI can show a precise, human message (never a stack trace). */
sealed class SourceError {
    data object NoConnectivity : SourceError()
    data object Timeout : SourceError()
    data object RateLimited : SourceError()
    data object NotFound : SourceError()
    data object Unsupported : SourceError()
    data class Http(val code: Int) : SourceError()
    data class Unknown(val message: String?) : SourceError()
}

/** Result type used by every provider call, deliberately distinct from Kotlin's Result so
 *  callers are forced to branch on a closed, UI-safe error type instead of an exception. */
sealed class SourceResult<out T> {
    data class Success<T>(val value: T) : SourceResult<T>()
    data class Failure(val error: SourceError, val sourceId: String) : SourceResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): SourceResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun getOrNull(): T? = (this as? Success)?.value
}

inline fun <T> sourceResultOf(sourceId: String, block: () -> T): SourceResult<T> = try {
    SourceResult.Success(block())
} catch (t: Throwable) {
    SourceResult.Failure(t.toSourceError(), sourceId)
}
