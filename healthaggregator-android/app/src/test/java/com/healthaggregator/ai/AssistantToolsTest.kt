package com.healthaggregator.ai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.entities.SourceRecord
import com.healthaggregator.data.repository.RecordsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AssistantToolsTest {
	private lateinit var db: AppDatabase
	private lateinit var tools: AssistantTools
	private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		val repo = makeRepo()
		tools = AssistantTools(repo, json)
	}

	private fun makeRepo(): RecordsRepository {
		return RecordsRepository(
			labs = db.labDao(),
			vitals = db.vitalsDao(),
			medications = db.medicationDao(),
			conditions = db.conditionDao(),
			allergies = db.allergyDao(),
			encounters = db.encounterDao(),
			documents = db.documentDao(),
			sources = db.medicalDataSourceDao(),
			syncJobs = db.syncJobDao(),
			sourceRecords = db.sourceRecordDao(),
		)
	}

	@After fun tearDown() { db.close() }

	@Test
	fun schemas_expose_three_tools() {
		val names = tools.schemas.map { it.name }
		assertTrue(names.contains("getRawObservation"))
		assertTrue(names.contains("getPanelComponents"))
		assertTrue(names.contains("searchFreeText"))
	}

	@Test
	fun schema_shape_has_required_properties() {
		tools.schemas.forEach { schema ->
			val obj = schema.parameters
			assertEquals("object", (obj["type"] as kotlinx.serialization.json.JsonPrimitive).content)
			assertTrue(schema.name, obj.containsKey("properties"))
			assertTrue(schema.name, obj.containsKey("required"))
		}
	}

	@Test
	fun getRawObservation_returns_stored_raw_json() = runTest {
		db.sourceRecordDao().upsert(SourceRecord(
			sourceSystem = "cc",
			sourceName = "CC",
			resourceType = "Observation",
			resourceId = "abc",
			fhirReference = "Observation/abc",
			rawJson = """{"resourceType":"Observation","id":"abc"}""",
			importedAt = Instant.parse("2026-04-18T00:00:00Z"),
		))
		val result = tools.dispatch(
			"getRawObservation",
			"""{"sourceSystem":"cc","fhirRef":"Observation/abc"}""",
		)
		assertTrue(result.contains("\"resourceType\":\"Observation\""))
	}

	@Test
	fun getRawObservation_missing_returns_not_found() = runTest {
		val result = tools.dispatch(
			"getRawObservation",
			"""{"sourceSystem":"cc","fhirRef":"Observation/missing"}""",
		)
		assertTrue(result.contains("\"error\":\"not_found\""))
	}

	@Test
	fun getPanelComponents_returns_array_of_components() = runTest {
		db.labDao().upsertAll(listOf(
			lab("a", sr = "ServiceRequest/bmp", testName = "Glucose"),
			lab("b", sr = "ServiceRequest/bmp", testName = "Creatinine"),
		))
		val result = tools.dispatch(
			"getPanelComponents",
			"""{"serviceRequestRef":"ServiceRequest/bmp"}""",
		)
		val arr = json.parseToJsonElement(result).jsonArray
		assertEquals(2, arr.size)
		val names = arr.map { it.jsonObject["testName"]!!.jsonPrimitive.content }
		assertTrue(names.contains("Glucose"))
		assertTrue(names.contains("Creatinine"))
	}

	@Test
	fun searchFreeText_finds_matching_rawJson() = runTest {
		db.sourceRecordDao().upsert(SourceRecord(
			sourceSystem = "cc",
			sourceName = "CC",
			resourceType = "DocumentReference",
			resourceId = "doc1",
			fhirReference = "DocumentReference/doc1",
			rawJson = """{"content":"Impression: Borderline metabolic syndrome risk."}""",
			importedAt = Instant.parse("2026-04-18T00:00:00Z"),
		))
		val result = tools.dispatch(
			"searchFreeText",
			"""{"query":"metabolic"}""",
		)
		val arr = json.parseToJsonElement(result).jsonArray
		assertEquals(1, arr.size)
	}

	@Test
	fun unknown_tool_returns_structured_error() = runTest {
		val result = tools.dispatch("doesNotExist", "{}")
		assertTrue(result.contains("\"error\":\"unknown_tool\""))
	}

	@Test
	fun malformed_json_returns_structured_error() = runTest {
		val result = tools.dispatch("getRawObservation", "this is not json")
		assertTrue(result.contains("\"error\":"))
	}

	@Test
	fun missing_arg_returns_structured_error() = runTest {
		val result = tools.dispatch("getRawObservation", """{"sourceSystem":"cc"}""")
		assertTrue(result.contains("missing required arg"))
	}

	private fun lab(
		id: String,
		sr: String,
		testName: String,
	) = LabObservation(
		sourceSystem = "cc",
		sourceName = "CC",
		fhirReference = "Observation/$id",
		resourceId = id,
		testName = testName,
		canonicalTestName = testName,
		serviceRequestReference = sr,
		numericValue = 1.0,
		unit = "",
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
