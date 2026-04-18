package com.healthaggregator.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
		composable("${TopLevelRoute.RECORDS.route}?type={type}") { backStackEntry ->
			val type = backStackEntry.arguments?.getString("type") ?: "all"
			PlaceholderScreen("Records (type=$type)") // Task 21 replaces
		}
		composable(TopLevelRoute.SETTINGS.route) {
			PlaceholderScreen("Settings") // Task 22 replaces
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
