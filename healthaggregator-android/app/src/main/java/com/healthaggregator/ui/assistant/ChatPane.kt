package com.healthaggregator.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthaggregator.data.entities.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPane(
	messages: List<ChatMessage>,
	streaming: Boolean,
	activeToolCallCount: Int,
	starterPrompts: List<String>,
	onSend: (String) -> Unit,
	onCitationClick: (String, String) -> Unit,
	modifier: Modifier = Modifier,
) {
	var input by remember { mutableStateOf("") }
	val listState = rememberLazyListState()

	LaunchedEffect(messages.size, streaming) {
		if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
	}

	Column(modifier = modifier.fillMaxSize()) {
		LazyColumn(
			state = listState,
			modifier = Modifier.weight(1f).fillMaxWidth(),
			contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
		) {
			if (messages.isEmpty()) {
				item {
					EmptyChatHint(starterPrompts) { input = it }
				}
			} else {
				items(messages, key = { it.id }) { msg ->
					MessageBubble(msg, onCitationClick)
				}
				if (streaming && activeToolCallCount > 0) {
					item { ToolWorkingIndicator(activeToolCallCount) }
				}
			}
		}

		Row(
			modifier = Modifier.fillMaxWidth().padding(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			OutlinedTextField(
				value = input,
				onValueChange = { input = it },
				placeholder = { Text("Ask about your health data…") },
				modifier = Modifier.weight(1f),
				enabled = !streaming,
				maxLines = 4,
			)
			Spacer(Modifier.width(8.dp))
			IconButton(
				onClick = {
					val text = input.trim()
					if (text.isNotEmpty() && !streaming) {
						onSend(text)
						input = ""
					}
				},
				enabled = input.trim().isNotEmpty() && !streaming,
			) {
				if (streaming) {
					CircularProgressIndicator(modifier = Modifier.height(24.dp).width(24.dp))
				} else {
					Icon(Icons.Filled.Send, contentDescription = "Send")
				}
			}
		}
	}
}

@Composable
private fun EmptyChatHint(starterPrompts: List<String>, onPick: (String) -> Unit) {
	Column(
		modifier = Modifier.fillMaxWidth().padding(24.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text("Ask about your data.", style = MaterialTheme.typography.titleLarge)
		Spacer(Modifier.height(8.dp))
		Text(
			"Everything you send includes your full health snapshot. Pick a suggested prompt or type your own.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.secondary,
		)
		Spacer(Modifier.height(16.dp))
		StarterChips(prompts = starterPrompts, onPick = onPick)
	}
}

@Composable
private fun ToolWorkingIndicator(count: Int) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.Start,
	) {
		CircularProgressIndicator(modifier = Modifier.height(12.dp).width(12.dp), strokeWidth = 1.dp)
		Spacer(Modifier.width(8.dp))
		Text(
			text = if (count == 1) "Looking up data…" else "Looking up data ($count requests)…",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.secondary,
		)
	}
}
