package io.github.kolod.updater.device

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.kolod.updater.ApkInstaller
import io.github.kolod.updater.InstallResult
import io.github.kolod.updater.core.catalog.ManagedApp
import io.github.kolod.updater.core.status.FailureReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Installs through a PackageInstaller session. The system always asks the user to confirm
 * (constitution, Principle II): user action is required explicitly, and the "update without user
 * action" permission is never declared. The result receiver is registered dynamically and only
 * for the duration of one session.
 */
class SessionInstaller(context: Context) : ApkInstaller {
    private val context = context.applicationContext

    override suspend fun install(file: File, app: ManagedApp): InstallResult {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val sessionId = withContext(Dispatchers.IO) { installer.createSession(params) }
        try {
            withContext(Dispatchers.IO) {
                installer.openSession(sessionId).use { session ->
                    session.openWrite("apk", 0, file.length()).use { out ->
                        file.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                }
            }
            return awaitResult(installer, sessionId)
        } catch (e: CancellationException) {
            abandonQuietly(installer, sessionId)
            throw e
        } catch (e: Exception) {
            abandonQuietly(installer, sessionId)
            return InstallResult.Failed(FailureReason.InstallerRejected(PackageInstaller.STATUS_FAILURE))
        }
    }

    private suspend fun awaitResult(installer: PackageInstaller, sessionId: Int): InstallResult =
        suspendCancellableCoroutine { continuation ->
            val action = "$ACTION_PREFIX.$sessionId"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        confirmationIntent(intent)?.let {
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(it)
                        }
                        return
                    }
                    context.unregisterReceiver(this)
                    if (continuation.isActive) continuation.resume(mapStatus(status))
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            continuation.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
                abandonQuietly(installer, sessionId)
            }
            // The system adds the status extras to this intent, so the PendingIntent must be mutable.
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            installer.openSession(sessionId).use { it.commit(pending.intentSender) }
        }

    @Suppress("DEPRECATION")
    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private fun mapStatus(status: Int): InstallResult = when (status) {
        PackageInstaller.STATUS_SUCCESS -> InstallResult.Installed
        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallResult.Declined
        // Reported for INSTALL_FAILED_UPDATE_INCOMPATIBLE, i.e. the signing key differs.
        PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallResult.Failed(FailureReason.SignatureMismatch)
        PackageInstaller.STATUS_FAILURE_STORAGE -> InstallResult.Failed(FailureReason.StorageFull)
        else -> InstallResult.Failed(FailureReason.InstallerRejected(status))
    }

    private fun abandonQuietly(installer: PackageInstaller, sessionId: Int) {
        runCatching { installer.abandonSession(sessionId) }
    }

    private companion object {
        const val ACTION_PREFIX = "io.github.kolod.updater.INSTALL_RESULT"
    }
}
