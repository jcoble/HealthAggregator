package com.healthaggregator.data.repository

import com.healthaggregator.data.AppDatabase
import com.healthaggregator.sync.SyncManager
import com.healthaggregator.sync.SyncState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
	private val manager: SyncManager,
	private val db: AppDatabase,
) {
	val state: StateFlow<SyncState> = manager.state

	suspend fun sync(): SyncState = manager.syncAll()

	/**
	 * Nukes every typed table + source_records + sync_jobs. MedicalDataSource mappings stay
	 * so the user's custom source names/colors persist across a reset.
	 */
	suspend fun clearAllData() {
		db.labDao().deleteAll()
		db.vitalsDao().deleteAll()
		db.medicationDao().deleteAll()
		db.conditionDao().deleteAll()
		db.allergyDao().deleteAll()
		db.encounterDao().deleteAll()
		db.documentDao().deleteAll()
		db.diagnosticReportDao().deleteAll()
		db.patientDao().deleteAll()
		db.sourceRecordDao().deleteAll()
		db.syncJobDao().deleteAll()
	}
}
