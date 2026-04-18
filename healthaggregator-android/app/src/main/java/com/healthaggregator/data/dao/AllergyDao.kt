package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.AllergyRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AllergyDao {
	// INSERT OR REPLACE so that re-importing the same (sourceSystem, fhirReference) updates
	// the existing row. Room's @Upsert only handles PK conflicts, not secondary unique indexes.
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(items: List<AllergyRecord>)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(item: AllergyRecord)

	@Query("SELECT * FROM allergies ORDER BY recordedAt DESC, id DESC")
	fun observeAll(): Flow<List<AllergyRecord>>

	@Query("SELECT * FROM allergies WHERE sourceSystem = :source ORDER BY recordedAt DESC, id DESC")
	fun observeBySource(source: String): Flow<List<AllergyRecord>>

	@Query("SELECT COUNT(*) FROM allergies")
	fun countAll(): Flow<Int>

	@Query("SELECT * FROM allergies")
	suspend fun getAllSnapshot(): List<AllergyRecord>

	@Query("DELETE FROM allergies")
	suspend fun deleteAll()
}
