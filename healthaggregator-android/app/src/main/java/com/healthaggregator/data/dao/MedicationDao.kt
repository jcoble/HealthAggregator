package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.MedicationRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
	@Upsert
	suspend fun upsertAll(items: List<MedicationRecord>)

	@Upsert
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
