package com.healthaggregator.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@Composable
fun LaptopSyncSection(
	onPair: () -> Unit,
	onOpenSyncLog: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: LaptopSyncViewModel = hiltViewModel(),
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	LaunchedEffect(Unit) { viewModel.refreshFromStorage() }

	Card(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
		Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text("Laptop Sync", style = MaterialTheme.typography.titleMedium)
			if (!state.paired) {
				Text("Not paired with a laptop yet.", style = MaterialTheme.typography.bodySmall)
				Button(onClick = onPair) { Text("Pair with laptop") }
			} else {
				Text("Paired with ${state.hostname}", style = MaterialTheme.typography.bodyMedium)
				if (state.lastSyncAt > 0) {
					val whenStr = DateFormat.getDateTimeInstance().format(Date(state.lastSyncAt))
					Text("Last sync: $whenStr", style = MaterialTheme.typography.bodySmall)
					state.lastSyncSummary?.let {
						Text(it, style = MaterialTheme.typography.bodySmall)
					}
					TextButton(onClick = onOpenSyncLog) { Text("View sync log") }
				}
				if (state.errorMessage != null) {
					Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
				}
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Button(onClick = viewModel::syncNow, enabled = !state.syncing) {
						if (state.syncing) {
							CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
							Spacer(Modifier.width(8.dp))
						}
						Text("Sync now")
					}
					OutlinedButton(onClick = viewModel::unpair) { Text("Unpair") }
				}
			}
		}
	}
}
