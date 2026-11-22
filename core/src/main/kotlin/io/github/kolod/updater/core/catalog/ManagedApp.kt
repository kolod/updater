package io.github.kolod.updater.core.catalog

/**
 * One app the updater manages. Fixed at build time (constitution, Principle IV); never read from a
 * remote or user-provided source.
 */
data class ManagedApp(
    val id: String,
    val displayName: String,
    val packageName: String,
    val repoOwner: String,
    val repoName: String,
    /** Release tags must start with this prefix; the rest must be a semantic version. */
    val tagPrefix: String,
    val apkAssetName: String,
    /** Name of a separate SHA-256 file published next to the APK, when the app has one. */
    val checksumAssetName: String? = null,
    /** True for the updater itself, which is installed last in "update all". */
    val isSelf: Boolean = false,
) {
    val releasesApiUrl: String
        get() = "https://api.github.com/repos/$repoOwner/$repoName/releases?per_page=30"
}
