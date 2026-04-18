package com.healthaggregator.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SlugifyTest {
	@Test fun spaces_become_dashes() { assertEquals("cleveland-clinic", slugify("Cleveland Clinic")) }
	@Test fun non_alpha_becomes_dash() { assertEquals("summa-health-system", slugify("Summa Health System.")) }
	@Test fun blanks_fall_back() { assertEquals("org-abc", slugify("", fallback = "Org/ABC")) }
	@Test fun preserves_digits() { assertEquals("clinic-123", slugify("Clinic 123")) }
}
