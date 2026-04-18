package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.SourceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceRecordDao {
	// INSERT OR REPLACE so that re-importing the same (sourceSystem, resourceType, resourceId)
	// updates the existing row. Room's @Upsert only handles PK conflicts, not secondary unique indexes.
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(items: List<SourceRecord>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertAllIgnore(rows: List<SourceRecord>): List<Long>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
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

	@Query("SELECT rawJson FROM source_records WHERE sourceSystem = :source AND fhirReference = :ref LIMIT 1")
	suspend fun findRawJson(source: String, ref: String): String?

	@Query("SELECT * FROM source_records WHERE rawJson LIKE '%' || :query || '%' ORDER BY importedAt DESC LIMIT :limit")
	suspend fun searchFreeText(query: String, limit: Int): List<SourceRecord>
}
