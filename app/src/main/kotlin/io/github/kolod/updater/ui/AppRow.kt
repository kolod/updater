package io.github.kolod.updater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kolod.updater.AppState
import io.github.kolod.updater.Freshness
import io.github.kolod.updater.R
import io.github.kolod.updater.core.status.UpdateStatus

@Composable
fun AppRow(row: AppState, modifier: Modifier = Modifier) {
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
            }
            if (row.freshness == Freshness.Stale && (row.latestKnown != null || row.status is UpdateStatus.Failed)) {
                Text(
                    stringResource(R.string.stale_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
