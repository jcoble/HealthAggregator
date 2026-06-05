package com.healthaggregator.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.isAbnormal
import com.healthaggregator.ui.components.AppTopBar
import com.healthaggregator.ui.components.RangeGauge
import com.healthaggregator.ui.components.SourceBadge
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelDetailScreen(
	onBack: () -> Unit,
	onOpenComponent: (sourceSystem: String, fhirReference: String) -> Unit,
	viewModel: PanelDetailViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()

	Column(modifier = Modifier.fillMaxSize()) {
		AppTopBar(
			title = state.panelName,
			onBack = onBack,
		)

		if (state.components.isEmpty()) {
			Text(if (state.loading) "Loading…" else "No components", modifier = Modifier.padding(24.dp))
			return@Column
		}

		Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
			SourceBadge(sourceSystem = state.sourceSystem, sourceName = state.sourceName)
			Spacer(Modifier.width(12.dp))
			Text(
				text = "${state.components.size} results" + if (state.abnormalCount > 0) " · ${state.abnormalCount} abnormal" else "",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.secondary,
			)
		}

		HorizontalDivider()

		LazyColumn(modifier = Modifier.fillMaxSize()) {
			items(state.components, key = { "comp-${it.id}" }) { comp ->
				ComponentRow(comp, onClick = { onOpenComponent(comp.sourceSystem, comp.fhirReference) })
			}
		}
	}
}

@Composable
private fun ComponentRow(lab: LabObservation, onClick: () -> Unit) {
	Card(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					text = lab.testName.uppercase(),
					style = MaterialTheme.typography.titleSmall,
					color = MaterialTheme.colorScheme.onSurface,
					modifier = Modifier.weight(1f),
				)
				if (isAbnormal(lab)) {
					Text(
						text = lab.interpretation ?: "Abnormal",
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.error,
					)
				}
			}

			Spacer(Modifier.height(4.dp))

			val refText = if (lab.referenceLow != null || lab.referenceHigh != null) {
				"Normal range: ${lab.referenceLow?.let { formatForNormal(it) } ?: "—"} – ${lab.referenceHigh?.let { formatForNormal(it) } ?: "—"}${lab.unit?.let { " $it" } ?: ""}"
			} else lab.referenceText

			refText?.let {
				Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
			}

			val numeric = lab.numericValue
			if (numeric != null && (lab.referenceLow != null || lab.referenceHigh != null)) {
				Spacer(Modifier.height(12.dp))
				RangeGauge(
					value = numeric,
					unit = lab.unit,
					refLow = lab.referenceLow,
					refHigh = lab.referenceHigh,
				)
			} else if (numeric != null) {
				// Numeric value but no range → just show the value
				Spacer(Modifier.height(8.dp))
				Text(
					text = buildString {
						append("$numeric")
						lab.unit?.let { append(" $it") }
					},
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
				)
			} else {
				// Text-only (e.g. "NEGATIVE")
				Spacer(Modifier.height(8.dp))
				Text(
					text = lab.textValue ?: "—",
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
				)
			}
		}
	}
}

private fun formatForNormal(v: Double): String =
	if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 800)
@Composable
private fun PreviewPanelDetail() = HealthAggregatorTheme {
	Column(modifier = Modifier.fillMaxSize()) {
		AppTopBar(title = "Basic Metabolic Panel", onBack = {})
		Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
			SourceBadge("cleveland-clinic", "Cleveland Clinic")
			Spacer(Modifier.width(12.dp))
			Text("8 results · 1 abnormal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
		}
	}
}
