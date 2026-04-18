package com.healthaggregator.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.healthaggregator.ui.home.HomeScreen

@Composable
fun AppNavHost(navController: NavHostController, onRequestPermissions: () -> Unit) {
	NavHost(
		navController = navController,
		startDestination = TopLevelRoute.HOME.route,
	) {
		composable(TopLevelRoute.HOME.route) {
			HomeScreen(
				onNavigateToRecords = { type ->
					navController.navigate("${TopLevelRoute.RECORDS.route}?type=$type")
				},
				onRequestPermissions = onRequestPermissions,
			)
		}
		composable(
			route = "${TopLevelRoute.RECORDS.route}?type={type}",
			arguments = listOf(navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null }),
		) {
			// Nav arg 'type' is read by RecordsViewModel via SavedStateHandle automatically
			com.healthaggregator.ui.records.RecordsScreen()
		}
		composable(TopLevelRoute.SETTINGS.route) {
			com.healthaggregator.ui.settings.SettingsScreen(onRequestPermissions = onRequestPermissions)
		}
	}
}

@Composable
private fun PlaceholderScreen(title: String) {
	Box(
		modifier = Modifier.fillMaxSize(),
		contentAlignment = Alignment.Center,
	) {
		Text(title)
	}
}
