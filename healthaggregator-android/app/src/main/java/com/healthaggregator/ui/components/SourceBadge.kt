package com.healthaggregator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.data.sourceColor
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@Composable
fun SourceBadge(sourceSystem: String, sourceName: String? = null, modifier: Modifier = Modifier) {
	val color = sourceColor(sourceSystem)
	Text(
		text = sourceName ?: sourceSystem,
		style = MaterialTheme.typography.labelSmall,
		color = color.label,
		modifier = modifier
			.clip(CircleShape)
			.background(color.container)
			.padding(horizontal = 10.dp, vertical = 3.dp),
	)
}

@Preview(name = "Cleveland Clinic", showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewCleveland() = HealthAggregatorTheme { SourceBadge("cleveland-clinic", "Cleveland Clinic") }

@Preview(name = "Summa Health", showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewSumma() = HealthAggregatorTheme { SourceBadge("summa-health", "Summa Health") }

@Preview(name = "Unknown source (fallback)", showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewUnknown() = HealthAggregatorTheme { SourceBadge("kaiser-permanente", "Kaiser Permanente") }
