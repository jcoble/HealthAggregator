package com.healthaggregator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import com.healthaggregator.ui.theme.extended
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

@Composable
fun TrendChart(
	values: List<Pair<Instant?, Double>>,
	refLow: Double? = null,
	refHigh: Double? = null,
	unit: String? = null,
	modifier: Modifier = Modifier,
) {
	val visible = values.takeLast(50)
	val numericValues = visible.map { it.second }
	val range = ChartRange.compute(numericValues, refLow, refHigh)
	val strokeColor = MaterialTheme.colorScheme.primary
	val bandColor = MaterialTheme.extended.success.copy(alpha = 0.15f)
	val guideColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
	val hintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

	var selectedIndex by remember(visible.size) {
		mutableStateOf(if (visible.isEmpty()) -1 else visible.lastIndex)
	}

	Column(modifier = modifier.fillMaxWidth()) {
		Text(
			text = calloutText(visible, selectedIndex, unit),
			style = MaterialTheme.typography.labelLarge,
			color = MaterialTheme.colorScheme.primary,
			textAlign = TextAlign.Center,
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
		)

		Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
			Canvas(
				modifier = Modifier
					.fillMaxWidth()
					.height(200.dp)
					.padding(horizontal = 16.dp, vertical = 8.dp)
					.pointerInput(visible.size) {
						if (visible.isEmpty()) return@pointerInput
						detectTapGestures { offset ->
							selectedIndex = nearestIndex(offset.x, size.width.toFloat(), visible.size)
						}
					}
					.pointerInput(visible.size) {
						if (visible.isEmpty()) return@pointerInput
						detectDragGestures(
							onDragStart = { offset ->
								selectedIndex = nearestIndex(offset.x, size.width.toFloat(), visible.size)
							},
							onDrag = { change, _ ->
								selectedIndex = nearestIndex(change.position.x, size.width.toFloat(), visible.size)
								change.consume()
							},
						)
					},
			) {
				val w = size.width
				val h = size.height

				referenceBandY(h, range, refLow, refHigh)?.let { (yTop, yBot) ->
					drawRect(color = bandColor, topLeft = Offset(0f, yTop), size = Size(w, yBot - yTop))
				}

				val pts = pointsFor(numericValues, w, h, range)
				if (pts.isEmpty()) return@Canvas

				if (pts.size == 1) {
					drawCircle(color = strokeColor, radius = 6f, center = Offset(pts[0].x, pts[0].y))
				} else {
					val path = Path().apply {
						moveTo(pts[0].x, pts[0].y)
						for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
					}
					drawPath(path = path, color = strokeColor, style = Stroke(width = 3f))
					pts.forEach { drawCircle(color = strokeColor, radius = 3f, center = Offset(it.x, it.y)) }
				}

				if (selectedIndex in pts.indices) {
					val sel = pts[selectedIndex]
					drawLine(
						color = guideColor,
						start = Offset(sel.x, 0f),
						end = Offset(sel.x, h),
						strokeWidth = 1.5f,
						pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
					)
					drawCircle(color = strokeColor, radius = 8f, center = Offset(sel.x, sel.y))
					drawCircle(color = bandColor.copy(alpha = 1f), radius = 4f, center = Offset(sel.x, sel.y))
				}
			}
		}

		Text(
			text = buildString {
				append("%.1f".format(range.min))
				append(" – ")
				append("%.1f".format(range.max))
				unit?.let { append(" $it") }
			},
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.secondary,
			modifier = Modifier.padding(horizontal = 16.dp),
		)

		if (visible.isNotEmpty()) {
			Text(
				text = buildString {
					append(visible.first().first?.let { formatDate(it) } ?: "—")
					if (visible.size > 1) {
						append("   →   ")
						append(visible.last().first?.let { formatDate(it) } ?: "—")
					}
				},
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.secondary,
				textAlign = TextAlign.Center,
				modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
			)
			Spacer(Modifier.size(2.dp))
			Text(
				text = "Tap or drag across the chart to scrub",
				style = MaterialTheme.typography.labelSmall,
				color = hintColor,
				textAlign = TextAlign.Center,
				modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
			)
		}

		if (visible.size < values.size) {
			Text(
				text = "Showing latest ${visible.size} of ${values.size} readings",
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.secondary,
				modifier = Modifier.padding(horizontal = 16.dp),
			)
		}
	}
}

private fun calloutText(
	visible: List<Pair<Instant?, Double>>,
	index: Int,
	unit: String?,
): String {
	if (visible.isEmpty() || index !in visible.indices) return " "
	val (ts, v) = visible[index]
	val date = ts?.let { formatDate(it) } ?: "—"
	val unitStr = unit?.let { " $it" } ?: ""
	return "$date  ·  ${formatValue(v)}$unitStr"
}

private fun formatValue(v: Double): String {
	val asInt = v.toLong()
	return if (v == asInt.toDouble()) "$asInt" else "%.2f".format(v)
}

private fun nearestIndex(x: Float, width: Float, size: Int): Int {
	if (size <= 1) return 0
	val step = width / (size - 1)
	val raw = (x / step).toInt()
	val candidates = listOf(raw - 1, raw, raw + 1).filter { it in 0 until size }
	return candidates.minBy { abs(it * step - x) }
}

private fun formatDate(i: Instant): String =
	LocalDate.ofInstant(i, ZoneId.systemDefault()).toString()

@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 320)
@Composable private fun PreviewTrend() = HealthAggregatorTheme {
	TrendChart(
		values = listOf(
			Instant.parse("2023-01-15T00:00:00Z") to 5.2,
			Instant.parse("2023-04-12T00:00:00Z") to 5.4,
			Instant.parse("2023-07-20T00:00:00Z") to 5.6,
			Instant.parse("2023-10-05T00:00:00Z") to 5.3,
			Instant.parse("2024-01-22T00:00:00Z") to 5.5,
			Instant.parse("2024-04-18T00:00:00Z") to 5.8,
		),
		refLow = 4.0,
		refHigh = 5.6,
		unit = "%",
	)
}
