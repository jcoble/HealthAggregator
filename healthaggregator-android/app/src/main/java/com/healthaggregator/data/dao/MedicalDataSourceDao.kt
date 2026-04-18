package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.MedicalDataSource
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicalDataSourceDao {
	@Upsert
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
