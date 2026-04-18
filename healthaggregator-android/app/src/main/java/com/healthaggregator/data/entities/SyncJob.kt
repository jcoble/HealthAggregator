package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sync_jobs")
data class SyncJob(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val status: String = "running",
	val startedAt: Instant,
	val completedAt: Instant? = null,
	val sourceRecordsUpserted: Int = 0,
	val labObservationsUpserted: Int = 0,
	val vitalsUpserted: Int = 0,
	val conditionsUpserted: Int = 0,
	val medicationsUpserted: Int = 0,
	val allergiesUpserted: Int = 0,
	val encountersUpserted: Int = 0,
	val documentsUpserted: Int = 0,
	val patientsUpserted: Int = 0,
	val diagnosticReportsUpserted: Int = 0,
	val error: String? = null,
)
