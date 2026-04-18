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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.isAbnormal
import com.healthaggregator.ui.components.SourceBadge
import com.healthaggregator.ui.components.Sparkline
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
		TopAppBar(
			title = { Text(state.panelName) },
			navigationIcon = {
				IconButton(onClick = onBack) {
					Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
				}
			},
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
				HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
			}
		}
	}
}

@Composable
private fun ComponentRow(lab: LabObservation, onClick: () -> Unit) {
	Row(
		modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(lab.testName, style = MaterialTheme.typography.bodyLarge)
			Spacer(Modifier.height(2.dp))
			Text(
				text = buildString {
					lab.numericValue?.let { append("$it") } ?: append(lab.textValue ?: "—")
					lab.unit?.let { append(" $it") }
				},
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.secondary,
			)
			if (lab.referenceLow != null || lab.referenceHigh != null) {
				Text(
					text = "Normal: ${lab.referenceLow ?: "—"} – ${lab.referenceHigh ?: "—"}",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.secondary,
				)
			}
		}
		if (isAbnormal(lab)) {
			Text(
				text = lab.interpretation?.take(2) ?: "!",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.error,
				modifier = Modifier.padding(end = 8.dp),
			)
		}
		lab.numericValue?.let {
			Sparkline(values = listOf(it), refLow = lab.referenceLow, refHigh = lab.referenceHigh, abnormal = isAbnormal(lab))
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 800)
@Composable
private fun PreviewPanelDetail() = HealthAggregatorTheme {
	Column(modifier = Modifier.fillMaxSize()) {
		TopAppBar(title = { Text("Basic Metabolic Panel") })
		Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
			SourceBadge("cleveland-clinic", "Cleveland Clinic")
			Spacer(Modifier.width(12.dp))
			Text("8 results · 1 abnormal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
		}
	}
}
