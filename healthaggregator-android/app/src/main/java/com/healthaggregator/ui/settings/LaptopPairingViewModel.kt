package com.healthaggregator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.sync.SyncClient
import com.healthaggregator.sync.SyncError
import com.healthaggregator.util.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairingUiState(
	val hostname: String = "",
	val token: String = "",
	val testing: Boolean = false,
	val errorMessage: String? = null,
	val success: Boolean = false,
)

@HiltViewModel
class LaptopPairingViewModel @Inject constructor(
	private val client: SyncClient,
	private val secure: SecureStorage,
) : ViewModel() {
	private val _state = MutableStateFlow(
		PairingUiState(hostname = secure.laptopHostname ?: "", token = secure.laptopToken ?: "")
	)
	val state: StateFlow<PairingUiState> = _state.asStateFlow()

	fun onHostnameChange(h: String) { _state.value = _state.value.copy(hostname = h, errorMessage = null, success = false) }
	fun onTokenChange(t: String) { _state.value = _state.value.copy(token = t, errorMessage = null, success = false) }

	fun testAndSave() {
		viewModelScope.launch {
			val s = _state.value
			if (s.hostname.isBlank() || s.token.isBlank()) {
				_state.value = s.copy(errorMessage = "Both fields required")
				return@launch
			}
			_state.value = s.copy(testing = true, errorMessage = null)
			secure.laptopHostname = s.hostname.trim()
			secure.laptopToken = s.token.trim()
			try {
				client.version()
				_state.value = _state.value.copy(testing = false, success = true)
			} catch (e: SyncError.BadAuth) {
				secure.clearLaptopPairing()
				_state.value = _state.value.copy(testing = false, errorMessage = "Bad token — double-check you copied it correctly.")
			} catch (e: SyncError.Network) {
				secure.clearLaptopPairing()
				_state.value = _state.value.copy(testing = false, errorMessage = "Can't reach laptop. Is Tailscale up and the daemon running?")
			} catch (e: Exception) {
				secure.clearLaptopPairing()
				_state.value = _state.value.copy(testing = false, errorMessage = "Pairing failed: ${e.message}")
			}
		}
	}
}
