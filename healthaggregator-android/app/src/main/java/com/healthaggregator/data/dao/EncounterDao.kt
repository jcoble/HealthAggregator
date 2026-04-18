package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.EncounterRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface EncounterDao {
	// INSERT OR REPLACE so that re-importing the same (sourceSystem, fhirReference) updates
	// the existing row. Room's @Upsert only handles PK conflicts, not secondary unique indexes.
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(items: List<EncounterRecord>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertAllIgnore(rows: List<EncounterRecord>): List<Long>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
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
