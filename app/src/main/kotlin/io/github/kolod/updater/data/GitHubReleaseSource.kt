package io.github.kolod.updater.data

import io.github.kolod.updater.FetchResult
import io.github.kolod.updater.ReleaseSource
import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.net.ApiOutcome
import io.github.kolod.updater.core.net.ResponseMapper
import io.github.kolod.updater.core.release.ParseResult
import io.github.kolod.updater.core.release.parseReleases
import io.github.kolod.updater.core.release.selectRelease
import io.github.kolod.updater.core.status.FailureReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/** One unauthenticated request per app (contracts/github-releases.md), with bounded retries. */
class GitHubReleaseSource : ReleaseSource {

    override suspend fun fetch(app: ManagedApp): FetchResult {
        var failures = 0
        while (true) {
            val result = attempt(app)
            if (result is FetchResult.Failure && ResponseMapper.shouldRetry(failures, result.reason)) {
                delay(ResponseMapper.backoffMillis(failures))
                failures++
                continue
            }
            return result
        }
    }

    private suspend fun attempt(app: ManagedApp): FetchResult = withContext(Dispatchers.IO) {
        try {
            runInterruptible { request(app) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FetchResult.Failure(ResponseMapper.mapException(e))
        }
    }

    private fun request(app: ManagedApp): FetchResult {
        val connection = Http.open(
            startUrl = app.releasesApiUrl,
            accept = "application/vnd.github+json",
            readTimeoutMs = 20_000,
            extraHeaders = mapOf("X-GitHub-Api-Version" to "2022-11-28"),
        )
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return when (val outcome = ResponseMapper.mapResponse(code, Http.headersOf(connection), body)) {
                is ApiOutcome.Ok -> when (val parsed = parseReleases(outcome.body, app)) {
                    is ParseResult.Ok -> FetchResult.Success(selectRelease(parsed.releases))
                    ParseResult.BadResponse -> FetchResult.Failure(FailureReason.BadResponse)
                }
                ApiOutcome.NotFound -> FetchResult.Success(null)
                is ApiOutcome.Failed -> FetchResult.Failure(outcome.reason)
            }
        } finally {
            connection.disconnect()
        }
    }
}
