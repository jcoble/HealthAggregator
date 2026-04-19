package com.healthaggregator.ui.export

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.ai.pdf.ReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class ReportType {
	COMPREHENSIVE,
	LABS,
	RECENT_ABNORMALS,
	ALL_ABNORMALS,
	ALLERGIES_AND_NARRATIVE,
	VITALS,
	CARDIOVASCULAR,
	ENDOCRINE,
}

data class ExportUiState(
	val generating: ReportType? = null,
	val lastError: String? = null,
)

data class ShareEvent(val file: File, val subject: String, val type: ReportType)

@HiltViewModel
class ExportViewModel @Inject constructor(
	@ApplicationContext private val appContext: Context,
	private val generator: ReportGenerator,
) : ViewModel() {
	private val _uiState = MutableStateFlow(ExportUiState())
	val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

	private val _shareEvents = MutableSharedFlow<ShareEvent>(extraBufferCapacity = 4)
	val shareEvents: SharedFlow<ShareEvent> = _shareEvents.asSharedFlow()

	fun generateReport(type: ReportType) {
		if (_uiState.value.generating != null) return
		viewModelScope.launch {
			_uiState.value = ExportUiState(generating = type)
			try {
				val file = generator.generate(appContext, type)
				_shareEvents.tryEmit(ShareEvent(file = file, subject = file.nameWithoutExtension, type = type))
				_uiState.value = ExportUiState(generating = null, lastError = null)
			} catch (t: Throwable) {
				Log.e(TAG, "Report generation failed for $type", t)
				_uiState.value = ExportUiState(generating = null, lastError = t.message ?: "Unknown error")
			}
		}
	}

	fun clearError() {
		_uiState.value = _uiState.value.copy(lastError = null)
	}

	private companion object {
		const val TAG = "ExportViewModel"
	}
}
