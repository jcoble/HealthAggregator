package com.healthaggregator.data

/**
 * Canonicalizes both panel-level display strings (Observation.basedOn[0].display) and
 * individual-test names (Observation.code.text / .coding[].display) so variants across
 * organizations collapse to the same canonical form.
 *
 * Why both: panel canonicalization drives consistent UI rows in Records>Labs; test
 * canonicalization enables a future LLM agent (γ stream) to join "my Hemoglobin A1c"
 * results across Cleveland Clinic + Summa Health without tenant-specific glue.
 * LOINC codes remain the authoritative cross-org key when populated; canonicalTestName
 * is the backup when LOINC is absent or inconsistent.
 *
 * Strategy:
 *  1. Clean: lowercase, strip parentheticals, strip punctuation, collapse whitespace.
 *  2. Look up the cleaned key in ALIASES. If found, return the canonical form.
 *  3. Else, return Title Case of the cleaned input.
 *
 * Called from FhirImportService.buildLab during import — canonical names stored on
 * LabObservation.{canonicalPanelName, canonicalTestName} (both indexed) and read by
 * LabDao.observePanels via COALESCE fallback.
 */
object LabNameNormalizer {

	/**
	 * Key = cleaned form of any known alias (lowercase, no punctuation, single-spaced).
	 * Value = the canonical display string we want shown in the UI + used by the LLM agent.
	 * Contains BOTH panel aliases and individual-test aliases.
	 */
	private val ALIASES: Map<String, String> = buildMap {
		// --- Panels ---
		listOf("cmp", "comprehensive metabolic panel").forEach { put(it, "Comprehensive Metabolic Panel") }
		listOf("bmp", "basic metabolic panel").forEach { put(it, "Basic Metabolic Panel") }
		listOf(
			"cbc",
			"cbc differential",
			"cbc with differential",
			"cbc w diff",
			"cbc w differential",
			"cbc w auto differential",
			"cbc with auto differential",
			"complete blood count",
			"complete blood count and differential",
			"complete blood count with differential",
		).forEach { put(it, "Complete Blood Count") }
		listOf(
			"lft",
			"liver function tests",
			"liver function panel",
			"hepatic function panel",
		).forEach { put(it, "Hepatic Function Panel") }
		listOf("lipid panel", "lipid profile").forEach { put(it, "Lipid Panel") }
		listOf("tft", "thyroid function tests", "thyroid panel", "tsh panel").forEach { put(it, "Thyroid Panel") }
		listOf("ua", "urinalysis").forEach { put(it, "Urinalysis") }
		listOf("pt inr", "prothrombin time inr", "pt ptt inr").forEach { put(it, "PT/INR") }
		listOf("iron studies", "iron panel").forEach { put(it, "Iron Studies") }
		listOf("renal panel", "renal function panel").forEach { put(it, "Renal Panel") }

		// --- Individual tests ---
		// Hemoglobin A1c (appears as both panel-level and test-level in different sources)
		listOf("a1c", "hba1c", "hemoglobin a1c", "hgba1c").forEach { put(it, "Hemoglobin A1c") }
		// Glucose
		listOf("glucose", "glucose serum", "serum glucose").forEach { put(it, "Glucose") }
		// Electrolytes
		listOf("sodium", "na", "sodium serum").forEach { put(it, "Sodium") }
		listOf("potassium", "k").forEach { put(it, "Potassium") }
		listOf("chloride", "cl").forEach { put(it, "Chloride") }
		listOf("bicarbonate", "co2", "carbon dioxide", "hco3").forEach { put(it, "Bicarbonate") }
		listOf("calcium", "ca").forEach { put(it, "Calcium") }
		// Renal markers
		listOf("creatinine", "creatinine serum").forEach { put(it, "Creatinine") }
		listOf("bun", "blood urea nitrogen", "urea nitrogen").forEach { put(it, "Blood Urea Nitrogen") }
		listOf("egfr", "estimated gfr", "estimated glomerular filtration rate").forEach { put(it, "eGFR") }
		// Liver markers
		listOf("alt", "alanine aminotransferase", "sgpt").forEach { put(it, "ALT") }
		listOf("ast", "aspartate aminotransferase", "sgot").forEach { put(it, "AST") }
		listOf("alp", "alkaline phosphatase").forEach { put(it, "Alkaline Phosphatase") }
		listOf("bilirubin total", "total bilirubin").forEach { put(it, "Total Bilirubin") }
		listOf("albumin", "albumin serum").forEach { put(it, "Albumin") }
		// Lipids
		listOf("total cholesterol", "cholesterol total", "cholesterol").forEach { put(it, "Total Cholesterol") }
		listOf("hdl", "hdl cholesterol", "hdl c", "high density lipoprotein").forEach { put(it, "HDL Cholesterol") }
		listOf("ldl", "ldl cholesterol", "ldl c", "low density lipoprotein", "ldl calc", "ldl calculated").forEach { put(it, "LDL Cholesterol") }
		listOf("triglycerides", "trig").forEach { put(it, "Triglycerides") }
		// Thyroid
		listOf("tsh", "thyroid stimulating hormone").forEach { put(it, "TSH") }
		listOf("t4 free", "free t4", "free thyroxine").forEach { put(it, "Free T4") }
		listOf("t3 free", "free t3", "free triiodothyronine").forEach { put(it, "Free T3") }
		// CBC components
		listOf("wbc", "white blood cell count", "white blood cells").forEach { put(it, "White Blood Cell Count") }
		listOf("rbc", "red blood cell count", "red blood cells").forEach { put(it, "Red Blood Cell Count") }
		listOf("hemoglobin", "hgb", "hb").forEach { put(it, "Hemoglobin") }
		listOf("hematocrit", "hct").forEach { put(it, "Hematocrit") }
		listOf("platelets", "platelet count", "plt").forEach { put(it, "Platelet Count") }
		// Vitamins
		listOf("vitamin d", "25 hydroxyvitamin d", "25 oh vitamin d").forEach { put(it, "Vitamin D 25-Hydroxy") }
		listOf("vitamin b12", "b12", "cobalamin").forEach { put(it, "Vitamin B12") }
		// Diabetes markers
		listOf("fasting glucose", "glucose fasting").forEach { put(it, "Fasting Glucose") }
	}

	fun normalize(raw: String?): String? {
		if (raw.isNullOrBlank()) return null
		val cleaned = clean(raw)
		if (cleaned.isEmpty()) return null
		ALIASES[cleaned]?.let { return it }
		return titleCase(cleaned)
	}

	private fun clean(raw: String): String {
		// Strip parentheticals and their contents, strip non-alphanumeric except spaces.
		val noParen = raw.replace(Regex("\\([^)]*\\)"), " ")
		val ascii = noParen.replace(Regex("[^A-Za-z0-9 ]"), " ")
		return ascii.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
	}

	private fun titleCase(cleaned: String): String = cleaned
		.split(' ')
		.joinToString(" ") { word ->
			if (word.isEmpty()) word
			else word[0].uppercaseChar() + word.substring(1)
		}
}
