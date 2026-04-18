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
	PATIENTS("patients", MergeStrategy.InsertOrIgnore),
	LAB_OBSERVATIONS("lab_observations", MergeStrategy.InsertOrIgnore),
	VITALS_OBSERVATIONS("vitals_observations", MergeStrategy.InsertOrIgnore),
	// CONDITIONS intentionally omitted — user opted out in migration 6→7. Phone neither
	// pushes nor pulls conditions; the table exists but stays empty.
	MEDICATIONS("medications", MergeStrategy.InsertOrIgnore),
	ALLERGIES("allergies", MergeStrategy.InsertOrIgnore),
	ENCOUNTERS("encounters", MergeStrategy.InsertOrIgnore),
	DOCUMENTS("documents", MergeStrategy.InsertOrIgnore),
	DIAGNOSTIC_REPORTS("diagnostic_reports", MergeStrategy.InsertOrIgnore),
	SOURCE_RECORDS("source_records", MergeStrategy.InsertOrIgnore),
	CHAT_CONVERSATIONS("chat_conversations", MergeStrategy.LastWriteWinsOn("updatedAt")),
	CHAT_MESSAGES("chat_messages", MergeStrategy.InsertOrIgnore),
}
