package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.PatientRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
	// INSERT OR REPLACE so that re-importing the same (sourceSystem, fhirId) updates
	// the existing row. Room's @Upsert only handles PK conflicts, not secondary unique indexes.
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(items: List<PatientRecord>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertAllIgnore(rows: List<PatientRecord>): List<Long>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(item: PatientRecord)

	@Query("SELECT * FROM patients ORDER BY updatedAt DESC, id DESC")
	fun observeAll(): Flow<List<PatientRecord>>

	@Query("SELECT * FROM patients WHERE sourceSystem = :source ORDER BY updatedAt DESC, id DESC")
	fun observeBySource(source: String): Flow<List<PatientRecord>>

	@Query("SELECT COUNT(*) FROM patients")
	fun countAll(): Flow<Int>

	@Query("SELECT * FROM patients")
	suspend fun getAllSnapshot(): List<PatientRecord>

	@Query("DELETE FROM patients")
	suspend fun deleteAll()
}
