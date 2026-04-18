package com.healthaggregator.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@Composable
fun OnboardingScreen(onAcknowledge: () -> Unit) {
	var checked by remember { mutableStateOf(false) }
	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
		verticalArrangement = Arrangement.Center,
	) {
		Text(
			text = "AI Health Assistant",
			style = MaterialTheme.typography.headlineMedium,
			fontWeight = FontWeight.SemiBold,
		)
		Spacer(Modifier.height(16.dp))
		Text(
			text = "This assistant analyzes your health data to help you spot trends and ask informed questions. It is not medical advice. Always verify findings with your doctor.",
			style = MaterialTheme.typography.bodyLarge,
		)
		Spacer(Modifier.height(12.dp))
		Text(
			text = "Conversations are sent to OpenAI for processing. If you're using the free tier, your prompts and responses may be used by OpenAI to train future models.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.secondary,
		)
		Spacer(Modifier.height(24.dp))
		Row(verticalAlignment = Alignment.CenterVertically) {
			Checkbox(checked = checked, onCheckedChange = { checked = it })
			Text(
				text = "I understand this is not medical advice and my data will be sent to OpenAI.",
				modifier = Modifier.padding(start = 8.dp),
				style = MaterialTheme.typography.bodyMedium,
			)
		}
		Spacer(Modifier.height(24.dp))
		Button(
			onClick = onAcknowledge,
			enabled = checked,
			modifier = Modifier.fillMaxWidth(),
		) { Text("Get started") }
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 800)
@Composable
private fun PreviewOnboarding() = HealthAggregatorTheme { OnboardingScreen(onAcknowledge = {}) }
