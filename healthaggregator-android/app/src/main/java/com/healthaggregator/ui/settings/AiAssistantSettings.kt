package com.healthaggregator.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.healthaggregator.util.SecureStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSettings(secure: SecureStorage, onClearHistory: () -> Unit) {
	var key by remember { mutableStateOf(secure.openAiApiKey ?: "") }
	var dataSharing by remember { mutableStateOf(secure.dataSharingEnabled) }
	var model by remember { mutableStateOf(secure.selectedModel) }
	var keyVisible by remember { mutableStateOf(false) }
	var modelMenuOpen by remember { mutableStateOf(false) }
	var confirmClear by remember { mutableStateOf(false) }

	Column {
		Text("OpenAI API key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
		Spacer(Modifier.height(4.dp))
		OutlinedTextField(
			value = key,
			onValueChange = {
				key = it
				secure.openAiApiKey = it.trim()
			},
			placeholder = { Text("sk-…") },
			modifier = Modifier.fillMaxWidth(),
			singleLine = true,
			visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
			trailingIcon = {
				IconButton(onClick = { keyVisible = !keyVisible }) {
					Icon(
						imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
						contentDescription = if (keyVisible) "Hide key" else "Show key",
					)
				}
			},
		)
		Spacer(Modifier.height(12.dp))

		Text("Model", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
		Spacer(Modifier.height(4.dp))
		Row(verticalAlignment = Alignment.CenterVertically) {
			OutlinedButton(
				onClick = { modelMenuOpen = true },
				modifier = Modifier.fillMaxWidth(),
			) {
				val label = SecureStorage.AVAILABLE_MODELS.firstOrNull { it.first == model }?.second ?: model
				Text(label, modifier = Modifier.weight(1f))
				Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
			}
			DropdownMenu(
				expanded = modelMenuOpen,
				onDismissRequest = { modelMenuOpen = false },
			) {
				SecureStorage.AVAILABLE_MODELS.forEach { (id, label) ->
					DropdownMenuItem(
						text = { Text(label) },
						onClick = { model = id; secure.selectedModel = id; modelMenuOpen = false },
					)
				}
			}
		}
		Spacer(Modifier.height(12.dp))

		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(modifier = Modifier.weight(1f)) {
				Text("Share prompts with OpenAI", style = MaterialTheme.typography.bodyMedium)
				Text(
					"Required for the gpt-5 free-token tier. Disable to use paid billing only.",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.secondary,
				)
			}
			Switch(
				checked = dataSharing,
				onCheckedChange = {
					dataSharing = it
					secure.dataSharingEnabled = it
				},
			)
		}
		Spacer(Modifier.height(12.dp))

		OutlinedButton(
			onClick = { confirmClear = true },
			modifier = Modifier.fillMaxWidth(),
			colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
		) { Text("Clear all chat history") }
	}

	if (confirmClear) {
		AlertDialog(
			onDismissRequest = { confirmClear = false },
			title = { Text("Clear all chat history?") },
			text = { Text("Every conversation and message will be deleted. This cannot be undone.") },
			confirmButton = {
				TextButton(onClick = { onClearHistory(); confirmClear = false }) {
					Text("Clear", color = MaterialTheme.colorScheme.error)
				}
			},
			dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
		)
	}
}
