package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.MedicalDataSource
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicalDataSourceDao {
	// INSERT OR REPLACE so that re-syncing the same healthConnectSourceId updates the row.
	// Room's @Upsert only handles PK conflicts, not secondary unique indexes.
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(src: MedicalDataSource)

	@Query("SELECT * FROM medical_data_sources WHERE healthConnectSourceId = :hcId LIMIT 1")
	suspend fun findByHealthConnectId(hcId: String): MedicalDataSource?

	@Query("SELECT * FROM medical_data_sources WHERE sourceSystem = :slug LIMIT 1")
	suspend fun findBySourceSystem(slug: String): MedicalDataSource?

	@Query("SELECT * FROM medical_data_sources ORDER BY displayName")
	fun observeAll(): Flow<List<MedicalDataSource>>

	@Query("DELETE FROM medical_data_sources")
	suspend fun deleteAll()
}
