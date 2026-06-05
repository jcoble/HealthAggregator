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
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.ui.components.AppTopBar
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
	onBack: () -> Unit,
	viewModel: RecordDetailViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()

	Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
		AppTopBar(
			title = state.resourceType.ifBlank { "Record" },
			onBack = onBack,
		)

		if (state.loading) {
			Text("Loading…", modifier = Modifier.padding(24.dp))
			return@Column
		}

		Card(
			modifier = Modifier.padding(16.dp).fillMaxWidth(),
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
		) {
			Column(modifier = Modifier.padding(16.dp)) {
				Text("FHIR reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
				Text(state.fhirReference, style = MaterialTheme.typography.bodyMedium)
				Spacer(Modifier.height(8.dp))
				Text("Source", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
				Text(state.sourceSystem, style = MaterialTheme.typography.bodyMedium)
			}
		}

		Text("Fields", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
		state.fields.forEach { (k, v) ->
			Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
				Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
				Spacer(Modifier.weight(1f))
				Text(v, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
			}
		}

		var expanded by remember { mutableStateOf(false) }
		HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
		Text(
			text = if (expanded) "▾ Raw FHIR JSON" else "▸ Raw FHIR JSON",
			style = MaterialTheme.typography.titleSmall,
			modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 8.dp),
		)
		if (expanded) {
			Text(
				text = state.rawJson ?: "(not available)",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.secondary,
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
			)
		}

		Spacer(Modifier.height(32.dp))
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 800)
@Composable
private fun PreviewRecordDetail() = HealthAggregatorTheme {
	Column(Modifier.fillMaxSize()) {
		AppTopBar(title = "MedicationRequest", onBack = {})
		Card(
			modifier = Modifier.padding(16.dp).fillMaxWidth(),
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
		) {
			Column(modifier = Modifier.padding(16.dp)) {
				Text("Fields", style = MaterialTheme.typography.titleSmall)
				Spacer(Modifier.height(8.dp))
				Text("status: active", style = MaterialTheme.typography.bodyMedium)
				Text("medicationCodeableConcept: Lisinopril 10 mg", style = MaterialTheme.typography.bodyMedium)
			}
		}
	}
}
