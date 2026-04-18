package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.EncounterRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface EncounterDao {
	@Upsert
	suspend fun upsertAll(items: List<EncounterRecord>)

	@Upsert
	suspend fun upsert(item: EncounterRecord)

	@Query("SELECT * FROM encounters ORDER BY startedAt DESC, id DESC")
	fun observeAll(): Flow<List<EncounterRecord>>

	@Query("SELECT * FROM encounters WHERE sourceSystem = :source ORDER BY startedAt DESC, id DESC")
	fun observeBySource(source: String): Flow<List<EncounterRecord>>

	@Query("SELECT COUNT(*) FROM encounters")
	fun countAll(): Flow<Int>

	@Query("SELECT * FROM encounters")
	suspend fun getAllSnapshot(): List<EncounterRecord>

	@Query("DELETE FROM encounters")
	suspend fun deleteAll()
}
