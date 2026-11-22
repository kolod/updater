package io.github.kolod.updater.device

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import io.github.kolod.updater.ApkInspector
import io.github.kolod.updater.core.verify.ApkFacts
import java.io.File

/** Reads package name, version and signing certificates straight from a downloaded APK file. */
class PackageArchiveInspector(context: Context) : ApkInspector {
    private val packageManager = context.applicationContext.packageManager

    @Suppress("DEPRECATION")
    override fun inspect(file: File): ApkFacts? {
        val info = try {
            packageManager.getPackageArchiveInfo(file.absolutePath, SIGNATURE_FLAGS)
        } catch (_: Exception) {
            null
        } ?: return null
        return ApkFacts(
            packageName = info.packageName ?: return null,
            versionName = info.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            signerSha256 = signerDigests(info),
        )
    }
}
