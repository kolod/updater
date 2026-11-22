package io.github.kolod.updater.core.status

import io.github.kolod.updater.core.catalog.Catalog
import io.github.kolod.updater.core.release.Release
import io.github.kolod.updater.core.release.ReleaseAsset
import io.github.kolod.updater.core.version.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusResolverTest {
    private val app = Catalog.byId("circuitopedia")

    private fun release(version: String, withApk: Boolean = true) = Release(
        tag = "v$version",
        version = SemVer.parse(version)!!,
        apk = if (withApk) ReleaseAsset("circuitopedia.apk", 1, "https://github.com/x", null) else null,
        checksumAsset = null,
    )

    private fun installed(versionName: String?) =
        InstalledApp(app.packageName, versionName, 1, emptySet())

    @Test
    fun equalVersionIsUpToDate() {
        assertEquals(
            UpdateStatus.UpToDate("0.2.0", "0.2.0"),
            resolveStatus(app, installed("0.2.0"), release("0.2.0")),
        )
    }

    @Test
    fun newerReleaseIsAnUpdate() {
        val r = release("0.2.0")
        assertEquals(UpdateStatus.UpdateAvailable("0.1.0", r), resolveStatus(app, installed("0.1.0"), r))
    }

    @Test
    fun notInstalledOffersInstallation() {
        val r = release("0.2.0")
        assertEquals(UpdateStatus.NotInstalled(r), resolveStatus(app, null, r))
    }

    @Test
    fun installedNewerThanLatestIsNeverADowngrade() {
        assertTrue(resolveStatus(app, installed("0.3.0"), release("0.2.0")) is UpdateStatus.UpToDate)
    }

    @Test
    fun developmentBuildIsOlderThanTheSameRelease() {
        assertTrue(resolveStatus(app, installed("0.2.0-dev"), release("0.2.0")) is UpdateStatus.UpdateAvailable)
    }

    @Test
    fun unparsableOrMissingInstalledVersionIsTreatedAsOlder() {
        assertTrue(resolveStatus(app, installed("weird"), release("0.2.0")) is UpdateStatus.UpdateAvailable)
        assertTrue(resolveStatus(app, installed(null), release("0.2.0")) is UpdateStatus.UpdateAvailable)
    }

    @Test
    fun noReleaseMeansNoRelease() {
        assertEquals(UpdateStatus.NoRelease, resolveStatus(app, installed("0.1.0"), null))
        assertEquals(UpdateStatus.NoRelease, resolveStatus(app, null, null))
    }

    @Test
    fun releaseWithoutApkIsNotInstallable() {
        assertEquals(
            UpdateStatus.NoInstallableFile("0.3.0"),
            resolveStatus(app, installed("0.1.0"), release("0.3.0", withApk = false)),
        )
    }
}
