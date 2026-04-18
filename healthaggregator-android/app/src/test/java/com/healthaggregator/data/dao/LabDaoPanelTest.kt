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

	@Test
	fun observeByLoincOrCanonical_union_matches_across_orgs() = runTest {
		// Two orgs' A1c: Cleveland has LOINC but Summa's LOINC differs; canonicalTestName joins them.
		dao.upsertAll(listOf(
			lab("cc-1", source = "cleveland-clinic", sourceName = "Cleveland Clinic",
				loinc = "4548-4", testName = "Hemoglobin A1c", canonicalTest = "Hemoglobin A1c"),
			lab("summa-1", source = "summa-health", sourceName = "Summa Health",
				loinc = "17856-6", testName = "HEMOGLOBIN A1C", canonicalTest = "Hemoglobin A1c"),
			// Not an A1c — must not match
			lab("other-1", source = "cleveland-clinic", sourceName = "Cleveland Clinic",
				loinc = "2339-0", testName = "Glucose", canonicalTest = "Glucose"),
		))

		val results = dao.observeByLoincOrCanonical(loinc = "4548-4", canonical = "Hemoglobin A1c").first()
		assertEquals(2, results.size)
		assertEquals(setOf("Observation/cc-1", "Observation/summa-1"), results.map { it.fhirReference }.toSet())
	}

	@Test
	fun observeByLoincOrCanonical_handles_null_loinc() = runTest {
		// Org that skipped LOINC — must still pick up via canonical
		dao.upsertAll(listOf(
			lab("no-loinc", source = "home-lab", sourceName = "Home Lab",
				loinc = null, testName = "HbA1c", canonicalTest = "Hemoglobin A1c"),
			lab("with-loinc", source = "cleveland-clinic", sourceName = "Cleveland Clinic",
				loinc = "4548-4", testName = "Hemoglobin A1c", canonicalTest = "Hemoglobin A1c"),
		))

		val results = dao.observeByLoincOrCanonical(loinc = null, canonical = "Hemoglobin A1c").first()
		assertEquals(2, results.size)
	}

	@Test
	fun observeByLoincOrCanonical_dedupes_when_row_matches_both() = runTest {
		// A row that matches on BOTH keys should appear once, not twice.
		dao.upsert(lab("both", loinc = "4548-4", testName = "Hemoglobin A1c", canonicalTest = "Hemoglobin A1c"))
		val results = dao.observeByLoincOrCanonical(loinc = "4548-4", canonical = "Hemoglobin A1c").first()
		assertEquals(1, results.size)
	}

	private fun lab(
		fhirRef: String,
		source: String = "cleveland-clinic",
		sourceName: String = "Cleveland Clinic",
		sr: String? = null,
		srDisplay: String? = null,
		testName: String = "test",
		loinc: String? = null,
		canonicalTest: String? = null,
		date: String? = "2024-03-15T09:30:00Z",
	) = LabObservation(
		sourceSystem = source,
		sourceName = sourceName,
		fhirReference = "Observation/$fhirRef",
		resourceId = fhirRef,
		testName = testName,
		loincCode = loinc,
		effectiveAt = date?.let { Instant.parse(it) },
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
		serviceRequestReference = sr,
		serviceRequestDisplay = srDisplay,
		canonicalTestName = canonicalTest,
	)
}
