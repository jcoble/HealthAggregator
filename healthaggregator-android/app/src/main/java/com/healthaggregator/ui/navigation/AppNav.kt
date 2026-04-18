package com.healthaggregator.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.healthaggregator.ui.assistant.AssistantScreen
import com.healthaggregator.ui.home.HomeScreen
import com.healthaggregator.ui.records.LabDetailScreen
import com.healthaggregator.ui.records.PanelDetailScreen
import com.healthaggregator.ui.records.RecordDetailScreen
import com.healthaggregator.ui.records.RecordsScreen
import com.healthaggregator.ui.settings.SettingsScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private fun encode(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.name())

@Composable
fun AppNavHost(navController: NavHostController, onRequestPermissions: () -> Unit) {
	NavHost(
		navController = navController,
		startDestination = TopLevelRoute.HOME.route,
	) {
		composable(TopLevelRoute.HOME.route) {
			HomeScreen(
				onNavigateToRecords = { type -> navController.navigate("${TopLevelRoute.RECORDS.route}?type=$type") },
				onRequestPermissions = onRequestPermissions,
			)
		}
		composable(
			route = "${TopLevelRoute.RECORDS.route}?type={type}",
			arguments = listOf(navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null }),
		) {
			RecordsScreen(
				onOpenPanel = { sr -> navController.navigate("panel/${encode(sr)}") },
				onOpenLab = { source, fhirRef ->
					navController.navigate("lab/${encode(source)}/${encode(fhirRef)}")
				},
				onOpenRecord = { source, fhirRef ->
					navController.navigate("record/${encode(source)}/${encode(fhirRef)}")
				},
			)
		}
		composable(TopLevelRoute.ASSISTANT.route) {
			AssistantScreen(
				onCitationClick = { source, fhirRef ->
					// Prefer the lab detail route if the fhirRef looks like an Observation; fall back to generic record.
					val dest = if (fhirRef.startsWith("Observation/")) "lab" else "record"
					navController.navigate("$dest/${encode(source)}/${encode(fhirRef)}")
				},
				onOpenSettings = { navController.navigate(TopLevelRoute.SETTINGS.route) },
			)
		}
		composable(TopLevelRoute.SETTINGS.route) {
			SettingsScreen(onRequestPermissions = onRequestPermissions)
		}
		composable(
			route = "lab/{source}/{fhirRef}",
			arguments = listOf(
				navArgument("source") { type = NavType.StringType },
				navArgument("fhirRef") { type = NavType.StringType },
			),
		) {
			LabDetailScreen(onBack = { navController.popBackStack() })
		}
		composable(
			route = "panel/{sr}",
			arguments = listOf(navArgument("sr") { type = NavType.StringType }),
		) {
			PanelDetailScreen(
				onBack = { navController.popBackStack() },
				onOpenComponent = { source, fhirRef ->
					navController.navigate("lab/${encode(source)}/${encode(fhirRef)}")
				},
			)
		}
		composable(
			route = "record/{source}/{fhirRef}",
			arguments = listOf(
				navArgument("source") { type = NavType.StringType },
				navArgument("fhirRef") { type = NavType.StringType },
			),
		) {
			RecordDetailScreen(onBack = { navController.popBackStack() })
		}
	}
}
