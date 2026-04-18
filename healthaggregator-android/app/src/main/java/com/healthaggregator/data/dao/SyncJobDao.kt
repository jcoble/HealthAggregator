package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.SyncJob
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncJobDao {
	@Upsert
	suspend fun upsert(job: SyncJob)

	@Query("SELECT * FROM sync_jobs ORDER BY startedAt DESC LIMIT 1")
	suspend fun latest(): SyncJob?

	@Query("SELECT * FROM sync_jobs ORDER BY startedAt DESC LIMIT :limit")
	fun observeRecent(limit: Int): Flow<List<SyncJob>>

	@Query("SELECT COUNT(*) FROM sync_jobs")
	fun countAll(): Flow<Int>

	@Query("DELETE FROM sync_jobs")
	suspend fun deleteAll()
}
