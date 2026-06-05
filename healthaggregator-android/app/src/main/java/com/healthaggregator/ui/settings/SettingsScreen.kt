package com.healthaggregator.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.data.entities.MedicalDataSource
import com.healthaggregator.ui.components.AppTopBar
import com.healthaggregator.ui.components.SourceBadge
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
	onRequestPermissions: () -> Unit,
	onPairLaptop: () -> Unit,
	onOpenSyncLog: () -> Unit,
	viewModel: SettingsViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	var resetDialogOpen by remember { mutableStateOf(false) }
	var editingSource by remember { mutableStateOf<MedicalDataSource?>(null) }

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
	) {
		AppTopBar(title = "Settings")

		LaptopSyncSection(
			onPair = onPairLaptop,
			onOpenSyncLog = onOpenSyncLog,
			modifier = Modifier.padding(horizontal = 16.dp),
		)

		SectionCard(title = "Health Connect") {
			LabelValueRow("Available", if (state.healthConnectAvailable) "Yes" else "No")
			LabelValueRow("Permissions granted", if (state.permissionsGranted) "Yes" else "No")
			Spacer(Modifier.height(8.dp))
			if (!state.permissionsGranted) {
				Button(onClick = onRequestPermissions) { Text("Grant Health Connect access") }
			} else {
				OutlinedButton(onClick = onRequestPermissions) { Text("Re-request permissions") }
			}
		}

		SectionCard(title = "Sources") {
			if (state.sources.isEmpty()) {
				Text("No sources connected yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
			} else {
				state.sources.forEach { src ->
					Row(
						modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						SourceBadge(sourceSystem = src.sourceSystem, sourceName = src.displayName)
						Spacer(Modifier.width(12.dp))
						Text(
							text = "${src.recordCount} records",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.secondary,
							modifier = Modifier.weight(1f),
						)
						TextButton(onClick = { editingSource = src }) { Text("Edit") }
					}
				}
			}
		}

		SectionCard(title = "Data") {
			LabelValueRow("Total records", state.totalRecords.toString())
			LabelValueRow("Database size", humanReadableBytes(state.dbSizeBytes))
			Spacer(Modifier.height(8.dp))
			Button(onClick = viewModel::syncNow, modifier = Modifier.fillMaxWidth()) { Text("Sync now") }
			Spacer(Modifier.height(8.dp))
			OutlinedButton(
				onClick = { resetDialogOpen = true },
				modifier = Modifier.fillMaxWidth(),
				colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
			) { Text("Reset local database") }
		}

		SectionCard(title = "For my doctor") {
			DoctorNarrativeEditor(
				initial = state.narrative,
				onSave = viewModel::updateNarrative,
			)
		}

		SectionCard(title = "AI Assistant") {
			AiAssistantSettings(
				secure = viewModel.secureStorage,
				onClearHistory = { viewModel.clearChatHistory() },
			)
		}

		SectionCard(title = "About") {
			LabelValueRow("Version", "0.1.0 (α.1)")
			Text(
				text = "Clinical record aggregator. Reads Health Connect (populated by CommonHealth).",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.secondary,
			)
		}

		Spacer(Modifier.height(24.dp))
	}

	if (resetDialogOpen) {
		AlertDialog(
			onDismissRequest = { resetDialogOpen = false },
			title = { Text("Reset local database?") },
			text = { Text("Wipes all imported records. Source colors + display names are preserved. You can re-sync afterwards.") },
			confirmButton = {
				TextButton(
					onClick = { viewModel.resetDatabase(); resetDialogOpen = false },
				) { Text("Reset", color = MaterialTheme.colorScheme.error) }
			},
			dismissButton = { TextButton(onClick = { resetDialogOpen = false }) { Text("Cancel") } },
		)
	}

	editingSource?.let { src ->
		SourceEditDialog(
			source = src,
			onDismiss = { editingSource = null },
			onSave = { newName ->
				viewModel.updateSourceDisplayName(src.id, newName)
				editingSource = null
			},
		)
	}
}

@Composable
private fun DoctorNarrativeEditor(initial: String, onSave: (String) -> Unit) {
	var draft by remember(initial) { mutableStateOf(initial) }
	val dirty = draft != initial
	Text(
		text = "Notes you want printed on the first page of every exported report — real diagnoses, concerns, history the chart doesn't show. Synced to the laptop too.",
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.secondary,
	)
	Spacer(Modifier.height(8.dp))
	OutlinedTextField(
		value = draft,
		onValueChange = { draft = it },
		placeholder = { Text("e.g. Type 2 diabetes diagnosed 2019. CPAP-treated sleep apnea. Intermittent joint pain in left knee since…") },
		modifier = Modifier.fillMaxWidth().height(220.dp),
		minLines = 6,
	)
	Spacer(Modifier.height(8.dp))
	Row(modifier = Modifier.fillMaxWidth()) {
		Spacer(Modifier.weight(1f))
		if (dirty) {
			TextButton(onClick = { draft = initial }) { Text("Discard") }
			Spacer(Modifier.width(8.dp))
		}
		Button(onClick = { onSave(draft) }, enabled = dirty) { Text("Save") }
	}
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
	Card(
		modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
			Spacer(Modifier.height(8.dp))
			content()
		}
	}
}

@Composable
private fun LabelValueRow(label: String, value: String) {
	Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
		Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
		Spacer(Modifier.weight(1f))
		Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
	}
}

@Composable
private fun SourceEditDialog(
	source: MedicalDataSource,
	onDismiss: () -> Unit,
	onSave: (String) -> Unit,
) {
	var displayName by remember { mutableStateOf(source.displayName) }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Edit source name") },
		text = {
			OutlinedTextField(
				value = displayName,
				onValueChange = { displayName = it },
				label = { Text("Display name") },
				modifier = Modifier.fillMaxWidth(),
			)
		},
		confirmButton = {
			TextButton(onClick = { onSave(displayName) }) { Text("Save") }
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
	)
}

private fun humanReadableBytes(bytes: Long): String = when {
	bytes < 1024 -> "$bytes B"
	bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
	bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
	else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 900)
@Composable
private fun PreviewSettingsPopulated() = HealthAggregatorTheme {
	Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
		AppTopBar(title = "Settings")
		SectionCard(title = "Health Connect") {
			LabelValueRow("Available", "Yes")
			LabelValueRow("Permissions granted", "Yes")
		}
		SectionCard(title = "Sources") {
			Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
				SourceBadge("cleveland-clinic", "Cleveland Clinic")
				Spacer(Modifier.width(12.dp))
				Text("342 records", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
				TextButton(onClick = {}) { Text("Edit") }
			}
		}
		SectionCard(title = "Data") {
			LabelValueRow("Total records", "342")
			LabelValueRow("Database size", "4.3 MB")
		}
		SectionCard(title = "About") {
			LabelValueRow("Version", "0.1.0 (α.1)")
		}
	}
}
