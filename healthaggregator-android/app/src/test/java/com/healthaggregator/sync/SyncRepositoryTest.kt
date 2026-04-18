package com.healthaggregator.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.PatientRecord
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncRepositoryTest {
	private lateinit var db: AppDatabase
	private lateinit var fakeClient: FakeSyncClient
	private lateinit var repo: SyncRepository

	@Before
	fun setUp() {
		val ctx: Context = ApplicationProvider.getApplicationContext()
		db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
		fakeClient = FakeSyncClient()
		repo = SyncRepository(
			client = fakeClient,
			db = db,
			serializer = RowSerializer(),
			isPairedProvider = { true },
		)
	}

	@After
	fun tearDown() { db.close() }

	@Test
	fun `syncNow pushes local rows then merges pull response`() = runBlocking {
		val localPatient = makePatientRecord("EpicCleveland", "Patient/5")
		db.patientDao().upsertAll(listOf(localPatient))

		fakeClient.pullResponse = PullResponse(
			rows_by_table = mapOf(
				"patient_records" to listOf(patientJson("LabCorp", "Patient/lc1")),
			),
		)

		val result = repo.syncNow().getOrThrow()
		assertEquals(1, result.pushedRowsByTable["patient_records"] ?: 0)
		assertEquals(1, result.pulledRowsByTable["patient_records"] ?: 0)

		val allPatients = db.patientDao().getAllSnapshot()
		assertEquals(2, allPatients.size)
		assertTrue(allPatients.any { it.sourceSystem == "LabCorp" })
	}

	@Test
	fun `syncNow returns failure when not paired`() = runBlocking {
		val unpairedRepo = SyncRepository(
			client = fakeClient,
			db = db,
			serializer = RowSerializer(),
			isPairedProvider = { false },
		)
		val result = unpairedRepo.syncNow()
		assertTrue(result.isFailure)
		assertTrue(result.exceptionOrNull() is SyncError.NotPaired)
	}

	private fun makePatientRecord(sourceSystem: String, fhirId: String) = PatientRecord(
		id = 0L,
		sourceSystem = sourceSystem,
		fhirId = fhirId,
		displayName = "Test Patient",
		birthDate = "1990-01-01",
		updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
	)

	private fun patientJson(sourceSystem: String, fhirId: String) = buildJsonObject {
		put("id", JsonPrimitive(0L))
		put("sourceSystem", JsonPrimitive(sourceSystem))
		put("fhirId", JsonPrimitive(fhirId))
		put("displayName", JsonPrimitive("Remote Patient"))
		put("birthDate", JsonPrimitive("1985-06-15"))
		put("updatedAt", JsonPrimitive(Instant.parse("2026-02-01T00:00:00Z").toEpochMilli()))
	}

	private class FakeSyncClient : SyncClient(okhttp3.OkHttpClient(), kotlinx.serialization.json.Json { }, { null }) {
		var pushedBatches = mutableListOf<PushRequest>()
		var pullResponse = PullResponse(rows_by_table = emptyMap())
		override suspend fun push(payload: PushRequest): PushResponse {
			pushedBatches.add(payload)
			val counts = payload.rows_by_table.mapValues { it.value.size }
			return PushResponse(inserted_by_table = counts, ignored_by_table = counts.mapValues { 0 })
		}
		override suspend fun pull(): PullResponse = pullResponse
	}
}
