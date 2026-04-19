package com.healthaggregator.ui.export

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Identifies which of the 8 PDF report buttons the user pressed.
 * Step 2 stub — Step 3 will add a handler per enum value that produces a PDF File.
 */
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

@HiltViewModel
class ExportViewModel @Inject constructor() : ViewModel() {
	fun generateReport(type: ReportType) {
		Log.i(TAG, "generateReport stub: $type — step 3 will wire PDF generation + share sheet")
	}

	private companion object {
		const val TAG = "ExportViewModel"
	}
}
