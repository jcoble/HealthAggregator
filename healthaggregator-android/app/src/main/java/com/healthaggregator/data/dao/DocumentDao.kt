package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.DocumentRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
	// INSERT OR REPLACE so that re-importing the same (sourceSystem, fhirReference) updates
	// the existing row. Room's @Upsert only handles PK conflicts, not secondary unique indexes.
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(items: List<DocumentRecord>)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(item: DocumentRecord)

	@Query("SELECT * FROM documents ORDER BY documentedAt DESC, id DESC")
	fun observeAll(): Flow<List<DocumentRecord>>

	@Query("SELECT * FROM documents WHERE sourceSystem = :source ORDER BY documentedAt DESC, id DESC")
	fun observeBySource(source: String): Flow<List<DocumentRecord>>

	@Query("SELECT COUNT(*) FROM documents")
	fun countAll(): Flow<Int>

	@Query("SELECT * FROM documents")
	suspend fun getAllSnapshot(): List<DocumentRecord>

	@Query("DELETE FROM documents")
	suspend fun deleteAll()
}
