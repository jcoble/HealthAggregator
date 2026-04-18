package com.healthaggregator.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavHostController

@Composable
fun AppNavHost(navController: NavHostController) {
	NavHost(
		navController = navController,
		startDestination = TopLevelRoute.HOME.route,
	) {
		composable(TopLevelRoute.HOME.route) {
			// HomeScreen is implemented in Phase 4 Task 20
			PlaceholderScreen("Home")
		}
		composable("${TopLevelRoute.RECORDS.route}?type={type}") { backStackEntry ->
			val type = backStackEntry.arguments?.getString("type") ?: "all"
			// RecordsScreen is implemented in Phase 4 Task 21
			PlaceholderScreen("Records (type=$type)")
		}
		composable(TopLevelRoute.SETTINGS.route) {
			// SettingsScreen is implemented in Phase 4 Task 22
			PlaceholderScreen("Settings")
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
