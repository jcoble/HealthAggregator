package com.healthaggregator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import com.healthaggregator.ui.theme.extended
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

	Column(modifier = modifier.fillMaxWidth()) {
		Canvas(
			modifier = Modifier
				.fillMaxWidth()
				.height(200.dp)
				.padding(horizontal = 16.dp, vertical = 8.dp),
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
