package com.healthaggregator.ai

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemPromptTest {
	@Test
	fun prompt_containsLoadBearingClauses() {
		val t = SystemPrompt.TEXT
		assertTrue(t.contains("HIGH-NORMAL"), "HIGH-NORMAL clause missing")
		assertTrue(t.contains("LOW-NORMAL"), "LOW-NORMAL clause missing")
		assertTrue(t.contains("[cite:sourceSystem/fhirRef]"), "citation syntax missing")
		assertTrue(t.contains("CITATIONS ARE MANDATORY"), "mandatory citations clause missing")
		assertTrue(t.contains("not a physician"), "not-a-physician clause missing")
		assertTrue(t.contains("blood pressure"), "BP caveat missing")
		assertTrue(t.contains("medications lack status"), "medication status caveat missing")
	}

	@Test
	fun prompt_startsWithRoleDefinition() {
		assertTrue(SystemPrompt.TEXT.startsWith("You are a medical data analyst"))
	}
}
