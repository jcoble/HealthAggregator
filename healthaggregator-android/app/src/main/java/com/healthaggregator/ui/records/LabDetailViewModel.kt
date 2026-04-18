package com.healthaggregator.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.isAbnormal
import com.healthaggregator.data.repository.RecordsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LabDetailUiState(
	val loading: Boolean = true,
	val current: LabObservation? = null,
	val trend: List<LabObservation> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LabDetailViewModel @Inject constructor(
	private val records: RecordsRepository,
	savedState: SavedStateHandle,
) : ViewModel() {
	private val sourceSystem: String = requireNotNull(savedState["source"])
	private val fhirReference: String = requireNotNull(savedState["fhirRef"])

	private val _rawJson = MutableStateFlow<String?>(null)
	val rawJson: StateFlow<String?> = _rawJson.asStateFlow()

	val uiState: StateFlow<LabDetailUiState> =
		records.observeLabs()
			.map { labs -> labs.firstOrNull { it.sourceSystem == sourceSystem && it.fhirReference == fhirReference } }
			.flatMapLatest { current ->
				if (current == null || (current.loincCode == null && current.canonicalTestName == null)) {
					flowOf(LabDetailUiState(loading = false, current = current))
				} else {
					records.observeLabsByLoincOrCanonical(current.loincCode, current.canonicalTestName)
						.map { trend ->
							LabDetailUiState(loading = false, current = current, trend = trend)
						}
				}
			}
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LabDetailUiState())

	init {
		viewModelScope.launch {
			_rawJson.value = records.findRawJson(sourceSystem, fhirReference)
		}
	}

	fun isAbnormalLab(lab: LabObservation): Boolean = isAbnormal(lab)
}
