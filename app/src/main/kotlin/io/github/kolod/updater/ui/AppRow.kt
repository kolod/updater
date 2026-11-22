package io.github.kolod.updater.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kolod.updater.AppState
import io.github.kolod.updater.Freshness
import io.github.kolod.updater.InstallAttempt
import io.github.kolod.updater.R
import io.github.kolod.updater.core.status.UpdateStatus

@Composable
fun AppRow(
    row: AppState,
    onInstall: () -> Unit,
    onCancelInstall: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.app.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    statusText(row),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (row.status is UpdateStatus.UpdateAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            val installed = row.installed
            Text(
                if (installed == null) {
                    stringResource(R.string.status_not_installed)
                } else {
                    stringResource(R.string.version_installed, installed.versionName ?: stringResource(R.string.version_unknown))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            row.latestKnown?.let {
                Text(stringResource(R.string.version_latest, it), style = MaterialTheme.typography.bodyMedium)
            }
            (row.status as? UpdateStatus.Failed)?.let {
                Text(failureText(it.reason), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.retry)) }
            }
            if (row.freshness == Freshness.Stale && (row.latestKnown != null || row.status is UpdateStatus.Failed)) {
                Text(
                    stringResource(R.string.stale_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AttemptSection(row, onInstall, onCancelInstall)
        }
    }
}

@Composable
private fun AttemptSection(row: AppState, onInstall: () -> Unit, onCancelInstall: () -> Unit) {
    when (val attempt = row.attempt) {
        InstallAttempt.Idle, is InstallAttempt.Failed -> {
            (attempt as? InstallAttempt.Failed)?.let {
                Text(failureText(it.reason), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            val label = when (row.status) {
                is UpdateStatus.UpdateAvailable -> R.string.update
                is UpdateStatus.NotInstalled -> R.string.install
                else -> null
            }
            if (label != null) {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
            }
        }
        InstallAttempt.Installed ->
            Text(stringResource(R.string.state_installed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        InstallAttempt.Queued -> BusyLine(stringResource(R.string.state_queued), onCancelInstall)
        InstallAttempt.Verifying -> BusyLine(stringResource(R.string.state_verifying), null)
        InstallAttempt.AwaitingConfirmation -> BusyLine(stringResource(R.string.state_awaiting_confirmation), onCancelInstall)
        is InstallAttempt.Downloading -> {
            val context = LocalContext.current
            Text(
                stringResource(
                    R.string.download_progress,
                    Formatter.formatShortFileSize(context, attempt.done),
                    Formatter.formatShortFileSize(context, attempt.total),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (attempt.total > 0) {
                LinearProgressIndicator(progress = { (attempt.done.toFloat() / attempt.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onCancelInstall) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun BusyLine(text: String, onCancel: (() -> Unit)?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (onCancel != null) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}
