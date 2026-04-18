package com.healthaggregator.data.entities

/**
 * Per-source rollup derived live from the data tables, not from medical_data_sources.
 * Populates the home dashboard sources list regardless of how rows arrived (Health Connect,
 * laptop-sync, manual ingest).
 */
data class SourceSummary(
	val sourceSystem: String,
	val displayName: String,
	val recordCount: Int,
	val colorOverride: String? = null,
)
