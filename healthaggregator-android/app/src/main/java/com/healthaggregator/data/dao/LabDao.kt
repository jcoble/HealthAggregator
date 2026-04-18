package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.LabPanelAggregate
import com.healthaggregator.data.entities.LabObservation
import kotlinx.coroutines.flow.Flow

data class BackfillRow(val id: Long, val testName: String, val serviceRequestDisplay: String?)

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

	/**
	 * Cross-organization trend query: returns every lab whose loincCode matches OR whose
	 * canonicalTestName matches. Pass both keys from the "current" lab. Either arg may be
	 * null — the corresponding branch then matches nothing. Rows are de-duplicated by id
	 * since a single row may satisfy both predicates.
	 */
	@Query("""
		SELECT * FROM lab_observations
		WHERE (:loinc IS NOT NULL AND loincCode = :loinc)
		   OR (:canonical IS NOT NULL AND canonicalTestName = :canonical)
		ORDER BY effectiveAt DESC
	""")
	fun observeByLoincOrCanonical(loinc: String?, canonical: String?): Flow<List<LabObservation>>

	@Query("SELECT * FROM lab_observations WHERE serviceRequestReference = :sr ORDER BY effectiveAt DESC")
	fun observeByServiceRequest(sr: String): Flow<List<LabObservation>>

	@Query("""
		SELECT serviceRequestReference AS serviceRequestReference,
		       COALESCE(canonicalPanelName, serviceRequestDisplay, testName) AS displayName,
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

	@Query("""
		SELECT id, testName, serviceRequestDisplay
		FROM lab_observations
		WHERE (canonicalPanelName IS NULL AND serviceRequestDisplay IS NOT NULL)
		   OR canonicalTestName IS NULL
	""")
	suspend fun rowsNeedingCanonicalBackfill(): List<BackfillRow>

	@Query("""
		UPDATE lab_observations
		SET canonicalPanelName = :canonicalPanel, canonicalTestName = :canonicalTest
		WHERE id = :id
	""")
	suspend fun setCanonicals(id: Long, canonicalPanel: String?, canonicalTest: String?)
}
