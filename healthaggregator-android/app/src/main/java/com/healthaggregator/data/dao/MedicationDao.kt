package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.MedicationRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
	// INSERT OR REPLACE so that re-importing the same (sourceSystem, fhirReference) updates
	// the existing row. Room's @Upsert only handles PK conflicts, not secondary unique indexes.
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(items: List<MedicationRecord>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertAllIgnore(rows: List<MedicationRecord>): List<Long>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(item: MedicationRecord)

	@Query("SELECT * FROM medications ORDER BY authoredAt DESC, id DESC")
	fun observeAll(): Flow<List<MedicationRecord>>

	@Query("SELECT * FROM medications WHERE sourceSystem = :source ORDER BY authoredAt DESC, id DESC")
	fun observeBySource(source: String): Flow<List<MedicationRecord>>

	@Query("SELECT COUNT(*) FROM medications")
	fun countAll(): Flow<Int>

	@Query("SELECT * FROM medications")
	suspend fun getAllSnapshot(): List<MedicationRecord>

	@Query("DELETE FROM medications")
	suspend fun deleteAll()
}
