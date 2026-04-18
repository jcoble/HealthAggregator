package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One row per FHIR Observation component (e.g., BP produces 2 rows: systolic + diastolic).
 * Non-component Observations (e.g., weight, pulse ox) produce a single row with componentCode = "".
 *
 * componentCode is NOT NULL (default "") because SQLite treats NULL values in unique indexes as
 * distinct — a nullable componentCode makes the unique(sourceSystem, fhirReference, componentCode)
 * index useless for the typical single-component case, causing every sync to re-insert duplicates.
 */
@Entity(
	tableName = "vitals_observations",
	indices = [
		Index(value = ["sourceSystem", "fhirReference", "componentCode"], unique = true),
		Index(value = ["loincCode", "effectiveAt"]),
	],
)
data class VitalsObservation(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val fhirReference: String,
	val resourceId: String,
	val patientFhirId: String? = null,
	val loincCode: String? = null,
	val code: String,
	val displayName: String,
	val numericValue: Double? = null,
	val unit: String? = null,
	val componentCode: String = "",
	val effectiveAt: Instant? = null,
	val importedAt: Instant,
)
