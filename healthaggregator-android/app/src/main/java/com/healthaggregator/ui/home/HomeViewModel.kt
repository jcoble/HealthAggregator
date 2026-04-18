package com.healthaggregator.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.data.entities.SourceSummary
import com.healthaggregator.data.entities.SyncJob
import com.healthaggregator.data.repository.RecordsRepository
import com.healthaggregator.data.repository.SyncRepository
import com.healthaggregator.sync.HealthConnectReader
import com.healthaggregator.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
	val permissionsGranted: Boolean = false,
	val healthConnectAvailable: Boolean = true,
	val labs: Int = 0,
	val vitals: Int = 0,
	val medications: Int = 0,
	val allergies: Int = 0,
	val encounters: Int = 0,
	val documents: Int = 0,
	val sources: List<SourceSummary> = emptyList(),
	val latestSync: SyncJob? = null,
	val syncState: SyncState = SyncState.Idle,
)

private data class Counts(
	val labs: Int, val vitals: Int, val meds: Int,
	val allergies: Int,
	val encounters: Int, val docs: Int,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
	private val records: RecordsRepository,
	private val sync: SyncRepository,
	private val reader: HealthConnectReader,
) : ViewModel() {

	private val _permission = MutableStateFlow(false)

	private val countsFlow: Flow<Counts> = combine(
		records.countLabs(), records.countVitals(), records.countMedications(),
		records.countAllergies(), records.countEncounters(), records.countDocuments(),
	) { arr ->
		Counts(arr[0], arr[1], arr[2], arr[3], arr[4], arr[5])
	}

	val uiState: StateFlow<HomeUiState> = combine(
		countsFlow,
		records.observeSourcesSummary(),
		records.observeLatestSync(),
		sync.state,
		_permission,
	) { c, srcs, latest, st, perm ->
		HomeUiState(
			permissionsGranted = perm,
			healthConnectAvailable = reader.isAvailable(),
			labs = c.labs, vitals = c.vitals, medications = c.meds,
			allergies = c.allergies, encounters = c.encounters,
			documents = c.docs,
			sources = srcs,
			latestSync = latest,
			syncState = st,
		)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

	init {
		viewModelScope.launch { _permission.value = reader.hasAllPermissions() }
		viewModelScope.launch { records.ensureNamesNormalized() }
	}

	fun onPermissionsUpdated() {
		viewModelScope.launch { _permission.value = reader.hasAllPermissions() }
	}

	fun refresh() {
		viewModelScope.launch { sync.sync() }
	}
}
