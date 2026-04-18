package com.healthaggregator.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@Composable
fun <T> FilterChipRow(
	options: List<T>,
	selected: T,
	onSelect: (T) -> Unit,
	label: (T) -> String,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		options.forEach { opt ->
			FilterChip(
				selected = opt == selected,
				onClick = { onSelect(opt) },
				label = { Text(label(opt)) },
			)
		}
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewFilters() = HealthAggregatorTheme {
	val items = listOf("All", "Labs", "Vitals", "Meds", "Conditions")
	FilterChipRow(options = items, selected = "Labs", onSelect = {}, label = { it })
}
