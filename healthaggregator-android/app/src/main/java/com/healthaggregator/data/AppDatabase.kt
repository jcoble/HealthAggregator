package com.healthaggregator.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.healthaggregator.data.entities.*

@Database(
	entities = [
		PatientRecord::class,
		LabObservation::class,
		DiagnosticReportRecord::class,
		ConditionRecord::class,
		MedicationRecord::class,
		AllergyRecord::class,
		EncounterRecord::class,
		DocumentRecord::class,
		VitalsObservation::class,
		SourceRecord::class,
		SyncJob::class,
		MedicalDataSource::class,
	],
	version = 1,
	exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
	// DAO accessors added in Task 9
}
