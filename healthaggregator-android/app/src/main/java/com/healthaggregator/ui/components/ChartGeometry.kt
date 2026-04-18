package com.healthaggregator.ui.components

data class ChartPoint(val x: Float, val y: Float)

data class ChartRange(val min: Double, val max: Double) {
	companion object {
		/** Auto-range from data + optional reference band. Guarantees non-zero span. */
		fun compute(values: List<Double>, refLow: Double? = null, refHigh: Double? = null): ChartRange {
			val all = buildList {
				addAll(values)
				refLow?.let { add(it) }
				refHigh?.let { add(it) }
			}
			if (all.isEmpty()) return ChartRange(0.0, 1.0)
			var lo = all.min()
			var hi = all.max()
			if (lo == hi) { lo -= 1.0; hi += 1.0 }
			val span = hi - lo
			return ChartRange(lo - span * 0.1, hi + span * 0.1)
		}
	}
}

fun pointsFor(values: List<Double>, width: Float, height: Float, range: ChartRange): List<ChartPoint> {
	if (values.isEmpty()) return emptyList()
	if (values.size == 1) return listOf(ChartPoint(width / 2f, height / 2f))
	val span = (range.max - range.min).toFloat()
	return values.mapIndexed { i, v ->
		val x = (i.toFloat() / (values.size - 1)) * width
		val yNorm = ((v - range.min) / span).toFloat()
		val y = height - (yNorm * height)
		ChartPoint(x, y)
	}
}

/** Returns (yTop, yBottom) in canvas coords for the reference-range band, or null if not applicable. */
fun referenceBandY(height: Float, range: ChartRange, refLow: Double?, refHigh: Double?): Pair<Float, Float>? {
	if (refLow == null && refHigh == null) return null
	val lo = refLow ?: range.min
	val hi = refHigh ?: range.max
	val span = (range.max - range.min).toFloat()
	val yTop = height - ((hi - range.min).toFloat() / span * height)
	val yBot = height - ((lo - range.min).toFloat() / span * height)
	return yTop to yBot
}
