package com.healthaggregator.ai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.entities.MedicationRecord
import com.healthaggregator.data.entities.VitalsObservation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HealthSnapshotBuilderTest {
	private lateinit var db: AppDatabase
	private lateinit var builder: HealthSnapshotBuilder

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		builder = HealthSnapshotBuilder(
			labs = db.labDao(),
			vitals = db.vitalsDao(),
			medications = db.medicationDao(),
			allergies = db.allergyDao(),
			documents = db.documentDao(),
		)
	}

	@After fun tearDown() { db.close() }

	@Test
	fun empty_dataset_produces_header_and_none_markers() = runTest {
		val out = builder.build(Instant.parse("2026-04-18T10:00:00Z"))
		assertTrue(out.contains("# Jesse's Full Health Data Context"))
		assertTrue(out.contains("**Generated:** 2026-04-18T10:00:00Z"))
		assertTrue(out.contains("## Labs"))
		assertTrue(out.contains("_(none)_"))
	}

	@Test
	fun lab_reading_inside_upper_10percent_flagged_HIGH_NORMAL() = runTest {
		db.labDao().upsert(a1c("abc", numericValue = 5.5, refLow = 4.0, refHigh = 5.6))
		val out = builder.build()
		assertTrue("HIGH-NORMAL not present: $out", out.contains("HIGH-NORMAL"))
	}

	@Test
	fun lab_reading_above_refHigh_flagged_HIGH() = runTest {
		db.labDao().upsert(a1c("def", numericValue = 6.1, refLow = 4.0, refHigh = 5.6))
		val out = builder.build()
		assertTrue("HIGH flag missing: $out", Regex("6\\.1.*HIGH").containsMatchIn(out))
	}

	@Test
	fun canonical_groups_readings_across_orgs() = runTest {
		db.labDao().upsertAll(listOf(
			a1c("cc-1", source = "cleveland-clinic", canonical = "Hemoglobin A1c", loinc = "4548-4"),
			a1c("summa-1", source = "summa-health", canonical = "Hemoglobin A1c", loinc = "17856-6"),
		))
		val out = builder.build()
		val heading = "### Hemoglobin A1c"
		assertTrue(out.contains(heading))
		assertTrue(out.split(heading).size - 1 == 1)
		assertTrue(out.contains("[cite:cleveland-clinic/Observation/cc-1]"))
		assertTrue(out.contains("[cite:summa-health/Observation/summa-1]"))
	}

	@Test
	fun bp_caveat_appears_when_BP_data_exists() = runTest {
		db.vitalsDao().upsert(vitals("sys-1", loinc = "8480-6", display = "Systolic BP"))
		val out = builder.build()
		assertTrue(out.contains("diastolic LOINC 8462-4"))
	}

	@Test
	fun bp_caveat_absent_when_no_BP_data() = runTest {
		db.vitalsDao().upsert(vitals("hr-1", loinc = "8867-4", display = "Heart Rate"))
		val out = builder.build()
		assertFalse(out.contains("diastolic LOINC 8462-4"))
	}

	@Test
	fun medication_caveat_always_present() = runTest {
		val out = builder.build()
		assertTrue(out.contains("Medication records are bare"))
	}

	@Test
	fun medication_includes_citation() = runTest {
		db.medicationDao().upsert(med("m1", "Metformin 500mg"))
		val out = builder.build()
		assertTrue(out.contains("- Metformin 500mg [cite:cleveland-clinic/MedicationStatement/m1]"))
	}

	@Test
	fun size_cap_preserves_all_under_limit() = runTest {
		val readings = (1..30).map { i ->
			a1c("n$i", canonical = "Hemoglobin A1c", numericValue = 5.0 + i * 0.01,
				effectiveAt = Instant.ofEpochMilli(1_000_000L * i))
		}
		db.labDao().upsertAll(readings)
		val out = builder.build()
		readings.forEach { assertTrue(out.contains("[cite:${it.sourceSystem}/${it.fhirReference}]")) }
	}

	private fun a1c(
		id: String,
		source: String = "cleveland-clinic",
		sourceName: String = source,
		canonical: String? = "Hemoglobin A1c",
		loinc: String? = "4548-4",
		numericValue: Double? = 5.2,
		unit: String = "%",
		refLow: Double? = 4.0,
		refHigh: Double? = 5.6,
		effectiveAt: Instant? = Instant.parse("2024-06-10T00:00:00Z"),
	) = LabObservation(
		sourceSystem = source,
		sourceName = sourceName,
		fhirReference = "Observation/$id",
		resourceId = id,
		testName = "Hemoglobin A1c",
		canonicalTestName = canonical,
		loincCode = loinc,
		numericValue = numericValue,
		unit = unit,
		referenceLow = refLow,
		referenceHigh = refHigh,
		effectiveAt = effectiveAt,
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)

	private fun vitals(id: String, loinc: String, display: String) = VitalsObservation(
		sourceSystem = "cleveland-clinic",
		sourceName = "Cleveland Clinic",
		fhirReference = "Observation/$id",
		resourceId = id,
		loincCode = loinc,
		code = loinc,
		displayName = display,
		numericValue = 120.0,
		unit = "mmHg",
		effectiveAt = Instant.parse("2024-06-10T00:00:00Z"),
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)

	private fun med(id: String, text: String) = MedicationRecord(
		sourceSystem = "cleveland-clinic",
		sourceName = "Cleveland Clinic",
		fhirReference = "MedicationStatement/$id",
		resourceId = id,
		medicationText = text,
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
