package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "medications",
	indices = [Index(value = ["sourceSystem", "fhirReference"], unique = true)],
)
data class MedicationRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val fhirReference: String,
	val resourceId: String,
	val patientFhirId: String? = null,
	val medicationText: String? = null,
	val status: String? = null,
	val authoredAt: Instant? = null,
	val importedAt: Instant,
)
