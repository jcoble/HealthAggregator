package com.healthaggregator.ai

/** Near-abnormal classification used by HealthSnapshotBuilder. */
enum class RangeFlag(val label: String) {
	HIGH("HIGH"),
	HIGH_NORMAL("HIGH-NORMAL"),
	NORMAL(""),
	LOW_NORMAL("LOW-NORMAL"),
	LOW("LOW"),
	NONE(""),
	;
}

/**
 * Classifies [value] against the optional reference range.
 *
 * - value > refHigh                    → HIGH
 * - refHigh * 0.9 < value <= refHigh   → HIGH_NORMAL
 * - refLow <= value < refLow * 1.1     → LOW_NORMAL
 * - value < refLow                     → LOW
 * - inside the range with both bounds  → NORMAL
 * - insufficient data (null value, or either needed ref null) → NONE
 */
fun classifyRange(value: Double?, refLow: Double?, refHigh: Double?): RangeFlag {
	if (value == null) return RangeFlag.NONE
	if (refLow == null || refHigh == null) return RangeFlag.NONE

	if (value > refHigh) return RangeFlag.HIGH
	if (value > refHigh * 0.9) return RangeFlag.HIGH_NORMAL
	if (value < refLow) return RangeFlag.LOW
	if (value < refLow * 1.1) return RangeFlag.LOW_NORMAL
	return RangeFlag.NORMAL
}
