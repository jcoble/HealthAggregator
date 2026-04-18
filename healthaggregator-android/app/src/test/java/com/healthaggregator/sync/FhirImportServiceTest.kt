package com.healthaggregator.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.TestFhirFixtures
import com.healthaggregator.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FhirImportServiceTest {
	private lateinit var db: AppDatabase
	private lateinit var service: FhirImportService

	@Before fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		service = FhirImportService(db)
	}

	@After fun tearDown() { db.close() }

	@Test fun lab_observation_routes_to_LabDao() = runTest {
		val counts = service.importResources("cleveland-clinic", "Cleveland Clinic", listOf(TestFhirFixtures.LAB_HBA1C))
		assertEquals(1, counts.labs)
		assertEquals(1, counts.sourceRecords)
		val labs = db.labDao().observeAll().first()
		assertEquals("Hemoglobin A1c", labs.first().testName)
		assertEquals("4548-4", labs.first().loincCode)
		assertEquals(5.8, labs.first().numericValue!!, 0.001)
		assertEquals("H", labs.first().interpretation)
	}

	@Test fun bp_observation_creates_two_component_rows() = runTest {
		val counts = service.importResources("cleveland-clinic", "Cleveland Clinic", listOf(TestFhirFixtures.VITAL_BP))
		assertEquals(2, counts.vitals)
		val vitals = db.vitalsDao().observeAll().first()
		assertEquals(2, vitals.size)
		val systolic = vitals.find { it.componentCode == "8480-6" }
		val diastolic = vitals.find { it.componentCode == "8462-4" }
		assertNotNull(systolic); assertNotNull(diastolic)
		assertEquals(120.0, systolic!!.numericValue!!, 0.001)
		assertEquals(80.0, diastolic!!.numericValue!!, 0.001)
	}

	@Test fun condition_routes_to_ConditionDao() = runTest {
		val counts = service.importResources("cleveland-clinic", "Cleveland Clinic", listOf(TestFhirFixtures.CONDITION_ASTHMA))
		assertEquals(1, counts.conditions)
		val c = db.conditionDao().observeAll().first().first()
		assertEquals("Asthma", c.codeText)
		assertEquals("active", c.clinicalStatus)
	}

	@Test fun unknown_resource_type_only_stores_SourceRecord() = runTest {
		val counts = service.importResources("x", "X", listOf(TestFhirFixtures.UNKNOWN_RESOURCE))
		assertEquals(1, counts.sourceRecords)
		assertEquals(0, counts.labs)
		assertEquals(0, counts.conditions)
		val sr = db.sourceRecordDao().observeAll().first().first()
		assertEquals("Goal", sr.resourceType)
	}

	@Test fun re_running_is_idempotent() = runTest {
		val resources = listOf(TestFhirFixtures.LAB_HBA1C)
		service.importResources("cleveland-clinic", "Cleveland Clinic", resources)
		service.importResources("cleveland-clinic", "Cleveland Clinic", resources)
		val labs = db.labDao().observeAll().first()
		assertEquals(1, labs.size)
	}

	@Test fun multi_source_same_resourceId_coexists() = runTest {
		service.importResources("cleveland-clinic", "Cleveland Clinic", listOf(TestFhirFixtures.LAB_HBA1C))
		service.importResources("summa-health",    "Summa Health",    listOf(TestFhirFixtures.LAB_HBA1C))
		val labs = db.labDao().observeAll().first()
		assertEquals(2, labs.size)
		assertEquals(setOf("cleveland-clinic", "summa-health"), labs.map { it.sourceSystem }.toSet())
	}
}
