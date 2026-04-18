package com.healthaggregator.ui.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
	onCitationClick: (source: String, fhirRef: String) -> Unit,
	onOpenSettings: () -> Unit,
	viewModel: AssistantViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()

	if (!state.disclaimerAcknowledged) {
		OnboardingScreen(onAcknowledge = { viewModel.acknowledgeDisclaimer() })
		return
	}
	if (state.apiKeyMissing) {
		ApiKeyMissingCard(onOpenSettings)
		return
	}

	val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
	val scope = rememberCoroutineScope()

	ModalNavigationDrawer(
		drawerState = drawerState,
		drawerContent = {
			ConversationsDrawer(
				conversations = state.conversations,
				activeId = state.activeConversationId,
				onSelect = { id ->
					viewModel.selectConversation(id)
					scope.launch { drawerState.close() }
				},
				onNew = {
					viewModel.newConversation()
					scope.launch { drawerState.close() }
				},
				onDelete = viewModel::deleteConversation,
			)
		},
	) {
		Column(modifier = Modifier.fillMaxSize()) {
			TopAppBar(
				title = {
					val title = state.conversations.firstOrNull { it.id == state.activeConversationId }?.title ?: "Assistant"
					Text(title)
				},
				navigationIcon = {
					IconButton(onClick = { scope.launch { drawerState.open() } }) {
						Icon(Icons.Outlined.Menu, contentDescription = "Conversations")
					}
				},
				actions = {
					var showExport by remember { mutableStateOf(false) }
					IconButton(
						onClick = { showExport = true },
						enabled = state.activeConversationId != null && state.messages.isNotEmpty(),
					) {
						Icon(Icons.Outlined.Share, contentDescription = "Export")
					}
					if (showExport) {
						ExportDialog(
							onDismiss = { showExport = false },
							onExportMarkdown = viewModel::exportMarkdown,
							onExportPdf = viewModel::exportPdf,
						)
					}
				},
			)
			state.error?.let {
				Text(
					text = "Error: $it",
					color = MaterialTheme.colorScheme.error,
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
				)
			}
			ChatPane(
				messages = state.messages,
				streaming = state.streaming,
				activeToolCallCount = state.activeToolCalls.size,
				starterPrompts = DefaultStarterPrompts,
				onSend = viewModel::send,
				onCitationClick = onCitationClick,
				modifier = Modifier.fillMaxSize(),
			)
		}
	}
}

@Composable
private fun ApiKeyMissingCard(onOpenSettings: () -> Unit) {
	Column(
		modifier = Modifier.fillMaxSize().padding(24.dp),
	) {
		Text("API key required", style = MaterialTheme.typography.titleLarge)
		Spacer(Modifier.height(8.dp))
		Text(
			"Set an OpenAI API key in Settings → AI Assistant to start chatting.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.secondary,
		)
		Spacer(Modifier.height(16.dp))
		androidx.compose.material3.Button(onClick = onOpenSettings) { Text("Open Settings") }
	}
}
