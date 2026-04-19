package com.healthaggregator.ai.pdf

import com.healthaggregator.data.entities.LabObservation

/**
 * Groups abnormal labs into clinically meaningful sections so a doctor can scan one system
 * at a time. Matches primarily by canonicalTestName (set by LabNameNormalizer during import),
 * falls back to LOINC for a handful of common codes, and finally to substring match on testName.
 *
 * Extend as new labs appear — any unmapped lab lands in [System.OTHER] which still renders
 * in the report under its own heading.
 */
object BodySystemClassifier {
	enum class System(val displayName: String) {
		CARDIAC("Cardiac"),
		ENDOCRINE("Endocrine"),
		METABOLIC("Glucose / Metabolic"),
		RENAL("Renal"),
		LIVER("Liver"),
		HEMATOLOGIC("Hematologic"),
		INFLAMMATORY("Inflammatory"),
		OTHER("Other"),
	}

	private val CANONICAL_TO_SYSTEM: Map<String, System> = mapOf(
		// Cardiac / Lipids
		"Total Cholesterol" to System.CARDIAC,
		"HDL Cholesterol" to System.CARDIAC,
		"LDL Cholesterol" to System.CARDIAC,
		"Triglycerides" to System.CARDIAC,
		"Apolipoprotein B" to System.CARDIAC,
		"Lipoprotein(a)" to System.CARDIAC,
		// Endocrine
		"Hemoglobin A1c" to System.ENDOCRINE,
		"TSH" to System.ENDOCRINE,
		"Free T4" to System.ENDOCRINE,
		"Free T3" to System.ENDOCRINE,
		"Vitamin D 25-Hydroxy" to System.ENDOCRINE,
		"Testosterone" to System.ENDOCRINE,
		"Estradiol" to System.ENDOCRINE,
		"DHEA-S" to System.ENDOCRINE,
		"Cortisol" to System.ENDOCRINE,
		// Glucose / Metabolic
		"Glucose" to System.METABOLIC,
		"Fasting Glucose" to System.METABOLIC,
		"Insulin" to System.METABOLIC,
		"HOMA-IR" to System.METABOLIC,
		// Renal
		"Blood Urea Nitrogen" to System.RENAL,
		"Creatinine" to System.RENAL,
		"eGFR" to System.RENAL,
		"Cystatin C" to System.RENAL,
		// Liver
		"ALT" to System.LIVER,
		"AST" to System.LIVER,
		"GGT" to System.LIVER,
		"Alkaline Phosphatase" to System.LIVER,
		"Total Bilirubin" to System.LIVER,
		"Albumin" to System.LIVER,
		// Hematologic
		"White Blood Cell Count" to System.HEMATOLOGIC,
		"Red Blood Cell Count" to System.HEMATOLOGIC,
		"Hemoglobin" to System.HEMATOLOGIC,
		"Hematocrit" to System.HEMATOLOGIC,
		"Platelet Count" to System.HEMATOLOGIC,
		"Ferritin" to System.HEMATOLOGIC,
		"Iron" to System.HEMATOLOGIC,
		"TIBC" to System.HEMATOLOGIC,
		"Vitamin B12" to System.HEMATOLOGIC,
		// Inflammatory
		"hsCRP" to System.INFLAMMATORY,
		"CRP" to System.INFLAMMATORY,
		"ESR" to System.INFLAMMATORY,
	)

	private val LOINC_TO_SYSTEM: Map<String, System> = mapOf(
		"2085-9" to System.CARDIAC,   // HDL
		"2089-1" to System.CARDIAC,   // LDL calculated
		"13457-7" to System.CARDIAC,  // LDL direct
		"2571-8" to System.CARDIAC,   // Triglycerides
		"2093-3" to System.CARDIAC,   // Total cholesterol
		"4548-4" to System.ENDOCRINE, // A1c
		"3016-3" to System.ENDOCRINE, // TSH
		"3024-7" to System.ENDOCRINE, // Free T4
		"3051-0" to System.ENDOCRINE, // Free T3
		"1989-3" to System.ENDOCRINE, // Vit D 25-OH
		"3094-0" to System.RENAL,     // BUN
		"2160-0" to System.RENAL,     // Creatinine
		"33914-3" to System.RENAL,    // eGFR (MDRD)
		"48642-3" to System.RENAL,    // eGFR (non-African)
		"48643-1" to System.RENAL,    // eGFR (African)
		"1742-6" to System.LIVER,     // ALT
		"1920-8" to System.LIVER,     // AST
		"6768-6" to System.LIVER,     // Alk Phos
		"1751-7" to System.LIVER,     // Albumin
		"2324-2" to System.LIVER,     // GGT
		"1975-2" to System.LIVER,     // Total bilirubin
		"6690-2" to System.HEMATOLOGIC, // WBC
		"789-8" to System.HEMATOLOGIC,  // RBC
		"718-7" to System.HEMATOLOGIC,  // Hemoglobin
		"4544-3" to System.HEMATOLOGIC, // Hematocrit
		"777-3" to System.HEMATOLOGIC,  // Platelets
		"2276-4" to System.HEMATOLOGIC, // Ferritin
		"30341-2" to System.INFLAMMATORY, // ESR
		"1988-5" to System.INFLAMMATORY,  // hsCRP
		"14647-2" to System.METABOLIC,    // Fasting glucose
	)

	fun classify(lab: LabObservation): System {
		lab.canonicalTestName?.let { CANONICAL_TO_SYSTEM[it]?.let { s -> return s } }
		lab.loincCode?.let { LOINC_TO_SYSTEM[it]?.let { s -> return s } }
		val lower = lab.testName.lowercase()
		return when {
			"cholesterol" in lower || "triglycer" in lower || "lipid" in lower -> System.CARDIAC
			"thyroid" in lower || "tsh" in lower || "a1c" in lower || "vitamin d" in lower -> System.ENDOCRINE
			"glucose" in lower || "insulin" in lower -> System.METABOLIC
			"creatinine" in lower || "urea" in lower || "egfr" in lower -> System.RENAL
			"bilirubin" in lower || "albumin" in lower || "alt" in lower || "ast" in lower -> System.LIVER
			"hemoglobin" in lower || "hematocrit" in lower || "platelet" in lower || "wbc" in lower -> System.HEMATOLOGIC
			"crp" in lower || "sedimentation" in lower -> System.INFLAMMATORY
			else -> System.OTHER
		}
	}
}
