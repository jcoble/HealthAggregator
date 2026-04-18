package com.healthaggregator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.healthaggregator.ui.navigation.AppBottomNav
import com.healthaggregator.ui.navigation.AppNavHost
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			HealthAggregatorTheme {
				AppRoot()
			}
		}
	}
}

@Composable
private fun AppRoot() {
	val navController = rememberNavController()
	Scaffold(
		bottomBar = { AppBottomNav(navController) },
	) { padding ->
		Box(Modifier.padding(padding)) {
			AppNavHost(navController)
		}
	}
}
