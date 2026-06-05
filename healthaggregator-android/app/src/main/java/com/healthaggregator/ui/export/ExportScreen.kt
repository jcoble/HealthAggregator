package com.healthaggregator.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.ui.components.AppTopBar
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import com.healthaggregator.util.SharePdf

private data class ReportCardModel(
	val type: ReportType,
	val icon: ImageVector,
	val title: String,
	val description: String,
)

private data class ReportGroup(
	val title: String,
	val cards: List<ReportCardModel>,
)

private val REPORT_GROUPS: List<ReportGroup> = listOf(
	ReportGroup(
		title = "Full reports",
		cards = listOf(
			ReportCardModel(
				type = ReportType.COMPREHENSIVE,
				icon = Icons.Outlined.Summarize,
				title = "Comprehensive",
				description = "Narrative, recent abnormals, labs, vitals, and allergies in one PDF.",
			),
		),
	),
	ReportGroup(
		title = "Labs & abnormals",
		cards = listOf(
			ReportCardModel(
				type = ReportType.LABS,
				icon = Icons.Outlined.Science,
				title = "Labs — longitudinal",
				description = "Every lab value over time, grouped by canonical test with reference ranges.",
			),
			ReportCardModel(
				type = ReportType.RECENT_ABNORMALS,
				icon = Icons.Outlined.Warning,
				title = "Recent abnormals",
				description = "Out-of-range values from the last two years, grouped by body system.",
			),
			ReportCardModel(
				type = ReportType.ALL_ABNORMALS,
				icon = Icons.Outlined.Warning,
				title = "All abnormals",
				description = "Every out-of-range value on record, grouped by body system.",
			),
		),
	),
	ReportGroup(
		title = "Focused snapshots",
		cards = listOf(
			ReportCardModel(
				type = ReportType.ALLERGIES_AND_NARRATIVE,
				icon = Icons.Outlined.Biotech,
				title = "Allergies + narrative",
				description = "Short document: patient-authored notes plus full allergy list.",
			),
			ReportCardModel(
				type = ReportType.VITALS,
				icon = Icons.Outlined.MonitorHeart,
				title = "Vitals & measurements",
				description = "Blood pressure, weight, heart rate, and other vitals over time.",
			),
			ReportCardModel(
				type = ReportType.CARDIOVASCULAR,
				icon = Icons.Outlined.Favorite,
				title = "Cardiovascular",
				description = "Lipids, blood pressure, fasting glucose, and A1c in one focused report.",
			),
			ReportCardModel(
				type = ReportType.ENDOCRINE,
				icon = Icons.Outlined.Opacity,
				title = "Endocrine",
				description = "A1c, thyroid panel, vitamin D, and hormones over time.",
			),
		),
	),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
	viewModel: ExportViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val context = LocalContext.current
	val snackbarHostState = remember { SnackbarHostState() }

	LaunchedEffect(viewModel) {
		viewModel.shareEvents.collect { event ->
			SharePdf.share(context, event.file, subject = event.subject)
		}
	}

	LaunchedEffect(state.lastError) {
		val msg = state.lastError
		if (msg != null) {
			snackbarHostState.showSnackbar(message = "Export failed: $msg")
			viewModel.clearError()
		}
	}

	Box(modifier = Modifier.fillMaxSize()) {
		Column(modifier = Modifier.fillMaxSize()) {
			AppTopBar(title = "Export")

			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.weight(1f),
				verticalArrangement = Arrangement.spacedBy(8.dp),
				contentPadding = PaddingValues(bottom = 24.dp),
			) {
				item { ExportHero() }

				REPORT_GROUPS.forEachIndexed { groupIndex, group ->
					item(key = "header-${group.title}") {
						SectionHeader(
							title = group.title,
							topPadding = if (groupIndex == 0) 8.dp else 16.dp,
						)
					}

					items(group.cards, key = { it.type.name }) { card ->
						ReportButtonCard(
							icon = card.icon,
							title = card.title,
							description = card.description,
							inProgress = state.generating == card.type,
							enabled = state.generating == null,
							onClick = { viewModel.generateReport(card.type) },
						)
					}
				}
			}
		}

		SnackbarHost(
			hostState = snackbarHostState,
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(16.dp),
		)
	}
}

