package com.healthaggregator.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

enum class TopLevelRoute(val route: String, val label: String, val icon: ImageVector) {
	HOME("home", "Home", Icons.Outlined.Dashboard),
	RECORDS("records", "Records", Icons.Outlined.FolderShared),
	SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}

@Composable
fun AppBottomNav(navController: NavHostController) {
	val currentEntry by navController.currentBackStackEntryAsState()
	val currentRoute = currentEntry?.destination?.route

	NavigationBar {
		TopLevelRoute.entries.forEach { top ->
			val selected = currentEntry?.destination?.hierarchy?.any { it.route?.startsWith(top.route) == true } ?: false
			NavigationBarItem(
				selected = selected,
				onClick = {
					navController.navigate(top.route) {
						popUpTo(navController.graph.startDestinationId) { saveState = true }
						launchSingleTop = true
						restoreState = true
					}
				},
				icon = { Icon(top.icon, contentDescription = top.label) },
				label = { Text(top.label) },
			)
		}
	}
}
