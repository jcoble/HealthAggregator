package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.SourceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceRecordDao {
	@Upsert
	suspend fun upsertAll(items: List<SourceRecord>)

	@Upsert
	suspend fun upsert(item: SourceRecord)

	@Query("SELECT * FROM source_records ORDER BY importedAt DESC, id DESC")
	fun observeAll(): Flow<List<SourceRecord>>

	@Query("SELECT * FROM source_records WHERE sourceSystem = :source ORDER BY importedAt DESC, id DESC")
	fun observeBySource(source: String): Flow<List<SourceRecord>>

	@Query("SELECT COUNT(*) FROM source_records")
	fun countAll(): Flow<Int>

	@Query("SELECT * FROM source_records")
	suspend fun getAllSnapshot(): List<SourceRecord>

	@Query("DELETE FROM source_records")
	suspend fun deleteAll()
}
