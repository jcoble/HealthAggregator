package com.healthaggregator.data

import com.healthaggregator.data.entities.LabObservation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AbnormalLabPredicateTest {
	@Test fun Normal_interpretation_does_not_count() {
		assertFalse(isAbnormal(sample(interpretation = "Normal", numericValue = 5.5, refLow = 4.0, refHigh = 6.0)))
	}

	@Test fun H_prefix_interpretation_counts() {
		assertTrue(isAbnormal(sample(interpretation = "H", numericValue = 7.0)))
	}

	@Test fun HH_prefix_counts() {
		assertTrue(isAbnormal(sample(interpretation = "HH", numericValue = 9.0)))
	}

	@Test fun L_prefix_counts() {
		assertTrue(isAbnormal(sample(interpretation = "L", numericValue = 2.0)))
	}

	@Test fun A_prefix_counts() {
		assertTrue(isAbnormal(sample(interpretation = "Abnormal", numericValue = 1.0)))
	}

	@Test fun out_of_range_high_numeric_counts() {
		assertTrue(isAbnormal(sample(numericValue = 7.0, refLow = 4.0, refHigh = 6.0)))
	}

	@Test fun out_of_range_low_numeric_counts() {
		assertTrue(isAbnormal(sample(numericValue = 3.0, refLow = 4.0, refHigh = 6.0)))
	}

	@Test fun in_range_numeric_does_not_count() {
		assertFalse(isAbnormal(sample(numericValue = 5.0, refLow = 4.0, refHigh = 6.0)))
	}

	@Test fun null_numeric_with_no_interpretation_does_not_count() {
		assertFalse(isAbnormal(sample(numericValue = null)))
	}

	private fun sample(
		interpretation: String? = null,
		numericValue: Double? = null,
		refLow: Double? = null,
		refHigh: Double? = null,
	) = LabObservation(
		sourceSystem = "x", sourceName = "", fhirReference = "Observation/1", resourceId = "1",
		testName = "t",
		interpretation = interpretation, numericValue = numericValue,
		referenceLow = refLow, referenceHigh = refHigh,
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
