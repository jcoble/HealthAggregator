package com.healthaggregator.ai

sealed interface CitationSegment {
	data class Text(val text: String) : CitationSegment
	data class Cite(val sourceSystem: String, val fhirRef: String) : CitationSegment
}

object CitationRenderer {
	private val REGEX = Regex("""\[cite:([^/\]]+)/([^\]]+)\]""")

	fun parse(input: String): List<CitationSegment> {
		if (input.isEmpty()) return emptyList()
		val result = mutableListOf<CitationSegment>()
		var cursor = 0
		for (match in REGEX.findAll(input)) {
			if (match.range.first > cursor) {
				result += CitationSegment.Text(input.substring(cursor, match.range.first))
			}
			val (source, ref) = match.destructured
			result += CitationSegment.Cite(source, ref)
			cursor = match.range.last + 1
		}
		if (cursor < input.length) {
			result += CitationSegment.Text(input.substring(cursor))
		}
		return result.toList()
	}
}
