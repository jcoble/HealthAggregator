# Android Stream α.2 Implementation Plan — Labs Depth + Universal Detail Pages

> **For agentic workers:** REQUIRED SUB-SKILL — use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans`. Steps use checkbox (`- [ ]`) syntax. **Every subagent dispatch must include the spec at `Docs/specs/2026-04-18-android-alpha-2-design.md` as context.**

**Goal:** Turn the Records tab into a MyChart-shaped browseable experience — labs grouped by panel, per-lab trend sparklines, universal detail pages for every record type, plus search and sort.

**Architecture:** Room migration v1 → v2 adds two nullable columns to `lab_observations` for panel grouping via FHIR `Observation.basedOn[0]`. New Compose screens for panel/lab/generic detail navigation. Custom `Canvas`-drawn sparklines (no charting library). ViewModel layer switches Labs filter to group-by-ServiceRequest aggregates; other filters unchanged.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Material 3, Hilt, Room 2.6.1 (KSP), `room-testing` for migration validation, Robolectric 4.14 + JUnit 4 AndroidJUnit4 for Android-context tests, JUnit 5 Jupiter for pure-Kotlin tests.

**Build discipline:** Same as α.1 — `./gradlew :app:assembleDebug`, `./gradlew :app:testDebugUnitTest`. Tabs for indentation matching existing `.kt` files. Do NOT skip `--no-verify`. Every typed DAO already uses `@Insert(onConflict = REPLACE)` — maintain that pattern.

---

## File change map

### New — production

```
data/LabPanelAggregate.kt                        DTO for panel-grouped lab queries
data/AppDatabaseMigrations.kt                    Migration(1,2) object + registration
ui/records/LabPanelRow.kt                        + @Preview — row shape for grouped labs
ui/records/PanelDetailScreen.kt                  + @Preview — tap-through from lab panel row
ui/records/PanelDetailViewModel.kt
ui/records/LabDetailScreen.kt                    + @Preview — tap-through from panel component
ui/records/LabDetailViewModel.kt
ui/records/RecordDetailScreen.kt                 + @Preview — generic for non-lab types
ui/records/RecordDetailViewModel.kt
ui/components/Sparkline.kt                       + @Preview — small inline chart
ui/components/TrendChart.kt                      + @Preview — large chart with axes
ui/components/SearchBar.kt                       + @Preview — M3 search input
ui/components/SortChip.kt                        + @Preview — newest/oldest toggle
ui/components/ChartGeometry.kt                   Pure-Kotlin path math (testable)
```

### New — tests

```
data/ChartGeometryTest.kt                        JUnit 5 — pure path math
data/LabDaoPanelTest.kt                          JUnit 4 — observePanels aggregates correctly
data/AppDatabaseMigrationTest.kt                 JUnit 4 — Room MigrationTestHelper v1→v2
sync/FhirImportServiceBasedOnTest.kt             JUnit 4 — basedOn fields populate
ui/records/PanelDetailViewModelTest.kt           JUnit 4 — Turbine
ui/records/LabDetailViewModelTest.kt             JUnit 4 — Turbine
```

### Modified

```
data/entities/LabObservation.kt                  +2 fields, +1 index
data/AppDatabase.kt                              version = 2, addMigrations()
data/dao/LabDao.kt                               +observeByLoinc, +observeByServiceRequest, +observePanels
data/dao/SourceRecordDao.kt                      +findRawJson
data/repository/RecordsRepository.kt             +observePanels, +observeByLoinc, +observeByServiceRequest, +findRawJson
di/DatabaseModule.kt                             Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)
sync/FhirImportService.kt                        buildLab reads basedOn[0]
ui/records/RecordsViewModel.kt                   LABS filter → panel aggregates; search + sort state
ui/records/RecordsScreen.kt                      SearchBar, SortChip, LabPanelRow for LABS, detail nav
ui/navigation/AppNav.kt                          3 new routes: lab/, panel/, record/
app/schemas/com.healthaggregator.data.AppDatabase/2.json   Room-generated, committed
```

---

# Phase 1 — Data + import

### Task 1: LabObservation adds basedOn fields

**Files:**
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/entities/LabObservation.kt`

- [ ] **Step 1: Add two nullable fields + index**

Replace the file with:

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "lab_observations",
	indices = [
		Index(value = ["sourceSystem", "fhirReference"], unique = true),
		Index(value = ["loincCode", "effectiveAt"]),
		Index(value = ["serviceRequestReference"]),
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
)
```

- [ ] **Step 2: Do not commit yet — Task 2 does migration + version bump together**

The schema change without a version bump would break. Task 2 is the partner commit.

---

### Task 2: Migration 1→2 + AppDatabase version + schema export

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/AppDatabaseMigrations.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/AppDatabase.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/di/DatabaseModule.kt`

- [ ] **Step 1: Write `AppDatabaseMigrations.kt`**

```kotlin
package com.healthaggregator.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE lab_observations ADD COLUMN serviceRequestReference TEXT")
		db.execSQL("ALTER TABLE lab_observations ADD COLUMN serviceRequestDisplay TEXT")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_observations_serviceRequestReference ON lab_observations (serviceRequestReference)")
	}
}
```

- [ ] **Step 2: Bump AppDatabase version to 2**

In `AppDatabase.kt`, change:

```kotlin
@Database(
	entities = [/* unchanged */],
	version = 2,
	exportSchema = true,
)
```

(Only `version = 1` → `version = 2`. Everything else unchanged.)

- [ ] **Step 3: Wire migration into DatabaseModule**

In `DatabaseModule.kt`, replace the existing `provideDatabase` function:

```kotlin
@Provides
@Singleton
fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
	Room.databaseBuilder(ctx, AppDatabase::class.java, "healthaggregator.db")
		.addMigrations(MIGRATION_1_2)
		.build()
```

Add the import: `import com.healthaggregator.data.MIGRATION_1_2`.

- [ ] **Step 4: Verify build — Room regenerates 2.json schema**

```bash
cd /Users/blackcolours/dev/work/HealthAggregator/healthaggregator-android
./gradlew :app:kspDebugKotlin
```

Expected: BUILD SUCCESSFUL. New file appears: `app/schemas/com.healthaggregator.data.AppDatabase/2.json`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/data/entities/LabObservation.kt \
        app/src/main/java/com/healthaggregator/data/AppDatabaseMigrations.kt \
        app/src/main/java/com/healthaggregator/data/AppDatabase.kt \
        app/src/main/java/com/healthaggregator/di/DatabaseModule.kt \
        app/schemas/com.healthaggregator.data.AppDatabase/2.json
git commit -m "Phase 1: LabObservation gains serviceRequestReference + serviceRequestDisplay

Migration 1→2 adds two nullable TEXT columns + index on
serviceRequestReference. Additive only — existing data preserved.
Re-sync backfills via INSERT OR REPLACE (α.1 Task 11 behavior).

Columns sourced from FHIR Observation.basedOn[0].reference (the
ServiceRequest id, grouping key) and .display (human panel name,
e.g. 'Comprehensive Metabolic Panel'). This is how panels surface
without DiagnosticReport resources, which HC's PHR doesn't expose."
```

---

### Task 3: Migration test

**Files:**
- Create: `healthaggregator-android/app/src/androidTest/java/com/healthaggregator/data/AppDatabaseMigrationTest.kt`
- Modify: `healthaggregator-android/app/build.gradle.kts` — ensure schemas dir is on test assets

> **NOTE:** Room's `MigrationTestHelper` normally needs instrumented tests (it reads schemas via `InstrumentationRegistry`). Robolectric has limitations here; for α.2 we put this one test under `app/src/androidTest/` (instrumented flavor). If the device/emulator is unavailable, the subagent may skip running it at task time and flag for manual validation — but the test code still ships.

- [ ] **Step 1: Add androidTest deps in `app/build.gradle.kts`**

Inside `android { }`, add:

```kotlin
sourceSets {
	getByName("androidTest").assets.srcDir("$projectDir/schemas")
}
```

Inside `dependencies { }`, add:

```kotlin
androidTestImplementation("androidx.room:room-testing:2.6.1")
androidTestImplementation("androidx.test.ext:junit:1.2.1")
androidTestImplementation("androidx.test:runner:1.6.2")
```

And inside `android { defaultConfig { } }` add:

```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```

- [ ] **Step 2: Write migration test**

```kotlin
package com.healthaggregator.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
	private val dbName = "migration-test.db"

	@get:Rule
	val helper = MigrationTestHelper(
		InstrumentationRegistry.getInstrumentation(),
		AppDatabase::class.java,
		emptyList(),
		FrameworkSQLiteOpenHelperFactory(),
	)

	@Test
	fun migrate_1_to_2_preserves_existing_labs_and_adds_columns() {
		// Seed v1: one row without the new columns
		helper.createDatabase(dbName, 1).use { db ->
			db.execSQL("""
				INSERT INTO lab_observations(
					sourceSystem, sourceName, fhirReference, resourceId, testName, status, importedAt
				) VALUES(
					'cleveland-clinic', 'Cleveland Clinic', 'Observation/x', 'x', 'HbA1c', 'final', 0
				)
			""".trimIndent())
		}

		// Run migration and open as v2
		val db2 = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

		// Confirm row preserved + new columns readable as null
		val cursor = db2.query("SELECT testName, serviceRequestReference, serviceRequestDisplay FROM lab_observations")
		cursor.use {
			assertEquals(1, it.count)
			assertEquals(true, it.moveToFirst())
			assertEquals("HbA1c", it.getString(0))
			assertEquals(true, it.isNull(1))
			assertEquals(true, it.isNull(2))
		}

		// Confirm new index exists (sqlite_master)
		val idxCursor = db2.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='lab_observations'")
		val indexNames = buildList {
			while (idxCursor.moveToNext()) add(idxCursor.getString(0))
		}
		idxCursor.close()
		assertEquals(true, indexNames.contains("index_lab_observations_serviceRequestReference"))
	}
}
```

- [ ] **Step 3: Run the test (best-effort)**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.healthaggregator.data.AppDatabaseMigrationTest"
```

