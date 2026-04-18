package com.healthaggregator.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.data.repository.RecordsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

data class RecordDetailUiState(
	val loading: Boolean = true,
	val resourceType: String = "",
	val rawJson: String? = null,
	val fields: List<Pair<String, String>> = emptyList(),
	val sourceSystem: String = "",
	val fhirReference: String = "",
)

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
	private val records: RecordsRepository,
	savedState: SavedStateHandle,
) : ViewModel() {
	private val sourceSystem: String = requireNotNull(savedState["source"])
	private val fhirReference: String = requireNotNull(savedState["fhirRef"])

	private val _state = MutableStateFlow(
		RecordDetailUiState(sourceSystem = sourceSystem, fhirReference = fhirReference)
	)
	val uiState: StateFlow<RecordDetailUiState> = _state.asStateFlow()

	init {
		viewModelScope.launch {
			val raw = records.findRawJson(sourceSystem, fhirReference)
			_state.value = RecordDetailUiState(
				loading = false,
				resourceType = fhirReference.substringBefore("/"),
				rawJson = raw,
				fields = extractFields(raw),
				sourceSystem = sourceSystem,
				fhirReference = fhirReference,
			)
		}
	}

	private fun extractFields(raw: String?): List<Pair<String, String>> {
		if (raw == null) return emptyList()
		return try {
			val root = Json { ignoreUnknownKeys = true }
				.parseToJsonElement(raw).jsonObject
			val fields = mutableListOf<Pair<String, String>>()
			root.entries.forEach { (k, v) ->
				if (k == "resourceType" || k == "id") return@forEach
				val display = when (v) {
					is JsonPrimitive -> v.contentOrNull ?: "null"
					is JsonArray -> "[${v.size} items]"
					is JsonObject -> extractReadable(v)
					else -> v.toString()
				}
				if (display.isNotBlank()) fields.add(k to display)
			}
			fields
		} catch (e: Exception) {
			emptyList()
		}
	}

	private fun extractReadable(obj: JsonObject): String {
		obj["text"]?.let { return (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
		obj["display"]?.let { return (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
		obj["reference"]?.let { return (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
		return "{…}"
	}
}
