# Lab-Name Normalization — Implementation Plan

**Goal:** Map the wildly varying lab display strings from different Epic tenants to a single canonical name — both at panel level (`"COMPREHENSIVE METABOLIC PANEL"` ≡ `"Comprehensive Metabolic Panel"`) AND at individual-test level (`"HbA1c"` ≡ `"HEMOGLOBIN A1C"` ≡ `"Hemoglobin A1c"`). Same canonical name across orgs → same row in the UI, and a future LLM agent (γ stream) can join "my A1c" across Cleveland Clinic + Summa Health without tenant-specific glue.

**Approach:** Import-time — compute a canonical name once during `FhirImportService.buildLab`, write it to new indexed columns. Display layer reads canonical with fallback. LOINC codes (already stored) remain the authoritative cross-org key when populated; `canonicalTestName` is the belt-and-suspenders for tests without LOINC.

**Two canonical columns:**
- `canonicalPanelName: String?` — derived from `serviceRequestDisplay` (the panel-level display). Drives panel rows in Records>Labs.
- `canonicalTestName: String?` — derived from `testName` (the individual-test display). Enables cross-org test identity for the LLM agent stream.

**Strategy:** shared normalizer (`LabNameNormalizer`) — cleanup (casefold + strip parentheticals + strip punctuation + collapse whitespace) → exact-match against a combined alias dictionary (panels AND common individual tests) → fall back to Title Case of the cleaned input.

**Migration:** v2 → v3 adds `canonicalPanelName: String?` AND `canonicalTestName: String?` columns + index on each. Post-migration backfill populates both columns on existing rows on next app launch (idempotent).

**Branch:** `feat/panel-name-normalization` (already cut). Commit per task, then ship-gate.

---

## File change map

### New — production
```
data/LabNameNormalizer.kt                  Pure-Kotlin alias dictionary + normalize()
```

### New — tests
```
data/LabNameNormalizerTest.kt              JUnit 5 — covers panels + individual tests + fallback
```

### Modified
```
data/entities/LabObservation.kt            +2 fields (canonicalPanelName, canonicalTestName)
data/AppDatabase.kt                        version = 3
data/AppDatabaseMigrations.kt              +MIGRATION_2_3 (2 new columns + 2 indices)
di/DatabaseModule.kt                       addMigrations(MIGRATION_1_2, MIGRATION_2_3)
sync/FhirImportService.kt                  buildLab calls LabNameNormalizer.normalize twice
data/dao/LabDao.kt                         observePanels uses COALESCE(canonicalPanelName, …);
                                           +rowsNeedingCanonicalBackfill / setCanonicals
data/repository/RecordsRepository.kt       +suspend fun ensureNamesNormalized()
ui/home/HomeViewModel.kt                   Invoke ensureNamesNormalized once on init
app/schemas/.../AppDatabase/3.json         Room-generated, committed
```

### Modified — tests
```
data/AppDatabaseMigrationTest.kt           +test for MIGRATION_2_3
```

---

## Task 1: LabNameNormalizer + test (TDD)

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/LabNameNormalizer.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/data/LabNameNormalizerTest.kt`

### Step 1: Write the test first

```kotlin
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
```

### Step 2: Run the test — should FAIL (class not defined)

```bash
cd /Users/blackcolours/dev/work/HealthAggregator/healthaggregator-android
./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.data.LabNameNormalizerTest"
```

### Step 3: Write `LabNameNormalizer.kt`

```kotlin
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
		listOf("vitamin d", "vitamin d 25 hydroxy", "25 hydroxyvitamin d", "25 oh vitamin d").forEach { put(it, "Vitamin D 25-Hydroxy") }
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
```

### Step 4: Run tests — all should PASS

```bash
./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.data.LabNameNormalizerTest"
```

Expected: all PASS (~18 tests). Fix mismatches in the implementation, not the tests.

### Step 5: Commit

```bash
git add app/src/main/java/com/healthaggregator/data/LabNameNormalizer.kt \
        app/src/test/java/com/healthaggregator/data/LabNameNormalizerTest.kt
git commit -m "Add LabNameNormalizer — panel + individual-test alias dictionary