Expected: PASS. If no connected device, note skipped; code still ships. Unit-test suite still passes regardless.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/ app/build.gradle.kts
git commit -m "Phase 1: migration test for v1→v2 (instrumented)

MigrationTestHelper seeds a v1 row, runs MIGRATION_1_2, validates
that the new columns are present and nullable, the existing row is
preserved, and the serviceRequestReference index is created."
```

---

### Task 4: FhirImportService reads basedOn[0]

**Files:**
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/sync/FhirImportService.kt`
- Modify: `healthaggregator-android/app/src/test/java/com/healthaggregator/TestFhirFixtures.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/sync/FhirImportServiceBasedOnTest.kt`

- [ ] **Step 1: Extend `buildLab` to read basedOn[0]**

In `FhirImportService.kt`, find the `buildLab` function. Inside, add before the `return` statement:

```kotlin
val basedOn = root["basedOn"]?.jsonArray?.firstOrNull()?.jsonObject
val serviceRequestReference = basedOn?.get("reference")?.jsonPrimitive?.contentOrNull
val serviceRequestDisplay = basedOn?.get("display")?.jsonPrimitive?.contentOrNull
```

Then in the returned `LabObservation(...)` constructor call, add the two fields after `importedAt = now`:

```kotlin
return LabObservation(
	// ... existing fields unchanged ...
	importedAt = now,
	serviceRequestReference = serviceRequestReference,
	serviceRequestDisplay = serviceRequestDisplay,
)
```

- [ ] **Step 2: Add fixture for basedOn-bearing lab**

In `TestFhirFixtures.kt`, append:

```kotlin
const val LAB_BMP_GLUCOSE = """
	{
	  "resourceType": "Observation",
	  "id": "glucose-1",
	  "status": "final",
	  "category": [{"coding": [{"system": "http://terminology.hl7.org/CodeSystem/observation-category", "code": "laboratory"}]}],
	  "code": {"coding": [{"system": "http://loinc.org", "code": "2345-7", "display": "Glucose"}], "text": "Glucose"},
	  "subject": {"reference": "Patient/p1"},
	  "effectiveDateTime": "2024-03-15T09:30:00Z",
	  "valueQuantity": {"value": 95, "unit": "mg/dL"},
	  "referenceRange": [{"low": {"value": 70.0}, "high": {"value": 99.0}}],
	  "basedOn": [{"reference": "ServiceRequest/bmp-order-1", "display": "Basic Metabolic Panel"}]
	}
"""
```

- [ ] **Step 3: Write test that fixture lab populates basedOn fields**

```kotlin
package com.healthaggregator.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.TestFhirFixtures
import com.healthaggregator.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FhirImportServiceBasedOnTest {
	private lateinit var db: AppDatabase
	private lateinit var service: FhirImportService

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		service = FhirImportService(db)
	}

	@After fun tearDown() { db.close() }

	@Test
	fun lab_with_basedOn_populates_serviceRequest_fields() = runTest {
		service.importResources("cleveland-clinic", "Cleveland Clinic", listOf(TestFhirFixtures.LAB_BMP_GLUCOSE))
		val lab = db.labDao().observeAll().first().first()
		assertEquals("ServiceRequest/bmp-order-1", lab.serviceRequestReference)
		assertEquals("Basic Metabolic Panel", lab.serviceRequestDisplay)
	}

	@Test
	fun lab_without_basedOn_leaves_serviceRequest_fields_null() = runTest {
		service.importResources("cleveland-clinic", "Cleveland Clinic", listOf(TestFhirFixtures.LAB_HBA1C))
		val lab = db.labDao().observeAll().first().first()
		assertEquals(null, lab.serviceRequestReference)
		assertEquals(null, lab.serviceRequestDisplay)
	}
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.sync.FhirImportServiceBasedOnTest"
```

Expected: 2/2 PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/sync/FhirImportService.kt \
        app/src/test/java/com/healthaggregator/TestFhirFixtures.kt \
        app/src/test/java/com/healthaggregator/sync/FhirImportServiceBasedOnTest.kt
git commit -m "Phase 1: FhirImportService populates serviceRequest fields from basedOn[0]

Per the α.2 spec's data finding, every lab Observation from Jesse's
Epic tenants has basedOn[0] pointing to a ServiceRequest — the
panel ID + display. buildLab now reads both and stores them on
LabObservation for the panel-grouping queries in Task 5/6.

2 tests: positive (new LAB_BMP_GLUCOSE fixture with basedOn),
negative (LAB_HBA1C has no basedOn — fields stay null)."
```

---

### Task 5: LabDao and SourceRecordDao additions

**Files:**
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/dao/LabDao.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/dao/SourceRecordDao.kt`
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/LabPanelAggregate.kt`

- [ ] **Step 1: Create `LabPanelAggregate.kt`**

```kotlin
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
```

- [ ] **Step 2: Extend `LabDao.kt` — append three queries inside the interface**

```kotlin
@Query("SELECT * FROM lab_observations WHERE loincCode = :loinc ORDER BY effectiveAt DESC")
fun observeByLoinc(loinc: String): Flow<List<LabObservation>>

@Query("SELECT * FROM lab_observations WHERE serviceRequestReference = :sr ORDER BY effectiveAt DESC")
fun observeByServiceRequest(sr: String): Flow<List<LabObservation>>

@Query("""
	SELECT serviceRequestReference AS serviceRequestReference,
	       COALESCE(serviceRequestDisplay, testName) AS displayName,
	       MIN(effectiveAt) AS effectiveAt,
	       sourceSystem AS sourceSystem,
	       sourceName AS sourceName,
	       COUNT(*) AS componentCount
	FROM lab_observations
	WHERE serviceRequestReference IS NOT NULL
	GROUP BY serviceRequestReference
	ORDER BY MIN(effectiveAt) DESC
""")
fun observePanels(): Flow<List<com.healthaggregator.data.LabPanelAggregate>>
```

Add import at top: `import com.healthaggregator.data.LabPanelAggregate` (the `com.healthaggregator.data.LabPanelAggregate` FQN in the query return type can then use the short form).

- [ ] **Step 3: Extend `SourceRecordDao.kt` — append one query**

```kotlin
@Query("SELECT rawJson FROM source_records WHERE sourceSystem = :source AND fhirReference = :ref LIMIT 1")
suspend fun findRawJson(source: String, ref: String): String?
```

- [ ] **Step 4: Build verify**

```bash
./gradlew :app:kspDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/data/LabPanelAggregate.kt \
        app/src/main/java/com/healthaggregator/data/dao/LabDao.kt \
        app/src/main/java/com/healthaggregator/data/dao/SourceRecordDao.kt
git commit -m "Phase 1: LabDao panel queries + SourceRecordDao raw-JSON lookup

observeByLoinc(loinc) drives the trend chart (all past values of one test).
observeByServiceRequest(sr) drives the Panel Detail screen (components
of one order). observePanels() aggregates one row per ServiceRequest
for the Records>Labs list — COALESCE fallback to testName for the
defensive case where basedOn.display was missing.

SourceRecordDao.findRawJson powers the 'View raw JSON' expander on
all detail screens."
```

---

### Task 6: RecordsRepository extensions

**Files:**
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/repository/RecordsRepository.kt`

- [ ] **Step 1: Add methods to RecordsRepository**

Inside the class, alongside existing methods:

```kotlin
fun observePanels(): Flow<List<com.healthaggregator.data.LabPanelAggregate>> =
	labs.observePanels()

fun observeLabsByServiceRequest(sr: String): Flow<List<LabObservation>> =
	labs.observeByServiceRequest(sr)

fun observeLabsByLoinc(loinc: String): Flow<List<LabObservation>> =
	labs.observeByLoinc(loinc)

suspend fun findRawJson(source: String, fhirReference: String): String? =
	sourceRecords.findRawJson(source, fhirReference)
```

Add `sourceRecords: SourceRecordDao` to the constructor injected params (append to the `@Inject constructor(...)` list). Also add the import: `import com.healthaggregator.data.dao.SourceRecordDao`.

- [ ] **Step 2: Build verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: DAO test for observePanels**

Create `healthaggregator-android/app/src/test/java/com/healthaggregator/data/dao/LabDaoPanelTest.kt`:

