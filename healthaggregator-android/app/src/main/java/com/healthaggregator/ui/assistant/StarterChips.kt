package com.healthaggregator.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val DefaultStarterPrompts = listOf(
	"Summarize my recent lab trends",
	"What's abnormal or near-abnormal right now?",
	"How's my A1c trending over the years?",
	"Are any of my labs concerning together?",
	"What patterns do you see in my vitals?",
	"Cross-reference my labs with my medications",
	"Help me prepare questions for my next appointment",
	"What should I ask my doctor about?",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StarterChips(
	prompts: List<String> = DefaultStarterPrompts,
	onPick: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	FlowRow(
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
		modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
	) {
		prompts.forEach { prompt ->
			AssistChip(
				onClick = { onPick(prompt) },
				label = { Text(prompt) },
			)
		}
	}
}
