package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.DiagnosticReportRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticReportDao {
	@Upsert
	suspend fun upsertAll(items: List<DiagnosticReportRecord>)

	@Upsert
	suspend fun upsert(item: DiagnosticReportRecord)

	@Query("SELECT * FROM diagnostic_reports ORDER BY issuedAt DESC, id DESC")
	fun observeAll(): Flow<List<DiagnosticReportRecord>>

	@Query("SELECT * FROM diagnostic_reports WHERE sourceSystem = :source ORDER BY issuedAt DESC, id DESC")
	fun observeBySource(source: String): Flow<List<DiagnosticReportRecord>>

	@Query("SELECT COUNT(*) FROM diagnostic_reports")
	fun countAll(): Flow<Int>

	@Query("SELECT * FROM diagnostic_reports")
	suspend fun getAllSnapshot(): List<DiagnosticReportRecord>

	@Query("DELETE FROM diagnostic_reports")
	suspend fun deleteAll()
}
