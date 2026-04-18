package com.healthaggregator.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CitationRendererTest {
	@Test
	fun parse_plain_text_returns_single_text_segment() {
		val out = CitationRenderer.parse("just words")
		assertEquals(listOf(CitationSegment.Text("just words")), out)
	}

	@Test
	fun parse_single_citation_splits_into_text_and_cite() {
		val out = CitationRenderer.parse("Your A1c [cite:summa/Observation/abc] is high.")
		assertEquals(
			listOf(
				CitationSegment.Text("Your A1c "),
				CitationSegment.Cite("summa", "Observation/abc"),
				CitationSegment.Text(" is high."),
			),
			out,
		)
	}

	@Test
	fun parse_multiple_citations() {
		val out = CitationRenderer.parse(
			"Both [cite:cc/Observation/a] and [cite:summa/Observation/b] are high.",
		)
		assertEquals(5, out.size)
		assertEquals(CitationSegment.Cite("cc", "Observation/a"), out[1])
		assertEquals(CitationSegment.Cite("summa", "Observation/b"), out[3])
	}

	@Test
	fun parse_adjacent_citations() {
		val out = CitationRenderer.parse("[cite:a/b/c][cite:d/e/f]")
		assertEquals(listOf(
			CitationSegment.Cite("a", "b/c"),
			CitationSegment.Cite("d", "e/f"),
		), out)
	}

	@Test
	fun parse_malformed_no_close_is_left_as_text() {
		val out = CitationRenderer.parse("text [cite:incomplete")
		assertEquals(listOf(CitationSegment.Text("text [cite:incomplete")), out)
	}

	@Test
	fun parse_non_cite_brackets_are_unaffected() {
		val out = CitationRenderer.parse("See [note] here.")
		assertEquals(listOf(CitationSegment.Text("See [note] here.")), out)
	}

	@Test
	fun parse_cite_with_slash_in_ref() {
		val out = CitationRenderer.parse("[cite:cleveland-clinic/Observation/sub/path-id]")
		assertEquals(
			listOf(CitationSegment.Cite("cleveland-clinic", "Observation/sub/path-id")),
			out,
		)
	}

	@Test
	fun parse_empty_string() {
		val out = CitationRenderer.parse("")
		assertEquals(emptyList<CitationSegment>(), out)
	}
}
