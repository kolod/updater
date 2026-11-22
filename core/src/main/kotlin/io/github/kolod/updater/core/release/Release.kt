package io.github.kolod.updater.core.release

import io.github.kolod.updater.core.version.SemVer

data class ReleaseAsset(
    val name: String,
    val size: Long,
    val downloadUrl: String,
    /** Lower-case hex SHA-256 reported by the release source, when available. */
    val sha256: String?,
)

/**
 * A published, non-draft, non-pre-release version whose tag matches the app's tag rule.
 * [apk] is null when the release has no (allowed) asset with the expected file name.
 */
data class Release(
    val tag: String,
    val version: SemVer,
    val apk: ReleaseAsset?,
    val checksumAsset: ReleaseAsset?,
)

sealed interface ParseResult {
    data class Ok(val releases: List<Release>) : ParseResult
    data object BadResponse : ParseResult
}
