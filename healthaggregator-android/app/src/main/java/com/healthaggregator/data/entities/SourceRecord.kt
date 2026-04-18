package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "source_records",
	indices = [Index(value = ["sourceSystem", "resourceType", "resourceId"], unique = true)],
)
data class SourceRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val syncJobId: Long? = null,
	val sourceSystem: String,
	val sourceName: String,
	val resourceType: String,
	val resourceId: String,
	val fhirReference: String,
	val rawJson: String,
	val importedAt: Instant,
)
