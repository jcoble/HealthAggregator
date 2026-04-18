package com.healthaggregator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import com.healthaggregator.ui.theme.extended

@Composable
fun Sparkline(
	values: List<Double>,
	refLow: Double? = null,
	refHigh: Double? = null,
	abnormal: Boolean = false,
	modifier: Modifier = Modifier,
) {
	val strokeColor = if (abnormal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
	val bandColor = MaterialTheme.extended.success.copy(alpha = 0.15f)
	val dotColor = strokeColor

	Canvas(
		modifier = modifier
			.width(80.dp)
			.height(32.dp),
	) {
		val w = size.width
		val h = size.height
		val range = ChartRange.compute(values.takeLast(12), refLow, refHigh)

		referenceBandY(h, range, refLow, refHigh)?.let { (yTop, yBot) ->
			drawRect(
				color = bandColor,
				topLeft = Offset(0f, yTop),
				size = Size(w, yBot - yTop),
			)
		}

		val pts = pointsFor(values.takeLast(12), w, h, range)
		if (pts.isEmpty()) return@Canvas

		if (pts.size == 1) {
			drawCircle(color = dotColor, radius = 3f, center = Offset(pts[0].x, pts[0].y))
			return@Canvas
		}

		val path = Path().apply {
			moveTo(pts[0].x, pts[0].y)
			for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
		}
		drawPath(path = path, color = strokeColor, style = Stroke(width = 2f))
		drawCircle(color = dotColor, radius = 2.5f, center = Offset(pts.last().x, pts.last().y))
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewNormal() = HealthAggregatorTheme {
	Sparkline(values = listOf(5.2, 5.3, 5.1, 5.4, 5.5, 5.3, 5.2), refLow = 4.0, refHigh = 5.6)
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewAbnormal() = HealthAggregatorTheme {
	Sparkline(values = listOf(5.2, 5.5, 5.9, 6.3, 6.8, 7.2, 7.5), refLow = 4.0, refHigh = 5.6, abnormal = true)
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewSingle() = HealthAggregatorTheme {
	Sparkline(values = listOf(5.2), refLow = 4.0, refHigh = 5.6)
}
