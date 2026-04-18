package com.healthaggregator.data

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SourceThemeTest {
	@Test fun locked_orgs_get_their_reserved_color() {
		assertSame(SourceColor.RED,   sourceColor("cleveland-clinic"))
		assertSame(SourceColor.BLUE,  sourceColor("summa-health"))
		assertSame(SourceColor.GREY,  sourceColor("epic-sandbox"))
		assertSame(SourceColor.AMBER, sourceColor("manual-upload"))
	}

	@Test fun unknown_source_gets_stable_fallback() {
		val a = sourceColor("kaiser-permanente")
		val b = sourceColor("kaiser-permanente")
		assertSame(a, b, "must be deterministic on repeat")
	}

	@Test fun unknown_never_collides_with_a_locked_color() {
		val fallbackColors = setOf(
			SourceColor.PURPLE, SourceColor.TEAL, SourceColor.PINK,
			SourceColor.ORANGE, SourceColor.CYAN, SourceColor.INDIGO,
		)
		listOf("foo", "bar", "baz", "mayo-clinic", "kaiser").forEach { name ->
			val c = sourceColor(name)
			assert(c in fallbackColors) { "$name resolved to $c which is locked-palette" }
		}
	}
}
