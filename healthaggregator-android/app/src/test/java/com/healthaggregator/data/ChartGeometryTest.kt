package com.healthaggregator.data

import com.healthaggregator.ui.components.ChartRange
import com.healthaggregator.ui.components.pointsFor
import com.healthaggregator.ui.components.referenceBandY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChartGeometryTest {
	@Test fun empty_list_returns_no_points() {
		val p = pointsFor(emptyList(), 100f, 50f, ChartRange(0.0, 1.0))
		assertEquals(0, p.size)
	}

	@Test fun single_point_centers_in_canvas() {
		val p = pointsFor(listOf(5.0), 100f, 50f, ChartRange(0.0, 10.0))
		assertEquals(1, p.size)
		assertEquals(50f, p[0].x, 0.01f)
		assertEquals(25f, p[0].y, 0.01f)
	}

	@Test fun two_points_span_x_and_invert_y() {
		val p = pointsFor(listOf(0.0, 10.0), 100f, 50f, ChartRange(0.0, 10.0))
		assertEquals(2, p.size)
		assertEquals(0f, p[0].x, 0.01f)
		assertEquals(100f, p[1].x, 0.01f)
		assertEquals(50f, p[0].y, 0.01f)
		assertEquals(0f, p[1].y, 0.01f)
	}

	@Test fun reference_band_null_when_no_refs() {
		assertNull(referenceBandY(50f, ChartRange(0.0, 10.0), null, null))
	}

	@Test fun reference_band_positions_correctly() {
		val band = referenceBandY(50f, ChartRange(0.0, 10.0), refLow = 4.0, refHigh = 6.0)
		assertNotNull(band)
		assertTrue(band!!.first < band.second)
	}

	@Test fun range_compute_handles_flat_series() {
		val r = ChartRange.compute(listOf(5.0, 5.0, 5.0))
		assertTrue(r.max > r.min)
	}

	@Test fun range_includes_reference_bounds() {
		val r = ChartRange.compute(values = listOf(5.0), refLow = 1.0, refHigh = 10.0)
		assertTrue(r.min <= 1.0)
		assertTrue(r.max >= 10.0)
	}
}
