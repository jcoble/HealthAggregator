package com.healthaggregator.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OpenAiClientTest {
	private lateinit var server: MockWebServer
	private lateinit var client: OpenAiClient

	@Before
	fun setup() {
		server = MockWebServer()
		server.start()
		val http = OkHttpClient.Builder()
			.readTimeout(5, TimeUnit.SECONDS)
			.callTimeout(10, TimeUnit.SECONDS)
			.build()
		client = OpenAiClient(
			baseUrl = server.url("/v1").toString().trimEnd('/'),
			apiKeyProvider = { "sk-test" },
			http = http,
		)
	}

	@After
	fun tearDown() { server.shutdown() }

	@Test
	fun streams_token_deltas_and_ends_with_done() = runTest {
		server.enqueue(sseResponse(
			"""data: {"choices":[{"delta":{"content":"Hel"}}]}""",
			"""data: {"choices":[{"delta":{"content":"lo"}}]}""",
			"""data: [DONE]""",
		))

		val events = client.stream(
			modelId = "gpt-5",
			messages = listOf(LlmMessage("user", "hi")),
			tools = emptyList(),
			dispatchTool = { _, _ -> "" },
		).toList()

		val tokens = events.filterIsInstance<StreamEvent.TokenDelta>().map { it.text }
		assertEquals(listOf("Hel", "lo"), tokens)
		assertTrue(events.last() is StreamEvent.Done)
	}

	@Test
	fun tool_call_round_trip_emits_started_completed_and_resumes() = runTest {
		server.enqueue(sseResponse(
			"""data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"getRawObservation"}}]}}]}""",
			"""data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"sourceSystem\":\"cc\",\"fhirRef\":\"Observation/abc\"}"}}]}}]}""",
			"""data: [DONE]""",
		))
		server.enqueue(sseResponse(
			"""data: {"choices":[{"delta":{"content":"done"}}]}""",
			"""data: [DONE]""",
		))

		var dispatched: Pair<String, String>? = null
		val events = client.stream(
			modelId = "gpt-5",
			messages = listOf(LlmMessage("user", "look it up")),
			tools = listOf(ToolSchema("getRawObservation", "fetch raw", buildJsonObject { put("type", "object") })),
			dispatchTool = { name, args -> dispatched = name to args; """{"resourceType":"Observation"}""" },
		).toList()

		assertEquals("getRawObservation", dispatched!!.first)
		assertTrue(dispatched!!.second.contains("Observation/abc"))

		assertTrue(events.any { it is StreamEvent.ToolCallStarted && it.name == "getRawObservation" })
		assertTrue(events.any { it is StreamEvent.ToolCallCompleted })
		val finalText = events.filterIsInstance<StreamEvent.TokenDelta>().joinToString("") { it.text }
		assertEquals("done", finalText)
		assertTrue(events.last() is StreamEvent.Done)
	}

	@Test
	fun rate_limit_emits_retryable_error() = runTest {
		server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"rate"}}"""))

		val events = client.stream(
			modelId = "gpt-5",
			messages = listOf(LlmMessage("user", "hi")),
			tools = emptyList(),
			dispatchTool = { _, _ -> "" },
		).toList()

		val err = events.filterIsInstance<StreamEvent.Error>().firstOrNull()
		assertTrue(err != null)
		assertEquals("rate_limit", err!!.message)
		assertTrue(err.retryable)
	}

	@Test
	fun auth_error_emits_non_retryable() = runTest {
		server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))

		val events = client.stream(
			modelId = "gpt-5",
			messages = listOf(LlmMessage("user", "hi")),
			tools = emptyList(),
			dispatchTool = { _, _ -> "" },
		).toList()

		val err = events.filterIsInstance<StreamEvent.Error>().firstOrNull()
		assertEquals("auth_invalid", err!!.message)
		assertFalse(err.retryable)
	}

	@Test
	fun missing_api_key_short_circuits_with_no_api_key_error() = runTest {
		val noKeyClient = OpenAiClient(
			baseUrl = server.url("/v1").toString().trimEnd('/'),
			apiKeyProvider = { null },
		)
		val events = noKeyClient.stream(
			modelId = "gpt-5",
			messages = listOf(LlmMessage("user", "hi")),
			tools = emptyList(),
			dispatchTool = { _, _ -> "" },
		).toList()

		val err = events.filterIsInstance<StreamEvent.Error>().firstOrNull()
		assertEquals("no_api_key", err!!.message)
		assertEquals(0, server.requestCount)
	}

	private fun sseResponse(vararg lines: String): MockResponse = MockResponse()
		.setHeader("Content-Type", "text/event-stream")
		.setBody(lines.joinToString("\n\n") + "\n\n")
}
