package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.VitalsObservation
import kotlinx.coroutines.flow.Flow

@Dao
interface VitalsDao {
	@Upsert
	suspend fun upsertAll(vitals: List<VitalsObservation>)

	@Query("SELECT * FROM vitals_observations ORDER BY effectiveAt DESC, id DESC")
	fun observeAll(): Flow<List<VitalsObservation>>

	@Query("SELECT * FROM vitals_observations WHERE sourceSystem = :source ORDER BY effectiveAt DESC, id DESC")
	fun observeBySource(source: String): Flow<List<VitalsObservation>>

	@Query("SELECT COUNT(*) FROM vitals_observations")
	fun countAll(): Flow<Int>

	@Query("DELETE FROM vitals_observations")
	suspend fun deleteAll()
}
