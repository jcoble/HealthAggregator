package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.ConditionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ConditionDao {
	// INSERT OR REPLACE so that re-importing the same (sourceSystem, fhirReference) updates
	// the existing row. Room's @Upsert only handles PK conflicts, not secondary unique indexes.
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(items: List<ConditionRecord>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertAllIgnore(rows: List<ConditionRecord>): List<Long>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(item: ConditionRecord)

	@Query("SELECT * FROM conditions ORDER BY COALESCE(onsetAt, recordedAt) DESC, id DESC")
	fun observeAll(): Flow<List<ConditionRecord>>

	@Query("SELECT * FROM conditions WHERE sourceSystem = :source ORDER BY COALESCE(onsetAt, recordedAt) DESC, id DESC")
	fun observeBySource(source: String): Flow<List<ConditionRecord>>

	@Query("SELECT COUNT(*) FROM conditions")
	fun countAll(): Flow<Int>

	@Query("SELECT * FROM conditions")
	suspend fun getAllSnapshot(): List<ConditionRecord>

	@Query("DELETE FROM conditions")
	suspend fun deleteAll()
}
