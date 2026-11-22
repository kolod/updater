package io.github.kolod.updater

import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.release.Release
import io.github.kolod.updater.core.release.ReleaseAsset
import io.github.kolod.updater.core.status.InstalledApp
import io.github.kolod.updater.core.version.SemVer
import kotlinx.coroutines.CompletableDeferred

fun releaseOf(app: ManagedApp, version: String, withApk: Boolean = true) = Release(
    tag = app.tagPrefix + version,
    version = SemVer.parse(version)!!,
    apk = if (withApk) {
        ReleaseAsset(
            app.apkAssetName,
            1000,
            "https://github.com/${app.repoOwner}/${app.repoName}/releases/download/${app.tagPrefix}$version/${app.apkAssetName}",
            null,
        )
    } else {
        null
    },
    checksumAsset = null,
)

fun installedOf(app: ManagedApp, version: String?, code: Long = 1, signers: Set<String> = setOf("sig")) =
    InstalledApp(app.packageName, version, code, signers)

class FakeSource : ReleaseSource {
    val results = mutableMapOf<String, FetchResult>()
    val calls = mutableListOf<String>()

    /** When set, every fetch suspends until it is completed. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun fetch(app: ManagedApp): FetchResult {
        calls += app.id
        gate?.await()
        return results[app.id] ?: FetchResult.Success(null)
    }
}

class FakeReader : InstalledAppReader {
    val installed = mutableMapOf<String, io.github.kolod.updater.core.status.InstalledApp?>()
    override fun read(app: ManagedApp) = installed[app.id]
}
