package com.healthaggregator.ui.assistant

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MedicalAdviceDisclaimer(modifier: Modifier = Modifier) {
	Text(
		text = "Not medical advice",
		style = MaterialTheme.typography.labelSmall,
		color = MaterialTheme.colorScheme.secondary,
		modifier = modifier.padding(4.dp),
	)
}
