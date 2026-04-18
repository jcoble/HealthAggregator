package com.healthaggregator.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE lab_observations ADD COLUMN serviceRequestReference TEXT")
		db.execSQL("ALTER TABLE lab_observations ADD COLUMN serviceRequestDisplay TEXT")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_observations_serviceRequestReference ON lab_observations (serviceRequestReference)")
	}
}

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE lab_observations ADD COLUMN canonicalPanelName TEXT")
		db.execSQL("ALTER TABLE lab_observations ADD COLUMN canonicalTestName TEXT")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_observations_canonicalPanelName ON lab_observations (canonicalPanelName)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_observations_canonicalTestName ON lab_observations (canonicalTestName)")
	}
}

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS chat_conversations (
				id TEXT NOT NULL PRIMARY KEY,
				title TEXT NOT NULL,
				createdAt INTEGER NOT NULL,
				updatedAt INTEGER NOT NULL,
				snapshotText TEXT,
				snapshotGeneratedAt INTEGER,
				modelId TEXT NOT NULL
			)
			""".trimIndent()
		)
		db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_conversations_updatedAt ON chat_conversations (updatedAt)")
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS chat_messages (
				id TEXT NOT NULL PRIMARY KEY,
				conversationId TEXT NOT NULL,
				role TEXT NOT NULL,
				content TEXT NOT NULL,
				toolCallsJson TEXT,
				toolCallId TEXT,
				modelId TEXT,
				createdAt INTEGER NOT NULL,
				FOREIGN KEY (conversationId) REFERENCES chat_conversations(id) ON DELETE CASCADE
			)
			""".trimIndent()
		)
		db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId_createdAt ON chat_messages (conversationId, createdAt)")
	}
}

/**
 * v4 → v5: fix vitals_observations duplication bug.
 *
 * The unique index (sourceSystem, fhirReference, componentCode) was useless when componentCode
 * was NULL (single-component Observations like weight / pulse ox) because SQLite treats each NULL
 * in a unique index as distinct. Every sync re-inserted duplicate rows, growing the table without
 * bound.
 *
 * Fix: componentCode becomes NOT NULL DEFAULT ''. Existing NULL values are coalesced to '' and
 * duplicates are collapsed (keep the row with the highest id, i.e. the most recent import).
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS vitals_observations_new (
				id INTEGER PRIMARY KEY AUTOINCREMENT,
				sourceSystem TEXT NOT NULL,
				sourceName TEXT NOT NULL,
				fhirReference TEXT NOT NULL,
				resourceId TEXT NOT NULL,
				patientFhirId TEXT,
				loincCode TEXT,
				code TEXT NOT NULL,
				displayName TEXT NOT NULL,
				numericValue REAL,
				unit TEXT,
				componentCode TEXT NOT NULL DEFAULT '',
				effectiveAt INTEGER,
				importedAt INTEGER NOT NULL
			)
			""".trimIndent()
		)
		db.execSQL(
			"""
			INSERT INTO vitals_observations_new (
				id, sourceSystem, sourceName, fhirReference, resourceId, patientFhirId,
				loincCode, code, displayName, numericValue, unit, componentCode,
				effectiveAt, importedAt
			)
			SELECT
				id, sourceSystem, sourceName, fhirReference, resourceId, patientFhirId,
				loincCode, code, displayName, numericValue, unit, COALESCE(componentCode, ''),
				effectiveAt, importedAt
			FROM vitals_observations
			WHERE id IN (
				SELECT MAX(id) FROM vitals_observations
				GROUP BY sourceSystem, fhirReference, COALESCE(componentCode, '')
			)
			""".trimIndent()
		)
		db.execSQL("DROP TABLE vitals_observations")
		db.execSQL("ALTER TABLE vitals_observations_new RENAME TO vitals_observations")
		db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_vitals_observations_sourceSystem_fhirReference_componentCode ON vitals_observations (sourceSystem, fhirReference, componentCode)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_vitals_observations_loincCode_effectiveAt ON vitals_observations (loincCode, effectiveAt)")
	}
}
