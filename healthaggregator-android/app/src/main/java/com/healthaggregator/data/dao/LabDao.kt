package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.LabPanelAggregate
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

	@Query("SELECT * FROM lab_observations WHERE loincCode = :loinc ORDER BY effectiveAt DESC")
	fun observeByLoinc(loinc: String): Flow<List<LabObservation>>

	@Query("SELECT * FROM lab_observations WHERE serviceRequestReference = :sr ORDER BY effectiveAt DESC")
	fun observeByServiceRequest(sr: String): Flow<List<LabObservation>>

	@Query("""
		SELECT serviceRequestReference AS serviceRequestReference,
		       COALESCE(serviceRequestDisplay, testName) AS displayName,
		       MIN(effectiveAt) AS effectiveAt,
		       sourceSystem AS sourceSystem,
		       sourceName AS sourceName,
		       COUNT(*) AS componentCount
		FROM lab_observations
		WHERE serviceRequestReference IS NOT NULL
		GROUP BY serviceRequestReference
		ORDER BY MIN(effectiveAt) DESC
	""")
	fun observePanels(): Flow<List<LabPanelAggregate>>
}
