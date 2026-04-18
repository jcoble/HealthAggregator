package com.healthaggregator.data

import androidx.compose.ui.graphics.Color

enum class SourceColor(val container: Color, val label: Color) {
	RED(Color(0x26EF4444),    Color(0xFFFCA5A5)),
	BLUE(Color(0x263B82F6),   Color(0xFF93C5FD)),
	GREY(Color(0x26A1A1AA),   Color(0xFFD4D4D8)),
	AMBER(Color(0x26F59E0B),  Color(0xFFFCD34D)),
	PURPLE(Color(0x26A855F7), Color(0xFFD8B4FE)),
	TEAL(Color(0x2614B8A6),   Color(0xFF5EEAD4)),
	PINK(Color(0x26EC4899),   Color(0xFFF9A8D4)),
	ORANGE(Color(0x26F97316), Color(0xFFFDBA74)),
	CYAN(Color(0x2606B6D4),   Color(0xFF67E8F9)),
	INDIGO(Color(0x266366F1), Color(0xFFA5B4FC)),
}

private val LOCKED = mapOf(
	"cleveland-clinic" to SourceColor.RED,
	"summa-health"     to SourceColor.BLUE,
	"manual-upload"    to SourceColor.AMBER,
	"epic-sandbox"     to SourceColor.GREY,
)

private val FALLBACK_PALETTE = listOf(
	SourceColor.PURPLE, SourceColor.TEAL, SourceColor.PINK,
	SourceColor.ORANGE, SourceColor.CYAN, SourceColor.INDIGO,
)

fun sourceColor(sourceSystem: String): SourceColor {
	LOCKED[sourceSystem]?.let { return it }
	val hash = sourceSystem.sumOf { it.code }
	return FALLBACK_PALETTE[hash % FALLBACK_PALETTE.size]
}
