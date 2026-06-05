package com.healthaggregator.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.ui.components.AppTopBar
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncLogScreen(
	onBack: () -> Unit,
	viewModel: LaptopSyncViewModel = hiltViewModel(),
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	LaunchedEffect(Unit) { viewModel.refreshFromStorage() }

	Scaffold(
		topBar = { AppTopBar(title = "Sync log", onBack = onBack) },
	) { padding ->
		Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			if (state.lastSyncAt > 0) {
				Text("Last sync: ${DateFormat.getDateTimeInstance().format(Date(state.lastSyncAt))}",
					style = MaterialTheme.typography.bodyMedium)
				Text(state.lastSyncSummary ?: "(no summary)",
					style = MaterialTheme.typography.bodySmall)
			} else {
				Text("No sync has run yet.", style = MaterialTheme.typography.bodyMedium)
			}
		}
	}
}
