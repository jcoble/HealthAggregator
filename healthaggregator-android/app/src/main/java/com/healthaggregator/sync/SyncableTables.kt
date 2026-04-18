package com.healthaggregator.sync

sealed interface MergeStrategy {
	object InsertOrIgnore : MergeStrategy
	data class LastWriteWinsOn(val timestampColumn: String) : MergeStrategy
}

/**
 * The set of tables that participate in phone↔laptop sync.
 * See Docs/specs/2026-04-18-laptop-sync-design.md §"Syncable vs non-syncable tables".
 */
enum class SyncableTable(val tableName: String, val mergeStrategy: MergeStrategy) {
	PATIENT_RECORDS("patient_records", MergeStrategy.InsertOrIgnore),
	LAB_OBSERVATIONS("lab_observations", MergeStrategy.InsertOrIgnore),
	VITALS_OBSERVATIONS("vitals_observations", MergeStrategy.InsertOrIgnore),
	CONDITION_RECORDS("condition_records", MergeStrategy.InsertOrIgnore),
	MEDICATION_RECORDS("medication_records", MergeStrategy.InsertOrIgnore),
	ALLERGY_RECORDS("allergy_records", MergeStrategy.InsertOrIgnore),
	ENCOUNTER_RECORDS("encounter_records", MergeStrategy.InsertOrIgnore),
	DOCUMENT_RECORDS("document_records", MergeStrategy.InsertOrIgnore),
	DIAGNOSTIC_REPORT_RECORDS("diagnostic_report_records", MergeStrategy.InsertOrIgnore),
	SOURCE_RECORDS("source_records", MergeStrategy.InsertOrIgnore),
	CHAT_CONVERSATIONS("chat_conversations", MergeStrategy.LastWriteWinsOn("updatedAt")),
	CHAT_MESSAGES("chat_messages", MergeStrategy.InsertOrIgnore),
}
