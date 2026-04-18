package com.healthaggregator.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.data.LabPanelAggregate
import com.healthaggregator.ui.components.SourceBadge
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun LabPanelRow(
	panel: LabPanelAggregate,
	abnormal: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(Icons.Outlined.Science, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
		Spacer(Modifier.width(12.dp))
		Column(modifier = Modifier.weight(1f)) {
			Text(panel.displayName, style = MaterialTheme.typography.bodyLarge)
			Spacer(Modifier.size(2.dp))
			Text(
				text = buildString {
					panel.effectiveAt?.let { append(LocalDate.ofInstant(it, ZoneId.systemDefault()).toString()) }
					append(" · ")
					append("${panel.componentCount} results")
				},
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.secondary,
			)
			Spacer(Modifier.size(4.dp))
			SourceBadge(sourceSystem = panel.sourceSystem, sourceName = panel.sourceName)
		}
		if (abnormal) {
			Spacer(Modifier.width(12.dp))
			Text("Abnormal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
		}
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewNormal() = HealthAggregatorTheme {
	LabPanelRow(
		panel = LabPanelAggregate(
			serviceRequestReference = "ServiceRequest/bmp-1",
			displayName = "Basic Metabolic Panel",
			effectiveAt = Instant.parse("2024-03-15T09:30:00Z"),
			sourceSystem = "cleveland-clinic",
			sourceName = "Cleveland Clinic",
			componentCount = 8,
		),
		abnormal = false,
		onClick = {},
	)
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewAbnormal() = HealthAggregatorTheme {
	LabPanelRow(
		panel = LabPanelAggregate(
			serviceRequestReference = "ServiceRequest/cbc-1",
			displayName = "COMPREHENSIVE METABOLIC PANEL",
			effectiveAt = Instant.parse("2024-03-15T09:30:00Z"),
			sourceSystem = "summa-health",
			sourceName = "Summa Health",
			componentCount = 14,
		),
		abnormal = true,
		onClick = {},
	)
}
