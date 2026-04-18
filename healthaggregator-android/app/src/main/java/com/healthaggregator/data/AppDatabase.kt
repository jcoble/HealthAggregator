package com.healthaggregator.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.healthaggregator.data.dao.AllergyDao
import com.healthaggregator.data.dao.ChatDao
import com.healthaggregator.data.dao.ConditionDao
import com.healthaggregator.data.dao.DiagnosticReportDao
import com.healthaggregator.data.dao.DocumentDao
import com.healthaggregator.data.dao.EncounterDao
import com.healthaggregator.data.dao.LabDao
import com.healthaggregator.data.dao.MedicalDataSourceDao
import com.healthaggregator.data.dao.MedicationDao
import com.healthaggregator.data.dao.PatientDao
import com.healthaggregator.data.dao.SourceRecordDao
import com.healthaggregator.data.dao.SyncJobDao
import com.healthaggregator.data.dao.VitalsDao
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
		ChatConversation::class,
		ChatMessage::class,
	],
	version = 4,
	exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
	abstract fun patientDao(): PatientDao
	abstract fun labDao(): LabDao
	abstract fun diagnosticReportDao(): DiagnosticReportDao
	abstract fun conditionDao(): ConditionDao
	abstract fun medicationDao(): MedicationDao
	abstract fun allergyDao(): AllergyDao
	abstract fun encounterDao(): EncounterDao
	abstract fun documentDao(): DocumentDao
	abstract fun vitalsDao(): VitalsDao
	abstract fun sourceRecordDao(): SourceRecordDao
	abstract fun syncJobDao(): SyncJobDao
	abstract fun medicalDataSourceDao(): MedicalDataSourceDao
	abstract fun chatDao(): ChatDao
}
