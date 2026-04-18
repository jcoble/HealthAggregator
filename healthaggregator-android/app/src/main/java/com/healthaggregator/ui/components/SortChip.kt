package com.healthaggregator.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.healthaggregator.ui.theme.HealthAggregatorTheme

enum class SortOrder { NEWEST_FIRST, OLDEST_FIRST }

fun SortOrder.toggled(): SortOrder = if (this == SortOrder.NEWEST_FIRST) SortOrder.OLDEST_FIRST else SortOrder.NEWEST_FIRST

@Composable
fun SortChip(order: SortOrder, onToggle: () -> Unit, modifier: Modifier = Modifier) {
	AssistChip(
		onClick = onToggle,
		label = { Text(if (order == SortOrder.NEWEST_FIRST) "Newest" else "Oldest") },
		leadingIcon = {
			Icon(
				imageVector = if (order == SortOrder.NEWEST_FIRST) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
				contentDescription = null,
			)
		},
		modifier = modifier,
	)
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewNewest() = HealthAggregatorTheme {
	SortChip(order = SortOrder.NEWEST_FIRST, onToggle = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewOldest() = HealthAggregatorTheme {
	SortChip(order = SortOrder.OLDEST_FIRST, onToggle = {})
}
