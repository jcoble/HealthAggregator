package com.healthaggregator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Compact top bar with vertically centered title. Shorter than Material 3's default
 * 64dp [androidx.compose.material3.TopAppBar] — sits at 52dp so the hero/content has more room.
 */
@Composable
fun AppTopBar(
	title: String,
	subtitle: String? = null,
	onBack: (() -> Unit)? = null,
	navigationIcon: (@Composable () -> Unit)? = null,
	actions: @Composable RowScope.() -> Unit = {},
) {
	Column(
		modifier = Modifier.background(MaterialTheme.colorScheme.background),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(52.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			when {
				navigationIcon != null -> Box(
					modifier = Modifier.size(48.dp),
					contentAlignment = Alignment.Center,
				) { navigationIcon() }
				onBack != null -> IconButton(onClick = onBack) {
					Icon(
						imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
						contentDescription = "Back",
						tint = MaterialTheme.colorScheme.onSurface,
					)
				}
				else -> Spacer(Modifier.width(16.dp))
			}

			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = title,
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.SemiBold,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
				)
				if (subtitle != null) {
					Text(
						text = subtitle,
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.secondary,
						maxLines = 1,
					)
				}
			}

			Row(
				modifier = Modifier
					.defaultMinSize(minWidth = 8.dp)
					.padding(end = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
				content = actions,
			)
		}
		HorizontalDivider(color = MaterialTheme.colorScheme.outline)
	}
}
