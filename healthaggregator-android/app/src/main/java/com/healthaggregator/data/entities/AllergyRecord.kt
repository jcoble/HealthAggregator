package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "allergies",
	indices = [Index(value = ["sourceSystem", "fhirReference"], unique = true)],
)
data class AllergyRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val fhirReference: String,
	val resourceId: String,
	val patientFhirId: String? = null,
	val allergyText: String? = null,
	val clinicalStatus: String? = null,
	val recordedAt: Instant? = null,
	val importedAt: Instant,
)
