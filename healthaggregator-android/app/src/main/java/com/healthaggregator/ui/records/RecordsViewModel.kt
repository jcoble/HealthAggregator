package com.healthaggregator.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.data.LabPanelAggregate
import com.healthaggregator.ui.components.SortOrder
import com.healthaggregator.ui.components.toggled
import com.healthaggregator.data.entities.AllergyRecord
import com.healthaggregator.data.entities.ConditionRecord
import com.healthaggregator.data.entities.DocumentRecord
import com.healthaggregator.data.entities.EncounterRecord
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.entities.MedicationRecord
import com.healthaggregator.data.entities.VitalsObservation
import com.healthaggregator.data.isAbnormal
import com.healthaggregator.data.repository.RecordsRepository
import com.healthaggregator.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

enum class FilterType(val navKey: String, val chipLabel: String) {
	ALL("all", "All"),
	LABS("labs", "Labs"),
	VITALS("vitals", "Vitals"),
	MEDICATIONS("medications", "Meds"),
	CONDITIONS("conditions", "Conditions"),
	ALLERGIES("allergies", "Allergies"),
	ENCOUNTERS("encounters", "Encounters"),
	DOCUMENTS("documents", "Documents");

	companion object {
		fun fromNavKey(key: String?): FilterType =
			entries.firstOrNull { it.navKey == key } ?: ALL
	}
}

