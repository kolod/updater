package io.github.kolod.updater.core.status

import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.release.Release
import io.github.kolod.updater.core.version.SemVer

/** Combines what is installed with the newest eligible [release] (null when none exists). */
fun resolveStatus(app: ManagedApp, installed: InstalledApp?, release: Release?): UpdateStatus {
    if (release == null) return UpdateStatus.NoRelease
    if (release.apk == null) return UpdateStatus.NoInstallableFile(release.version.toString())
    if (installed == null) return UpdateStatus.NotInstalled(release)
    val installedVersion = installed.versionName?.let { SemVer.parse(it) }
    // An unreadable installed version is treated as older so the user can still repair it.
    return if (installedVersion == null || release.version > installedVersion) {
        UpdateStatus.UpdateAvailable(installed.versionName, release)
    } else {
        UpdateStatus.UpToDate(installed.versionName, release.version.toString())
    }
}
