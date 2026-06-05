package com.healthaggregator.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.ui.components.AppTopBar
import com.healthaggregator.ui.components.SourceBadge
import com.healthaggregator.ui.components.TrendChart
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabDetailScreen(
	onBack: () -> Unit,
	viewModel: LabDetailViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val rawJson by viewModel.rawJson.collectAsStateWithLifecycle()
	val current = state.current

	Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
		AppTopBar(
			title = current?.testName ?: "Lab",
			onBack = onBack,
		)

		if (state.loading) {
			Text("Loading…", modifier = Modifier.padding(24.dp))
			return@Column
		}
		if (current == null) {
			Text("Lab not found", modifier = Modifier.padding(24.dp))
			return@Column
		}

		Card(
			modifier = Modifier.padding(16.dp).fillMaxWidth(),
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
		) {
			Column(modifier = Modifier.padding(20.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(
						text = buildString {
							current.numericValue?.let { append("$it") } ?: append(current.textValue ?: "—")
							current.unit?.let { append(" $it") }
						},
						style = MaterialTheme.typography.displaySmall,
						fontWeight = FontWeight.SemiBold,
					)
					Spacer(Modifier.weight(1f))
					if (viewModel.isAbnormalLab(current)) {
						Text(
							text = current.interpretation ?: "Abnormal",
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.error,
						)
					}
				}
				Spacer(Modifier.height(8.dp))
				SourceBadge(sourceSystem = current.sourceSystem, sourceName = current.sourceName)
				current.effectiveAt?.let {
					Spacer(Modifier.height(4.dp))
					Text(
						LocalDate.ofInstant(it, ZoneId.systemDefault()).toString(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.secondary,
					)
				}
				val refText = current.referenceText
					?: (if (current.referenceLow != null || current.referenceHigh != null)
						"Normal: ${current.referenceLow ?: "—"} – ${current.referenceHigh ?: "—"}" else null)
				refText?.let {
					Spacer(Modifier.height(4.dp))
					Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
				}
			}
		}

		if (state.trend.size > 1) {
			Text(
				"Trend",
				style = MaterialTheme.typography.titleSmall,
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
			)
			TrendChart(
				values = state.trend
					.sortedBy { it.effectiveAt }
					.mapNotNull { lab ->
						val v = lab.numericValue ?: return@mapNotNull null
						lab.effectiveAt to v
					},
				refLow = current.referenceLow,
				refHigh = current.referenceHigh,
				unit = current.unit,
			)
		}

		HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

		Text("Metadata", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
		MetadataRow("Test", current.testName)
		MetadataRow("LOINC", current.loincCode ?: "—")
		MetadataRow("Status", current.status.ifBlank { "—" })
		MetadataRow("FHIR reference", current.fhirReference)
		MetadataRow("Imported", current.importedAt.toString())

		var expanded by remember { mutableStateOf(false) }
		HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
		Text(
			text = if (expanded) "▾ Raw FHIR JSON" else "▸ Raw FHIR JSON",
			style = MaterialTheme.typography.titleSmall,
			modifier = Modifier
				.fillMaxWidth()
				.clickable { expanded = !expanded }
				.padding(horizontal = 16.dp, vertical = 8.dp),
		)
		if (expanded) {
			Text(
				text = rawJson ?: "(not available)",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.secondary,
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
			)
		}

		Spacer(Modifier.height(32.dp))
	}
}

@Composable
private fun MetadataRow(label: String, value: String) {
	Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
		Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
		Spacer(Modifier.weight(1f))
		Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 900)
@Composable
private fun PreviewLabDetail() = HealthAggregatorTheme {
	Column(Modifier.fillMaxSize()) {
		AppTopBar(title = "Hemoglobin A1c", onBack = {})
		Card(
			modifier = Modifier.padding(16.dp).fillMaxWidth(),
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
		) {
			Column(modifier = Modifier.padding(20.dp)) {
				Text("5.8 %", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
				Spacer(Modifier.height(8.dp))
				SourceBadge("cleveland-clinic", "Cleveland Clinic")
				Spacer(Modifier.height(4.dp))
				Text("Normal: 4.0 – 5.6 %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
			}
		}
	}
}
