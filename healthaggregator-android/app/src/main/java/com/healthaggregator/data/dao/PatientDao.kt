package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.PatientRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
	@Upsert
	suspend fun upsertAll(items: List<PatientRecord>)

	@Upsert
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
