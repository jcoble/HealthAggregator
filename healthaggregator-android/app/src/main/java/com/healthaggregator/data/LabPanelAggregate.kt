package com.healthaggregator.data

import java.time.Instant

/**
 * DTO for @Query-returned grouped-panel rows on the Labs filter.
 * Not a @Entity — derived from lab_observations GROUP BY serviceRequestReference.
 */
data class LabPanelAggregate(
	val serviceRequestReference: String,
	val displayName: String,
	val effectiveAt: Instant?,
	val sourceSystem: String,
	val sourceName: String,
	val componentCount: Int,
)
