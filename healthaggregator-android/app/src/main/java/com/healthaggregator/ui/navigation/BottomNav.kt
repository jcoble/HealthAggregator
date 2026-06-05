package com.healthaggregator.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

enum class TopLevelRoute(val route: String, val label: String, val icon: ImageVector) {
	HOME("home", "Home", Icons.Outlined.Dashboard),
	RECORDS("records", "Records", Icons.Outlined.FolderShared),
	ASSISTANT("assistant", "Assistant", Icons.Outlined.Chat),
	EXPORT("export", "Export", Icons.Outlined.PictureAsPdf),
	SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}

@Composable
fun AppBottomNav(navController: NavHostController) {
	val currentEntry by navController.currentBackStackEntryAsState()

	Column {
		HorizontalDivider(color = MaterialTheme.colorScheme.outline)
		NavigationBar(
			containerColor = MaterialTheme.colorScheme.surface,
			tonalElevation = 0.dp,
		) {
			TopLevelRoute.entries.forEach { top ->
				val selected = currentEntry?.destination?.hierarchy?.any { it.route?.startsWith(top.route) == true } ?: false
				NavigationBarItem(
					selected = selected,
					onClick = {
						navController.navigate(top.route) {
							popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
							launchSingleTop = true
						}
					},
					icon = { Icon(top.icon, contentDescription = top.label) },
					label = {
						Text(
							text = top.label,
							fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
						)
					},
					colors = NavigationBarItemDefaults.colors(
						selectedIconColor = MaterialTheme.colorScheme.primary,
						selectedTextColor = MaterialTheme.colorScheme.primary,
						indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
						unselectedIconColor = MaterialTheme.colorScheme.secondary,
						unselectedTextColor = MaterialTheme.colorScheme.secondary,
					),
				)
			}
		}
	}
}
