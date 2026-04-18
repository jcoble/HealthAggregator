package com.healthaggregator.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.VitalsObservation
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
class VitalsDaoTest {
	private lateinit var db: AppDatabase
	private lateinit var dao: VitalsDao

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		dao = db.vitalsDao()
	}

	@After
	fun tearDown() { db.close() }

	@Test
	fun bp_components_coexist_as_separate_rows() = runTest {
		val systolic = sampleVital(componentCode = "8480-6", code = "BP systolic", numericValue = 120.0)
		val diastolic = sampleVital(componentCode = "8462-4", code = "BP diastolic", numericValue = 80.0)
		dao.upsertAll(listOf(systolic, diastolic))
		val all = dao.observeAll().first()
		assertEquals(2, all.size)
	}

	private fun sampleVital(
		sourceSystem: String = "cleveland-clinic",
		fhirReference: String = "Observation/bp-1",
		componentCode: String? = null,
		code: String = "Vital",
		numericValue: Double? = 120.0,
		unit: String? = "mmHg",
	) = VitalsObservation(
		sourceSystem = sourceSystem,
		sourceName = "Cleveland Clinic",
		fhirReference = fhirReference,
		resourceId = fhirReference.substringAfter('/'),
		code = code,
		displayName = code,
		numericValue = numericValue,
		unit = unit,
		componentCode = componentCode,
		effectiveAt = Instant.parse("2024-01-01T00:00:00Z"),
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
