package io.github.kolod.updater.device

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import io.github.kolod.updater.core.verify.Sha256
import java.security.MessageDigest

/** Flags that make [PackageManager] return signing certificates on every supported API level. */
@Suppress("DEPRECATION")
internal val SIGNATURE_FLAGS: Int =
    if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

/** Lower-case hex SHA-256 digests of the certificates that sign [info]. */
@Suppress("DEPRECATION")
internal fun signerDigests(info: PackageInfo): Set<String> {
    val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
        val signing = info.signingInfo
        // The certificate history covers key rotation, so old and new signers still overlap.
        if (signing == null) null
        else if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
    } else {
        info.signatures
    }
    return signatures.orEmpty().map { Sha256.toHex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
}
