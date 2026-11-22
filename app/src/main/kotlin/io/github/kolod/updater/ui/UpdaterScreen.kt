package io.github.kolod.updater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kolod.updater.R
import io.github.kolod.updater.UpdaterUiState

@Composable
fun UpdaterScreen(state: UpdaterUiState, onCheck: () -> Unit, onCancel: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Header(state, onCheck, onCancel)
            }
            items(state.apps, key = { it.app.id }) { row -> AppRow(row) }
        }
    }
}

@Composable
private fun Header(state: UpdaterUiState, onCheck: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        if (state.isChecking) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
            Text(stringResource(R.string.checking), style = MaterialTheme.typography.bodyMedium)
        } else {
            Button(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.check_updates))
            }
        }
        Text(
            state.lastCheckedAt?.let { stringResource(R.string.last_checked, formatDateTime(it)) }
                ?: stringResource(R.string.never_checked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.updatesAvailable > 0) {
            Text(
                pluralStringResource(R.plurals.updates_available, state.updatesAvailable, state.updatesAvailable),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
