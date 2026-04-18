package com.healthaggregator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import com.healthaggregator.ui.theme.extended
import kotlin.math.max
import kotlin.math.min

/**
 * MyChart-style horizontal range gauge.
 *
 * Layout (vertical):
 *   - Value pill (colored by in-range/out-of-range) positioned horizontally over the bar
 *   - Tiny downward arrow under the pill
 *   - The bar itself: yellow outside the normal range, green band inside it
 *   - refLow / refHigh labels underneath (left / right edges of the green band)
 *
 * Domain of the bar:
 *   - If both refLow and refHigh present: extended 25% of the normal-range span on each side
 *   - Extends further if the value falls outside that extended band so it's always visible
 *
 * Callers should hide the gauge entirely when numericValue is null, or when both
 * refLow and refHigh are null.
 */
@Composable
fun RangeGauge(
	value: Double,
	unit: String?,
	refLow: Double?,
	refHigh: Double?,
	modifier: Modifier = Modifier,
) {
	val inRange = isInRange(value, refLow, refHigh)
	val pillBg = if (inRange) MaterialTheme.extended.success else MaterialTheme.colorScheme.error
	val pillFg = MaterialTheme.colorScheme.onPrimary
	val outOfRangeColor = MaterialTheme.extended.warning.copy(alpha = 0.85f)
	val inRangeBarColor = MaterialTheme.extended.success
	val arrowColor = pillBg

	// Compute bar domain
	val (domainMin, domainMax) = computeDomain(value, refLow, refHigh)
	val span = (domainMax - domainMin).coerceAtLeast(1e-9)
	val valueFrac = ((value - domainMin) / span).toFloat().coerceIn(0f, 1f)
	val lowFrac = refLow?.let { ((it - domainMin) / span).toFloat().coerceIn(0f, 1f) } ?: 0f
	val highFrac = refHigh?.let { ((it - domainMin) / span).toFloat().coerceIn(0f, 1f) } ?: 1f

	Column(modifier = modifier.fillMaxWidth()) {
		// Value pill + arrow row — positioned horizontally at valueFrac across the bar
		Box(modifier = Modifier.fillMaxWidth()) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				modifier = Modifier.layout { measurable, constraints ->
					val placeable = measurable.measure(constraints.copy(minWidth = 0))
					val barWidth = constraints.maxWidth
					// Center the pill over valueFrac * barWidth; clamp so it doesn't overflow edges
					val x = (barWidth * valueFrac - placeable.width / 2)
						.toInt()
						.coerceIn(0, barWidth - placeable.width)
					layout(constraints.maxWidth, placeable.height) {
						placeable.place(x, 0)
					}
				},
			) {
				Surface(
					color = pillBg,
					shape = RoundedCornerShape(percent = 50),
					modifier = Modifier.padding(horizontal = 2.dp),
				) {
					Text(
						text = formatValue(value),
						color = pillFg,
						fontWeight = FontWeight.SemiBold,
						style = MaterialTheme.typography.labelMedium,
						modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
					)
				}
				// Downward triangle arrow
				Canvas(modifier = Modifier.height(6.dp).padding(top = 1.dp)) {
					val w = 10f
					val h = size.height
					val path = Path().apply {
						moveTo(size.width / 2f - w / 2f, 0f)
						lineTo(size.width / 2f + w / 2f, 0f)
						lineTo(size.width / 2f, h)
						close()
					}
					drawPath(path, color = arrowColor)
				}
			}
		}

		Spacer(Modifier.height(2.dp))

		// The bar
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(10.dp)
				.clip(RoundedCornerShape(5.dp)),
		) {
			Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
				val w = size.width
				val h = size.height
				// Entire background: yellow
				drawRect(color = outOfRangeColor, topLeft = Offset(0f, 0f), size = Size(w, h))
				// Green normal-range band
				val gLeft = w * lowFrac
				val gRight = w * highFrac
				if (gRight > gLeft) {
					drawRect(color = inRangeBarColor, topLeft = Offset(gLeft, 0f), size = Size(gRight - gLeft, h))
				}
			}
		}

		// refLow / refHigh labels — simple edge-anchored Row for reliable layout
		Spacer(Modifier.height(2.dp))
		Row(modifier = Modifier.fillMaxWidth()) {
			val leftLabel = refLow?.let { formatValue(it) + (unit?.let { u -> " $u" } ?: "") }
			val rightLabel = refHigh?.let { formatValue(it) + (unit?.let { u -> " $u" } ?: "") }
			if (leftLabel != null) {
				Text(
					text = leftLabel,
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.secondary,
				)
			}
			Spacer(Modifier.weight(1f))
			if (rightLabel != null) {
				Text(
					text = rightLabel,
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.secondary,
				)
			}
		}
	}
}

private fun isInRange(value: Double, refLow: Double?, refHigh: Double?): Boolean {
	val aboveLow = refLow?.let { value >= it } ?: true
	val belowHigh = refHigh?.let { value <= it } ?: true
	return aboveLow && belowHigh
}

/**
 * Domain of the bar — extends 25% of the normal-range span past each reference bound.
 * If only one bound exists, expand the other side symmetrically around the value.
 * Always ensures the value is within the domain.
 */
private fun computeDomain(value: Double, refLow: Double?, refHigh: Double?): Pair<Double, Double> {
	val pad = when {
		refLow != null && refHigh != null -> (refHigh - refLow).coerceAtLeast(1e-9) * 0.25
		else -> (value.let { kotlin.math.abs(it).coerceAtLeast(1.0) }) * 0.25
	}
	var lo = (refLow ?: (value - pad * 2)) - pad
	var hi = (refHigh ?: (value + pad * 2)) + pad
	if (value < lo) lo = value - pad
	if (value > hi) hi = value + pad
	return lo to hi
}

private fun formatValue(v: Double): String {
	// Show integer when whole, else 1-2 decimal places depending on magnitude
	return when {
		v == v.toLong().toDouble() -> v.toLong().toString()
		kotlin.math.abs(v) >= 10.0 -> "%.1f".format(v)
		else -> "%.2f".format(v).trimEnd('0').trimEnd('.')
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B, widthDp = 320)
@Composable private fun PreviewInRange() = HealthAggregatorTheme {
	Column(Modifier.padding(16.dp)) {
		RangeGauge(value = 6.6, unit = "g/dL", refLow = 6.1, refHigh = 8.1)
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B, widthDp = 320)
@Composable private fun PreviewCloseToLow() = HealthAggregatorTheme {
	Column(Modifier.padding(16.dp)) {
		RangeGauge(value = 4.3, unit = "g/dL", refLow = 3.6, refHigh = 5.1)
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B, widthDp = 320)
@Composable private fun PreviewBelowLow() = HealthAggregatorTheme {
	Column(Modifier.padding(16.dp)) {
		RangeGauge(value = 3.1, unit = "g/dL", refLow = 3.6, refHigh = 5.1)
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B, widthDp = 320)
@Composable private fun PreviewAboveHigh() = HealthAggregatorTheme {
	Column(Modifier.padding(16.dp)) {
		RangeGauge(value = 10.2, unit = "g/dL", refLow = 6.1, refHigh = 8.1)
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B, widthDp = 320)
@Composable private fun PreviewOnlyHigh() = HealthAggregatorTheme {
	Column(Modifier.padding(16.dp)) {
		RangeGauge(value = 180.0, unit = "mg/dL", refLow = null, refHigh = 200.0)
	}
}
