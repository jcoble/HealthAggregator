package com.healthaggregator.sync

import com.healthaggregator.data.APP_DATABASE_VERSION
import com.healthaggregator.data.AppDatabase
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FHIR resource types the user has opted out of entirely — neither pushed nor pulled in the
 * source_records table. Mirrors FhirImportService.SUPPRESSED_RESOURCE_TYPES (same list for now).
 * Without this filter, a pull from the laptop would happily re-insert rows that the user just
 * deleted via migration, because insertAllIgnore only dedups on natural keys, not on intent.
 */
private val SUPPRESSED_SYNC_RESOURCE_TYPES = setOf("Condition")

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
	// Defaults to APP_DATABASE_VERSION. The real Hilt binding in SyncModule also reads that
	// constant; both match so adding a new migration only needs a bump in AppDatabase.kt.
	private val phoneSchemaVersion: Int = APP_DATABASE_VERSION,
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
		rows[SyncableTable.MEDICATIONS.tableName] = db.medicationDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.ALLERGIES.tableName] = db.allergyDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.ENCOUNTERS.tableName] = db.encounterDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.DOCUMENTS.tableName] = db.documentDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.DIAGNOSTIC_REPORTS.tableName] = db.diagnosticReportDao().getAllSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.SOURCE_RECORDS.tableName] = db.sourceRecordDao().getAllSnapshot()
			.filter { it.resourceType !in SUPPRESSED_SYNC_RESOURCE_TYPES }
			.map { serializer.toRow(it) }
		rows[SyncableTable.CHAT_CONVERSATIONS.tableName] = db.chatDao().getAllConversationsSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.CHAT_MESSAGES.tableName] = db.chatDao().getAllMessagesSnapshot().map { serializer.toRow(it) }
		rows[SyncableTable.USER_NARRATIVE.tableName] = db.userNarrativeDao().getAllSnapshot().map { serializer.toRow(it) }
		return PushRequest(batch_id = UUID.randomUUID().toString(), rows_by_table = rows)
	}

	private suspend fun mergePull(resp: PullResponse): Map<String, Int> {
		val counts = mutableMapOf<String, Int>()
		resp.rows_by_table.forEach { (tableName, rows) ->
			counts[tableName] = when (tableName) {
				SyncableTable.PATIENTS.tableName -> mergePatients(rows)
				SyncableTable.LAB_OBSERVATIONS.tableName -> mergeLabs(rows)
				SyncableTable.VITALS_OBSERVATIONS.tableName -> mergeVitals(rows)
				SyncableTable.MEDICATIONS.tableName -> mergeMedications(rows)
				SyncableTable.ALLERGIES.tableName -> mergeAllergies(rows)
				SyncableTable.ENCOUNTERS.tableName -> mergeEncounters(rows)
				SyncableTable.DOCUMENTS.tableName -> mergeDocuments(rows)
				SyncableTable.DIAGNOSTIC_REPORTS.tableName -> mergeDiagnosticReports(rows)
				SyncableTable.SOURCE_RECORDS.tableName -> mergeSourceRecords(rows)
				SyncableTable.CHAT_CONVERSATIONS.tableName -> mergeChatConversations(rows)
				SyncableTable.CHAT_MESSAGES.tableName -> mergeChatMessages(rows)
				SyncableTable.USER_NARRATIVE.tableName -> mergeUserNarrative(rows)
				else -> 0
			}
		}
		return counts
	}

	private suspend fun mergePatients(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromPatientRow(it).copy(id = 0L) }
		return db.patientDao().insertAllIgnore(entities).count { it != -1L }
	}

	private suspend fun mergeLabs(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromLabRow(it).copy(id = 0L) }
		return db.labDao().insertAllIgnore(entities).count { it != -1L }
	}

	private suspend fun mergeVitals(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromVitalsRow(it).copy(id = 0L) }
		return db.vitalsDao().insertAllIgnore(entities).count { it != -1L }
	}

	private suspend fun mergeMedications(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromMedicationRow(it).copy(id = 0L) }
		return db.medicationDao().insertAllIgnore(entities).count { it != -1L }
	}

	private suspend fun mergeAllergies(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromAllergyRow(it).copy(id = 0L) }
		return db.allergyDao().insertAllIgnore(entities).count { it != -1L }
	}

	private suspend fun mergeEncounters(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromEncounterRow(it).copy(id = 0L) }
		return db.encounterDao().insertAllIgnore(entities).count { it != -1L }
	}

	private suspend fun mergeDocuments(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromDocumentRow(it).copy(id = 0L) }
		return db.documentDao().insertAllIgnore(entities).count { it != -1L }
	}

	private suspend fun mergeDiagnosticReports(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromDiagnosticReportRow(it).copy(id = 0L) }
		return db.diagnosticReportDao().insertAllIgnore(entities).count { it != -1L }
	}

	private suspend fun mergeSourceRecords(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromSourceRecordRow(it).copy(id = 0L) }
			.filter { it.resourceType !in SUPPRESSED_SYNC_RESOURCE_TYPES }
		return db.sourceRecordDao().insertAllIgnore(entities).count { it != -1L }
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
		return db.chatDao().insertMessagesIgnore(entities).count { it != -1L }
	}

	private suspend fun mergeUserNarrative(rows: List<JsonObject>): Int {
		if (rows.isEmpty()) return 0
		val incoming = serializer.fromUserNarrativeRow(rows.first())
		val existing = db.userNarrativeDao().getSnapshot()
		return if (existing == null || incoming.updatedAt.isAfter(existing.updatedAt)) {
			db.userNarrativeDao().upsert(incoming)
			1
		} else {
			0
		}
	}
}
