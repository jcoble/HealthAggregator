package com.healthaggregator.ai

import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class OpenAiClient(
	private val baseUrl: String = "https://api.openai.com/v1",
	private val apiKeyProvider: () -> String?,
	private val http: OkHttpClient = defaultHttp(),
	private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
) : LlmClient {

	override fun stream(
		modelId: String,
		messages: List<LlmMessage>,
		tools: List<ToolSchema>,
		dispatchTool: suspend (name: String, argsJson: String) -> String,
	): Flow<StreamEvent> = channelFlow {
		val apiKey = apiKeyProvider()
		if (apiKey.isNullOrBlank()) {
			trySend(StreamEvent.Error("no_api_key", retryable = false))
			trySend(StreamEvent.Done)
			close()
			return@channelFlow
		}

		val working = messages.toMutableList()

		while (true) {
			val body = buildRequestBody(modelId, working, tools)
			val req = Request.Builder()
				.url("$baseUrl/chat/completions")
				.addHeader("Authorization", "Bearer $apiKey")
				.addHeader("Accept", "text/event-stream")
				.post(body.toString().toRequestBody(JSON_MEDIA))
				.build()

			val accumulated = StringBuilder()
			val accumulatedToolCalls = mutableMapOf<Int, AccumulatedToolCall>()
			var terminalError: StreamEvent.Error? = null
			var sawTools = false
			val completion = kotlinx.coroutines.CompletableDeferred<Unit>()

			val listener = object : EventSourceListener() {
				override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
					if (data == "[DONE]") {
						completion.complete(Unit); return
					}
					val delta = parseDelta(data) ?: return
					delta.content?.let {
						accumulated.append(it)
						trySendBlocking(StreamEvent.TokenDelta(it))
					}
					delta.toolCalls.forEach { tc ->
						val slot = accumulatedToolCalls.getOrPut(tc.index) { AccumulatedToolCall() }
						tc.id?.let { slot.id = it }
						tc.name?.let {
							slot.name = it
							sawTools = true
							trySendBlocking(StreamEvent.ToolCallStarted(slot.id ?: "unknown", it))
						}
						tc.argsJsonChunk?.let { slot.argsJson.append(it) }
					}
				}

				override fun onClosed(eventSource: EventSource) {
					if (!completion.isCompleted) completion.complete(Unit)
				}

				override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
					val code = response?.code ?: -1
					val retryable = code == 429 || code in 500..599 || t != null
					val msg = when {
						code == 401 -> "auth_invalid"
						code == 429 -> "rate_limit"
						code in 500..599 -> "server_error_$code"
						t != null -> "network_error:${t.message}"
						else -> "unknown_error_$code"
					}
					terminalError = StreamEvent.Error(msg, retryable)
					if (!completion.isCompleted) completion.complete(Unit)
				}
			}

			val es = EventSources.createFactory(http).newEventSource(req, listener)
			completion.await()
			es.cancel()

			if (terminalError != null) {
				trySend(terminalError!!)
				trySend(StreamEvent.Done)
				close()
				return@channelFlow
			}

			if (!sawTools) {
				trySend(StreamEvent.Done)
				close()
				return@channelFlow
			}

			val toolCalls = accumulatedToolCalls.values.mapNotNull {
				val id = it.id ?: return@mapNotNull null
				val name = it.name ?: return@mapNotNull null
				LlmToolCall(id, name, it.argsJson.toString())
			}
			working.add(LlmMessage(role = "assistant", content = accumulated.toString(), toolCalls = toolCalls))
			for (call in toolCalls) {
				val result = try {
					dispatchTool(call.name, call.argsJson)
				} catch (e: Exception) {
					"""{"error":"dispatch_failed","message":"${e.message?.replace("\"", "\\\"")}"}"""
				}
				working.add(LlmMessage(role = "tool", content = result, toolCallId = call.id))
				trySend(StreamEvent.ToolCallCompleted(call.id))
			}
		}

		@Suppress("UNREACHABLE_CODE")
		Unit // loop exits only via explicit close(); this point is unreachable
	}

	private fun buildRequestBody(
		modelId: String,
		messages: List<LlmMessage>,
		tools: List<ToolSchema>,
	): JsonObject = buildJsonObject {
		put("model", modelId)
		put("stream", true)
		put("messages", buildJsonArray {
			messages.forEach { m ->
				addJsonObject {
					put("role", m.role)
					put("content", m.content)
					m.toolCallId?.let { put("tool_call_id", it) }
					m.toolCalls?.let { tcs ->
						put("tool_calls", buildJsonArray {
							tcs.forEach { tc ->
								addJsonObject {
									put("id", tc.id)
									put("type", "function")
									put("function", buildJsonObject {
										put("name", tc.name)
										put("arguments", tc.argsJson)
									})
								}
							}
						})
					}
				}
			}
		})
		if (tools.isNotEmpty()) {
			put("tools", buildJsonArray {
				tools.forEach { t ->
					addJsonObject {
						put("type", "function")
						put("function", buildJsonObject {
							put("name", t.name)
							put("description", t.description)
							put("parameters", t.parameters)
						})
					}
				}
			})
		}
	}

	private data class AccumulatedToolCall(
		var id: String? = null,
		var name: String? = null,
		val argsJson: StringBuilder = StringBuilder(),
	)

	private data class Delta(
		val content: String?,
		val toolCalls: List<ToolCallDelta>,
	)

	private data class ToolCallDelta(
		val index: Int,
		val id: String?,
		val name: String?,
		val argsJsonChunk: String?,
	)

	private fun parseDelta(dataLine: String): Delta? {
		val root = try { json.parseToJsonElement(dataLine).jsonObject } catch (_: Exception) { return null }
		val choice = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
		val delta = choice["delta"]?.jsonObject ?: return null
		val content = (delta["content"] as? JsonPrimitive)?.content
		val toolCalls = (delta["tool_calls"] as? JsonArray)?.map {
			val tc = it.jsonObject
			ToolCallDelta(
				index = (tc["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
				id = (tc["id"] as? JsonPrimitive)?.content,
				name = (tc["function"]?.jsonObject?.get("name") as? JsonPrimitive)?.content,
				argsJsonChunk = (tc["function"]?.jsonObject?.get("arguments") as? JsonPrimitive)?.content,
			)
		} ?: emptyList()
		return Delta(content, toolCalls)
	}

	companion object {
		private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
		// SSE streaming timeouts: reasoning models (gpt-5 family) can think for 60+s
		// before emitting the first token. readTimeout=0 disables the per-read timeout
		// (standard for SSE); callTimeout caps total wall time for the whole stream.
		fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
			.connectTimeout(20, TimeUnit.SECONDS)
			.readTimeout(0, TimeUnit.MILLISECONDS)
			.callTimeout(10, TimeUnit.MINUTES)
			.build()
	}
}
