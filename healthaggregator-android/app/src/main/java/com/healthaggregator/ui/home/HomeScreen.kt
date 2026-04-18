package com.healthaggregator.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.sync.SyncState
import com.healthaggregator.ui.components.BannerVariant
import com.healthaggregator.ui.components.EmptyState
import com.healthaggregator.ui.components.ErrorBanner
import com.healthaggregator.ui.components.SourceBadge
import com.healthaggregator.ui.components.StatCard
import com.healthaggregator.ui.components.SyncStatusChip
import com.healthaggregator.ui.theme.HealthAggregatorTheme

data class StatTile(val key: String, val icon: ImageVector, val label: String, val value: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
	onNavigateToRecords: (String) -> Unit,
	onRequestPermissions: () -> Unit,
	viewModel: HomeViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()

	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
		viewModel.onPermissionsUpdated()
	}

	Column(modifier = Modifier.fillMaxSize()) {
		TopAppBar(title = { Text("Health Aggregator") })

		when {
			!state.healthConnectAvailable -> {
				ErrorBanner(
					variant = BannerVariant.Warning,
					title = "Health Connect unavailable",
					content = "Install Health Connect from Google Play to sync clinical records.",
					modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
				)
			}
			!state.permissionsGranted -> {
				EmptyState(
					icon = Icons.Outlined.Warning,
					title = "Grant Health Connect access",
					description = "Allow the app to read your clinical records from CommonHealth.",
					actionLabel = "Grant access",
					onAction = onRequestPermissions,
				)
			}
			else -> {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 8.dp),
					horizontalArrangement = Arrangement.End,
				) {
					SyncStatusChip(
						lastSyncAt = state.latestSync?.completedAt,
						isSyncing = state.syncState is SyncState.Syncing,
						onRefresh = viewModel::refresh,
					)
				}

				val tiles = listOf(
					StatTile("all", Icons.Outlined.Folder, "Labs", state.labs),
					StatTile("vitals", Icons.Outlined.MonitorHeart, "Vitals", state.vitals),
					StatTile("medications", Icons.Outlined.LocalPharmacy, "Meds", state.medications),
					StatTile("conditions", Icons.Outlined.Healing, "Conditions", state.conditions),
					StatTile("allergies", Icons.Outlined.Biotech, "Allergies", state.allergies),
					StatTile("encounters", Icons.Outlined.LocalHospital, "Encounters", state.encounters),
					StatTile("documents", Icons.Outlined.Assignment, "Documents", state.documents),
				)
				LazyVerticalGrid(
					columns = GridCells.Fixed(2),
					contentPadding = androidx.compose.foundation.layout.PaddingValues(
						horizontal = 16.dp,
						vertical = 8.dp,
					),
					verticalArrangement = Arrangement.spacedBy(8.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					modifier = Modifier.fillMaxWidth(),
				) {
					items(tiles, key = { it.key }) { tile ->
						StatCard(
							icon = tile.icon,
							label = tile.label,
							value = tile.value.toString(),
							onClick = { onNavigateToRecords(tile.key) },
						)
					}
				}

				if (state.sources.isNotEmpty()) {
					Spacer(Modifier.height(8.dp))
					Card(
						modifier = Modifier
							.padding(horizontal = 16.dp)
							.fillMaxWidth(),
						colors = CardDefaults.cardColors(
							containerColor = MaterialTheme.colorScheme.surfaceContainer,
						),
					) {
						Column(modifier = Modifier.padding(16.dp)) {
							Text(
								text = "Connected sources",
								style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.secondary,
							)
							Spacer(Modifier.height(8.dp))
							state.sources.forEach { src ->
								Row(
									modifier = Modifier
										.fillMaxWidth()
										.padding(vertical = 4.dp),
									verticalAlignment = Alignment.CenterVertically,
								) {
									SourceBadge(
										sourceSystem = src.sourceSystem,
										sourceName = src.displayName,
									)
									Spacer(Modifier.height(8.dp))
									Text(
										text = "${src.recordCount} records",
										style = MaterialTheme.typography.bodySmall,
										color = MaterialTheme.colorScheme.secondary,
										modifier = Modifier.padding(start = 12.dp),
									)
								}
							}
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 720)
@Composable
private fun PreviewNoPermissions() = HealthAggregatorTheme {
	Column(modifier = Modifier.fillMaxSize()) {
		CenterAlignedTopAppBar(title = { Text("Health Aggregator") })
		EmptyState(
			icon = Icons.Outlined.Warning,
			title = "Grant Health Connect access",
			description = "Allow the app to read your clinical records from CommonHealth.",
			actionLabel = "Grant access",
			onAction = {},
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 720)
@Composable
private fun PreviewPopulated() = HealthAggregatorTheme {
	Column(modifier = Modifier.fillMaxSize()) {
		CenterAlignedTopAppBar(title = { Text("Health Aggregator") })
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			horizontalArrangement = Arrangement.End,
		) {
			SyncStatusChip(null, false, {})
		}
		LazyVerticalGrid(
			columns = GridCells.Fixed(2),
			contentPadding = androidx.compose.foundation.layout.PaddingValues(
				horizontal = 16.dp,
				vertical = 8.dp,
			),
			verticalArrangement = Arrangement.spacedBy(8.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			items(
				listOf(
					StatTile("all", Icons.Outlined.Folder, "Labs", 47),
					StatTile("vitals", Icons.Outlined.MonitorHeart, "Vitals", 18),
					StatTile("medications", Icons.Outlined.LocalPharmacy, "Meds", 6),
					StatTile("conditions", Icons.Outlined.Healing, "Conditions", 3),
				),
				key = { it.key },
			) { tile ->
				StatCard(icon = tile.icon, label = tile.label, value = tile.value.toString())
			}
		}
	}
}
