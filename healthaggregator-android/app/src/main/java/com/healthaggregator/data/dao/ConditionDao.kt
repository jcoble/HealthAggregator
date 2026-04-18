package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.ConditionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ConditionDao {
	@Upsert
	suspend fun upsertAll(items: List<ConditionRecord>)

	@Upsert
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
