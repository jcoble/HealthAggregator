package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Maps a Health Connect data source (opaque id assigned by HC/CommonHealth) to a
 * stable slug that the app uses for source attribution colors, tagging, and future UX.
 */
@Entity(
	tableName = "medical_data_sources",
	indices = [Index(value = ["healthConnectSourceId"], unique = true)],
)
data class MedicalDataSource(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val healthConnectSourceId: String,
	val sourceSystem: String, // stable slug: cleveland-clinic, summa-health, etc.
	val displayName: String,
	val colorOverride: String? = null, // optional override for the auto-resolved color
	val firstSeenAt: Instant,
	val lastSeenAt: Instant,
	val recordCount: Int = 0,
)
