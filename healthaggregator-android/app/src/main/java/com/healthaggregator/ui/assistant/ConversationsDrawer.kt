package com.healthaggregator.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthaggregator.data.entities.ChatConversation
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ConversationsDrawer(
	conversations: List<ChatConversation>,
	activeId: String?,
	onSelect: (String) -> Unit,
	onNew: () -> Unit,
	onDelete: (String) -> Unit,
) {
	var confirmingDelete by remember { mutableStateOf<ChatConversation?>(null) }
	ModalDrawerSheet(modifier = Modifier.fillMaxHeight().width(320.dp)) {
		Column(modifier = Modifier.padding(12.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
				Text("Conversations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
				Button(onClick = onNew) {
					Icon(Icons.Outlined.Add, contentDescription = "New conversation")
					Spacer(Modifier.width(4.dp))
					Text("New")
				}
			}
			Spacer(Modifier.height(8.dp))
			Divider()
			Spacer(Modifier.height(8.dp))
			if (conversations.isEmpty()) {
				Text(
					"No conversations yet. Tap New to start.",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.secondary,
				)
			} else {
				LazyColumn {
					items(conversations, key = { it.id }) { conv ->
						Row(verticalAlignment = Alignment.CenterVertically) {
							NavigationDrawerItem(
								selected = conv.id == activeId,
								onClick = { onSelect(conv.id) },
								label = {
									Column {
										Text(conv.title, style = MaterialTheme.typography.bodyMedium)
										Text(formatDate(conv.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
									}
								},
								modifier = Modifier.weight(1f),
							)
							IconButton(onClick = { confirmingDelete = conv }) {
								Icon(Icons.Outlined.Delete, contentDescription = "Delete conversation")
							}
						}
					}
				}
			}
		}
	}

	confirmingDelete?.let { conv ->
		AlertDialog(
			onDismissRequest = { confirmingDelete = null },
			title = { Text("Delete conversation?") },
			text = { Text("\"${conv.title}\" and its messages will be removed.") },
			confirmButton = {
				TextButton(onClick = { onDelete(conv.id); confirmingDelete = null }) {
					Text("Delete", color = MaterialTheme.colorScheme.error)
				}
			},
			dismissButton = { TextButton(onClick = { confirmingDelete = null }) { Text("Cancel") } },
		)
	}
}

private fun formatDate(i: java.time.Instant): String =
	DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault()).format(i)
