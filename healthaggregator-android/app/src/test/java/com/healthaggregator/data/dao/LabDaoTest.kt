package com.healthaggregator.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.LabObservation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LabDaoTest {
	private lateinit var db: AppDatabase
	private lateinit var dao: LabDao

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		dao = db.labDao()
	}

	@After
	fun tearDown() { db.close() }

	@Test
	fun upsert_is_idempotent_by_sourceSystem_and_fhirReference() = runTest {
		val lab = sampleLab(fhirReference = "Observation/abc")
		dao.upsert(lab)
		dao.upsert(lab.copy(testName = "HbA1c v2"))
		val all = dao.getAllSnapshot()
		assertEquals(1, all.size)
		assertEquals("HbA1c v2", all.first().testName)
	}

	@Test
	fun observeAll_orders_newest_first() = runTest {
		dao.upsert(sampleLab(fhirReference = "Observation/1", effectiveAt = Instant.parse("2024-01-01T00:00:00Z")))
		dao.upsert(sampleLab(fhirReference = "Observation/2", effectiveAt = Instant.parse("2025-06-01T00:00:00Z")))
		val first = dao.observeAll().first()
		assertEquals(2, first.size)
		assertEquals("Observation/2", first[0].fhirReference)
	}

	@Test
	fun observeBySource_filters() = runTest {
		dao.upsert(sampleLab(sourceSystem = "cleveland-clinic", fhirReference = "Observation/1"))
		dao.upsert(sampleLab(sourceSystem = "summa-health", fhirReference = "Observation/2"))
		val cc = dao.observeBySource("cleveland-clinic").first()
		assertEquals(1, cc.size)
		assertEquals("cleveland-clinic", cc.first().sourceSystem)
	}

	private fun sampleLab(
		sourceSystem: String = "cleveland-clinic",
		fhirReference: String = "Observation/abc",
		effectiveAt: Instant? = Instant.parse("2024-01-01T00:00:00Z"),
	) = LabObservation(
		sourceSystem = sourceSystem,
		sourceName = "Cleveland Clinic",
		fhirReference = fhirReference,
		resourceId = fhirReference.substringAfter('/'),
		testName = "HbA1c",
		effectiveAt = effectiveAt,
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
