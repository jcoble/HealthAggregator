package com.healthaggregator.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import java.time.Duration
import java.time.Instant

@Composable
fun SyncStatusChip(
	lastSyncAt: Instant?,
	isSyncing: Boolean,
	onRefresh: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val label = when {
		isSyncing -> "Syncing\u2026"
		lastSyncAt == null -> "Never synced"
		else -> "Last synced " + humanizeAgo(lastSyncAt)
	}
	AssistChip(
		onClick = { if (!isSyncing) onRefresh() },
		label = { Text(label) },
		leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", modifier = Modifier) },
		modifier = modifier,
		colors = AssistChipDefaults.assistChipColors(),
	)
}

private fun humanizeAgo(at: Instant): String {
	val d = Duration.between(at, Instant.now())
	return when {
		d.toMinutes() < 1 -> "just now"
		d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
		d.toHours() < 24 -> "${d.toHours()} h ago"
		else -> "${d.toDays()} d ago"
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewNever() = HealthAggregatorTheme { SyncStatusChip(null, false, {}) }

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewRecent() = HealthAggregatorTheme {
	SyncStatusChip(Instant.now().minusSeconds(600), false, {})
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewSyncing() = HealthAggregatorTheme {
	SyncStatusChip(Instant.now().minusSeconds(3600), true, {})
}
