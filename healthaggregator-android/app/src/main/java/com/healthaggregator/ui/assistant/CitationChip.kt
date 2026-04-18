package com.healthaggregator.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

@Composable
fun CitationChip(
	sourceSystem: String,
	fhirRef: String,
	label: String = sourceSystem,
	onClick: (String, String) -> Unit,
) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.clip(RoundedCornerShape(8.dp))
			.background(MaterialTheme.colorScheme.surfaceVariant)
			.clickable { onClick(sourceSystem, fhirRef) }
			.padding(horizontal = 8.dp, vertical = 2.dp),
	) {
		Icon(
			imageVector = Icons.Filled.Link,
			contentDescription = "Citation link",
			modifier = Modifier.size(12.dp),
			tint = MaterialTheme.colorScheme.primary,
		)
		Spacer(Modifier.width(4.dp))
		Text(
			text = label,
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
