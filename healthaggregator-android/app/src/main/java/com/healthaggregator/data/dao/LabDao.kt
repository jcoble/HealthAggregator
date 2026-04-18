package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.LabObservation
import kotlinx.coroutines.flow.Flow

@Dao
interface LabDao {
	@Upsert
	suspend fun upsertAll(labs: List<LabObservation>)

	@Upsert
	suspend fun upsert(lab: LabObservation)

	@Query("SELECT * FROM lab_observations ORDER BY effectiveAt DESC, id DESC")
	fun observeAll(): Flow<List<LabObservation>>

	@Query("SELECT * FROM lab_observations WHERE sourceSystem = :source ORDER BY effectiveAt DESC, id DESC")
	fun observeBySource(source: String): Flow<List<LabObservation>>

	@Query("SELECT COUNT(*) FROM lab_observations")
	fun countAll(): Flow<Int>

	@Query("SELECT * FROM lab_observations")
	suspend fun getAllSnapshot(): List<LabObservation>

	@Query("DELETE FROM lab_observations")
	suspend fun deleteAll()
}
