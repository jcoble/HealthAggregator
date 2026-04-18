package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "patients",
	indices = [Index(value = ["sourceSystem", "fhirId"], unique = true)],
)
data class PatientRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val fhirId: String,
	val displayName: String? = null,
	val birthDate: String? = null, // ISO date (YYYY-MM-DD)
	val updatedAt: Instant,
)
