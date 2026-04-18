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
	private lateinit var migrationLoader: MigrationLoader
	private lateinit var repo: SyncRepository

	@Before
	fun setUp() {
		val ctx: Context = ApplicationProvider.getApplicationContext()
		db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
		fakeClient = FakeSyncClient()
		migrationLoader = MigrationLoader(ctx)
		repo = SyncRepository(
			client = fakeClient,
			db = db,
			serializer = RowSerializer(),
			migrationLoader = migrationLoader,
			phoneSchemaVersion = 5,
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
				"patients" to listOf(patientJson("LabCorp", "Patient/lc1")),
			),
		)

		val result = repo.syncNow().getOrThrow()
		assertEquals(1, result.pushedRowsByTable["patients"] ?: 0)
		assertEquals(1, result.pulledRowsByTable["patients"] ?: 0)

		val allPatients = db.patientDao().getAllSnapshot()
		assertEquals(2, allPatients.size)
		assertTrue(allPatients.any { it.sourceSystem == "LabCorp" })
	}

	@Test
	fun `syncNow applies migrations when daemon schema is behind`() = runBlocking {
		fakeClient.versionResponse = VersionResponse(schema_version = 3, daemon_version = "0.1.0")
		val result = repo.syncNow().getOrThrow()
		assertEquals(listOf(3 to 4, 4 to 5), result.migrationsApplied)
		assertEquals(2, fakeClient.migrateCalls.size)
		assertEquals(3, fakeClient.migrateCalls[0].from_version)
		assertEquals(4, fakeClient.migrateCalls[0].to_version)
		assertEquals(4, fakeClient.migrateCalls[1].from_version)
		assertEquals(5, fakeClient.migrateCalls[1].to_version)
	}

	@Test
	fun `syncNow returns failure when not paired`() = runBlocking {
		val unpairedRepo = SyncRepository(
			client = fakeClient,
			db = db,
			serializer = RowSerializer(),
			migrationLoader = migrationLoader,
			phoneSchemaVersion = 5,
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
		var versionResponse = VersionResponse(schema_version = 5, daemon_version = "0.1.0")
		val migrateCalls = mutableListOf<MigrateRequest>()
		override suspend fun version(): VersionResponse = versionResponse
		override suspend fun push(payload: PushRequest): PushResponse {
			pushedBatches.add(payload)
			val counts = payload.rows_by_table.mapValues { it.value.size }
			return PushResponse(inserted_by_table = counts, ignored_by_table = counts.mapValues { 0 })
		}
		override suspend fun pull(): PullResponse = pullResponse
		override suspend fun migrate(payload: MigrateRequest): MigrateResponse {
			migrateCalls.add(payload)
			return MigrateResponse(applied = listOf(AppliedMigration(payload.from_version, payload.to_version)))
		}
	}
}
