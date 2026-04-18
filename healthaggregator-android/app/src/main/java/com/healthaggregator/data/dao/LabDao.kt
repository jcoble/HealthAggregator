package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.LabObservation
import kotlinx.coroutines.flow.Flow

@Dao
interface LabDao {
	// INSERT OR REPLACE so that re-importing the same (sourceSystem, fhirReference) updates
	// the existing row rather than leaving it stale. Room's @Upsert only handles PK conflicts.
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(labs: List<LabObservation>)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
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
