package com.healthaggregator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.health.connect.client.PermissionController
import androidx.navigation.compose.rememberNavController
import com.healthaggregator.sync.HealthConnectReader
import com.healthaggregator.ui.navigation.AppBottomNav
import com.healthaggregator.ui.navigation.AppNavHost
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

	@Inject lateinit var reader: HealthConnectReader

	private lateinit var permissionLauncher: ActivityResultLauncher<Set<String>>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		val contract: ActivityResultContract<Set<String>, Set<String>> =
			PermissionController.createRequestPermissionResultContract()
		permissionLauncher = registerForActivityResult(contract) {
			// HomeViewModel re-reads permissions on recomposition via onPermissionsUpdated
		}

		setContent {
			HealthAggregatorTheme {
				AppRoot(onRequestPermissions = { permissionLauncher.launch(reader.readPermissions) })
			}
		}
	}
}

@Composable
private fun AppRoot(onRequestPermissions: () -> Unit) {
	val navController = rememberNavController()
	Scaffold(
		bottomBar = { AppBottomNav(navController) },
	) { padding ->
		Box(Modifier.padding(padding)) {
			AppNavHost(navController, onRequestPermissions)
		}
	}
}
