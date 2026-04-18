package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.DiagnosticReportRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticReportDao {
	// INSERT OR REPLACE so that re-importing the same (sourceSystem, fhirReference) updates
	// the existing row. Room's @Upsert only handles PK conflicts, not secondary unique indexes.
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(items: List<DiagnosticReportRecord>)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertAllIgnore(rows: List<DiagnosticReportRecord>): List<Long>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
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
