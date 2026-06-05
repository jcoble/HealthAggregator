package com.healthaggregator.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@Composable
fun SearchBar(
	query: String,
	onQueryChange: (String) -> Unit,
	placeholder: String = "Search records",
	modifier: Modifier = Modifier,
) {
	OutlinedTextField(
		value = query,
		onValueChange = onQueryChange,
		placeholder = { Text(placeholder) },
		leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
		trailingIcon = {
			if (query.isNotEmpty()) {
				IconButton(onClick = { onQueryChange("") }) {
					Icon(Icons.Outlined.Close, contentDescription = "Clear")
				}
			}
		},
		singleLine = true,
		keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
		shape = RoundedCornerShape(20.dp),
		modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
	)
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewEmpty() = HealthAggregatorTheme {
	SearchBar(query = "", onQueryChange = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewFilled() = HealthAggregatorTheme {
	SearchBar(query = "hba1c", onQueryChange = {})
}
