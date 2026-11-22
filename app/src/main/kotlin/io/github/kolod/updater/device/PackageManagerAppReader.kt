package io.github.kolod.updater.device

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import io.github.kolod.updater.InstalledAppReader
import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.status.InstalledApp

/** Reads installed managed apps through the package manager (visible via the manifest's queries). */
class PackageManagerAppReader(context: Context) : InstalledAppReader {
    private val packageManager = context.applicationContext.packageManager

    @Suppress("DEPRECATION")
    override fun read(app: ManagedApp): InstalledApp? = try {
        val info = packageManager.getPackageInfo(app.packageName, SIGNATURE_FLAGS)
        InstalledApp(
            packageName = app.packageName,
            versionName = info.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            signerSha256 = signerDigests(info),
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
