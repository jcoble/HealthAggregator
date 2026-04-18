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
class LabDaoPanelTest {
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

	@After fun tearDown() { db.close() }

	@Test
	fun observePanels_groups_by_serviceRequestReference() = runTest {
		// Two components of one panel + one from a different panel + one orphan (serviceRequestReference null)
		dao.upsertAll(listOf(
			lab("obs-1", sr = "ServiceRequest/bmp-1", srDisplay = "Basic Metabolic Panel", date = "2024-03-15T09:30:00Z"),
			lab("obs-2", sr = "ServiceRequest/bmp-1", srDisplay = "Basic Metabolic Panel", date = "2024-03-15T09:30:00Z"),
			lab("obs-3", sr = "ServiceRequest/cbc-1", srDisplay = "Complete Blood Count", date = "2024-03-16T10:00:00Z"),
			lab("obs-4", sr = null, srDisplay = null, date = "2024-03-17T11:00:00Z"),
		))
		val panels = dao.observePanels().first()
		assertEquals(2, panels.size) // orphan excluded
		val bmp = panels.first { it.serviceRequestReference == "ServiceRequest/bmp-1" }
		val cbc = panels.first { it.serviceRequestReference == "ServiceRequest/cbc-1" }
		assertEquals(2, bmp.componentCount)
		assertEquals(1, cbc.componentCount)
		assertEquals("Basic Metabolic Panel", bmp.displayName)
		// Ordered newest first
		assertEquals("ServiceRequest/cbc-1", panels[0].serviceRequestReference)
	}

	@Test
	fun observePanels_falls_back_to_testName_when_display_null() = runTest {
		dao.upsert(lab("obs-5", sr = "ServiceRequest/x", srDisplay = null, testName = "Urinalysis"))
		val panels = dao.observePanels().first()
		assertEquals(1, panels.size)
		assertEquals("Urinalysis", panels[0].displayName)
	}

	private fun lab(
		fhirRef: String,
		sr: String? = null,
		srDisplay: String? = null,
		testName: String = "test",
		date: String? = "2024-03-15T09:30:00Z",
	) = LabObservation(
		sourceSystem = "cleveland-clinic",
		sourceName = "Cleveland Clinic",
		fhirReference = "Observation/$fhirRef",
		resourceId = fhirRef,
		testName = testName,
		effectiveAt = date?.let { Instant.parse(it) },
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
		serviceRequestReference = sr,
		serviceRequestDisplay = srDisplay,
	)
}
