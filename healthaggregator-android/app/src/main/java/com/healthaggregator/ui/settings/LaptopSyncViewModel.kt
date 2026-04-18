package com.healthaggregator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.sync.SyncError
import com.healthaggregator.sync.SyncRepository
import com.healthaggregator.sync.SyncResult
import com.healthaggregator.util.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LaptopSyncUiState(
	val paired: Boolean,
	val hostname: String?,
	val lastSyncAt: Long,
	val lastSyncSummary: String?,
	val syncing: Boolean = false,
	val errorMessage: String? = null,
)

@HiltViewModel
class LaptopSyncViewModel @Inject constructor(
	private val repo: SyncRepository,
	private val secure: SecureStorage,
) : ViewModel() {
	private val _state = MutableStateFlow(snapshot())
	val state: StateFlow<LaptopSyncUiState> = _state.asStateFlow()

	private fun snapshot() = LaptopSyncUiState(
		paired = secure.laptopHostname != null && secure.laptopToken != null,
		hostname = secure.laptopHostname,
		lastSyncAt = secure.lastSyncAt,
		lastSyncSummary = secure.lastSyncSummary,
	)

	fun refreshFromStorage() { _state.value = snapshot() }

	fun syncNow() {
		viewModelScope.launch {
			_state.value = _state.value.copy(syncing = true, errorMessage = null)
			val result = repo.syncNow()
			result.onSuccess { r ->
				secure.lastSyncAt = System.currentTimeMillis()
				secure.lastSyncSummary = summarize(r)
				_state.value = snapshot().copy(syncing = false)
			}.onFailure { e ->
				_state.value = _state.value.copy(
					syncing = false,
					errorMessage = when (e) {
						is SyncError.NotPaired -> "Not paired yet."
						is SyncError.BadAuth -> "Bad token — re-pair with laptop."
						is SyncError.Network -> "Can't reach laptop. Is Tailscale up?"
						is SyncError.DaemonAheadOfPhone -> "Daemon is on schema v${e.daemon}, phone is on v${e.phone}. Update the app."
						else -> "Sync failed: ${e.message}"
					},
				)
			}
		}
	}

	fun unpair() {
		secure.clearLaptopPairing()
		refreshFromStorage()
	}

	private fun summarize(r: SyncResult): String {
		val pushed = r.pushedRowsByTable.values.sum()
		val pulled = r.pulledRowsByTable.values.sum()
		return "+$pulled from laptop, +$pushed to laptop"
	}
}
