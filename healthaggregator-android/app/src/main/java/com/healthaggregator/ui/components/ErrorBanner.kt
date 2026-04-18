package com.healthaggregator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import com.healthaggregator.ui.theme.extended

enum class BannerVariant { Info, Warning, Destructive }

@Composable
fun ErrorBanner(
	variant: BannerVariant,
	title: String,
	content: String? = null,
	onDismiss: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val (bg: Color, fg: Color, icon: ImageVector) = when (variant) {
		BannerVariant.Info -> Triple(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurface, Icons.Outlined.Info)
		BannerVariant.Warning -> Triple(MaterialTheme.extended.warning.copy(alpha = 0.15f), MaterialTheme.extended.onWarning, Icons.Outlined.Warning)
		BannerVariant.Destructive -> Triple(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), MaterialTheme.colorScheme.error, Icons.Outlined.Error)
	}
	Row(
		modifier = modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(8.dp))
			.background(bg)
			.padding(horizontal = 12.dp, vertical = 10.dp),
		verticalAlignment = Alignment.Top,
	) {
		Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
		Spacer(Modifier.width(10.dp))
		Column(modifier = Modifier.weight(1f)) {
			Text(title, style = MaterialTheme.typography.labelLarge, color = fg)
			if (content != null) {
				Spacer(Modifier.size(2.dp))
				Text(content, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.85f))
			}
		}
		if (onDismiss != null) {
			IconButton(onClick = onDismiss) {
				Icon(Icons.Outlined.Close, contentDescription = "Dismiss", tint = fg)
			}
		}
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewInfo() = HealthAggregatorTheme {
	ErrorBanner(BannerVariant.Info, "Sync complete", "Imported 127 records.")
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewWarning() = HealthAggregatorTheme {
	ErrorBanner(BannerVariant.Warning, "Missing permissions", "Grant Health Connect access to sync.")
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewDestructive() = HealthAggregatorTheme {
	ErrorBanner(BannerVariant.Destructive, "Sync failed", "Health Connect returned error code 7.", onDismiss = {})
}
