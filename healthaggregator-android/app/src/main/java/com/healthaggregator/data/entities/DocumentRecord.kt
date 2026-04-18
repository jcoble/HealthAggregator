package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "documents",
	indices = [Index(value = ["sourceSystem", "fhirReference"], unique = true)],
)
data class DocumentRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val fhirReference: String,
	val resourceId: String,
	val patientFhirId: String? = null,
	val typeText: String? = null,
	val status: String? = null,
	val documentedAt: Instant? = null,
	val contentUrl: String? = null,
	val importedAt: Instant,
)
