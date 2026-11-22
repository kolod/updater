package io.github.kolod.updater.data

import io.github.kolod.updater.ApkDownloader
import io.github.kolod.updater.DownloadResult
import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.net.ApiOutcome
import io.github.kolod.updater.core.net.NetworkPolicy
import io.github.kolod.updater.core.net.ResponseMapper
import io.github.kolod.updater.core.release.Release
import io.github.kolod.updater.core.status.FailureReason
import io.github.kolod.updater.core.verify.ChecksumFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads the release APK over HTTPS from the allowed GitHub hosts into the private download
 * directory, hashing it on the way, and collects every SHA-256 the release publishes for it.
 */
class HttpApkDownloader(private val files: DownloadFiles) : ApkDownloader {

    override suspend fun download(
        app: ManagedApp,
        release: Release,
        onProgress: (done: Long, total: Long) -> Unit,
    ): DownloadResult {
        var failures = 0
        while (true) {
            val result = attempt(app, release, onProgress)
            if (result is DownloadResult.Failed && ResponseMapper.shouldRetry(failures, result.reason)) {
                delay(ResponseMapper.backoffMillis(failures))
                failures++
                continue
            }
            return result
        }
    }

    private suspend fun attempt(app: ManagedApp, release: Release, onProgress: (Long, Long) -> Unit): DownloadResult {
        val apk = release.apk ?: return DownloadResult.Failed(FailureReason.BadResponse)
        if (!NetworkPolicy.isAllowed(apk.downloadUrl, app)) return DownloadResult.Failed(FailureReason.BadResponse)
        val job = currentCoroutineContext().job
        val part = files.partFor(app, release.version.toString())
        return withContext(Dispatchers.IO) {
            try {
                runInterruptible {
                    val checksum = fetchChecksum(app, release, job.isActive)
                    if (checksum is Checksum.Failed) return@runInterruptible DownloadResult.Failed(checksum.reason)
                    val published = listOfNotNull(apk.sha256) + listOfNotNull((checksum as Checksum.Value).hex)
                    downloadApk(apk.downloadUrl, apk.size, part, published, onProgress) { job.isActive }
                }
            } catch (e: CancellationException) {
                files.discard(part)
                throw e
            } catch (e: Exception) {
                files.discard(part)
                DownloadResult.Failed(if (e is RefusedUrlException) FailureReason.BadResponse else ResponseMapper.mapException(e))
            }
        }
    }

    private sealed interface Checksum {
        data class Value(val hex: String?) : Checksum
        data class Failed(val reason: FailureReason) : Checksum
    }

    /** Reads the separate checksum file when the catalog expects one and the release has it. */
    private fun fetchChecksum(app: ManagedApp, release: Release, active: Boolean): Checksum {
        val asset = release.checksumAsset ?: return Checksum.Value(null)
        val name = app.checksumAssetName ?: return Checksum.Value(null)
        if (!active || !NetworkPolicy.isAllowed(asset.downloadUrl, app)) return Checksum.Failed(FailureReason.BadResponse)
        val connection = Http.open(asset.downloadUrl, "application/octet-stream", 20_000)
        try {
            val code = connection.responseCode
            val body = if (code in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
            return when (val outcome = ResponseMapper.mapResponse(code, Http.headersOf(connection), body)) {
                is ApiOutcome.Ok -> ChecksumFile.parse(outcome.body, name)
                    ?.let { Checksum.Value(it) }
                    ?: Checksum.Failed(FailureReason.BadResponse)
                ApiOutcome.NotFound -> Checksum.Failed(FailureReason.BadResponse)
                is ApiOutcome.Failed -> Checksum.Failed(outcome.reason)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadApk(
        url: String,
        expectedSize: Long,
        part: File,
        published: List<String>,
        onProgress: (Long, Long) -> Unit,
        isActive: () -> Boolean,
    ): DownloadResult {
        val connection = Http.open(url, "application/octet-stream", 30_000)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                return when (val outcome = ResponseMapper.mapResponse(code, Http.headersOf(connection), "")) {
                    is ApiOutcome.Failed -> DownloadResult.Failed(outcome.reason)
                    else -> DownloadResult.Failed(FailureReason.BadResponse)
                }
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            val (sha, written) = connection.inputStream.use { input ->
                files.write(input, part, { onProgress(it, total) }) {
                    if (!isActive()) throw CancellationException("download cancelled")
                }
            }
            if (expectedSize > 0 && written != expectedSize) {
                files.discard(part)
                return DownloadResult.Failed(FailureReason.BadResponse)
            }
            return DownloadResult.Done(files.commit(part), sha, published)
        } finally {
            connection.disconnect()
        }
    }
}
