package com.healthaggregator.sync

import com.healthaggregator.data.AppDatabase
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
	val pushedRowsByTable: Map<String, Int>,
	val pulledRowsByTable: Map<String, Int>,
	val ignoredRowsByTable: Map<String, Int>,
	val migrationsApplied: List<Pair<Int, Int>> = emptyList(),
	val durationMs: Long,
)

@Singleton
class SyncRepository @Inject constructor(
	private val client: SyncClient,
	private val db: AppDatabase,
	private val serializer: RowSerializer,
	private val isPairedProvider: () -> Boolean,
) {
	suspend fun syncNow(): Result<SyncResult> = runCatching {
		if (!isPairedProvider()) throw SyncError.NotPaired
		val start = System.currentTimeMillis()

		val pushPayload = buildPushPayload()
		val pushResp = client.push(pushPayload)

		val pullResp = client.pull()
		val pulled = mergePull(pullResp)

		SyncResult(
			pushedRowsByTable = pushResp.inserted_by_table,
			pulledRowsByTable = pulled,
			ignoredRowsByTable = pushResp.ignored_by_table,
			durationMs = System.currentTimeMillis() - start,
		)
	}

	private suspend fun buildPushPayload(): PushRequest {
		val rows = mutableMapOf<String, List<SyncRow>>()
		rows["patient_records"] = db.patientDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["lab_observations"] = db.labDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["vitals_observations"] = db.vitalsDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["condition_records"] = db.conditionDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["medication_records"] = db.medicationDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["allergy_records"] = db.allergyDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["encounter_records"] = db.encounterDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["document_records"] = db.documentDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["diagnostic_report_records"] = db.diagnosticReportDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["source_records"] = db.sourceRecordDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["chat_conversations"] = db.chatDao().getAllConversationsSnapshot().map { serializer.toRow(it) }
		rows["chat_messages"] = db.chatDao().getAllMessagesSnapshot().map { serializer.toRow(it) }
		return PushRequest(batch_id = UUID.randomUUID().toString(), rows_by_table = rows)
	}

	private suspend fun mergePull(resp: PullResponse): Map<String, Int> {
		val counts = mutableMapOf<String, Int>()
		resp.rows_by_table.forEach { (tableName, rows) ->
			counts[tableName] = when (tableName) {
				"patient_records" -> mergePatients(rows)
				"lab_observations" -> mergeLabs(rows)
				"vitals_observations" -> mergeVitals(rows)
				"condition_records" -> mergeConditions(rows)
				"medication_records" -> mergeMedications(rows)
				"allergy_records" -> mergeAllergies(rows)
				"encounter_records" -> mergeEncounters(rows)
				"document_records" -> mergeDocuments(rows)
				"diagnostic_report_records" -> mergeDiagnosticReports(rows)
				"source_records" -> mergeSourceRecords(rows)
				"chat_conversations" -> mergeChatConversations(rows)
				"chat_messages" -> mergeChatMessages(rows)
				else -> 0
			}
		}
		return counts
	}

	private suspend fun mergePatients(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromPatientRow(it) }
		db.patientDao().upsertAll(entities)
		return entities.size
	}

	private suspend fun mergeLabs(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromLabRow(it) }
		db.labDao().upsertAll(entities)
		return entities.size
	}

	private suspend fun mergeVitals(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromVitalsRow(it) }
		db.vitalsDao().upsertAll(entities)
		return entities.size
	}

	private suspend fun mergeConditions(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromConditionRow(it) }
		db.conditionDao().upsertAll(entities)
		return entities.size
	}

	private suspend fun mergeMedications(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromMedicationRow(it) }
		db.medicationDao().upsertAll(entities)
		return entities.size
	}

	private suspend fun mergeAllergies(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromAllergyRow(it) }
		db.allergyDao().upsertAll(entities)
		return entities.size
	}

	private suspend fun mergeEncounters(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromEncounterRow(it) }
		db.encounterDao().upsertAll(entities)
		return entities.size
	}

	private suspend fun mergeDocuments(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromDocumentRow(it) }
		db.documentDao().upsertAll(entities)
		return entities.size
	}

	private suspend fun mergeDiagnosticReports(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromDiagnosticReportRow(it) }
		db.diagnosticReportDao().upsertAll(entities)
		return entities.size
	}

	private suspend fun mergeSourceRecords(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromSourceRecordRow(it) }
		db.sourceRecordDao().upsertAll(entities)
		return entities.size
	}

	private suspend fun mergeChatConversations(rows: List<JsonObject>): Int {
		var applied = 0
		for (row in rows) {
			val incoming = serializer.fromChatConversationRow(row)
			val existing = db.chatDao().getConversation(incoming.id)
			if (existing == null || incoming.updatedAt.isAfter(existing.updatedAt)) {
				db.chatDao().upsertConversation(incoming)
				applied++
			}
		}
		return applied
	}

	private suspend fun mergeChatMessages(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromChatMessageRow(it) }
		db.chatDao().upsertMessages(entities)
		return entities.size
	}
}
