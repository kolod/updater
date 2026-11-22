package io.github.kolod.updater

import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.release.Release
import io.github.kolod.updater.core.release.ReleaseAsset
import io.github.kolod.updater.core.status.FailureReason
import io.github.kolod.updater.core.status.InstalledApp
import io.github.kolod.updater.core.verify.ApkFacts
import io.github.kolod.updater.core.version.SemVer
import kotlinx.coroutines.CompletableDeferred
import java.io.File

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
    val installed = mutableMapOf<String, InstalledApp?>()
    override fun read(app: ManagedApp) = installed[app.id]
}

/** Creates a real temp file per download so tests can assert that it is deleted afterwards. */
class FakeDownloader(private val dir: File) : ApkDownloader {
    val calls = mutableListOf<String>()
    val created = mutableListOf<File>()
    val failures = mutableMapOf<String, FailureReason>()
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    var computedSha = "a".repeat(64)
    var publishedSha: List<String> = listOf("a".repeat(64))

    override suspend fun download(app: ManagedApp, release: Release, onProgress: (Long, Long) -> Unit): DownloadResult {
        calls += app.id
        onProgress(10, 100)
        gates[app.id]?.await()
        failures[app.id]?.let { return DownloadResult.Failed(it) }
        val file = File(dir, "${app.id}-${release.version}.apk").also { it.writeText("apk"); created += it }
        return DownloadResult.Done(file, computedSha, publishedSha)
    }
}

class FakeInspector : ApkInspector {
    /** Overrides per app id; by default the facts match the release being installed. */
    val overrides = mutableMapOf<String, ApkFacts?>()
    var versionByApp = mutableMapOf<String, String>()
    var versionCode = 100L

    override fun inspect(file: File): ApkFacts? {
        val id = file.name.substringBefore('-')
        if (id in overrides) return overrides[id]
        val app = io.github.kolod.updater.core.catalog.Catalog.byId(id)
        val version = file.name.removePrefix("$id-").removeSuffix(".apk")
        return ApkFacts(app.packageName, version, versionCode, setOf("sig"))
    }
}

class FakeInstaller(private val reader: FakeReader) : ApkInstaller {
    val calls = mutableListOf<String>()
    val results = mutableMapOf<String, InstallResult>()
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    override suspend fun install(file: File, app: ManagedApp): InstallResult {
        calls += app.id
        gates[app.id]?.await()
        val result = results[app.id] ?: InstallResult.Installed
        if (result == InstallResult.Installed) {
            val version = file.name.removePrefix("${app.id}-").removeSuffix(".apk")
            reader.installed[app.id] = InstalledApp(app.packageName, version, 100, setOf("sig"))
        }
        return result
    }
}