data class RecordRowData(
	val id: String,
	val kind: FilterType,
	val title: String,
	val summary: String,
	val sourceSystem: String,
	val sourceName: String,
	val trailingText: String?,
	val fhirReference: String,
	val effectiveAt: Instant?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordsViewModel @Inject constructor(
	private val records: RecordsRepository,
	private val sync: SyncRepository,
	savedState: SavedStateHandle,
) : ViewModel() {

	private val _filter = MutableStateFlow(FilterType.fromNavKey(savedState.get<String>("type")))
	val filter = _filter.asStateFlow()

	private val _query = MutableStateFlow("")
	val query = _query.asStateFlow()

	private val _sort = MutableStateFlow(SortOrder.NEWEST_FIRST)
	val sort = _sort.asStateFlow()

	private val panelRowsFlow: Flow<List<LabPanelAggregate>> = records.observePanels()

	private val abnormalServiceRequestsFlow: Flow<Set<String>> = records.observeLabs().map { list ->
		list.filter { isAbnormal(it) && it.serviceRequestReference != null }
			.map { it.serviceRequestReference!! }
			.toSet()
	}

	data class LabsUiState(val panels: List<LabPanelAggregate>, val abnormalSrs: Set<String>)

	val labsState: StateFlow<LabsUiState> =
		combine(panelRowsFlow, abnormalServiceRequestsFlow) { panels, abn -> LabsUiState(panels, abn) }
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LabsUiState(emptyList(), emptySet()))

	fun setQuery(q: String) { _query.value = q }
	fun toggleSort() { _sort.value = _sort.value.toggled() }

	private val labRowsFlow = records.observeLabs().map { list -> list.map { it.toRow() } }
	private val vitalRowsFlow = records.observeVitals().map { list -> list.map { it.toRow() } }
	private val medRowsFlow = records.observeMedications().map { list -> list.map { it.toRow() } }
	private val conditionRowsFlow = records.observeConditions().map { list -> list.map { it.toRow() } }
	private val allergyRowsFlow = records.observeAllergies().map { list -> list.map { it.toRow() } }
	private val encounterRowsFlow = records.observeEncounters().map { list -> list.map { it.toRow() } }
	private val documentRowsFlow = records.observeDocuments().map { list -> list.map { it.toRow() } }

	val rows: StateFlow<List<RecordRowData>> = _filter.flatMapLatest { f ->
		when (f) {
			FilterType.ALL -> combine(
				labRowsFlow, vitalRowsFlow, medRowsFlow,
				conditionRowsFlow, allergyRowsFlow,
				encounterRowsFlow, documentRowsFlow,
			) { arr -> arr.toList().flatten().sortedByDescending { it.effectiveAt ?: Instant.EPOCH } }
			FilterType.LABS -> labRowsFlow
			FilterType.VITALS -> vitalRowsFlow
			FilterType.MEDICATIONS -> medRowsFlow
			FilterType.CONDITIONS -> conditionRowsFlow
			FilterType.ALLERGIES -> allergyRowsFlow
			FilterType.ENCOUNTERS -> encounterRowsFlow
			FilterType.DOCUMENTS -> documentRowsFlow
		}
	}.combine(_query) { rows, q ->
		if (q.isBlank()) rows else rows.filter { it.title.contains(q, ignoreCase = true) || it.summary.contains(q, ignoreCase = true) }
	}.combine(_sort) { rows, s ->
		when (s) {
			SortOrder.NEWEST_FIRST -> rows.sortedByDescending { it.effectiveAt ?: Instant.EPOCH }
			SortOrder.OLDEST_FIRST -> rows.sortedBy { it.effectiveAt ?: Instant.EPOCH }
		}
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	fun setFilter(f: FilterType) { _filter.value = f }

	fun refresh() {
		viewModelScope.launch { sync.sync() }
	}
}

private fun LabObservation.toRow(): RecordRowData {
	val unit = unit?.let { " $it" } ?: ""
	val valueStr = numericValue?.let { "$it$unit" } ?: (textValue ?: "")
	return RecordRowData(
		id = "lab-$id",
		kind = FilterType.LABS,
		title = testName,
		summary = listOfNotNull(valueStr.ifBlank { null }, effectiveAt?.let { formatDate(it) }).joinToString(" — "),
		sourceSystem = sourceSystem,
		sourceName = sourceName,
		trailingText = if (isAbnormal(this)) (interpretation?.take(2) ?: "!") else null,
		fhirReference = fhirReference,
		effectiveAt = effectiveAt,
	)
}

private fun VitalsObservation.toRow(): RecordRowData {
	val unit = unit?.let { " $it" } ?: ""
	val valueStr = numericValue?.let { "$it$unit" } ?: ""
	return RecordRowData(
		id = "vital-$id",
		kind = FilterType.VITALS,
		title = displayName,
		summary = listOfNotNull(valueStr.ifBlank { null }, effectiveAt?.let { formatDate(it) }).joinToString(" — "),
		sourceSystem = sourceSystem,
		sourceName = sourceName,
		trailingText = null,
		fhirReference = fhirReference,
		effectiveAt = effectiveAt,
	)
}

private fun MedicationRecord.toRow() = RecordRowData(
	id = "med-$id",
	kind = FilterType.MEDICATIONS,
	title = medicationText ?: "Unknown medication",
	summary = listOfNotNull(status, authoredAt?.let { formatDate(it) }).joinToString(" — "),
	sourceSystem = sourceSystem,
	sourceName = sourceName,
	trailingText = null,
	fhirReference = fhirReference,
	effectiveAt = authoredAt,
)

private fun ConditionRecord.toRow(): RecordRowData {
	val primaryDate = onsetAt ?: recordedAt
	return RecordRowData(
		id = "cond-$id",
		kind = FilterType.CONDITIONS,
		title = codeText ?: "Unknown condition",
		summary = listOfNotNull(clinicalStatus, primaryDate?.let { formatDate(it) }).joinToString(" — "),
		sourceSystem = sourceSystem,
		sourceName = sourceName,
		trailingText = null,
		fhirReference = fhirReference,
		effectiveAt = primaryDate,
	)
}

private fun AllergyRecord.toRow() = RecordRowData(
	id = "allergy-$id",
	kind = FilterType.ALLERGIES,
	title = allergyText ?: "Unknown allergy",
	summary = listOfNotNull(clinicalStatus, recordedAt?.let { formatDate(it) }).joinToString(" — "),
	sourceSystem = sourceSystem,
	sourceName = sourceName,
	trailingText = null,
	fhirReference = fhirReference,
	effectiveAt = recordedAt,
)

private fun EncounterRecord.toRow() = RecordRowData(
	id = "enc-$id",
	kind = FilterType.ENCOUNTERS,
	title = typeText ?: "Encounter",
	summary = listOfNotNull(status, startedAt?.let { formatDate(it) }).joinToString(" — "),
	sourceSystem = sourceSystem,
	sourceName = sourceName,
	trailingText = null,
	fhirReference = fhirReference,
	effectiveAt = startedAt,
)

private fun DocumentRecord.toRow() = RecordRowData(
	id = "doc-$id",
	kind = FilterType.DOCUMENTS,
	title = typeText ?: "Document",
	summary = listOfNotNull(status, documentedAt?.let { formatDate(it) }).joinToString(" — "),
	sourceSystem = sourceSystem,
	sourceName = sourceName,
	trailingText = null,
	fhirReference = fhirReference,
	effectiveAt = documentedAt,
)

private fun formatDate(i: Instant): String {
	val ldt = java.time.LocalDateTime.ofInstant(i, java.time.ZoneId.systemDefault())
	return ldt.toLocalDate().toString()
}
