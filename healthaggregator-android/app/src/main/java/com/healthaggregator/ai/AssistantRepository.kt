package com.healthaggregator.ai

import com.healthaggregator.data.dao.ChatDao
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import com.healthaggregator.util.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantRepository @Inject constructor(
	private val chat: ChatDao,
	private val llm: LlmClient,
	private val tools: AssistantTools,
	private val snapshots: HealthSnapshotBuilder,
	private val secure: SecureStorage,
) {
	fun observeConversations(): Flow<List<ChatConversation>> = chat.observeConversations()
	fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = chat.observeMessages(conversationId)

	suspend fun createConversation(title: String = "New chat"): ChatConversation {
		val now = Instant.now()
		val c = ChatConversation(
			id = UUID.randomUUID().toString(),
			title = title,
			createdAt = now,
			updatedAt = now,
			modelId = secure.selectedModel,
		)
		chat.upsertConversation(c)
		return c
	}

	suspend fun renameConversation(id: String, title: String) {
		chat.renameConversation(id, title, Instant.now())
	}

	suspend fun deleteConversation(id: String) {
		chat.deleteConversation(id)
	}

	suspend fun clearAll() {
		chat.deleteAllConversations()
	}

	suspend fun clearAllChatHistory() { chat.deleteAllConversations() }

	fun send(conversationId: String, userText: String): Flow<StreamEvent> = flow {
		val now = Instant.now()
		val userMsg = ChatMessage(
			id = UUID.randomUUID().toString(),
			conversationId = conversationId,
			role = "user",
			content = userText,
			createdAt = now,
		)
		chat.upsertMessage(userMsg)
		chat.touch(conversationId, now)

		val conversation = chat.getConversation(conversationId) ?: error("conversation not found: $conversationId")

		if (conversation.title == "New chat") {
			chat.renameConversation(conversationId, userText.take(60).trim().ifEmpty { "Chat" }, now)
		}

		val snapshot: String = conversation.snapshotText ?: run {
			val built = snapshots.build(now)
			chat.setSnapshot(conversationId, built, now)
			built
		}

		val priorMessages = chat.messagesSnapshot(conversationId).filter { it.role == "user" || it.role == "assistant" || it.role == "tool" }
		val llmMessages = buildList {
			add(LlmMessage(role = "system", content = SystemPrompt.TEXT))
			add(LlmMessage(role = "user", content = snapshot))
			priorMessages.forEach { msg ->
				add(LlmMessage(
					role = msg.role,
					content = msg.content,
					toolCallId = msg.toolCallId,
				))
			}
		}

		val assistantBuffer = StringBuilder()
		val assistantId = UUID.randomUUID().toString()
		val assistantMsg = ChatMessage(
			id = assistantId,
			conversationId = conversationId,
			role = "assistant",
			content = "",
			modelId = conversation.modelId,
			createdAt = Instant.now(),
		)
		chat.upsertMessage(assistantMsg)

		llm.stream(
			modelId = conversation.modelId,
			messages = llmMessages,
			tools = tools.schemas,
			dispatchTool = { name, args -> tools.dispatch(name, args) },
		).collect { ev ->
			when (ev) {
				is StreamEvent.TokenDelta -> {
					assistantBuffer.append(ev.text)
					chat.upsertMessage(assistantMsg.copy(content = assistantBuffer.toString(), createdAt = Instant.now()))
				}
				is StreamEvent.ToolCallStarted, is StreamEvent.ToolCallCompleted -> { }
				is StreamEvent.Error, StreamEvent.Done -> {}
			}
			emit(ev)
		}

		chat.touch(conversationId, Instant.now())
	}
}
