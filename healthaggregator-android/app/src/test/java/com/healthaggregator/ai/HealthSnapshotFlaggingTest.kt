package com.healthaggregator.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthSnapshotFlaggingTest {
	@Test
	fun value_below_refLow_is_LOW() {
		assertEquals(RangeFlag.LOW, classifyRange(value = 3.5, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun value_above_refHigh_is_HIGH() {
		assertEquals(RangeFlag.HIGH, classifyRange(value = 6.1, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun value_within_10percent_of_upper_is_HIGH_NORMAL() {
		assertEquals(RangeFlag.HIGH_NORMAL, classifyRange(value = 5.2, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun value_within_10percent_of_lower_is_LOW_NORMAL() {
		assertEquals(RangeFlag.LOW_NORMAL, classifyRange(value = 4.2, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun value_squarely_in_middle_is_NORMAL() {
		assertEquals(RangeFlag.NORMAL, classifyRange(value = 4.8, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun value_exactly_at_boundary_is_still_normal_edge() {
		assertEquals(RangeFlag.HIGH_NORMAL, classifyRange(value = 5.6, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun no_refHigh_no_upperFlag() {
		assertEquals(RangeFlag.NONE, classifyRange(value = 100.0, refLow = 4.0, refHigh = null))
	}

	@Test
	fun no_refLow_no_lowerFlag() {
		assertEquals(RangeFlag.NONE, classifyRange(value = 0.0, refLow = null, refHigh = 5.6))
	}

	@Test
	fun no_ranges_no_flag() {
		assertEquals(RangeFlag.NONE, classifyRange(value = 123.0, refLow = null, refHigh = null))
	}

	@Test
	fun null_value_no_flag() {
		assertEquals(RangeFlag.NONE, classifyRange(value = null, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun flagLabel_renders_readable_strings() {
		assertEquals("HIGH", RangeFlag.HIGH.label)
		assertEquals("HIGH-NORMAL", RangeFlag.HIGH_NORMAL.label)
		assertEquals("LOW-NORMAL", RangeFlag.LOW_NORMAL.label)
		assertEquals("LOW", RangeFlag.LOW.label)
		assertEquals("", RangeFlag.NORMAL.label)
		assertEquals("", RangeFlag.NONE.label)
	}
}
