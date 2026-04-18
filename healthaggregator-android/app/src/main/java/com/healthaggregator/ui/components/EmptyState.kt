package com.healthaggregator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@Composable
fun EmptyState(
	icon: ImageVector,
	title: String,
	description: String,
	actionLabel: String? = null,
	onAction: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier.fillMaxSize().padding(32.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(64.dp))
		Spacer(Modifier.height(16.dp))
		Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
		Spacer(Modifier.height(4.dp))
		Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
		if (actionLabel != null && onAction != null) {
			Spacer(Modifier.height(16.dp))
			Button(onClick = onAction) { Text(actionLabel) }
		}
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewEmptyState() = HealthAggregatorTheme {
	EmptyState(Icons.Outlined.Inbox, "No labs yet", "Sync from Health Connect to get started.", actionLabel = "Sync now", onAction = {})
}
