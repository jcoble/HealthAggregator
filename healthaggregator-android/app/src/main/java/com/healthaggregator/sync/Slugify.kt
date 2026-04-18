package com.healthaggregator.sync

/** Generate a stable, lowercase, dashed slug from a display name. Fallback to HC id if name is blank. */
fun slugify(displayName: String, fallback: String = ""): String {
	val base = displayName.lowercase()
		.replace(Regex("[^a-z0-9]+"), "-")
		.trim('-')
	return if (base.isNotBlank()) base else fallback.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
