package com.healthaggregator.data

import com.healthaggregator.data.entities.LabObservation

private val ABNORMAL_PREFIXES = listOf("HH", "LL", "H", "L", "A")

fun isAbnormal(lab: LabObservation): Boolean {
	val interp = lab.interpretation?.uppercase() ?: ""
	if (interp.isNotBlank() && ABNORMAL_PREFIXES.any { interp.startsWith(it) }) return true
	val value = lab.numericValue ?: return false
	if (lab.referenceLow != null && value < lab.referenceLow) return true
	if (lab.referenceHigh != null && value > lab.referenceHigh) return true
	return false
}
