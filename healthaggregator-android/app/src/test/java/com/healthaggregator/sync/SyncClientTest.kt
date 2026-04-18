package com.healthaggregator.sync

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncClientTest {
	private lateinit var server: MockWebServer
	private lateinit var client: SyncClient

	@Before
	fun setUp() {
		server = MockWebServer().also { it.start() }
		val creds = SyncCredentials(baseUrl = server.url("/").toString().trimEnd('/'), token = "test-token")
		client = SyncClient(
			http = OkHttpClient(),
			json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
			credentialsProvider = { creds },
		)
	}

	@After
	fun tearDown() { server.shutdown() }

	@Test
	fun `push posts JSON and parses response`() = runBlocking {
		server.enqueue(
			MockResponse().setResponseCode(200).setBody(
				"""{"inserted_by_table":{"lab_observations":3},"ignored_by_table":{"lab_observations":0}}"""
			)
		)
		val payload = PushRequest(
			batch_id = "b1",
			rows_by_table = mapOf(
				"lab_observations" to listOf(buildJsonObject { put("fhirReference", "Observation/1") }),
			),
		)
		val response = client.push(payload)
		assertEquals(3, response.inserted_by_table["lab_observations"])
		val recorded = server.takeRequest()
		assertEquals("POST", recorded.method)
		assertEquals("/sync/push", recorded.path)
		assertEquals("Bearer test-token", recorded.getHeader("Authorization"))
	}
}
