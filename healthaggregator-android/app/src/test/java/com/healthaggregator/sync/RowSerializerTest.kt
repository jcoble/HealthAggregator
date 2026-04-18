package com.healthaggregator.sync

import com.healthaggregator.data.entities.LabObservation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RowSerializerTest {
	private val serializer = RowSerializer()

	@Test
	fun `LabObservation round-trips through JSON`() {
		val original = LabObservation(
			id = 42L,
			sourceSystem = "EpicCleveland",
			sourceName = "Cleveland Clinic",
			fhirReference = "Observation/123",
			resourceId = "123",
			patientFhirId = "Patient/5",
			loincCode = "2345-7",
			testName = "Glucose",
			numericValue = 95.0,
			unit = "mg/dL",
			referenceLow = 70.0,
			referenceHigh = 99.0,
			effectiveAt = Instant.parse("2025-11-12T10:30:00Z"),
			status = "final",
			importedAt = Instant.parse("2026-04-18T12:00:00Z"),
			canonicalTestName = "glucose_fasting",
		)
		val row = serializer.toRow(original)
		val restored = serializer.fromLabRow(row)
		assertEquals(original, restored)
	}
}
