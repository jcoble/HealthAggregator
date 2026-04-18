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
	private val migrationLoader: MigrationLoader,
	private val phoneSchemaVersion: Int = 5,
	private val isPairedProvider: () -> Boolean,
) {
	suspend fun syncNow(): Result<SyncResult> = runCatching {
		if (!isPairedProvider()) throw SyncError.NotPaired
		val start = System.currentTimeMillis()

		val version = client.version()
		val daemonSchema = version.schema_version
		val migrationsApplied = mutableListOf<Pair<Int, Int>>()

		if (daemonSchema > phoneSchemaVersion) {
			throw SyncError.DaemonAheadOfPhone(daemonSchema, phoneSchemaVersion)
		}
		if (daemonSchema < phoneSchemaVersion) {
			var current = if (daemonSchema == 0) 1 else daemonSchema
			while (current < phoneSchemaVersion) {
				val sql = migrationLoader.load(current, current + 1)
				client.migrate(MigrateRequest(from_version = current, to_version = current + 1, sql = sql))
				migrationsApplied.add(current to (current + 1))
				current++
			}
		}

		val pushResp = client.push(buildPushPayload())
		val pullResp = client.pull()
		val pulled = mergePull(pullResp)

		SyncResult(
			pushedRowsByTable = pushResp.inserted_by_table,
			pulledRowsByTable = pulled,
			ignoredRowsByTable = pushResp.ignored_by_table,
			migrationsApplied = migrationsApplied,
			durationMs = System.currentTimeMillis() - start,
		)
	}

	private suspend fun buildPushPayload(): PushRequest {
		val rows = mutableMapOf<String, List<SyncRow>>()
		rows[SyncableTable.PATIENTS.tableName] = db.patientDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.LAB_OBSERVATIONS.tableName] = db.labDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.VITALS_OBSERVATIONS.tableName] = db.vitalsDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.CONDITIONS.tableName] = db.conditionDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.MEDICATIONS.tableName] = db.medicationDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.ALLERGIES.tableName] = db.allergyDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.ENCOUNTERS.tableName] = db.encounterDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.DOCUMENTS.tableName] = db.documentDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.DIAGNOSTIC_REPORTS.tableName] = db.diagnosticReportDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.SOURCE_RECORDS.tableName] = db.sourceRecordDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.CHAT_CONVERSATIONS.tableName] = db.chatDao().getAllConversationsSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.CHAT_MESSAGES.tableName] = db.chatDao().getAllMessagesSnapshot().map { serializer.toRow(it) }
		return PushRequest(batch_id = UUID.randomUUID().toString(), rows_by_table = rows)
	}

	private suspend fun mergePull(resp: PullResponse): Map<String, Int> {
		val counts = mutableMapOf<String, Int>()
		resp.rows_by_table.forEach { (tableName, rows) ->
			counts[tableName] = when (tableName) {
				SyncableTable.PATIENTS.tableName -> mergePatients(rows)
				SyncableTable.LAB_OBSERVATIONS.tableName -> mergeLabs(rows)
				SyncableTable.VITALS_OBSERVATIONS.tableName -> mergeVitals(rows)
				SyncableTable.CONDITIONS.tableName -> mergeConditions(rows)
				SyncableTable.MEDICATIONS.tableName -> mergeMedications(rows)
				SyncableTable.ALLERGIES.tableName -> mergeAllergies(rows)
				SyncableTable.ENCOUNTERS.tableName -> mergeEncounters(rows)
				SyncableTable.DOCUMENTS.tableName -> mergeDocuments(rows)
				SyncableTable.DIAGNOSTIC_REPORTS.tableName -> mergeDiagnosticReports(rows)
				SyncableTable.SOURCE_RECORDS.tableName -> mergeSourceRecords(rows)
				SyncableTable.CHAT_CONVERSATIONS.tableName -> mergeChatConversations(rows)
				SyncableTable.CHAT_MESSAGES.tableName -> mergeChatMessages(rows)
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
