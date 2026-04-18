package com.healthaggregator.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaptopPairingScreen(
	onBack: () -> Unit,
	onPaired: () -> Unit,
	viewModel: LaptopPairingViewModel = hiltViewModel(),
) {
	val state by viewModel.state.collectAsStateWithLifecycle()

	LaunchedEffect(state.success) {
		if (state.success) onPaired()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Pair with laptop") },
				navigationIcon = {
					TextButton(onClick = onBack) { Text("Back") }
				},
			)
		},
	) { padding ->
		Column(
			modifier = Modifier
				.padding(padding)
				.padding(16.dp)
				.fillMaxSize(),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(
				"Enter your laptop's Tailscale hostname (e.g. laptop.tail-abc.ts.net) and the sync token shown by `make run`.",
				style = MaterialTheme.typography.bodyMedium,
			)
			OutlinedTextField(
				value = state.hostname,
				onValueChange = viewModel::onHostnameChange,
				label = { Text("Hostname") },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
			)
			OutlinedTextField(
				value = state.token,
				onValueChange = viewModel::onTokenChange,
				label = { Text("Auth token") },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
			)
			if (state.errorMessage != null) {
				Text(
					state.errorMessage!!,
					color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.bodySmall,
				)
			}
			Button(
				onClick = viewModel::testAndSave,
				enabled = !state.testing && state.hostname.isNotBlank() && state.token.isNotBlank(),
				modifier = Modifier.fillMaxWidth(),
			) {
				if (state.testing) {
					CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
					Spacer(Modifier.width(8.dp))
				}
				Text("Test and save")
			}
		}
	}
}
