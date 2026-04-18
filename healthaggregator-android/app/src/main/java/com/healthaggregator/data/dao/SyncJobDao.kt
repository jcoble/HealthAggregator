package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.SyncJob
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncJobDao {
	@Upsert
	suspend fun upsert(job: SyncJob)

	@Insert
	suspend fun insert(job: SyncJob): Long

	@Query("SELECT * FROM sync_jobs ORDER BY startedAt DESC LIMIT 1")
	suspend fun latest(): SyncJob?

	@Query("SELECT * FROM sync_jobs ORDER BY startedAt DESC LIMIT :limit")
	fun observeRecent(limit: Int): Flow<List<SyncJob>>

	@Query("SELECT COUNT(*) FROM sync_jobs")
	fun countAll(): Flow<Int>

	@Query("DELETE FROM sync_jobs")
	suspend fun deleteAll()

	@Query("UPDATE sync_jobs SET completedAt = :completedAt, status = :status, sourceRecordsUpserted = :sourceRecords, labObservationsUpserted = :labs, vitalsUpserted = :vitals, conditionsUpserted = :conditions, medicationsUpserted = :medications, allergiesUpserted = :allergies, encountersUpserted = :encounters, documentsUpserted = :documents, patientsUpserted = :patients, diagnosticReportsUpserted = :reports WHERE id = :id")
	suspend fun markCompleted(
		id: Long, completedAt: Instant, status: String,
		sourceRecords: Int, labs: Int, vitals: Int, conditions: Int, medications: Int,
		allergies: Int, encounters: Int, documents: Int, patients: Int, reports: Int,
	)

	@Query("UPDATE sync_jobs SET completedAt = :completedAt, status = 'failed', error = :error WHERE id = :id")
	suspend fun markFailed(id: Long, completedAt: Instant, error: String)
}
