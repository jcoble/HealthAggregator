package com.healthaggregator.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordRow(
	icon: ImageVector,
	title: String,
	summary: String,
	sourceSystem: String,
	sourceName: String?,
	trailingText: String? = null,
	onClick: (() -> Unit)? = null,
	onLongClick: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val base = modifier.fillMaxWidth()
	val clickable = if (onClick != null || onLongClick != null) {
		base.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
	} else base
	Row(
		modifier = clickable.padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
		Spacer(Modifier.width(12.dp))
		Column(modifier = Modifier.weight(1f)) {
			Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
			Spacer(Modifier.size(2.dp))
			Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
			Spacer(Modifier.size(4.dp))
			SourceBadge(sourceSystem = sourceSystem, sourceName = sourceName)
		}
		if (trailingText != null) {
			Spacer(Modifier.width(12.dp))
			Text(trailingText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
		}
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun PreviewRecordRow() {
	com.healthaggregator.ui.theme.HealthAggregatorTheme {
		RecordRow(
			icon = Icons.Outlined.Science,
			title = "Hemoglobin A1c",
			summary = "5.8% — 2024-03-15",
			sourceSystem = "cleveland-clinic",
			sourceName = "Cleveland Clinic",
			trailingText = "H",
		)
	}
}
