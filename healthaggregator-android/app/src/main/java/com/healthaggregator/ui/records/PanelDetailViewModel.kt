package com.healthaggregator.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.isAbnormal
import com.healthaggregator.data.repository.RecordsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PanelDetailUiState(
	val loading: Boolean = true,
	val sourceRequestReference: String = "",
	val panelName: String = "",
	val sourceSystem: String = "",
	val sourceName: String = "",
	val components: List<LabObservation> = emptyList(),
	val abnormalCount: Int = 0,
)

@HiltViewModel
class PanelDetailViewModel @Inject constructor(
	private val records: RecordsRepository,
	savedState: SavedStateHandle,
) : ViewModel() {
	private val serviceRequest: String = requireNotNull(savedState["sr"])

	val uiState: StateFlow<PanelDetailUiState> =
		records.observeLabsByServiceRequest(serviceRequest)
			.map { components ->
				val first = components.firstOrNull()
				PanelDetailUiState(
					loading = false,
					sourceRequestReference = serviceRequest,
					panelName = first?.serviceRequestDisplay ?: first?.testName ?: "Panel",
					sourceSystem = first?.sourceSystem ?: "",
					sourceName = first?.sourceName ?: "",
					components = components,
					abnormalCount = components.count { isAbnormal(it) },
				)
			}
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PanelDetailUiState())
}
