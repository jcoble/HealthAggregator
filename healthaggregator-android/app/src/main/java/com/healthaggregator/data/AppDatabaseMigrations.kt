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