Maps both raw serviceRequestDisplay variants (panel level) and raw
testName variants (individual test level) to canonical names, so a
future LLM agent can join 'my Hemoglobin A1c' across organizations
without tenant-specific glue.

Panels covered: CMP, BMP, CBC (several differential forms), hepatic,
lipid, thyroid, urinalysis, A1c, PT/INR, iron, renal.
Individual tests covered: electrolytes (Na/K/Cl/CO2/Ca), renal
(creatinine/BUN/eGFR), liver (ALT/AST/ALP/bili/albumin), lipids
(total/HDL/LDL/trig), thyroid (TSH/fT3/fT4), CBC components
(WBC/RBC/Hgb/Hct/platelets), vitamins (D/B12), glucose.

Strips parentheticals + punctuation + case + whitespace. Unknown
inputs fall back to Title Case of cleaned form. 18+ JUnit 5 tests."
```

---

## Task 2: Schema — LabObservation + migration + DI

**Files:**
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/entities/LabObservation.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/AppDatabase.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/AppDatabaseMigrations.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/di/DatabaseModule.kt`

### Step 1: Add 2 fields + 2 indices to LabObservation

Append `canonicalPanelName: String? = null` AND `canonicalTestName: String? = null` at the end of the fields. Add `Index(value = ["canonicalPanelName"])` AND `Index(value = ["canonicalTestName"])` to the indices array.

Complete entity should look like (field order preserved from α.2 + 2 new fields at end):

```kotlin
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
```

### Step 2: Bump AppDatabase version 2 → 3

In `AppDatabase.kt`, change `version = 2` → `version = 3`. Nothing else.

### Step 3: Append MIGRATION_2_3 to `AppDatabaseMigrations.kt`

Add below the existing `MIGRATION_1_2`:

```kotlin
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE lab_observations ADD COLUMN canonicalPanelName TEXT")
		db.execSQL("ALTER TABLE lab_observations ADD COLUMN canonicalTestName TEXT")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_observations_canonicalPanelName ON lab_observations (canonicalPanelName)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_observations_canonicalTestName ON lab_observations (canonicalTestName)")
	}
}
```

### Step 4: Register migration in `DatabaseModule.kt`

Update the `.addMigrations(MIGRATION_1_2)` call to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`. Add import: `import com.healthaggregator.data.MIGRATION_2_3`.

### Step 5: Verify Room regenerates 3.json

```bash
./gradlew :app:kspDebugKotlin
```

Expected: BUILD SUCCESSFUL. A new file appears at `app/schemas/com.healthaggregator.data.AppDatabase/3.json`.

### Step 6: Commit

```bash
git add app/src/main/java/com/healthaggregator/data/entities/LabObservation.kt \
        app/src/main/java/com/healthaggregator/data/AppDatabase.kt \
        app/src/main/java/com/healthaggregator/data/AppDatabaseMigrations.kt \
        app/src/main/java/com/healthaggregator/di/DatabaseModule.kt \
        app/schemas/com.healthaggregator.data.AppDatabase/3.json
git commit -m "LabObservation gains canonicalPanelName + canonicalTestName + migration 2→3

Additive, nullable, both indexed. MIGRATION_2_3 adds both columns +
both indices. FhirImportService will populate them via
LabNameNormalizer in the next commit; existing rows populated by a
post-migration backfill pass (Task 4).

