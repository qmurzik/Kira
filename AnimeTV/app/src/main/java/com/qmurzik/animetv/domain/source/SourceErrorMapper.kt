package com.qmurzik.animetv.domain.source

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Maps arbitrary exceptions thrown by a provider implementation into a UI-safe [SourceError]. */
fun Throwable.toSourceError(): SourceError = when (this) {
    is UnknownHostException -> SourceError.NoConnectivity
    is SocketTimeoutException -> SourceError.Timeout
    is HttpException -> when (code()) {
        429 -> SourceError.RateLimited
        404 -> SourceError.NotFound
        else -> SourceError.Http(code())
    }
    is IOException -> SourceError.NoConnectivity
    is UnsupportedOperationException -> SourceError.Unsupported
    else -> SourceError.Unknown(message)
}
