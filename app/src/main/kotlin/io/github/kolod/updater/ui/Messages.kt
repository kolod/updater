package io.github.kolod.updater.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.kolod.updater.AppState
import io.github.kolod.updater.R
import io.github.kolod.updater.core.status.FailureReason
import io.github.kolod.updater.core.status.UpdateStatus
import java.text.DateFormat
import java.util.Date

/** Date and time in the user's locale. */
@Composable
fun formatDateTime(epochMillis: Long): String =
    remember(epochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
    }

/** One localized message per failure reason (constitution, Principle VII). */
@Composable
fun failureText(reason: FailureReason): String = when (reason) {
    FailureReason.Offline -> stringResource(R.string.failure_offline)
    is FailureReason.RateLimited -> {
        val resetAt = reason.resetAtEpochSeconds
        if (resetAt != null) {
            stringResource(R.string.failure_rate_limited_until, formatDateTime(resetAt * 1000))
        } else {
            stringResource(R.string.failure_rate_limited)
        }
    }
    is FailureReason.ServerError -> stringResource(R.string.failure_server_error, reason.httpCode)
    FailureReason.BadResponse -> stringResource(R.string.failure_bad_response)
    FailureReason.StorageFull -> stringResource(R.string.failure_storage_full)
    FailureReason.ChecksumMismatch -> stringResource(R.string.failure_checksum)
    FailureReason.WrongPackage -> stringResource(R.string.failure_wrong_package)
    FailureReason.VersionMismatch -> stringResource(R.string.failure_version_mismatch)
    FailureReason.VersionNotNewer -> stringResource(R.string.failure_version_not_newer)
    FailureReason.SignatureMismatch -> stringResource(R.string.failure_signature)
    is FailureReason.InstallerRejected -> stringResource(R.string.failure_installer_rejected, reason.code)
    FailureReason.NotAllowedToInstall -> stringResource(R.string.failure_not_allowed)
    FailureReason.Cancelled -> ""
}

/** Short status label of a row. */
@Composable
fun statusText(row: AppState): String = when (row.status) {
    null -> stringResource(R.string.never_checked)
    is UpdateStatus.UpToDate -> stringResource(R.string.status_up_to_date)
    is UpdateStatus.UpdateAvailable -> stringResource(R.string.status_update_available)
    is UpdateStatus.NotInstalled -> stringResource(R.string.status_not_installed)
    UpdateStatus.NoRelease -> stringResource(R.string.status_no_release)
    is UpdateStatus.NoInstallableFile -> stringResource(R.string.status_no_file)
    is UpdateStatus.Failed -> stringResource(R.string.status_check_failed)
}