canonicalTestName is the belt-and-suspenders cross-org key for the
future LLM agent stream — LOINC remains authoritative when populated."
```

---

## Task 3: Migration test for v2→v3

**Files:**
- Modify: `healthaggregator-android/app/src/androidTest/java/com/healthaggregator/data/AppDatabaseMigrationTest.kt`

### Step 1: Append a second `@Test` to the existing class

Add below the existing `migrate_1_to_2_preserves_existing_labs_and_adds_columns` test:

```kotlin
@Test
fun migrate_2_to_3_adds_canonical_columns_and_preserves_rows() {
	// Seed v2: insert a row with serviceRequestDisplay + testName set, no canonicals yet
	helper.createDatabase(dbName, 2).use { db ->
		db.execSQL("""
			INSERT INTO lab_observations(
				sourceSystem, sourceName, fhirReference, resourceId, testName, status, importedAt,
				serviceRequestReference, serviceRequestDisplay
			) VALUES(
				'cleveland-clinic', 'Cleveland Clinic', 'Observation/y', 'y', 'GLUCOSE', 'final', 0,
				'ServiceRequest/bmp-1', 'COMPREHENSIVE METABOLIC PANEL'
			)
		""".trimIndent())
	}

	val db3 = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

	val cursor = db3.query("SELECT testName, serviceRequestDisplay, canonicalPanelName, canonicalTestName FROM lab_observations")
	cursor.use {
		assertEquals(1, it.count)
		assertEquals(true, it.moveToFirst())
		assertEquals("GLUCOSE", it.getString(0))
		assertEquals("COMPREHENSIVE METABOLIC PANEL", it.getString(1))
		assertEquals(true, it.isNull(2)) // not backfilled by the migration itself
		assertEquals(true, it.isNull(3))
	}

	val idxCursor = db3.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='lab_observations'")
	val indexNames = buildList {
		while (idxCursor.moveToNext()) add(idxCursor.getString(0))
	}
	idxCursor.close()
	assertEquals(true, indexNames.contains("index_lab_observations_canonicalPanelName"))
	assertEquals(true, indexNames.contains("index_lab_observations_canonicalTestName"))
}
```

### Step 2: Run (best-effort — needs device)

```bash
./gradlew :app:connectedDebugAndroidTest 2>&1 | tail -20
```

If no device, verify compile:

```bash
./gradlew :app:assembleDebugAndroidTest 2>&1 | tail -10
```

### Step 3: Commit

```bash
git add app/src/androidTest/java/com/healthaggregator/data/AppDatabaseMigrationTest.kt
git commit -m "Migration test for v2→v3 — both canonical columns added + indices + row preserved"
```

---

## Task 4: Wire normalizer into import + DAO + backfill

**Files:**
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/sync/FhirImportService.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/dao/LabDao.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/repository/RecordsRepository.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/MainActivity.kt` (or whichever file hosts `setContent { App() }` — read to find the right place)

### Step 1: Extend `FhirImportService.buildLab` to populate both canonical columns

In `buildLab`, immediately after the existing `serviceRequestDisplay` assignment, add:

```kotlin
val canonicalPanelName = LabNameNormalizer.normalize(serviceRequestDisplay)
val canonicalTestName = LabNameNormalizer.normalize(testName)
```

(`testName` is already a local in `buildLab` — find it from the existing code.)

Add the two new fields to the `return LabObservation(...)` constructor call after `serviceRequestDisplay`:

```kotlin
serviceRequestDisplay = serviceRequestDisplay,
canonicalPanelName = canonicalPanelName,
canonicalTestName = canonicalTestName,
```

Add import at top: `import com.healthaggregator.data.LabNameNormalizer`.

### Step 2: Update `LabDao.observePanels()` query

The current query uses `COALESCE(serviceRequestDisplay, testName) AS displayName`. Change to prefer canonical:

```kotlin
@Query("""
	SELECT serviceRequestReference AS serviceRequestReference,
	       COALESCE(canonicalPanelName, serviceRequestDisplay, testName) AS displayName,
	       MIN(effectiveAt) AS effectiveAt,
	       sourceSystem AS sourceSystem,
	       sourceName AS sourceName,
	       COUNT(*) AS componentCount
	FROM lab_observations
	WHERE serviceRequestReference IS NOT NULL
	GROUP BY serviceRequestReference
	ORDER BY MIN(effectiveAt) DESC
""")
fun observePanels(): Flow<List<LabPanelAggregate>>
```

### Step 3: Add backfill method to `RecordsRepository`

Reads rows where either canonical column is NULL but the source string exists, computes canonicals in Kotlin, UPDATEs in one pass.

First add DAO helpers to `LabDao.kt`:

```kotlin
@Query("""
	SELECT id, testName, serviceRequestDisplay
	FROM lab_observations
	WHERE (canonicalPanelName IS NULL AND serviceRequestDisplay IS NOT NULL)
	   OR canonicalTestName IS NULL
""")
suspend fun rowsNeedingCanonicalBackfill(): List<BackfillRow>

@Query("""
	UPDATE lab_observations
	SET canonicalPanelName = :canonicalPanel, canonicalTestName = :canonicalTest
	WHERE id = :id
""")
suspend fun setCanonicals(id: Long, canonicalPanel: String?, canonicalTest: String?)
```

