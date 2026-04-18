package com.healthaggregator.di

import android.content.Context
import androidx.room.Room
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

	@Provides
	@Singleton
	fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
		Room.databaseBuilder(ctx, AppDatabase::class.java, "healthaggregator.db").build()

	@Provides fun providePatientDao(db: AppDatabase): PatientDao = db.patientDao()
	@Provides fun provideLabDao(db: AppDatabase): LabDao = db.labDao()
	@Provides fun provideDiagnosticReportDao(db: AppDatabase): DiagnosticReportDao = db.diagnosticReportDao()
	@Provides fun provideConditionDao(db: AppDatabase): ConditionDao = db.conditionDao()
	@Provides fun provideMedicationDao(db: AppDatabase): MedicationDao = db.medicationDao()
	@Provides fun provideAllergyDao(db: AppDatabase): AllergyDao = db.allergyDao()
	@Provides fun provideEncounterDao(db: AppDatabase): EncounterDao = db.encounterDao()
	@Provides fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()
	@Provides fun provideVitalsDao(db: AppDatabase): VitalsDao = db.vitalsDao()
	@Provides fun provideSourceRecordDao(db: AppDatabase): SourceRecordDao = db.sourceRecordDao()
	@Provides fun provideSyncJobDao(db: AppDatabase): SyncJobDao = db.syncJobDao()
	@Provides fun provideMedicalDataSourceDao(db: AppDatabase): MedicalDataSourceDao = db.medicalDataSourceDao()
}
