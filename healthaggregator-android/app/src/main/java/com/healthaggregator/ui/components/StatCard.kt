package com.healthaggregator.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@Composable
fun StatCard(
	icon: ImageVector,
	label: String,
	value: String,
	subtitle: String? = null,
	onClick: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val base = modifier.fillMaxWidth()
	val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
	Card(
		modifier = clickable,
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
				Spacer(Modifier.width(8.dp))
				Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
			}
			Spacer(Modifier.height(8.dp))
			Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
			if (subtitle != null) {
				Spacer(Modifier.height(2.dp))
				Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
			}
		}
	}
}

@Preview(name = "Basic", showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewStatCard() = HealthAggregatorTheme {
	StatCard(icon = Icons.Outlined.Science, label = "Labs", value = "47", subtitle = "2 abnormal")
}