And create `BackfillRow` top-level in the same package:

```kotlin
data class BackfillRow(val id: Long, val testName: String, val serviceRequestDisplay: String?)
```

Then in `RecordsRepository.kt`, add:

```kotlin
suspend fun ensureNamesNormalized() {
	val needsBackfill = labs.rowsNeedingCanonicalBackfill()
	for (row in needsBackfill) {
		val canonicalPanel = com.healthaggregator.data.LabNameNormalizer.normalize(row.serviceRequestDisplay)
		val canonicalTest = com.healthaggregator.data.LabNameNormalizer.normalize(row.testName)
		labs.setCanonicals(row.id, canonicalPanel, canonicalTest)
	}
}
```

### Step 4: Call `ensureNamesNormalized()` on app startup

Add to `HomeViewModel.kt` inside its existing `init { }` block (or create one if absent) a new `viewModelScope.launch { records.ensureNamesNormalized() }` line. HomeScreen is the start destination, so it runs on first launch. Idempotent — subsequent invocations find zero NULL rows.

If HomeViewModel doesn't already inject `RecordsRepository`, it does — α.2 HomeViewModel uses it for sync status. Read the file to confirm the field name and match it.

### Step 5: Build + full test

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | tail -15
```

All 43 + 18+ new = 61+ tests should pass.

### Step 6: Commit

```bash
git add app/src/main/java/com/healthaggregator/sync/FhirImportService.kt \
        app/src/main/java/com/healthaggregator/data/dao/LabDao.kt \
        app/src/main/java/com/healthaggregator/data/repository/RecordsRepository.kt \
        app/src/main/java/com/healthaggregator/ui/home/HomeViewModel.kt
git commit -m "Wire LabNameNormalizer into import + read path + startup backfill

FhirImportService.buildLab computes canonicalPanelName from
serviceRequestDisplay AND canonicalTestName from testName.
LabDao.observePanels reads COALESCE(canonicalPanelName,
serviceRequestDisplay, testName) so existing α.2 rows fall back
gracefully until the backfill runs.

RecordsRepository.ensureNamesNormalized reads rows with any canonical
NULL and populates both in one UPDATE per row. Called once from
HomeViewModel.init — idempotent, runs at most once per launch."
```

---

## Task 5: Ship gate

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | tail -15
./gradlew :app:installDebug 2>&1 | tail -5
```

Device smoke checklist:
1. App launches, existing records still visible (migration v2→v3 didn't break anything).
2. HomeScreen loads — backfill runs in background.
3. Switch to Records>Labs — panel rows now show canonical names (e.g. "Comprehensive Metabolic Panel" instead of "COMPREHENSIVE METABOLIC PANEL").
4. Cross-source panels with different cases now display with the same canonical name.
5. Inspect the DB (optional: `adb exec-out run-as com.healthaggregator cat databases/healthaggregator.db > /tmp/hc.db && sqlite3 /tmp/hc.db "SELECT DISTINCT canonicalTestName FROM lab_observations WHERE canonicalTestName IS NOT NULL ORDER BY 1 LIMIT 30;"`) — confirm canonicalTestName populated for common tests.

If smoke passes:

```bash
git commit --allow-empty -m "Lab-name normalization complete — ready to merge

LabNameNormalizer dictionary covers common panels (CMP, BMP, CBC,
hepatic, lipid, thyroid, UA, A1c, PT/INR, iron, renal) AND common
individual tests (electrolytes, renal markers, liver enzymes, lipids,
thyroid, CBC components, vitamins). Falls back to title-case for
everything else.

Migration v2→v3 ships + startup backfill catches existing rows.
Fresh syncs populate directly via FhirImportService. Cross-org test
identity is now a first-class property of the data — LLM agent
stream (γ) will build on top."
```

---

## Rules

- Tabs for indentation in all .kt files.
- Do NOT use `--no-verify`.
- If the full suite fails at any step, fix before proceeding — don't batch.
- Match existing codebase style (imports grouped, no unnecessary comments).
- PanelNameNormalizer is intentionally not `open`/`abstract` — it's a pure function.