@Composable
private fun ExportHero() {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 20.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.size(48.dp)
				.background(
					color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
					shape = CircleShape,
				),
			contentAlignment = Alignment.Center,
		) {
			Icon(
				imageVector = Icons.Outlined.PictureAsPdf,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(24.dp),
			)
		}
		Spacer(Modifier.size(16.dp))
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = "Share-ready reports",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Spacer(Modifier.height(2.dp))
			Text(
				text = "Tap a report to generate a PDF, then email, text, or save it.",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.secondary,
			)
		}
	}
}

@Composable
private fun SectionHeader(title: String, topPadding: androidx.compose.ui.unit.Dp) {
	Text(
		text = title.uppercase(),
		style = MaterialTheme.typography.labelSmall.copy(
			fontWeight = FontWeight.SemiBold,
			letterSpacing = 0.8.sp,
		),
		color = MaterialTheme.colorScheme.secondary,
		modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = topPadding, bottom = 6.dp),
	)
}

@Composable
private fun ReportButtonCard(
	icon: ImageVector,
	title: String,
	description: String,
	inProgress: Boolean,
	enabled: Boolean,
	onClick: () -> Unit,
) {
	val dimAlpha = if (enabled || inProgress) 1f else 0.55f
	Card(
		onClick = onClick,
		enabled = enabled,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceContainer,
			disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
			contentColor = MaterialTheme.colorScheme.onSurface,
			disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = dimAlpha),
		),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp, vertical = 14.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconBadge(icon = icon, inProgress = inProgress, dimAlpha = dimAlpha)
			Spacer(Modifier.size(14.dp))
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = title,
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Medium,
					color = LocalContentColor.current,
				)
				Spacer(Modifier.height(2.dp))
				Text(
					text = if (inProgress) "Generating PDF…" else description,
					style = MaterialTheme.typography.bodySmall,
					color = if (inProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = dimAlpha),
				)
			}
			Spacer(Modifier.size(8.dp))
			Icon(
				imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.secondary.copy(alpha = dimAlpha * 0.7f),
				modifier = Modifier.size(22.dp),
			)
		}
	}
}

@Composable
private fun IconBadge(icon: ImageVector, inProgress: Boolean, dimAlpha: Float) {
	Box(
		modifier = Modifier
			.size(40.dp)
			.background(
				color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * dimAlpha),
				shape = CircleShape,
			),
		contentAlignment = Alignment.Center,
	) {
		if (inProgress) {
			CircularProgressIndicator(
				modifier = Modifier.size(20.dp),
				strokeWidth = 2.dp,
				color = MaterialTheme.colorScheme.primary,
			)
		} else {
			Icon(
				imageVector = icon,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.primary.copy(alpha = dimAlpha),
				modifier = Modifier.size(20.dp),
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 980)
@Composable
private fun PreviewExportScreen() = HealthAggregatorTheme {
	Column(modifier = Modifier.fillMaxSize()) {
		AppTopBar(title = "Export")
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			verticalArrangement = Arrangement.spacedBy(8.dp),
			contentPadding = PaddingValues(bottom = 24.dp),
		) {
			item { ExportHero() }
			REPORT_GROUPS.forEachIndexed { groupIndex, group ->
				item {
					SectionHeader(
						title = group.title,
						topPadding = if (groupIndex == 0) 8.dp else 16.dp,
					)
				}
				items(group.cards, key = { it.type.name }) { card ->
					ReportButtonCard(
						icon = card.icon,
						title = card.title,
						description = card.description,
						inProgress = card.type == ReportType.RECENT_ABNORMALS,
						enabled = card.type != ReportType.RECENT_ABNORMALS,
						onClick = {},
					)
				}
			}
		}
	}
}
