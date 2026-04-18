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
