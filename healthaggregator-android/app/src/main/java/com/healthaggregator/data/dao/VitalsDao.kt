package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.VitalsObservation
import kotlinx.coroutines.flow.Flow

@Dao
interface VitalsDao {
	// INSERT OR REPLACE so that re-importing the same (sourceSystem, fhirReference, componentCode)
	// updates the existing row. Room's @Upsert only handles PK conflicts, not secondary unique indexes.
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(vitals: List<VitalsObservation>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertAllIgnore(rows: List<VitalsObservation>): List<Long>

	@Query("SELECT * FROM vitals_observations ORDER BY effectiveAt DESC, id DESC")
	fun observeAll(): Flow<List<VitalsObservation>>

	@Query("SELECT * FROM vitals_observations WHERE sourceSystem = :source ORDER BY effectiveAt DESC, id DESC")
	fun observeBySource(source: String): Flow<List<VitalsObservation>>

	@Query("SELECT COUNT(*) FROM vitals_observations")
	fun countAll(): Flow<Int>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(vital: VitalsObservation)

	@Query("SELECT * FROM vitals_observations")
	suspend fun getAllSnapshot(): List<VitalsObservation>

	@Query("DELETE FROM vitals_observations")
	suspend fun deleteAll()
}
