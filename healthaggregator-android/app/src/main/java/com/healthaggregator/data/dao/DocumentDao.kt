package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.DocumentRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
	@Upsert
	suspend fun upsertAll(items: List<DocumentRecord>)

	@Upsert
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
