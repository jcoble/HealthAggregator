package com.healthaggregator.data

import com.healthaggregator.data.LabNameNormalizer.normalize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LabNameNormalizerTest {
	@Test fun null_and_blank_return_null() {
		assertNull(normalize(null))
		assertNull(normalize(""))
		assertNull(normalize("   "))
	}

	@Test fun case_variants_of_cmp_canonicalize_to_same() {
		val expected = "Comprehensive Metabolic Panel"
		assertEquals(expected, normalize("Comprehensive Metabolic Panel"))
		assertEquals(expected, normalize("COMPREHENSIVE METABOLIC PANEL"))
		assertEquals(expected, normalize("comprehensive metabolic panel"))
		assertEquals(expected, normalize("CMP"))
		assertEquals(expected, normalize("  Comprehensive   Metabolic Panel  "))
	}

	@Test fun bmp_variants_canonicalize() {
		val expected = "Basic Metabolic Panel"
		assertEquals(expected, normalize("Basic Metabolic Panel"))
		assertEquals(expected, normalize("BASIC METABOLIC PANEL"))
		assertEquals(expected, normalize("BMP"))
	}

	@Test fun cbc_variants_canonicalize() {
		val expected = "Complete Blood Count"
		assertEquals(expected, normalize("Complete Blood Count"))
		assertEquals(expected, normalize("CBC"))
		assertEquals(expected, normalize("CBC W/ AUTO DIFFERENTIAL"))
		assertEquals(expected, normalize("CBC with Differential"))
		assertEquals(expected, normalize("Complete Blood Count and Differential"))
		assertEquals(expected, normalize("CBC w/ Diff"))
	}

	@Test fun hepatic_function_variants_canonicalize() {
		val expected = "Hepatic Function Panel"
		assertEquals(expected, normalize("Hepatic Function Panel"))
		assertEquals(expected, normalize("HEPATIC FUNCTION PANEL"))
		assertEquals(expected, normalize("LFT"))
		assertEquals(expected, normalize("Liver Function Tests"))
	}

	@Test fun lipid_variants_canonicalize() {
		val expected = "Lipid Panel"
		assertEquals(expected, normalize("Lipid Panel"))
		assertEquals(expected, normalize("LIPID PANEL"))
		assertEquals(expected, normalize("Lipid Profile"))
	}

	@Test fun thyroid_variants_canonicalize() {
		val expected = "Thyroid Panel"
		assertEquals(expected, normalize("Thyroid Panel"))
		assertEquals(expected, normalize("TSH Panel"))
	}

	@Test fun urinalysis_variants_canonicalize() {
		val expected = "Urinalysis"
		assertEquals(expected, normalize("Urinalysis"))
		assertEquals(expected, normalize("URINALYSIS"))
		assertEquals(expected, normalize("UA"))
	}

	@Test fun hemoglobin_a1c_variants_canonicalize() {
		val expected = "Hemoglobin A1c"
		assertEquals(expected, normalize("Hemoglobin A1c"))
		assertEquals(expected, normalize("HbA1c"))
		assertEquals(expected, normalize("HEMOGLOBIN A1C"))
		assertEquals(expected, normalize("A1C"))
	}

	@Test fun unknown_input_returns_title_case_of_cleaned() {
		// Not in the dictionary — just title-cased + cleaned
		assertEquals("Custom Exotic Panel", normalize("custom  exotic panel"))
		assertEquals("Weird Lab Order", normalize("WEIRD LAB ORDER"))
	}

	@Test fun punctuation_and_quotes_are_normalized() {
		// "CMP (fasting)" → should still match CMP since we strip parenthetical
		assertEquals("Comprehensive Metabolic Panel", normalize("CMP (fasting)"))
		assertEquals("Complete Blood Count", normalize("CBC, differential"))
	}

	@Test fun preserves_alphanumeric_specialty_panels() {
		// Single-test "panels" still get title-cased sensibly
		assertEquals("Vitamin D 25 Hydroxy", normalize("VITAMIN D 25 HYDROXY"))
	}

	// --- Individual test names (cross-org LLM-agent requirement) ---

	@Test fun glucose_variants_canonicalize() {
		val expected = "Glucose"
		assertEquals(expected, normalize("Glucose"))
		assertEquals(expected, normalize("GLUCOSE"))
		assertEquals(expected, normalize("Glucose, Serum"))
		assertEquals(expected, normalize("Serum Glucose"))
	}

	@Test fun sodium_variants_canonicalize() {
		val expected = "Sodium"
		assertEquals(expected, normalize("Sodium"))
		assertEquals(expected, normalize("SODIUM"))
		assertEquals(expected, normalize("Na"))
		assertEquals(expected, normalize("Sodium, Serum"))
	}

	@Test fun potassium_variants_canonicalize() {
		val expected = "Potassium"
		assertEquals(expected, normalize("Potassium"))
		assertEquals(expected, normalize("POTASSIUM"))
		assertEquals(expected, normalize("K"))
	}

	@Test fun total_cholesterol_variants_canonicalize() {
		val expected = "Total Cholesterol"
		assertEquals(expected, normalize("Total Cholesterol"))
		assertEquals(expected, normalize("Cholesterol, Total"))
		assertEquals(expected, normalize("TOTAL CHOLESTEROL"))
	}

	@Test fun creatinine_variants_canonicalize() {
		val expected = "Creatinine"
		assertEquals(expected, normalize("Creatinine"))
		assertEquals(expected, normalize("CREATININE"))
		assertEquals(expected, normalize("Creatinine, Serum"))
	}

	@Test fun tsh_variants_canonicalize() {
		val expected = "TSH"
		assertEquals(expected, normalize("TSH"))
		assertEquals(expected, normalize("Thyroid Stimulating Hormone"))
		assertEquals(expected, normalize("THYROID STIMULATING HORMONE"))
	}
}
