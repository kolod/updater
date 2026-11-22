package io.github.kolod.updater.core.status

import io.github.kolod.updater.core.release.Release

/** What the device reports about an installed managed app. Absence is modelled as `null`. */
data class InstalledApp(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    /** Lower-case hex SHA-256 digests of the signing certificates. */
    val signerSha256: Set<String>,
)

/** Outcome of one check for one managed app. */
sealed interface UpdateStatus {
    data class UpToDate(val installed: String?, val latest: String) : UpdateStatus
    data class UpdateAvailable(val installed: String?, val release: Release) : UpdateStatus
    data class NotInstalled(val release: Release) : UpdateStatus
    data object NoRelease : UpdateStatus
    data class NoInstallableFile(val latest: String) : UpdateStatus
    data class Failed(val reason: FailureReason) : UpdateStatus
}
