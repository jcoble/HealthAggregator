package com.healthaggregator.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.ai.AssistantRepository
import com.healthaggregator.ai.StreamEvent
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import com.healthaggregator.util.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssistantUiState(
	val conversations: List<ChatConversation> = emptyList(),
	val activeConversationId: String? = null,
	val messages: List<ChatMessage> = emptyList(),
	val streaming: Boolean = false,
	val error: String? = null,
	val apiKeyMissing: Boolean = false,
	val disclaimerAcknowledged: Boolean = false,
	val activeToolCalls: Set<String> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssistantViewModel @Inject constructor(
	private val repo: AssistantRepository,
	private val secure: SecureStorage,
) : ViewModel() {

	private val activeId = MutableStateFlow<String?>(null)
	private val streaming = MutableStateFlow(false)
	private val error = MutableStateFlow<String?>(null)
	private val activeToolCalls = MutableStateFlow<Set<String>>(emptySet())

	private val conversations: StateFlow<List<ChatConversation>> =
		repo.observeConversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

	private val messages: StateFlow<List<ChatMessage>> =
		activeId.flatMapLatest { id ->
			if (id == null) emptyFlow() else repo.observeMessages(id)
		}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

	val uiState: StateFlow<AssistantUiState> =
		kotlinx.coroutines.flow.combine(
			conversations, activeId, messages, streaming, error, activeToolCalls,
		) { arr ->
			@Suppress("UNCHECKED_CAST")
			AssistantUiState(
				conversations = arr[0] as List<ChatConversation>,
				activeConversationId = arr[1] as String?,
				messages = arr[2] as List<ChatMessage>,
				streaming = arr[3] as Boolean,
				error = arr[4] as String?,
				activeToolCalls = arr[5] as Set<String>,
				apiKeyMissing = secure.openAiApiKey.isNullOrBlank(),
				disclaimerAcknowledged = secure.disclaimerAcknowledged,
			)
		}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState())

	fun selectConversation(id: String) { activeId.value = id }

	fun newConversation() {
		viewModelScope.launch {
			val c = repo.createConversation()
			activeId.value = c.id
		}
	}

	fun renameActive(newTitle: String) {
		val id = activeId.value ?: return
		viewModelScope.launch { repo.renameConversation(id, newTitle) }
	}

	fun deleteConversation(id: String) {
		viewModelScope.launch {
			repo.deleteConversation(id)
			if (activeId.value == id) activeId.value = null
		}
	}

	fun send(text: String) {
		val trimmed = text.trim()
		if (trimmed.isEmpty()) return
		val id = activeId.value
		if (id == null) {
			viewModelScope.launch {
				val c = repo.createConversation()
				activeId.value = c.id
				runStream(c.id, trimmed)
			}
			return
		}
		viewModelScope.launch { runStream(id, trimmed) }
	}

	fun acknowledgeDisclaimer() {
		secure.disclaimerAcknowledged = true
	}

	fun clearError() { error.value = null }

	private suspend fun runStream(id: String, text: String) {
		streaming.value = true
		error.value = null
		try {
			repo.send(id, text).collect { ev ->
				when (ev) {
					is StreamEvent.ToolCallStarted -> activeToolCalls.update { it + ev.id }
					is StreamEvent.ToolCallCompleted -> activeToolCalls.update { it - ev.id }
					is StreamEvent.Error -> error.value = ev.message
					StreamEvent.Done, is StreamEvent.TokenDelta -> {}
				}
			}
		} catch (e: Exception) {
			error.value = e.message ?: "unknown_error"
		} finally {
			streaming.value = false
			activeToolCalls.value = emptySet()
		}
	}
}