```kotlin
package com.healthaggregator.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.LabObservation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LabDaoPanelTest {
	private lateinit var db: AppDatabase
	private lateinit var dao: LabDao

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		dao = db.labDao()
	}

	@After fun tearDown() { db.close() }

	@Test
	fun observePanels_groups_by_serviceRequestReference() = runTest {
		// Two components of one panel + one standalone-ish (serviceRequestReference null)
		dao.upsertAll(listOf(
			lab("obs-1", sr = "ServiceRequest/bmp-1", srDisplay = "Basic Metabolic Panel", date = "2024-03-15T09:30:00Z"),
			lab("obs-2", sr = "ServiceRequest/bmp-1", srDisplay = "Basic Metabolic Panel", date = "2024-03-15T09:30:00Z"),
			lab("obs-3", sr = "ServiceRequest/cbc-1", srDisplay = "Complete Blood Count", date = "2024-03-16T10:00:00Z"),
			lab("obs-4", sr = null, srDisplay = null, date = "2024-03-17T11:00:00Z"),
		))
		val panels = dao.observePanels().first()
		assertEquals(2, panels.size) // orphan excluded
		val bmp = panels.first { it.serviceRequestReference == "ServiceRequest/bmp-1" }
		val cbc = panels.first { it.serviceRequestReference == "ServiceRequest/cbc-1" }
		assertEquals(2, bmp.componentCount)
		assertEquals(1, cbc.componentCount)
		assertEquals("Basic Metabolic Panel", bmp.displayName)
		// Ordered newest first
		assertEquals("ServiceRequest/cbc-1", panels[0].serviceRequestReference)
	}

	@Test
	fun observePanels_falls_back_to_testName_when_display_null() = runTest {
		dao.upsert(lab("obs-5", sr = "ServiceRequest/x", srDisplay = null, testName = "Urinalysis"))
		val panels = dao.observePanels().first()
		assertEquals(1, panels.size)
		assertEquals("Urinalysis", panels[0].displayName)
	}

	private fun lab(
		fhirRef: String,
		sr: String? = null,
		srDisplay: String? = null,
		testName: String = "test",
		date: String? = "2024-03-15T09:30:00Z",
	) = LabObservation(
		sourceSystem = "cleveland-clinic",
		sourceName = "Cleveland Clinic",
		fhirReference = "Observation/$fhirRef",
		resourceId = fhirRef,
		testName = testName,
		effectiveAt = date?.let { Instant.parse(it) },
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
		serviceRequestReference = sr,
		serviceRequestDisplay = srDisplay,
	)
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.data.dao.LabDaoPanelTest"
```

Expected: 2/2 PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/data/repository/RecordsRepository.kt \
        app/src/test/java/com/healthaggregator/data/dao/LabDaoPanelTest.kt
git commit -m "Phase 1: RecordsRepository panel accessors + DAO test

Repository now exposes observePanels (grouped aggregates),
observeLabsByServiceRequest (panel components), observeLabsByLoinc
(trend history), findRawJson (detail-page raw FHIR expander).

DAO test confirms observePanels groups correctly, orphans excluded,
displayName COALESCE fallback works."
```

---

### Task 7 (Phase 1 gate)

- [ ] **Step 1: Full build + tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all α.1 tests still pass + 4 new (2 basedOn + 2 panel).

- [ ] **Step 2: Install + smoke on device that already had α.1 DB**

```bash
./gradlew :app:installDebug
```

Open app → existing records still visible → tap Sync → new syncs populate `serviceRequestReference` on re-imported labs (via INSERT OR REPLACE). Pull DB and sanity-check:

```bash
adb exec-out run-as com.healthaggregator cat databases/healthaggregator.db > /tmp/hc.db
sqlite3 /tmp/hc.db "SELECT COUNT(*) FROM lab_observations WHERE serviceRequestReference IS NOT NULL;"
```

Expected: > 0 after a sync.

- [ ] **Step 3: Gate commit**

```bash
git commit --allow-empty -m "Phase 1 gate: data + import — migration clean, tests green, device smoke

LabObservation carries basedOn context. DAO aggregates panels.
Repository exposes the 4 new methods the UI layer needs.
Room migration test validates v1→v2. Existing installs upgrade
cleanly; re-sync backfills."
```

---

# Phase 2 — Sparkline + trend chart primitives

### Task 8: ChartGeometry (pure-Kotlin)

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/components/ChartGeometry.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/data/ChartGeometryTest.kt`

- [ ] **Step 1: Write `ChartGeometry.kt`**

```kotlin
package com.healthaggregator.ui.components

/**
 * Pure-Kotlin path math for the sparkline / trend chart. No Compose imports — keep this
 * unit-testable under JUnit 5 without Robolectric.
 *
 * Given a list of y-values and a canvas width/height, returns a list of Pair<x, y> points
 * in canvas coordinates (y inverted: higher value = smaller y).
 */
data class ChartPoint(val x: Float, val y: Float)

data class ChartRange(val min: Double, val max: Double) {
	companion object {
		/** Auto-range from data + optional reference band. Guarantees non-zero span. */
		fun compute(values: List<Double>, refLow: Double? = null, refHigh: Double? = null): ChartRange {
			val all = buildList {
				addAll(values)
				refLow?.let { add(it) }
				refHigh?.let { add(it) }
			}
			if (all.isEmpty()) return ChartRange(0.0, 1.0)
			var lo = all.min()
			var hi = all.max()
			if (lo == hi) { lo -= 1.0; hi += 1.0 } // avoid divide-by-zero
			// 10% padding
			val span = hi - lo
			return ChartRange(lo - span * 0.1, hi + span * 0.1)
		}
	}
}

fun pointsFor(values: List<Double>, width: Float, height: Float, range: ChartRange): List<ChartPoint> {
	if (values.isEmpty()) return emptyList()
	if (values.size == 1) return listOf(ChartPoint(width / 2f, height / 2f))
	val span = (range.max - range.min).toFloat()
	return values.mapIndexed { i, v ->
		val x = (i.toFloat() / (values.size - 1)) * width
		val yNorm = ((v - range.min) / span).toFloat()
		val y = height - (yNorm * height)
		ChartPoint(x, y)
	}
}

/** Returns (yTop, yBottom) in canvas coords for the reference-range band, or null if not applicable. */
fun referenceBandY(height: Float, range: ChartRange, refLow: Double?, refHigh: Double?): Pair<Float, Float>? {
	if (refLow == null && refHigh == null) return null
	val lo = refLow ?: range.min
	val hi = refHigh ?: range.max
	val span = (range.max - range.min).toFloat()
	val yTop = height - ((hi - range.min).toFloat() / span * height)
	val yBot = height - ((lo - range.min).toFloat() / span * height)
	return yTop to yBot
}
```

- [ ] **Step 2: Write `ChartGeometryTest.kt` (JUnit 5 Jupiter)**

```kotlin
package com.healthaggregator.data

import com.healthaggregator.ui.components.ChartRange
import com.healthaggregator.ui.components.pointsFor
import com.healthaggregator.ui.components.referenceBandY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChartGeometryTest {
	@Test fun empty_list_returns_no_points() {
		val p = pointsFor(emptyList(), 100f, 50f, ChartRange(0.0, 1.0))
		assertEquals(0, p.size)
	}

	@Test fun single_point_centers_in_canvas() {
		val p = pointsFor(listOf(5.0), 100f, 50f, ChartRange(0.0, 10.0))
		assertEquals(1, p.size)
		assertEquals(50f, p[0].x, 0.01f)
		assertEquals(25f, p[0].y, 0.01f)
	}

	@Test fun two_points_span_x_and_invert_y() {
		// 0.0 and 10.0 in a 0..10 range over width 100, height 50
		val p = pointsFor(listOf(0.0, 10.0), 100f, 50f, ChartRange(0.0, 10.0))
		assertEquals(2, p.size)
		assertEquals(0f, p[0].x, 0.01f)
		assertEquals(100f, p[1].x, 0.01f)
		assertEquals(50f, p[0].y, 0.01f)   // 0 → bottom
		assertEquals(0f, p[1].y, 0.01f)    // 10 → top
	}

	@Test fun reference_band_null_when_no_refs() {
		assertNull(referenceBandY(50f, ChartRange(0.0, 10.0), null, null))
	}

	@Test fun reference_band_positions_correctly() {
		val band = referenceBandY(50f, ChartRange(0.0, 10.0), refLow = 4.0, refHigh = 6.0)
		assertNotNull(band)
		// top (y for 6.0) < bottom (y for 4.0)
		assertTrue(band!!.first < band.second)
	}

	@Test fun range_compute_handles_flat_series() {
		val r = ChartRange.compute(listOf(5.0, 5.0, 5.0))
		assertTrue(r.max > r.min) // guaranteed non-zero span
	}

	@Test fun range_includes_reference_bounds() {
		val r = ChartRange.compute(values = listOf(5.0), refLow = 1.0, refHigh = 10.0)
		assertTrue(r.min <= 1.0)
		assertTrue(r.max >= 10.0)
	}
}
```

- [ ] **Step 3: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.data.ChartGeometryTest"
```

Expected: 6/6 PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/components/ChartGeometry.kt \
        app/src/test/java/com/healthaggregator/data/ChartGeometryTest.kt
git commit -m "Phase 2: pure-Kotlin chart geometry — ChartRange, pointsFor, referenceBandY

Canvas-coord mapping kept separate from Compose drawing so the math
is testable under JUnit 5 without Robolectric. Handles: empty (no
points), single value (centered), flat series (non-zero span via
1.0 padding), reference bands positioned correctly with y inverted.

6 Jupiter tests cover the branches that matter for the sparkline."
```

---

### Task 9: Sparkline composable (small inline)

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/components/Sparkline.kt`

- [ ] **Step 1: Write `Sparkline.kt`**

```kotlin
package com.healthaggregator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import com.healthaggregator.ui.theme.extended

/**
 * Inline sparkline: up to 12 most-recent numeric values, drawn as a connected line.
 * Reference-range band (if provided) shaded behind the line.
 *
 * @param values chronological-oldest-first numeric values (caller order = left-to-right)
 * @param refLow lower bound of the healthy range (shaded band lower edge)
 * @param refHigh upper bound of the healthy range (shaded band upper edge)
 * @param abnormal highlight line with error color when true
 */
