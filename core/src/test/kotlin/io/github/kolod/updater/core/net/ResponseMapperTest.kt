package io.github.kolod.updater.core.net

import io.github.kolod.updater.core.status.FailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ResponseMapperTest {
    private fun headersFromFixture(): Map<String, String> =
        javaClass.getResource("/fixtures/rate-limit-headers.txt")!!.readText().lines()
            .drop(1)
            .filter { ": " in it }
            .associate { it.substringBefore(": ") to it.substringAfter(": ") }

    @Test
    fun successIsOkWithBody() {
        assertEquals(ApiOutcome.Ok("[]"), ResponseMapper.mapResponse(200, emptyMap(), "[]"))
    }

    @Test
    fun notFoundIsItsOwnOutcome() {
        assertEquals(ApiOutcome.NotFound, ResponseMapper.mapResponse(404, emptyMap(), ""))
    }

    @Test
    fun exhaustedLimitCarriesTheResetTime() {
        assertEquals(
            ApiOutcome.Failed(FailureReason.RateLimited(1795352754L)),
            ResponseMapper.mapResponse(403, headersFromFixture(), ""),
        )
    }

    @Test
    fun headerNamesAreCaseInsensitive() {
        val headers = mapOf("x-ratelimit-remaining" to "0", "X-RATELIMIT-RESET" to "123")
        assertEquals(
            ApiOutcome.Failed(FailureReason.RateLimited(123L)),
            ResponseMapper.mapResponse(403, headers, ""),
        )
    }

    @Test
    fun tooManyRequestsIsRateLimitedEvenWithoutHeaders() {
        assertEquals(
            ApiOutcome.Failed(FailureReason.RateLimited(null)),
            ResponseMapper.mapResponse(429, emptyMap(), ""),
        )
    }

    @Test
    fun forbiddenWithoutExhaustedLimitIsAServerError() {
        assertEquals(
            ApiOutcome.Failed(FailureReason.ServerError(403)),
            ResponseMapper.mapResponse(403, mapOf("X-RateLimit-Remaining" to "12"), ""),
        )
    }

    @Test
    fun otherStatusCodesAreServerErrors() {
        assertEquals(ApiOutcome.Failed(FailureReason.ServerError(500)), ResponseMapper.mapResponse(500, emptyMap(), ""))
        assertEquals(ApiOutcome.Failed(FailureReason.ServerError(418)), ResponseMapper.mapResponse(418, emptyMap(), ""))
    }

    @Test
    fun networkExceptionsMeanOffline() {
        listOf(UnknownHostException(), SocketTimeoutException(), ConnectException(), IOException("reset"))
            .forEach { assertEquals(FailureReason.Offline, ResponseMapper.mapException(it)) }
    }

    @Test
    fun outOfSpaceMeansStorageFull() {
        assertEquals(FailureReason.StorageFull, ResponseMapper.mapException(IOException("write failed: ENOSPC (No space left on device)")))
    }

    @Test
    fun nonIoExceptionsAreBadResponses() {
        assertEquals(FailureReason.BadResponse, ResponseMapper.mapException(IllegalStateException("x")))
    }

    @Test
    fun retriesOnlyConnectionFailuresAndServerErrorsAndOnlyTwice() {
        assertTrue(ResponseMapper.shouldRetry(0, FailureReason.Offline))
        assertTrue(ResponseMapper.shouldRetry(1, FailureReason.ServerError(503)))
        assertFalse(ResponseMapper.shouldRetry(2, FailureReason.Offline))
        assertFalse(ResponseMapper.shouldRetry(0, FailureReason.RateLimited(1L)))
        assertFalse(ResponseMapper.shouldRetry(0, FailureReason.ServerError(404)))
        assertFalse(ResponseMapper.shouldRetry(0, FailureReason.ServerError(403)))
        assertFalse(ResponseMapper.shouldRetry(0, FailureReason.BadResponse))
    }

    @Test
    fun backoffIsOneThenTwoSeconds() {
        assertEquals(1000L, ResponseMapper.backoffMillis(0))
        assertEquals(2000L, ResponseMapper.backoffMillis(1))
    }
}
