package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.AllergyRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AllergyDao {
	@Upsert
	suspend fun upsertAll(items: List<AllergyRecord>)

	@Upsert
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
