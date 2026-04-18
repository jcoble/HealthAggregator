package com.healthaggregator.ai

import kotlinx.coroutines.flow.Flow

/** Provider-agnostic LLM streaming contract. */
interface LlmClient {
	fun stream(
		modelId: String,
		messages: List<LlmMessage>,
		tools: List<ToolSchema>,
		dispatchTool: suspend (name: String, argsJson: String) -> String,
	): Flow<StreamEvent>
}

data class LlmMessage(
	val role: String, // "system" | "user" | "assistant" | "tool"
	val content: String,
	val toolCalls: List<LlmToolCall>? = null,
	val toolCallId: String? = null,
)

data class LlmToolCall(
	val id: String,
	val name: String,
	val argsJson: String,
)

sealed interface StreamEvent {
	data class TokenDelta(val text: String) : StreamEvent
	data class ToolCallStarted(val id: String, val name: String) : StreamEvent
	data class ToolCallCompleted(val id: String) : StreamEvent
	data class Error(val message: String, val retryable: Boolean) : StreamEvent
	data object Done : StreamEvent
}
