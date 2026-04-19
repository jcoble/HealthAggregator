package com.healthaggregator.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import com.healthaggregator.util.SharePdf

private data class ReportCardModel(
	val type: ReportType,
	val icon: ImageVector,
	val title: String,
	val description: String,
)

private val REPORT_CARDS: List<ReportCardModel> = listOf(
	ReportCardModel(
		type = ReportType.COMPREHENSIVE,
		icon = Icons.Outlined.Summarize,
		title = "Comprehensive",
		description = "Everything in one PDF: narrative, recent abnormals, labs, vitals, allergies.",
	),
	ReportCardModel(
		type = ReportType.LABS,
		icon = Icons.Outlined.Science,
		title = "Labs (longitudinal)",
		description = "Every lab value over time, grouped by canonical test, with reference ranges.",
	),
	ReportCardModel(
		type = ReportType.RECENT_ABNORMALS,
		icon = Icons.Outlined.Warning,
		title = "Recent abnormals (2 yrs)",
		description = "Out-of-range values from the last two years, grouped by body system.",
	),
	ReportCardModel(
		type = ReportType.ALL_ABNORMALS,
		icon = Icons.Outlined.Warning,
		title = "All abnormals",
		description = "Every out-of-range value on record, grouped by body system.",
	),
	ReportCardModel(
		type = ReportType.ALLERGIES_AND_NARRATIVE,
		icon = Icons.Outlined.Biotech,
		title = "Allergies + narrative",
		description = "Short two-page doc: patient-authored notes plus allergy list.",
	),
	ReportCardModel(
		type = ReportType.VITALS,
		icon = Icons.Outlined.MonitorHeart,
		title = "Vitals / measurements",
		description = "Blood pressure, weight, heart rate and other measurements over time.",
	),
	ReportCardModel(
		type = ReportType.CARDIOVASCULAR,
		icon = Icons.Outlined.Favorite,
		title = "Cardiovascular snapshot",
		description = "Lipids, blood pressure, fasting glucose and A1c in one focused report.",
	),
	ReportCardModel(
		type = ReportType.ENDOCRINE,
		icon = Icons.Outlined.Opacity,
		title = "Endocrine snapshot",
		description = "A1c, thyroid panel, vitamin D and hormones over time.",
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

	Column(modifier = Modifier.fillMaxSize()) {
		TopAppBar(title = { Text("Export") })

		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.weight(1f)
				.padding(horizontal = 16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
			contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
		) {
			item {
				Text(
					text = "Generate a PDF for your next doctor visit. Each report opens the Android share sheet so you can email, text, or save it.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.secondary,
				)
			}

			items(REPORT_CARDS, key = { it.type.name }) { card ->
				ReportButtonCard(
					icon = card.icon,
					title = card.title,
					description = card.description,
					inProgress = state.generating == card.type,
					enabled = state.generating == null,
					onClick = { viewModel.generateReport(card.type) },
				)
			}

			item { Spacer(Modifier.height(8.dp)) }
		}

		SnackbarHost(hostState = snackbarHostState)
	}
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
	Card(
		onClick = onClick,
		enabled = enabled,
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(
			containerColor = MaterialTheme.colorScheme.surfaceContainer,
		),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (inProgress) {
				CircularProgressIndicator(
					modifier = Modifier.size(24.dp),
					strokeWidth = 2.dp,
				)
			} else {
				Icon(
					imageVector = icon,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary,
				)
			}
			Column(modifier = Modifier.padding(start = 16.dp)) {
				Text(text = title, style = MaterialTheme.typography.titleMedium)
				Spacer(Modifier.height(4.dp))
				Text(
					text = if (inProgress) "Generating PDF…" else description,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.secondary,
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 900)
@Composable
private fun PreviewExportScreen() = HealthAggregatorTheme {
	Column(modifier = Modifier.fillMaxSize()) {
		TopAppBar(title = { Text("Export") })
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
			contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
		) {
			items(REPORT_CARDS, key = { it.type.name }) { card ->
				ReportButtonCard(
					icon = card.icon,
					title = card.title,
					description = card.description,
					inProgress = false,
					enabled = true,
					onClick = {},
				)
			}
		}
	}
}
