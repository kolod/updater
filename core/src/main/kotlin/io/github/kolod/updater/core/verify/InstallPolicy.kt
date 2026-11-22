package io.github.kolod.updater.core.verify

import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.release.Release
import io.github.kolod.updater.core.status.FailureReason
import io.github.kolod.updater.core.status.InstalledApp
import io.github.kolod.updater.core.version.SemVer

/** Facts read from the downloaded APK file itself. */
data class ApkFacts(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    /** Lower-case hex SHA-256 digests of the signing certificates. */
    val signerSha256: Set<String>,
)

sealed interface VerifyResult {
    data object Ok : VerifyResult
    data class Fail(val reason: FailureReason) : VerifyResult
}

/**
 * The gate every APK must pass before it is handed to the system installer (constitution,
 * Principle II). Order: checksum, package, version, signer. The system's own signature check
 * remains the final authority; the signer comparison only gives a clearer message beforehand.
 */
fun checkDownloadedApk(
    app: ManagedApp,
    release: Release,
    apk: ApkFacts,
    installed: InstalledApp?,
    computedSha256: String,
    publishedSha256: List<String>,
): VerifyResult {
    if (!Sha256.allMatch(computedSha256, publishedSha256)) return fail(FailureReason.ChecksumMismatch)
    if (apk.packageName != app.packageName) return fail(FailureReason.WrongPackage)

    val apkVersion = apk.versionName?.let { SemVer.parse(it) }
    if (apkVersion == null || apkVersion != release.version) return fail(FailureReason.VersionMismatch)

    if (installed != null) {
        val installedVersion = installed.versionName?.let { SemVer.parse(it) }
        if (installedVersion != null && apkVersion <= installedVersion) return fail(FailureReason.VersionNotNewer)
        if (apk.versionCode <= installed.versionCode) return fail(FailureReason.VersionNotNewer)

        val installedSigners = installed.signerSha256.map { it.lowercase() }.toSet()
        val apkSigners = apk.signerSha256.map { it.lowercase() }.toSet()
        if (installedSigners.isNotEmpty() && installedSigners.intersect(apkSigners).isEmpty()) {
            return fail(FailureReason.SignatureMismatch)
        }
    }
    return VerifyResult.Ok
}

private fun fail(reason: FailureReason) = VerifyResult.Fail(reason)
