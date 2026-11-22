package io.github.kolod.updater

import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.release.Release
import io.github.kolod.updater.core.status.FailureReason
import io.github.kolod.updater.core.status.InstalledApp
import io.github.kolod.updater.core.verify.ApkFacts
import java.io.File

/**
 * Boundaries between the pure logic and Android/network. Each has a real implementation and a test
 * fake (the two uses that justify an interface under constitution Principle V).
 */

sealed interface FetchResult {
    /** [release] is null when the repository has no eligible release. */
    data class Success(val release: Release?) : FetchResult
    data class Failure(val reason: FailureReason) : FetchResult
}

interface ReleaseSource {
    suspend fun fetch(app: ManagedApp): FetchResult
}

interface InstalledAppReader {
    /** Null when the app is not installed. */
    fun read(app: ManagedApp): InstalledApp?
}

sealed interface DownloadResult {
    /** [file] must be deleted by the caller once it is no longer needed. */
    data class Done(
        val file: File,
        val computedSha256: String,
        /** Every SHA-256 the release publishes for the APK (API digest and/or checksum file). */
        val publishedSha256: List<String>,
    ) : DownloadResult

    data class Failed(val reason: FailureReason) : DownloadResult
}

interface ApkDownloader {
    suspend fun download(app: ManagedApp, release: Release, onProgress: (done: Long, total: Long) -> Unit): DownloadResult
}

interface ApkInspector {
    /** Null when [file] is not a parsable APK. */
    fun inspect(file: File): ApkFacts?
}

sealed interface InstallResult {
    data object Installed : InstallResult
    /** The user declined in the system dialog; not an error. */
    data object Declined : InstallResult
    data class Failed(val reason: FailureReason) : InstallResult
}

interface ApkInstaller {
    suspend fun install(file: File, app: ManagedApp): InstallResult
}
