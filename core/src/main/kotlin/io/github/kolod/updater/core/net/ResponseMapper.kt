package io.github.kolod.updater.core.net

import io.github.kolod.updater.core.status.FailureReason
import java.io.IOException

sealed interface ApiOutcome {
    data class Ok(val body: String) : ApiOutcome
    data object NotFound : ApiOutcome
    data class Failed(val reason: FailureReason) : ApiOutcome
}

/** Maps HTTP responses and exceptions to outcomes, and decides about retries (contracts/network-policy.md). */
object ResponseMapper {
    private const val MAX_RETRIES = 2

    fun mapResponse(code: Int, headers: Map<String, String>, body: String): ApiOutcome {
        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        return when {
            code in 200..299 -> ApiOutcome.Ok(body)
            code == 404 -> ApiOutcome.NotFound
            code == 429 || (code == 403 && header("X-RateLimit-Remaining")?.trim() == "0") ->
                ApiOutcome.Failed(FailureReason.RateLimited(header("X-RateLimit-Reset")?.trim()?.toLongOrNull()))
            else -> ApiOutcome.Failed(FailureReason.ServerError(code))
        }
    }

    fun mapException(e: Throwable): FailureReason = when {
        e is IOException && isOutOfSpace(e) -> FailureReason.StorageFull
        e is IOException -> FailureReason.Offline
        else -> FailureReason.BadResponse
    }

    private fun isOutOfSpace(e: IOException): Boolean {
        val message = e.message.orEmpty()
        return message.contains("ENOSPC", ignoreCase = true) ||
            message.contains("No space left", ignoreCase = true)
    }

    /** [failuresSoFar] counts failed attempts already made (0 before the first retry decision). */
    fun shouldRetry(failuresSoFar: Int, reason: FailureReason): Boolean {
        if (failuresSoFar >= MAX_RETRIES) return false
        return reason is FailureReason.Offline ||
            (reason is FailureReason.ServerError && reason.httpCode >= 500)
    }

    fun backoffMillis(failuresSoFar: Int): Long = 1000L shl failuresSoFar.coerceIn(0, 10)
}