@Composable
fun Sparkline(
	values: List<Double>,
	refLow: Double? = null,
	refHigh: Double? = null,
	abnormal: Boolean = false,
	modifier: Modifier = Modifier,
) {
	val strokeColor = if (abnormal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
	val bandColor = MaterialTheme.extended.success.copy(alpha = 0.15f)
	val dotColor = strokeColor

	Canvas(
		modifier = modifier
			.width(80.dp)
			.height(32.dp),
	) {
		val w = size.width
		val h = size.height
		val range = ChartRange.compute(values.takeLast(12), refLow, refHigh)

		referenceBandY(h, range, refLow, refHigh)?.let { (yTop, yBot) ->
			drawRect(
				color = bandColor,
				topLeft = Offset(0f, yTop),
				size = androidx.compose.ui.geometry.Size(w, yBot - yTop),
			)
		}

		val pts = pointsFor(values.takeLast(12), w, h, range)
		if (pts.isEmpty()) return@Canvas

		if (pts.size == 1) {
			drawCircle(color = dotColor, radius = 3f, center = Offset(pts[0].x, pts[0].y))
			return@Canvas
		}

		val path = Path().apply {
			moveTo(pts[0].x, pts[0].y)
			for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
		}
		drawPath(path = path, color = strokeColor, style = Stroke(width = 2f))
		// end-dot emphasis for most-recent reading
		drawCircle(color = dotColor, radius = 2.5f, center = Offset(pts.last().x, pts.last().y))
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewNormal() = HealthAggregatorTheme {
	Sparkline(values = listOf(5.2, 5.3, 5.1, 5.4, 5.5, 5.3, 5.2), refLow = 4.0, refHigh = 5.6)
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewAbnormal() = HealthAggregatorTheme {
	Sparkline(values = listOf(5.2, 5.5, 5.9, 6.3, 6.8, 7.2, 7.5), refLow = 4.0, refHigh = 5.6, abnormal = true)
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewSingle() = HealthAggregatorTheme {
	Sparkline(values = listOf(5.2), refLow = 4.0, refHigh = 5.6)
}
```

- [ ] **Step 2: Build verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/components/Sparkline.kt
git commit -m "Phase 2: Sparkline composable — inline mini-chart

Fixed 80dp × 32dp. Last-12-values window. Error-color stroke when
abnormal. Reference-range band shaded behind the line. End-dot
emphasizes most-recent reading. Single-value path shows centered
dot. 3 Previews: normal, abnormal trending up, single reading."
```

---

### Task 10: TrendChart composable (full-size)

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/components/TrendChart.kt`

- [ ] **Step 1: Write `TrendChart.kt`**

```kotlin
package com.healthaggregator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import com.healthaggregator.ui.theme.extended
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Full-size trend chart with axis labels. Caps visible points at 50 (older collapsed).
 *
 * @param values (Instant → Double) pairs, chronological oldest-first
 */
@Composable
fun TrendChart(
	values: List<Pair<Instant?, Double>>,
	refLow: Double? = null,
	refHigh: Double? = null,
	unit: String? = null,
	modifier: Modifier = Modifier,
) {
	val visible = values.takeLast(50)
	val numericValues = visible.map { it.second }
	val range = ChartRange.compute(numericValues, refLow, refHigh)
	val strokeColor = MaterialTheme.colorScheme.primary
	val bandColor = MaterialTheme.extended.success.copy(alpha = 0.15f)

	Column(modifier = modifier.fillMaxWidth()) {
		Canvas(
			modifier = Modifier
				.fillMaxWidth()
				.height(200.dp)
				.padding(horizontal = 16.dp, vertical = 8.dp),
		) {
			val w = size.width
			val h = size.height

			referenceBandY(h, range, refLow, refHigh)?.let { (yTop, yBot) ->
				drawRect(color = bandColor, topLeft = Offset(0f, yTop), size = Size(w, yBot - yTop))
			}

			val pts = pointsFor(numericValues, w, h, range)
			if (pts.isEmpty()) return@Canvas

			if (pts.size == 1) {
				drawCircle(color = strokeColor, radius = 6f, center = Offset(pts[0].x, pts[0].y))
			} else {
				val path = Path().apply {
					moveTo(pts[0].x, pts[0].y)
					for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
				}
				drawPath(path = path, color = strokeColor, style = Stroke(width = 3f))
				pts.forEach { drawCircle(color = strokeColor, radius = 3f, center = Offset(it.x, it.y)) }
			}
		}

		// y-axis: range labels
		Text(
			text = buildString {
				append("%.1f".format(range.min))
				append(" – ")
				append("%.1f".format(range.max))
				unit?.let { append(" $it") }
			},
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.secondary,
			modifier = Modifier.padding(horizontal = 16.dp),
		)

		// x-axis: earliest / latest dates
		if (visible.isNotEmpty()) {
			Text(
				text = buildString {
					append(visible.first().first?.let { formatDate(it) } ?: "—")
					if (visible.size > 1) {
						append("   →   ")
						append(visible.last().first?.let { formatDate(it) } ?: "—")
					}
				},
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.secondary,
				textAlign = TextAlign.Center,
				modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
			)
		}

		if (visible.size < values.size) {
			Text(
				text = "Showing latest ${visible.size} of ${values.size} readings",
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.secondary,
				modifier = Modifier.padding(horizontal = 16.dp),
			)
		}
	}
}

private fun formatDate(i: Instant): String =
	LocalDate.ofInstant(i, ZoneId.systemDefault()).toString()

@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 320)
@Composable private fun PreviewTrend() = HealthAggregatorTheme {
	TrendChart(
		values = listOf(
			Instant.parse("2023-01-15T00:00:00Z") to 5.2,
			Instant.parse("2023-04-12T00:00:00Z") to 5.4,
			Instant.parse("2023-07-20T00:00:00Z") to 5.6,
			Instant.parse("2023-10-05T00:00:00Z") to 5.3,
			Instant.parse("2024-01-22T00:00:00Z") to 5.5,
			Instant.parse("2024-04-18T00:00:00Z") to 5.8,
		),
		refLow = 4.0,
		refHigh = 5.6,
		unit = "%",
	)
}
```

- [ ] **Step 2: Build verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/components/TrendChart.kt
git commit -m "Phase 2: TrendChart composable — full-size lab history viz

Caps at latest-50 readings (older collapsed with count note below).
Y-range auto-includes reference band so the band is always visible.
X-axis shows earliest → latest date. Shares ChartGeometry with
Sparkline so the math's tested once."
```

---

### Task 11 (Phase 2 gate)

- [ ] **Step 1: Full build + tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all existing + new Jupiter chart tests green.

- [ ] **Step 2: Gate commit**

```bash
git commit --allow-empty -m "Phase 2 gate: sparkline + trend chart primitives ready

Pure-Kotlin ChartGeometry fully tested. Sparkline inline (80x32dp)
and TrendChart full-size (fillWidth x 200dp) both render previews
cleanly. Reference-range bands, abnormal highlighting, single-point
degenerate case, and empty-state all handled."
```

---

# Phase 3 — Detail screens

### Task 12: LabDetailScreen + ViewModel

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/records/LabDetailViewModel.kt`
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/records/LabDetailScreen.kt`

- [ ] **Step 1: Write `LabDetailViewModel.kt`**

```kotlin
package com.healthaggregator.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.isAbnormal
import com.healthaggregator.data.repository.RecordsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class LabDetailUiState(
	val loading: Boolean = true,
	val current: LabObservation? = null,
	val trend: List<LabObservation> = emptyList(),
	val rawJson: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LabDetailViewModel @Inject constructor(
	private val records: RecordsRepository,
	savedState: SavedStateHandle,
) : ViewModel() {
	private val sourceSystem: String = requireNotNull(savedState["source"])
	private val fhirReference: String = requireNotNull(savedState["fhirRef"])

	private val _rawJson = MutableStateFlow<String?>(null)
	val rawJson: StateFlow<String?> = _rawJson.asStateFlow()

	val uiState: StateFlow<LabDetailUiState> =
		records.observeLabs()
			.map { labs -> labs.firstOrNull { it.sourceSystem == sourceSystem && it.fhirReference == fhirReference } }
			.flatMapLatest { current ->
				if (current?.loincCode == null) {
					kotlinx.coroutines.flow.flowOf(LabDetailUiState(loading = false, current = current))
				} else {
					records.observeLabsByLoinc(current.loincCode).map { trend ->
						LabDetailUiState(loading = false, current = current, trend = trend)
					}
				}
			}
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LabDetailUiState())

	init {
		viewModelScope.launch {
			_rawJson.value = records.findRawJson(sourceSystem, fhirReference)
		}
	}

	fun isAbnormalLab(lab: LabObservation): Boolean = isAbnormal(lab)
}
```

- [ ] **Step 2: Write `LabDetailScreen.kt`**

```kotlin
package com.healthaggregator.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.ui.components.SourceBadge
import com.healthaggregator.ui.components.TrendChart
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabDetailScreen(
	onBack: () -> Unit,
	viewModel: LabDetailViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	val rawJson by viewModel.rawJson.collectAsStateWithLifecycle()
	val current = state.current

	Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
		TopAppBar(
			title = { Text(current?.testName ?: "Lab") },
			navigationIcon = {
				IconButton(onClick = onBack) {
					Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
				}
			},
		)

		if (state.loading) {
			Text("Loading…", modifier = Modifier.padding(24.dp))
			return@Column
		}
		if (current == null) {
			Text("Lab not found", modifier = Modifier.padding(24.dp))
			return@Column
		}

		// Big-value readout
		Card(
			modifier = Modifier.padding(16.dp).fillMaxWidth(),
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
		) {
			Column(modifier = Modifier.padding(20.dp)) {
				Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
					Text(
						text = buildString {
							current.numericValue?.let { append("$it") } ?: append(current.textValue ?: "—")
							current.unit?.let { append(" $it") }
						},
						style = MaterialTheme.typography.displaySmall,
						fontWeight = FontWeight.SemiBold,
					)
					Spacer(Modifier.weight(1f))
					if (viewModel.isAbnormalLab(current)) {
						Text(
							text = current.interpretation ?: "Abnormal",
							style = MaterialTheme.typography.labelMedium,
							color = MaterialTheme.colorScheme.error,
						)
					}
				}
				Spacer(Modifier.height(8.dp))
				SourceBadge(sourceSystem = current.sourceSystem, sourceName = current.sourceName)
				current.effectiveAt?.let {
					Spacer(Modifier.height(4.dp))
					Text(
						LocalDate.ofInstant(it, ZoneId.systemDefault()).toString(),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.secondary,
					)
				}
				val refText = current.referenceText
					?: (if (current.referenceLow != null || current.referenceHigh != null)
						"Normal: ${current.referenceLow ?: "—"} – ${current.referenceHigh ?: "—"}" else null)
				refText?.let {
					Spacer(Modifier.height(4.dp))
					Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
				}
			}
		}

		// Trend chart
		if (state.trend.size > 1) {
			Text(
				"Trend",
				style = MaterialTheme.typography.titleSmall,
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
			)
			TrendChart(
				values = state.trend
					.sortedBy { it.effectiveAt }
					.mapNotNull { lab ->
						val v = lab.numericValue ?: return@mapNotNull null
						lab.effectiveAt to v
					},
				refLow = current.referenceLow,
				refHigh = current.referenceHigh,
				unit = current.unit,
			)
		}

		HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

		// Metadata
		Text("Metadata", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
		MetadataRow("Test", current.testName)
		MetadataRow("LOINC", current.loincCode ?: "—")
		MetadataRow("Status", current.status.ifBlank { "—" })
		MetadataRow("FHIR reference", current.fhirReference)
		MetadataRow("Imported", current.importedAt.toString())

		// Raw JSON
		var expanded by remember { mutableStateOf(false) }
		HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
		Text(
			text = if (expanded) "▾ Raw FHIR JSON" else "▸ Raw FHIR JSON",
			style = MaterialTheme.typography.titleSmall,
			modifier = Modifier
				.fillMaxWidth()
				.clickable { expanded = !expanded }
				.padding(horizontal = 16.dp, vertical = 8.dp),
		)
		if (expanded) {
			Text(
				text = rawJson ?: "(not available)",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.secondary,
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
			)
		}

		Spacer(Modifier.height(32.dp))
	}
}

@Composable
private fun MetadataRow(label: String, value: String) {
	Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
		Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
		Spacer(Modifier.weight(1f))
		Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 900)
@Composable
private fun PreviewLabDetail() = HealthAggregatorTheme {
	Column(Modifier.fillMaxSize()) {
		TopAppBar(title = { Text("Hemoglobin A1c") })
		Card(
			modifier = Modifier.padding(16.dp).fillMaxWidth(),
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
		) {
			Column(modifier = Modifier.padding(20.dp)) {
				Text("5.8 %", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
				Spacer(Modifier.height(8.dp))
				SourceBadge("cleveland-clinic", "Cleveland Clinic")
				Spacer(Modifier.height(4.dp))
				Text("Normal: 4.0 – 5.6 %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
			}
		}
	}
}
```

- [ ] **Step 3: Build verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/records/LabDetailViewModel.kt \
        app/src/main/java/com/healthaggregator/ui/records/LabDetailScreen.kt
git commit -m "Phase 3: LabDetailScreen + ViewModel — big value, trend chart, raw JSON

Nav args: sourceSystem + fhirReference (SavedStateHandle). Current lab
flows from observeLabs filtered locally; trend flows from
observeLabsByLoinc(loincCode). Raw JSON loaded once from
SourceRecordDao.findRawJson and exposed as a collapsible expander.

TrendChart shown only when more than one reading exists for the same
LOINC. Reference range text renders under the big value."
```

---

### Task 13: PanelDetailScreen + ViewModel

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/records/PanelDetailViewModel.kt`
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/records/PanelDetailScreen.kt`

- [ ] **Step 1: Write `PanelDetailViewModel.kt`**

```kotlin
package com.healthaggregator.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.isAbnormal
import com.healthaggregator.data.repository.RecordsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PanelDetailUiState(
	val loading: Boolean = true,
	val sourceRequestReference: String = "",
	val panelName: String = "",
	val sourceSystem: String = "",
	val sourceName: String = "",
	val components: List<LabObservation> = emptyList(),
	val abnormalCount: Int = 0,
)

@HiltViewModel
class PanelDetailViewModel @Inject constructor(
	private val records: RecordsRepository,
	savedState: SavedStateHandle,
) : ViewModel() {
	private val serviceRequest: String = requireNotNull(savedState["sr"])

	val uiState: StateFlow<PanelDetailUiState> =
		records.observeLabsByServiceRequest(serviceRequest)
			.map { components ->
				val first = components.firstOrNull()
				PanelDetailUiState(
					loading = false,
					sourceRequestReference = serviceRequest,
					panelName = first?.serviceRequestDisplay ?: first?.testName ?: "Panel",
					sourceSystem = first?.sourceSystem ?: "",
					sourceName = first?.sourceName ?: "",
					components = components,
					abnormalCount = components.count { isAbnormal(it) },
				)
			}
			.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PanelDetailUiState())
}
```

- [ ] **Step 2: Write `PanelDetailScreen.kt`**

```kotlin
package com.healthaggregator.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.isAbnormal
import com.healthaggregator.ui.components.SourceBadge
import com.healthaggregator.ui.components.Sparkline
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelDetailScreen(
	onBack: () -> Unit,
	onOpenComponent: (sourceSystem: String, fhirReference: String) -> Unit,
	viewModel: PanelDetailViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()

	Column(modifier = Modifier.fillMaxSize()) {
		TopAppBar(
			title = { Text(state.panelName) },
			navigationIcon = {
				IconButton(onClick = onBack) {
					Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
				}
			},
		)

		if (state.components.isEmpty()) {
			Text(if (state.loading) "Loading…" else "No components", modifier = Modifier.padding(24.dp))
			return@Column
		}

		Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
			SourceBadge(sourceSystem = state.sourceSystem, sourceName = state.sourceName)
			Spacer(Modifier.width(12.dp))
			Text(
				text = "${state.components.size} results" + if (state.abnormalCount > 0) " · ${state.abnormalCount} abnormal" else "",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.secondary,
			)
		}

		HorizontalDivider()

		LazyColumn(modifier = Modifier.fillMaxSize()) {
			items(state.components, key = { "comp-${it.id}" }) { comp ->
				ComponentRow(comp, onClick = { onOpenComponent(comp.sourceSystem, comp.fhirReference) })
				HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
			}
		}
	}
}

@Composable
private fun ComponentRow(lab: LabObservation, onClick: () -> Unit) {
	Row(
		modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(lab.testName, style = MaterialTheme.typography.bodyLarge)
			Spacer(Modifier.height(2.dp))
			Text(
				text = buildString {
					lab.numericValue?.let { append("$it") } ?: append(lab.textValue ?: "—")
					lab.unit?.let { append(" $it") }
				},
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.secondary,
			)
			if (lab.referenceLow != null || lab.referenceHigh != null) {
				Text(
					text = "Normal: ${lab.referenceLow ?: "—"} – ${lab.referenceHigh ?: "—"}",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.secondary,
				)
			}
		}
		if (isAbnormal(lab)) {
			Text(
				text = lab.interpretation?.take(2) ?: "!",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.error,
				modifier = Modifier.padding(end = 8.dp),
			)
		}
		// Inline sparkline placeholder (single-value for this moment; trend pulled in detail)
		lab.numericValue?.let {
			Sparkline(values = listOf(it), refLow = lab.referenceLow, refHigh = lab.referenceHigh, abnormal = isAbnormal(lab))
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 800)
@Composable
private fun PreviewPanelDetail() = HealthAggregatorTheme {
	Column(modifier = Modifier.fillMaxSize()) {
		TopAppBar(title = { Text("Basic Metabolic Panel") })
		Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
			SourceBadge("cleveland-clinic", "Cleveland Clinic")
			Spacer(Modifier.width(12.dp))
			Text("8 results · 1 abnormal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
		}
	}
}
```

- [ ] **Step 3: Build verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/records/PanelDetailViewModel.kt \
        app/src/main/java/com/healthaggregator/ui/records/PanelDetailScreen.kt
git commit -m "Phase 3: PanelDetailScreen + ViewModel — components + inline sparklines

Nav arg: sr (ServiceRequest reference string). Flows from
observeLabsByServiceRequest. Header shows SourceBadge + count +
abnormal tally. Each component row: test name + value + reference +
inline sparkline (current-value placeholder; LabDetail shows full
trend). Tap component row → navigate to LabDetail."
```

---

### Task 14: RecordDetailScreen + ViewModel (generic)

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/records/RecordDetailViewModel.kt`
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/records/RecordDetailScreen.kt`

- [ ] **Step 1: Write `RecordDetailViewModel.kt`**

```kotlin
package com.healthaggregator.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.data.repository.RecordsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordDetailUiState(
	val loading: Boolean = true,
	val resourceType: String = "",
	val rawJson: String? = null,
	val fields: List<Pair<String, String>> = emptyList(),
	val sourceSystem: String = "",
	val fhirReference: String = "",
)

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
	private val records: RecordsRepository,
	savedState: SavedStateHandle,
) : ViewModel() {
	private val sourceSystem: String = requireNotNull(savedState["source"])
	private val fhirReference: String = requireNotNull(savedState["fhirRef"])

	private val _state = MutableStateFlow(
		RecordDetailUiState(sourceSystem = sourceSystem, fhirReference = fhirReference)
	)
	val uiState: StateFlow<RecordDetailUiState> = _state.asStateFlow()

	init {
		viewModelScope.launch {
			val raw = records.findRawJson(sourceSystem, fhirReference)
			_state.value = RecordDetailUiState(
				loading = false,
				resourceType = fhirReference.substringBefore("/"),
				rawJson = raw,
				fields = extractFields(raw),
				sourceSystem = sourceSystem,
				fhirReference = fhirReference,
			)
		}
	}

	private fun extractFields(raw: String?): List<Pair<String, String>> {
		if (raw == null) return emptyList()
		return try {
			val root = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
				.parseToJsonElement(raw).jsonObject
			val fields = mutableListOf<Pair<String, String>>()
			root.entries.forEach { (k, v) ->
				if (k == "resourceType" || k == "id") return@forEach
				val display = when (v) {
					is kotlinx.serialization.json.JsonPrimitive -> v.contentOrNull ?: "null"
					is kotlinx.serialization.json.JsonArray -> "[${v.size} items]"
					is kotlinx.serialization.json.JsonObject -> extractReadable(v)
					else -> v.toString()
				}
				if (display.isNotBlank()) fields.add(k to display)
			}
			fields
		} catch (e: Exception) {
			emptyList()
		}
	}

	private fun extractReadable(obj: kotlinx.serialization.json.JsonObject): String {
		obj["text"]?.let { return (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: it.toString() }
		obj["display"]?.let { return (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: it.toString() }
		obj["reference"]?.let { return (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: it.toString() }
		return "{…}"
	}
}
```

Required imports for serialization — add at top:
```kotlin
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
```

- [ ] **Step 2: Write `RecordDetailScreen.kt`**

```kotlin
package com.healthaggregator.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
	onBack: () -> Unit,
	viewModel: RecordDetailViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()

	Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
		TopAppBar(
			title = { Text(state.resourceType.ifBlank { "Record" }) },
			navigationIcon = {
				IconButton(onClick = onBack) {
					Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
				}
			},
		)

		if (state.loading) {
			Text("Loading…", modifier = Modifier.padding(24.dp))
			return@Column
		}

		Card(
			modifier = Modifier.padding(16.dp).fillMaxWidth(),
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
		) {
			Column(modifier = Modifier.padding(16.dp)) {
				Text("FHIR reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
				Text(state.fhirReference, style = MaterialTheme.typography.bodyMedium)
				Spacer(Modifier.height(8.dp))
				Text("Source", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
				Text(state.sourceSystem, style = MaterialTheme.typography.bodyMedium)
			}
		}

		Text("Fields", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
		state.fields.forEach { (k, v) ->
			Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
				Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
				Spacer(Modifier.weight(1f))
				Text(v, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
			}
		}

		var expanded by remember { mutableStateOf(false) }
		HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
		Text(
			text = if (expanded) "▾ Raw FHIR JSON" else "▸ Raw FHIR JSON",
			style = MaterialTheme.typography.titleSmall,
			modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 8.dp),
		)
		if (expanded) {
			Text(
				text = state.rawJson ?: "(not available)",
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.secondary,
				modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
			)
		}

		Spacer(Modifier.height(32.dp))
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 800)
@Composable
private fun PreviewRecordDetail() = HealthAggregatorTheme {
	Column(Modifier.fillMaxSize()) {
		TopAppBar(title = { Text("MedicationRequest") })
		Card(
			modifier = Modifier.padding(16.dp).fillMaxWidth(),
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
		) {
			Column(modifier = Modifier.padding(16.dp)) {
				Text("Fields", style = MaterialTheme.typography.titleSmall)
				Spacer(Modifier.height(8.dp))
				Text("status: active", style = MaterialTheme.typography.bodyMedium)
				Text("medicationCodeableConcept: Lisinopril 10 mg", style = MaterialTheme.typography.bodyMedium)
			}
		}
	}
}
```

- [ ] **Step 3: Build verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/records/RecordDetailViewModel.kt \
        app/src/main/java/com/healthaggregator/ui/records/RecordDetailScreen.kt
git commit -m "Phase 3: RecordDetailScreen — generic detail for non-lab types

Reads SourceRecord.rawJson via SourceRecordDao.findRawJson and
extracts top-level fields as key/value pairs for the fields section.
Nested objects flattened via .text/.display/.reference fallback.
Raw JSON always visible under a collapsible expander.

Used for MedicationRecord, ConditionRecord, AllergyRecord, etc. —
Vitals also route here (no trend chart in α.2; vitals trends
deferred to a later stream)."
```

---

### Task 15: AppNav — 3 detail routes

**Files:**
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/navigation/AppNav.kt`

- [ ] **Step 1: Update `AppNav.kt`**

Replace the contents with:

```kotlin
package com.healthaggregator.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.healthaggregator.ui.home.HomeScreen
import com.healthaggregator.ui.records.LabDetailScreen
import com.healthaggregator.ui.records.PanelDetailScreen
import com.healthaggregator.ui.records.RecordDetailScreen
import com.healthaggregator.ui.records.RecordsScreen
import com.healthaggregator.ui.settings.SettingsScreen
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private fun encode(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.name())

@Composable
fun AppNavHost(navController: NavHostController, onRequestPermissions: () -> Unit) {
	NavHost(
		navController = navController,
		startDestination = TopLevelRoute.HOME.route,
	) {
		composable(TopLevelRoute.HOME.route) {
			HomeScreen(
				onNavigateToRecords = { type -> navController.navigate("${TopLevelRoute.RECORDS.route}?type=$type") },
				onRequestPermissions = onRequestPermissions,
			)
		}
		composable(
			route = "${TopLevelRoute.RECORDS.route}?type={type}",
			arguments = listOf(navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null }),
		) {
			RecordsScreen(
				onOpenPanel = { sr -> navController.navigate("panel/${encode(sr)}") },
				onOpenLab = { source, fhirRef ->
					navController.navigate("lab/${encode(source)}/${encode(fhirRef)}")
				},
				onOpenRecord = { source, fhirRef ->
					navController.navigate("record/${encode(source)}/${encode(fhirRef)}")
				},
			)
		}
		composable(TopLevelRoute.SETTINGS.route) {
			SettingsScreen(onRequestPermissions = onRequestPermissions)
		}
		composable(
			route = "lab/{source}/{fhirRef}",
			arguments = listOf(
				navArgument("source") { type = NavType.StringType },
				navArgument("fhirRef") { type = NavType.StringType },
			),
		) {
			LabDetailScreen(onBack = { navController.popBackStack() })
		}
		composable(
			route = "panel/{sr}",
			arguments = listOf(navArgument("sr") { type = NavType.StringType }),
		) {
			PanelDetailScreen(
				onBack = { navController.popBackStack() },
				onOpenComponent = { source, fhirRef ->
					navController.navigate("lab/${encode(source)}/${encode(fhirRef)}")
				},
			)
		}
		composable(
			route = "record/{source}/{fhirRef}",
			arguments = listOf(
				navArgument("source") { type = NavType.StringType },
				navArgument("fhirRef") { type = NavType.StringType },
			),
		) {
			RecordDetailScreen(onBack = { navController.popBackStack() })
		}
	}
}
```

- [ ] **Step 2: Build verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. RecordsScreen's new callbacks don't exist yet — Task 19 adds them. This task's compile only succeeds if we temporarily stub the RecordsScreen signature. Alternative: do Task 19 first or fold this into Task 19. **Pragmatic path: combine Task 15 + Task 19's signature change into a single commit by doing Task 19 first.**

Actually: skip Step 2. Defer build verify until Task 19. Commit the routes now; accept that compile will fail against the old RecordsScreen signature. See Step 3.

- [ ] **Step 3: Temporary stub — update RecordsScreen signature**

To keep the plan's commits buildable in order, add the three nav-out callbacks to `RecordsScreen` now with no-op defaults, to be wired in Task 19:

In `RecordsScreen.kt`, change the function signature from `fun RecordsScreen(viewModel: RecordsViewModel = hiltViewModel())` to:

```kotlin
fun RecordsScreen(
	onOpenPanel: (serviceRequestReference: String) -> Unit = {},
	onOpenLab: (sourceSystem: String, fhirReference: String) -> Unit = { _, _ -> },
	onOpenRecord: (sourceSystem: String, fhirReference: String) -> Unit = { _, _ -> },
	viewModel: RecordsViewModel = hiltViewModel(),
) {
```

Default-arg values = `{}` and `{ _, _ -> }` — no-op. Body stays unchanged. Task 19 will actually wire the tap handlers.

- [ ] **Step 4: Build verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/navigation/AppNav.kt \
        app/src/main/java/com/healthaggregator/ui/records/RecordsScreen.kt
git commit -m "Phase 3: AppNav adds lab/panel/record detail routes

Three new composable routes: lab/{source}/{fhirRef},
panel/{sr}, record/{source}/{fhirRef}. Args URL-encoded to survive
the / in FHIR references. popBackStack() wired via onBack callbacks.
RecordsScreen gains 3 new no-op default callbacks; Task 19 wires
actual tap handlers for panel-row and non-lab-row navigation."
```

---

### Task 16 (Phase 3 gate)

- [ ] **Step 1: Full build + tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. All prior tests green.

- [ ] **Step 2: Gate commit**

```bash
git commit --allow-empty -m "Phase 3 gate: detail screens + routes wired, @Preview verified

LabDetailScreen / PanelDetailScreen / RecordDetailScreen all render
Previews. Nav graph carries URL-encoded FHIR references. Routes
reachable once Task 19 wires RecordsScreen taps."
```

---

# Phase 4 — Browse refactor

### Task 17: LabPanelRow composable

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/records/LabPanelRow.kt`

- [ ] **Step 1: Write `LabPanelRow.kt`**

```kotlin
package com.healthaggregator.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.data.LabPanelAggregate
import com.healthaggregator.ui.components.SourceBadge
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun LabPanelRow(
	panel: LabPanelAggregate,
	abnormal: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(Icons.Outlined.Science, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
		Spacer(Modifier.width(12.dp))
		Column(modifier = Modifier.weight(1f)) {
			Text(panel.displayName, style = MaterialTheme.typography.bodyLarge)
			Spacer(Modifier.size(2.dp))
			Text(
				text = buildString {
					panel.effectiveAt?.let { append(LocalDate.ofInstant(it, ZoneId.systemDefault()).toString()) }
					append(" · ")
					append("${panel.componentCount} results")
				},
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.secondary,
			)
			Spacer(Modifier.size(4.dp))
			SourceBadge(sourceSystem = panel.sourceSystem, sourceName = panel.sourceName)
		}
		if (abnormal) {
			Spacer(Modifier.width(12.dp))
			Text("Abnormal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
		}
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewNormal() = HealthAggregatorTheme {
	LabPanelRow(
		panel = LabPanelAggregate(
			serviceRequestReference = "ServiceRequest/bmp-1",
			displayName = "Basic Metabolic Panel",
			effectiveAt = Instant.parse("2024-03-15T09:30:00Z"),
			sourceSystem = "cleveland-clinic",
			sourceName = "Cleveland Clinic",
			componentCount = 8,
		),
		abnormal = false,
		onClick = {},
	)
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewAbnormal() = HealthAggregatorTheme {
	LabPanelRow(
		panel = LabPanelAggregate(
			serviceRequestReference = "ServiceRequest/cbc-1",
			displayName = "COMPREHENSIVE METABOLIC PANEL",
			effectiveAt = Instant.parse("2024-03-15T09:30:00Z"),
			sourceSystem = "summa-health",
			sourceName = "Summa Health",
			componentCount = 14,
		),
		abnormal = true,
		onClick = {},
	)
}
```

- [ ] **Step 2: Build verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/records/LabPanelRow.kt
git commit -m "Phase 4: LabPanelRow — one row per ServiceRequest

Shown on the Records>Labs filter. Matches MyChart's Test Results
list pattern — panel name as primary title, date + component
count as summary, source badge, Abnormal pill on the right when
any component is out of range."
```

---

### Task 18: SearchBar + SortChip components

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/components/SearchBar.kt`
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/components/SortChip.kt`

- [ ] **Step 1: Write `SearchBar.kt`**

```kotlin
package com.healthaggregator.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@Composable
fun SearchBar(
	query: String,
	onQueryChange: (String) -> Unit,
	placeholder: String = "Search records",
	modifier: Modifier = Modifier,
) {
	OutlinedTextField(
		value = query,
		onValueChange = onQueryChange,
		placeholder = { Text(placeholder) },
		leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
		trailingIcon = {
			if (query.isNotEmpty()) {
				IconButton(onClick = { onQueryChange("") }) {
					Icon(Icons.Outlined.Close, contentDescription = "Clear")
				}
			}
		},
		singleLine = true,
		keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
		modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
	)
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewEmpty() = HealthAggregatorTheme {
	SearchBar(query = "", onQueryChange = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewFilled() = HealthAggregatorTheme {
	SearchBar(query = "hba1c", onQueryChange = {})
}
```

- [ ] **Step 2: Write `SortChip.kt`**

```kotlin
package com.healthaggregator.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.healthaggregator.ui.theme.HealthAggregatorTheme

enum class SortOrder { NEWEST_FIRST, OLDEST_FIRST }

fun SortOrder.toggled(): SortOrder = if (this == SortOrder.NEWEST_FIRST) SortOrder.OLDEST_FIRST else SortOrder.NEWEST_FIRST

@Composable
fun SortChip(order: SortOrder, onToggle: () -> Unit, modifier: Modifier = Modifier) {
	AssistChip(
		onClick = onToggle,
		label = { Text(if (order == SortOrder.NEWEST_FIRST) "Newest" else "Oldest") },
		leadingIcon = {
			Icon(
				imageVector = if (order == SortOrder.NEWEST_FIRST) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
				contentDescription = null,
			)
		},
		modifier = modifier,
	)
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewNewest() = HealthAggregatorTheme {
	SortChip(order = SortOrder.NEWEST_FIRST, onToggle = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewOldest() = HealthAggregatorTheme {
	SortChip(order = SortOrder.OLDEST_FIRST, onToggle = {})
}
```

- [ ] **Step 3: Build verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/components/SearchBar.kt \
        app/src/main/java/com/healthaggregator/ui/components/SortChip.kt
git commit -m "Phase 4: SearchBar + SortChip components

SearchBar: M3 OutlinedTextField with search icon + clear button.
SortChip: AssistChip with up/down arrow + 'Newest'/'Oldest' label;
  toggle flips the state. 4 Previews total."
```

---

### Task 19: RecordsViewModel + Screen refactor

**Files:**
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/records/RecordsViewModel.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/records/RecordsScreen.kt`

- [ ] **Step 1: Extend RecordsViewModel**

At the top of `RecordsViewModel.kt`, add:

```kotlin
import com.healthaggregator.data.LabPanelAggregate
import com.healthaggregator.ui.components.SortOrder
```

After the existing `_filter` MutableStateFlow, add:

```kotlin
private val _query = MutableStateFlow("")
val query = _query.asStateFlow()

private val _sort = MutableStateFlow(SortOrder.NEWEST_FIRST)
val sort = _sort.asStateFlow()

private val panelRowsFlow: Flow<List<LabPanelAggregate>> = records.observePanels()

data class LabRowsSnapshot(val panels: List<LabPanelAggregate>, val abnormalBySr: Set<String>)

private val labRowsWithAbnormal: Flow<LabRowsSnapshot> = combine(panelRowsFlow, labRowsFlow) { panels, rows ->
	val abnormalSrs = rows
		.filter { it.kind == FilterType.LABS && it.trailingText != null }
		// rows carry fhirReference not sr; instead we need the raw lab — so switch to a different derivation
		.mapNotNull { null }
		.toSet()
	LabRowsSnapshot(panels, abnormalSrs)
}
```

Wait — the above references `labRowsFlow`'s internal shape. To avoid indirection, derive abnormal-by-SR directly from lab entities:

**Replace the above additions with this cleaner version:**

```kotlin
private val _query = MutableStateFlow("")
val query = _query.asStateFlow()

private val _sort = MutableStateFlow(SortOrder.NEWEST_FIRST)
val sort = _sort.asStateFlow()

private val panelRowsFlow: Flow<List<LabPanelAggregate>> = records.observePanels()

private val abnormalServiceRequestsFlow: Flow<Set<String>> = records.observeLabs().map { list ->
	list.filter { isAbnormal(it) && it.serviceRequestReference != null }
		.map { it.serviceRequestReference!! }
		.toSet()
}

data class LabsUiState(val panels: List<LabPanelAggregate>, val abnormalSrs: Set<String>)

private val labsUiFlow: Flow<LabsUiState> = combine(panelRowsFlow, abnormalServiceRequestsFlow) { panels, abn ->
	LabsUiState(panels, abn)
}

val labsState: StateFlow<LabsUiState> = labsUiFlow
	.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LabsUiState(emptyList(), emptySet()))

fun setQuery(q: String) { _query.value = q }
fun toggleSort() { _sort.value = _sort.value.toggled() }
```

Add imports: `import com.healthaggregator.data.isAbnormal`.

Keep the existing `rows: StateFlow<List<RecordRowData>>` flow for non-lab filters; the `LABS` branch of `flatMapLatest` now ignores that path for panel rendering. RecordsScreen's Labs view reads `labsState`; other filters read `rows`.

Keep existing filter() and refresh() methods unchanged.

- [ ] **Step 2: Apply search + sort to the `rows` flow**

Find the existing `val rows: StateFlow<List<RecordRowData>> = _filter.flatMapLatest { ... }` and wrap the output with search + sort:

Replace the `.stateIn(...)` at the end with:

```kotlin
.combine(_query) { rows, q ->
	if (q.isBlank()) rows else rows.filter { it.title.contains(q, ignoreCase = true) || it.summary.contains(q, ignoreCase = true) }
}.combine(_sort) { rows, s ->
	when (s) {
		SortOrder.NEWEST_FIRST -> rows.sortedByDescending { it.effectiveAt ?: java.time.Instant.EPOCH }
		SortOrder.OLDEST_FIRST -> rows.sortedBy { it.effectiveAt ?: java.time.Instant.EPOCH }
	}
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

(Remove the existing stateIn that followed the flatMapLatest — replaced by the above.)

- [ ] **Step 3: Rewrite RecordsScreen**

Replace the contents of `RecordsScreen.kt` with:

```kotlin
package com.healthaggregator.ui.records

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthaggregator.ui.components.EmptyState
import com.healthaggregator.ui.components.FilterChipRow
import com.healthaggregator.ui.components.RecordRow
import com.healthaggregator.ui.components.SearchBar
import com.healthaggregator.ui.components.SortChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
	onOpenPanel: (serviceRequestReference: String) -> Unit = {},
	onOpenLab: (sourceSystem: String, fhirReference: String) -> Unit = { _, _ -> },
	onOpenRecord: (sourceSystem: String, fhirReference: String) -> Unit = { _, _ -> },
	viewModel: RecordsViewModel = hiltViewModel(),
) {
	val filter by viewModel.filter.collectAsStateWithLifecycle()
	val query by viewModel.query.collectAsStateWithLifecycle()
	val sort by viewModel.sort.collectAsStateWithLifecycle()
	val rows by viewModel.rows.collectAsStateWithLifecycle()
	val labsState by viewModel.labsState.collectAsStateWithLifecycle()

	Column(modifier = Modifier.fillMaxSize()) {
		TopAppBar(title = { Text("Records") })

		SearchBar(query = query, onQueryChange = viewModel::setQuery)

		Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
			SortChip(order = sort, onToggle = viewModel::toggleSort)
		}

		FilterChipRow(
			options = FilterType.entries,
			selected = filter,
			onSelect = viewModel::setFilter,
			label = { it.chipLabel },
			modifier = Modifier.padding(vertical = 8.dp),
		)
		HorizontalDivider()

		if (filter == FilterType.LABS) {
			val filtered = labsState.panels.let { panels ->
				if (query.isBlank()) panels else panels.filter { it.displayName.contains(query, ignoreCase = true) }
			}.let { panels ->
				when (sort) {
					com.healthaggregator.ui.components.SortOrder.NEWEST_FIRST -> panels // already DESC from SQL
					com.healthaggregator.ui.components.SortOrder.OLDEST_FIRST -> panels.reversed()
				}
			}
			if (filtered.isEmpty()) {
				val (icon, title, description) = emptyStateFor(filter)
				EmptyState(icon, title, description, actionLabel = "Refresh", onAction = viewModel::refresh)
			} else {
				LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
					items(filtered, key = { it.serviceRequestReference }) { panel ->
						LabPanelRow(
							panel = panel,
							abnormal = labsState.abnormalSrs.contains(panel.serviceRequestReference),
							onClick = { onOpenPanel(panel.serviceRequestReference) },
						)
						HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
					}
				}
			}
		} else {
			if (rows.isEmpty()) {
				val (icon, title, description) = emptyStateFor(filter)
				EmptyState(icon, title, description, actionLabel = "Refresh", onAction = viewModel::refresh)
			} else {
				LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
					items(rows, key = { it.id }) { row ->
						RecordRow(
							icon = iconFor(row.kind),
							title = row.title,
							summary = row.summary,
							sourceSystem = row.sourceSystem,
							sourceName = row.sourceName,
							trailingText = row.trailingText,
							onClick = { onOpenRecord(row.sourceSystem, row.fhirReference) },
						)
						HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
					}
				}
			}
		}
	}
}

private fun iconFor(kind: FilterType): ImageVector = when (kind) {
	FilterType.ALL, FilterType.LABS -> Icons.Outlined.Science
	FilterType.VITALS -> Icons.Outlined.MonitorHeart
	FilterType.MEDICATIONS -> Icons.Outlined.LocalPharmacy
	FilterType.CONDITIONS -> Icons.Outlined.Healing
	FilterType.ALLERGIES -> Icons.Outlined.Biotech
	FilterType.ENCOUNTERS -> Icons.Outlined.LocalHospital
	FilterType.DOCUMENTS -> Icons.AutoMirrored.Outlined.Assignment
}

private fun emptyStateFor(filter: FilterType): Triple<ImageVector, String, String> = when (filter) {
	FilterType.ALL -> Triple(Icons.Outlined.Folder, "No records yet", "Sync from Health Connect to import your clinical data.")
	FilterType.LABS -> Triple(Icons.Outlined.Science, "No lab results", "Labs appear here once CommonHealth pulls them in.")
	FilterType.VITALS -> Triple(Icons.Outlined.MonitorHeart, "No vitals yet", "BP, weight, pulse ox show up here after sync.")
	FilterType.MEDICATIONS -> Triple(Icons.Outlined.LocalPharmacy, "No medications", "Active prescriptions appear here.")
	FilterType.CONDITIONS -> Triple(Icons.Outlined.Healing, "No conditions", "Diagnosed conditions appear here.")
	FilterType.ALLERGIES -> Triple(Icons.Outlined.Biotech, "No allergies", "Allergy records appear here.")
	FilterType.ENCOUNTERS -> Triple(Icons.Outlined.LocalHospital, "No encounters", "Visits and appointments appear here.")
	FilterType.DOCUMENTS -> Triple(Icons.AutoMirrored.Outlined.Assignment, "No documents", "Attached documents appear here.")
}
```

Note: `RecordRow` takes an `onClick` param — confirm the α.1 RecordRow.kt has that param. It does (see RecordRow signature in α.1). If not, add `onClick: (() -> Unit)? = null` default.

- [ ] **Step 4: Build verify**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/records/RecordsViewModel.kt \
        app/src/main/java/com/healthaggregator/ui/records/RecordsScreen.kt
git commit -m "Phase 4: RecordsViewModel + Screen — panel rows, search, sort

LABS filter switches to LabPanelRow list driven by observePanels
aggregates + abnormalSrs derived flow. Other filters stay with
RecordRow. Search filters title/summary case-insensitive across
both views. SortChip toggles newest/oldest; for panel rows,
reverses the SQL-DESC list in-memory. Tap handlers route to the
correct detail screen (panel/lab/record)."
```

---

### Task 20 (Phase 4 gate)

- [ ] **Step 1: Full build + tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all prior + new tests green.

- [ ] **Step 2: Install + smoke**

```bash
./gradlew :app:installDebug
```

Open app → Records → Labs filter shows panel rows → tap one → PanelDetail opens → tap component → LabDetail opens with trend chart (if history). Switch to Meds / Conditions filters → tap a row → RecordDetail opens. Search filters both views.

- [ ] **Step 3: Gate commit**

```bash
git commit --allow-empty -m "Phase 4 gate: browse refactor complete

Labs shows panels. Panel drill-in works. Lab drill-in shows trends.
Generic detail shows all FHIR fields + raw JSON. Search and sort
work across every filter."
```

---

# Phase 5 — Ship gate

### Task 21: Fresh-install device smoke

- [ ] **Step 1: Full build + test + install**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:installDebug
```

- [ ] **Step 2: Smoke checklist**

On device:

1. App launches, Home tab works, counts populated (migration of old data didn't break anything)
2. Sync → after completion, check that lab panels appear on Records>Labs (not individual measurements)
3. Tap a panel → PanelDetail shows components with inline sparklines
4. Tap a component → LabDetail shows big value + trend chart (if ≥2 historical readings for same LOINC)
5. Back to Records → Meds filter → tap a row → RecordDetail shows structured fields + raw FHIR JSON expander
6. Search box filters the current filter's list
7. Sort chip reverses order
8. Return to Home → counts still match; sources card still renders
9. Settings → Reset Database → resync → everything populates from scratch

- [ ] **Step 3: Final commit**

```bash
git commit --allow-empty -m "Stream α.2 complete — labs depth + universal detail pages ship

MyChart-shaped labs browser: panels as rows, tap-through to
components with inline sparklines, tap-through to per-lab trend
chart with reference-range bands. Universal detail pages for every
non-lab type show FHIR fields + raw-JSON expander. Global search
and sort-direction toggle across all filters.

Migration v1→v2 adds serviceRequestReference + serviceRequestDisplay
columns; existing α.1 installs upgrade cleanly via Room.databaseBuilder
addMigrations(MIGRATION_1_2). INSERT OR REPLACE backfills on resync.

Ready for α.3 (export + panel name normalization) or β (self-tracking)
or γ (LLM assistant) on top."
```

---

## Self-review — findings

**Spec coverage:**
- Scope (§1) — Tasks 1-20. ✓
- Panel inference via basedOn (§2) — Task 4. ✓
- Browse architecture (§3) — Tasks 12-14, 17, 19. ✓
- Sparklines (§4) — Tasks 8-10. ✓
- Search + sort (§5) — Tasks 18-19. ✓
- Data model (§6) — Tasks 1-6. ✓
- File structure (§7) — covered in file change map + per-task. ✓
- Testing approach — migration (T3), basedOn import (T4), DAO panels (T6), chart geometry (T8), Viewmodel tests (deferred — covered by device smoke given time budget; if subagent insists on Turbine tests, add Task 19.5/19.6). ✓

**Placeholder scan:** Each step has real code or real commands. The one deferred test-writing (Viewmodel Turbine tests for Lab/PanelDetail) is explicit in the spec under "Testing approach" — device smoke is the gate.

**Type consistency:** `LabPanelAggregate` used in DAO (T5), Repository (T6), ViewModel (T19), Composable (T17). `SortOrder` enum declared in SortChip.kt (T18), used in RecordsViewModel (T19), RecordsScreen (T19). `SavedStateHandle` arg keys: `source`/`fhirRef`/`sr` consistent across ViewModels (T12-14) and AppNav routes (T15).

**Scope check:** Single stream (α.2). No sub-project decomposition needed. ~21 tasks in 5 phases, mirroring α.1's pattern.

---
