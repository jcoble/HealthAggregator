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
