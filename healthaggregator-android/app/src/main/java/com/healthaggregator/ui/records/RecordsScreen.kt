package com.healthaggregator.ui.records

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.ui.components.EmptyState
import com.healthaggregator.ui.components.FilterChipRow
import com.healthaggregator.ui.components.RecordRow
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
	onOpenPanel: (serviceRequestReference: String) -> Unit = {},
	onOpenLab: (sourceSystem: String, fhirReference: String) -> Unit = { _, _ -> },
	onOpenRecord: (sourceSystem: String, fhirReference: String) -> Unit = { _, _ -> },
	viewModel: RecordsViewModel = hiltViewModel(),
) {
	val filter by viewModel.filter.collectAsStateWithLifecycle()
	val rows by viewModel.rows.collectAsStateWithLifecycle()

	Column(modifier = Modifier.fillMaxSize()) {
		TopAppBar(title = { Text("Records") })
		FilterChipRow(
			options = FilterType.entries,
			selected = filter,
			onSelect = viewModel::setFilter,
			label = { it.chipLabel },
			modifier = Modifier.padding(vertical = 8.dp),
		)
		HorizontalDivider()

		if (rows.isEmpty()) {
			val (icon, title, description) = emptyStateFor(filter)
			EmptyState(
				icon = icon,
				title = title,
				description = description,
				actionLabel = "Refresh",
				onAction = viewModel::refresh,
			)
		} else {
			LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
				items(rows, key = { it.id }) { row ->
					RecordRow(
						icon = iconFor(row.kind),
						title = row.title,
						summary = row.summary,
						sourceSystem = row.sourceSystem,
						sourceName = row.sourceName,
						trailingText = row.trailingText,
					)
					HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
				}
			}
		}
	}
}

private fun iconFor(kind: FilterType): ImageVector = when (kind) {
	FilterType.ALL, FilterType.LABS -> Icons.Outlined.Science
	FilterType.VITALS -> Icons.Outlined.MonitorHeart
	FilterType.MEDICATIONS -> Icons.Outlined.LocalPharmacy
	FilterType.CONDITIONS -> Icons.Outlined.Healing
	FilterType.ALLERGIES -> Icons.Outlined.Biotech
	FilterType.ENCOUNTERS -> Icons.Outlined.LocalHospital
	FilterType.DOCUMENTS -> Icons.Outlined.Assignment
}

private fun emptyStateFor(filter: FilterType): Triple<ImageVector, String, String> = when (filter) {
	FilterType.ALL -> Triple(Icons.Outlined.Folder, "No records yet", "Sync from Health Connect to import your clinical data.")
	FilterType.LABS -> Triple(Icons.Outlined.Science, "No lab results", "Labs appear here once CommonHealth pulls them in.")
	FilterType.VITALS -> Triple(Icons.Outlined.MonitorHeart, "No vitals yet", "BP, weight, pulse ox show up here after sync.")
	FilterType.MEDICATIONS -> Triple(Icons.Outlined.LocalPharmacy, "No medications", "Active prescriptions appear here.")
	FilterType.CONDITIONS -> Triple(Icons.Outlined.Healing, "No conditions", "Diagnosed conditions appear here.")
	FilterType.ALLERGIES -> Triple(Icons.Outlined.Biotech, "No allergies", "Allergy records appear here.")
	FilterType.ENCOUNTERS -> Triple(Icons.Outlined.LocalHospital, "No encounters", "Visits and appointments appear here.")
	FilterType.DOCUMENTS -> Triple(Icons.Outlined.Assignment, "No documents", "Attached documents appear here.")
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 720)
@Composable
private fun PreviewEmpty() = HealthAggregatorTheme {
	Column(modifier = Modifier.fillMaxSize()) {
		TopAppBar(title = { Text("Records") })
		FilterChipRow(options = FilterType.entries, selected = FilterType.LABS, onSelect = {}, label = { it.chipLabel })
		Spacer(Modifier.height(8.dp))
		EmptyState(Icons.Outlined.Science, "No lab results", "Labs appear here once CommonHealth pulls them in.", actionLabel = "Refresh", onAction = {})
	}
}
