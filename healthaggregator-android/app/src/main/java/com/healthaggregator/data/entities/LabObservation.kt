package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "lab_observations",
	indices = [
		Index(value = ["sourceSystem", "fhirReference"], unique = true),
		Index(value = ["loincCode", "effectiveAt"]),
		Index(value = ["serviceRequestReference"]),
		Index(value = ["canonicalPanelName"]),
		Index(value = ["canonicalTestName"]),
	],
)
data class LabObservation(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val fhirReference: String,
	val resourceId: String,
	val patientFhirId: String? = null,
	val diagnosticReportReference: String? = null,
	val loincCode: String? = null,
	val testName: String,
	val numericValue: Double? = null,
	val textValue: String? = null,
	val unit: String? = null,
	val referenceLow: Double? = null,
	val referenceHigh: Double? = null,
	val referenceText: String? = null,
	val interpretation: String? = null,
	val effectiveAt: Instant? = null,
	val status: String = "",
	val importedAt: Instant,
	val serviceRequestReference: String? = null,
	val serviceRequestDisplay: String? = null,
	val canonicalPanelName: String? = null,
	val canonicalTestName: String? = null,
)
