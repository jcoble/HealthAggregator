# Stream α.1 — Android Foundation (Walking Skeleton) — Design Spec

**Status:** draft, ready for plan · **Date:** 2026-04-18 · **Parent decomposition:** Android pivot — α foundation · β self-tracking · γ LLM assistant · δ polish

---

## Why this matters

The web-app pivot to native Android was triggered by a concrete breakthrough: **CommonHealth on Android 16 successfully pulls clinical FHIR records from both Cleveland Clinic AND Summa Health into Google Health Connect** — bypassing Epic's per-org production-app approval process that would otherwise take weeks-to-months per provider. Every clinical record the user needs (labs, meds, conditions, allergies, encounters, documents, vitals) is already on their phone, structured as FHIR R4. A native Android app that reads from Health Connect gives direct access with no cloud, no server, no Epic bureaucracy.

The expanded vision driving the overall Android pivot is phone-first, diagnostic-focused: passive Epic records + active self-tracking (BP, glucose, symptoms) + LLM-driven cross-correlation for unresolved health questions. This spec (**α.1**) lands the foundation — Android app scaffolding, Health Connect data flow, Room persistence, navigation shell, basic record-browsing UI. β (self-tracking), γ (LLM assistant), δ (polish) build on top.

## Problem

The existing web app (.NET Web API + SvelteKit, on branch `feat/ui-foundation`, merged to `main` as prior art) was architected for a desktop-first user and Epic-direct API integration. Both assumptions are now wrong: the user is phone-primary, and Epic-direct is obsoleted by the CommonHealth / Health Connect path. Continuing to iterate on the web stack adds desktop-dependent, bridge-required, Epic-approval-gated friction to every feature going forward.

α.1's concrete goal: a **walking-skeleton Android app** that reads every clinical FHIR record out of Health Connect, mirrors it into a local Room database tagged per source (Cleveland Clinic, Summa Health), and surfaces it in a three-tab Compose UI with source attribution throughout. No detail pages, no trends, no filters beyond a record-type picker, no charts. The design goal is to *prove the data flow and establish the architecture that β / γ / δ extend*.

## Scope summary

**In scope (α.1)**

- Kotlin 2.0 + Jetpack Compose + Material 3, targeting Android 16 (API 36)
- Room database (primary persistence) with 12 entities total — 10 mirrored 1:1 from the web app's EF schema, plus one new `VitalsObservation` (structured vital-signs), plus one new `MedicalDataSource` (Android-specific: maps Health Connect source ids to our stable source slugs)
- Hilt for DI, Coroutines + Flow for async
- Health Connect client reading MedicalResources across all 11 `READ_MEDICAL_DATA_*` permission categories
- `FhirImportService` Kotlin port (from .NET) walking FHIR bundles, dispatching per `resourceType` to typed upserts
- Foreground-only sync triggers (app-cold-start if >1h stale, pull-to-refresh, explicit "Sync now" button). No background sync — that's δ.
- Three-tab bottom navigation: Home / Records / Settings
- `SourceBadge` composable rendering stable-colored source attribution on every record row and sources-list row
- Dark-only Material 3 theme mirroring the web app's token palette
- Shared component roster: `StatCard`, `EmptyState`, `LoadingState`, `ErrorBanner`, `RecordRow`, `SyncStatusChip`, `FilterChipRow`
- `@Preview` composables for every screen (empty / loading / populated states) and every shared component
- Unit tests for pure Kotlin utilities, `FhirImportService`, DAOs (in-memory Room), repositories; ViewModel state-transition tests via Turbine

**Out of scope, deferred by later stream**

- **α.2 (next Android spec):** per-record detail pages, sparkline trend charts, text search, date-range filters, richer filter UI
- **β (self-tracking):** daily measurement entry (BP / glucose / weight / HR / pulse-ox / temp), symptom journal (ontology + free-text), widgets, trend views over self-tracked data
- **γ (LLM assistant):** chat UI, Claude API or Gemini Nano tool-use over Room, cross-source correlations, saved analyses / personal notes
- **δ (polish):** WorkManager background sync, biometric lock, notifications, calendar integration
- No light-theme / Material You dynamic color
- No Epic OAuth — the web app's `EpicFhirClient`, `SmartConfigurationClient`, Epic-OAuth tables are not ported. CommonHealth handles OAuth upstream.
- No companion iOS / web build — if desktop access matters later, the existing web app on `main` serves as prior art and could be revived as a read-only viewer against a shared DB.

## Design

### Architecture & stack

- **Kotlin 2.0** + **Jetpack Compose** + **Material 3** + **Android 16 (API 36) minimum**. Android 16 is required for the stable `ExperimentalPersonalHealthRecordApi` surface in Health Connect — required for clinical-FHIR reads.
- **Room** as primary persistence (SQLite). Mirrors Health Connect data into typed tables with `@Upsert` semantics keyed to `(sourceSystem, fhirReference)`.
- **Hilt** for DI. `@HiltAndroidApp Application`, `@AndroidEntryPoint MainActivity`, constructor injection into ViewModels.
- **Coroutines + Flow** for async; `Flow<List<T>>` from DAOs feeds Compose via `collectAsStateWithLifecycle`.
- **ViewModel per screen**, state as `StateFlow<UiState>` sealed interfaces.
- **Repository pattern** between ViewModels and DAOs — two repositories: `RecordsRepository` (read) and `SyncRepository` (write).
- **`@Preview` discipline:** every screen + component has `@Preview` siblings for side-panel iteration in Android Studio.
- **Single-activity architecture.** One `MainActivity` hosts Compose + NavHost. No fragments, no legacy activity lifecycle.

**Directory layout:**

```
healthaggregator-android/
├── settings.gradle.kts                        (existing from bridge scaffold)
├── build.gradle.kts                           (existing)
├── gradle.properties                          (existing)
├── gradle/libs.versions.toml                  (extend — add Hilt, Room, JUnit5, Turbine)
└── app/
    ├── build.gradle.kts                       (extend)
    ├── proguard-rules.pro                     (existing)
    └── src/main/
        ├── AndroidManifest.xml                (existing — verify Health Connect perms)
        ├── java/com/healthaggregator/          ← renamed from .bridge
        │   ├── App.kt                         @HiltAndroidApp Application
        │   ├── MainActivity.kt                Compose root + NavHost
        │   ├── ui/
        │   │   ├── theme/
        │   │   │   ├── Color.kt               dark palette constants
        │   │   │   ├── Theme.kt               HealthAggregatorTheme + ExtendedColors
        │   │   │   └── Type.kt                Typography
        │   │   ├── navigation/
        │   │   │   ├── AppNav.kt              NavHost + routes
        │   │   │   └── BottomNav.kt           NavigationBar composable
        │   │   ├── home/
        │   │   │   ├── HomeScreen.kt
        │   │   │   └── HomeViewModel.kt
        │   │   ├── records/
        │   │   │   ├── RecordsScreen.kt
        │   │   │   └── RecordsViewModel.kt
        │   │   ├── settings/
        │   │   │   ├── SettingsScreen.kt
        │   │   │   └── SettingsViewModel.kt
        │   │   └── components/
        │   │       ├── SourceBadge.kt
        │   │       ├── StatCard.kt
        │   │       ├── RecordRow.kt
        │   │       ├── EmptyState.kt
        │   │       ├── LoadingState.kt
        │   │       ├── ErrorBanner.kt
        │   │       ├── SyncStatusChip.kt
        │   │       └── FilterChipRow.kt
        │   ├── data/
        │   │   ├── AppDatabase.kt             @Database, version 1
        │   │   ├── Converters.kt              Instant / enum TypeConverters
        │   │   ├── entities/                  11 @Entity classes
        │   │   ├── dao/                       11 @Dao interfaces
        │   │   ├── repository/
        │   │   │   ├── RecordsRepository.kt
        │   │   │   └── SyncRepository.kt
        │   │   ├── SourceTheme.kt             sourceColor() — Kotlin port
        │   │   └── AbnormalLabPredicate.kt    isAbnormal() — Kotlin port
        │   ├── sync/
        │   │   ├── HealthConnectReader.kt
        │   │   ├── FhirImportService.kt       core algorithm port from .NET
        │   │   └── SyncManager.kt             orchestrates HC → FhirImport → Room
        │   └── di/
        │       └── AppModule.kt               Hilt @Module @Provides for singletons
        └── res/
            ├── values/
            │   ├── strings.xml                (existing)
            │   └── themes.xml                 (existing)
            └── xml/
                └── data_extraction_rules.xml  (existing)
```

**Discarded from existing bridge scaffold:**
- `Prefs.kt`, `HealthConnectReader.kt` (old), `UploadClient.kt` — all bridge-era code, replaced with the new package structure above.

### Room schema

Entities in `com.healthaggregator.data.entities`. Start at database version 1. Field mappings from .NET EF:

- `int Id` → `@PrimaryKey(autoGenerate = true) val id: Long = 0L`
- `DateTimeOffset?` → `Instant?` (via `Converters` on `java.time.Instant`)
- `decimal?` → `Double?` (precision loss vs EF negligible for display purposes)
- `string` → `String`, `string?` → `String?`

| Entity | Unique index | Extra indexes | Mirrored from EF? |
|---|---|---|---|
| `PatientRecord` | `(sourceSystem, fhirId)` | — | yes |
| `LabObservation` | `(sourceSystem, fhirReference)` | `(loincCode, effectiveAt)` | yes |
| `DiagnosticReportRecord` | `(sourceSystem, fhirReference)` | — | yes |
| `ConditionRecord` | `(sourceSystem, fhirReference)` | — | yes |
| `MedicationRecord` | `(sourceSystem, fhirReference)` | — | yes |
| `AllergyRecord` | `(sourceSystem, fhirReference)` | — | yes |
| `EncounterRecord` | `(sourceSystem, fhirReference)` | — | yes |
| `DocumentRecord` | `(sourceSystem, fhirReference)` | — | yes |
| **`VitalsObservation`** | `(sourceSystem, fhirReference, componentCode)` | `(loincCode, effectiveAt)` | new |
| `SourceRecord` | `(sourceSystem, resourceType, resourceId)` | — | yes (raw FHIR JSON backup) |
| `SyncJob` | none | — | yes (audit row per sync run) |
| **`MedicalDataSource`** | `(healthConnectSourceId)` | — | new (Android-specific mapping) |

**`VitalsObservation` shape** — simpler than FHIR's polymorphism: each FHIR `Observation` with `category=vital-signs` that has a `component` array produces one `VitalsObservation` row *per component* (so BP's `systolic` and `diastolic` become two rows sharing a parent `fhirReference` but with distinct `componentCode` and `value`). Columns: `id`, `sourceSystem`, `sourceName`, `fhirReference`, `resourceId`, `patientFhirId?`, `loincCode?`, `code`, `displayName`, `numericValue?`, `unit?`, `componentCode?`, `effectiveAt?`, `importedAt`.

**`MedicalDataSource` shape** — `id`, `healthConnectSourceId` (unique), `sourceSystem` (our stable slug), `displayName`, `colorOverride?`, `firstSeenAt`, `lastSeenAt`, `recordCount`.

**DAOs:** one `@Dao` interface per entity. CRUD + `observeAll(): Flow<List<T>>` + `observeBySource(source): Flow<List<T>>` + relevant date-range variants. Upserts use Room's `@Upsert` against the unique index.

**Repositories:**
- `RecordsRepository` — read-only query surface exposed to ViewModels. Returns `Flow<List<T>>` so UI observes live updates on sync.
- `SyncRepository` — owns the upsert logic (`FhirImportService`) and orchestrates sync runs. Only write path.

### Sync architecture

End-to-end flow on app start (if >1h stale) + pull-to-refresh + manual "Sync now":

```
Health Connect (populated by CommonHealth)
    │ readMedicalResources per (dataSourceId, resourceType)
    v
HealthConnectReader
    │ List<FhirResource> per MedicalDataSource
    v
FhirImportService
    For each (source, fhirResource) within one Room @Transaction per source:
      1. Upsert SourceRecord (raw JSON backup, by (sourceSystem, resourceType, resourceId))
      2. Dispatch on resource.resourceType:
           Patient          → PatientDao.upsert
           Observation      → if category=laboratory  → LabDao.upsert
                            → if category=vital-signs → VitalsDao.upsert (one per component)
                            → else                     → SourceRecord only
           DiagnosticReport → ReportsDao.upsert
           Condition        → ConditionsDao.upsert
           MedicationRequest/
           MedicationStatement → MedicationsDao.upsert
           AllergyIntolerance → AllergiesDao.upsert
           Encounter        → EncountersDao.upsert
           DocumentReference → DocumentsDao.upsert
    v
Room DB (all entities populated, tagged sourceSystem)
    v
Flow<List<T>> emissions → Compose UIs recompose
```

**Permission model.** Health Connect is all-or-nothing per permission. `HealthConnectReader` holds the 11-permission set. First launch: Home `EmptyState` with "Grant Health Connect access" button → system perm dialog → returns with grant/deny. Per-permission denial reports back which is missing + re-prompt.

**MedicalDataSource slug generation.** First time a HC source id is seen: slugify the `displayName` (lowercase, spaces → dashes, strip non-alphanumeric) and upsert a `MedicalDataSource` row. User can rename in Settings later. Examples: `"Cleveland Clinic"` → `cleveland-clinic`; `"Summa Health"` → `summa-health`. The slug feeds `SourceTheme.sourceColor()` for stable per-source colors across restarts.

**Upsert semantics.** Room's `@Upsert` uses the entity's unique index to replace existing rows. Re-running sync is idempotent — no duplicates.

**Sync trigger matrix:**

| Trigger | Behavior |
|---|---|
| App cold start | Check `SyncJob` table; if last sync > 1h or none, run sync. Otherwise skip. |
| Pull-to-refresh on Home or Records | Full sync. |
| "Sync now" button in Settings | Full sync. |
| App resume (foreground) | No automatic sync — avoids cost spikes. User controls via refresh. |
| Background | Not in α.1. WorkManager is δ. |

**Error handling.** Sync errors log into the `SyncJob.error` column + surface a toast. Partial progress is preserved (resources already upserted stay). Next sync retries from scratch; idempotent upsert means no duplication.

**Abnormal predicate.** Same semantics as web app — `Interpretation` starts with `H`/`L`/`A`/`HH`/`LL` (case-insensitive) OR `numericValue < referenceLow` OR `numericValue > referenceHigh`. Lives in `data/AbnormalLabPredicate.kt`, called from UI (Row composables decide badge rendering), not stored.

### Navigation & screens

Material 3 `NavigationBar` at bottom with 3 destinations. Single-activity Compose `NavHost`.

**Home tab** (`ui/home/HomeScreen.kt`)

- `TopAppBar("Overview")` with refresh icon action.
- `SyncStatusChip` — "Synced 5m ago" or "Never synced"; button triggers sync.
- Stats grid (`StatCard` × 7, 2 columns): Labs · Vitals · Medications · Conditions · Allergies · Encounters · Documents. Each shows count + "from N sources" subtitle. Tap → Records tab with that type pre-selected.
- Sources list card — row per `MedicalDataSource` with `SourceBadge` + display name + record count + last-updated relative time.
- Empty state (no HC permission): centered `EmptyState` with "Grant Health Connect access" + button launching permission flow.
- Empty state (permissions granted, no sources): `EmptyState` "No Health Connect sources found. Open CommonHealth to add a provider, then pull to refresh" + deep-link button.

**Records tab** (`ui/records/RecordsScreen.kt`)

- `TopAppBar("Records")`.
- `FilterChipRow` at top: All · Labs · Vitals · Meds · Conditions · Allergies · Encounters · Documents. Single-select. State persists across tab switches via ViewModel.
- `LazyColumn` of `RecordRow` composables matching the filter, sorted newest-first by entity primary date.
- Pull-to-refresh gesture triggers sync.
- Row tap is a no-op in α.1 (detail pages are α.2). Long-press copies raw FHIR JSON to clipboard for debugging.
- Empty state per filter: "No labs yet" / "No labs match your filter" via `getEmptyStateProps()`-style branching.

**Settings tab** (`ui/settings/SettingsScreen.kt`)

- `TopAppBar("Settings")`.
- Health Connect section: status row (Available / Not installed / Needs permissions), "Re-request permissions" button, "Open Health Connect" deep-link button.
- Sources section: list of `MedicalDataSource` rows with edit dialog (rename display + change source slug → changes source color).
- Data section: total record count, DB file size, "Sync now" button, "Reset local database" destructive button behind `ConfirmDialog` equivalent.
- About section: version name + code, repo URL placeholder, license.

**Navigation graph:**

```kotlin
NavHost(navController, startDestination = "home") {
    composable("home") { HomeScreen(...) }
    composable("records?type={type}", arguments = listOf(navArgument("type") { defaultValue = "all" })) { RecordsScreen(...) }
    composable("settings") { SettingsScreen(...) }
}
```

Stat card taps navigate `records?type=labs` etc.

### Theme, source attribution, shared components

**Theme.** Dark-only Material 3. `HealthAggregatorTheme` wraps `MaterialTheme` with a fixed `ColorScheme` + a `CompositionLocal ExtendedColors` providing `success` / `warning` since M3 doesn't standardize them. Token mapping:

| M3 token | Value |
|---|---|
| `background` | `#09090B` |
| `surface` | `#141416` |
| `surfaceContainer` | `#1A1A1D` |
| `outline` | `#27272A` |
| `primary` | `#3B82F6` |
| `onPrimary` | `#EFF6FF` |
| `error` | `#EF4444` |
| `secondary` | `#A1A1AA` |
| (extended) `success` | `#22C55E` |
| (extended) `warning` | `#F59E0B` |

**Typography.** Material 3 default (Roboto Flex). Optional Inter swap later if cross-app parity matters.

**`SourceTheme.kt` — ported from web `source-theme.ts`:**

```kotlin
enum class SourceColor(val container: Color, val label: Color) {
    RED(…), BLUE(…), GREY(…), AMBER(…),
    PURPLE(…), TEAL(…), PINK(…), ORANGE(…), CYAN(…), INDIGO(…)
}

private val LOCKED = mapOf(
    "cleveland-clinic" to SourceColor.RED,
    "summa-health"     to SourceColor.BLUE,
    "manual-upload"    to SourceColor.AMBER,
    "epic-sandbox"     to SourceColor.GREY,
)
private val FALLBACK = listOf(
    SourceColor.PURPLE, SourceColor.TEAL, SourceColor.PINK,
    SourceColor.ORANGE, SourceColor.CYAN, SourceColor.INDIGO,
)

fun sourceColor(sourceSystem: String): SourceColor =
    LOCKED[sourceSystem] ?: FALLBACK[sourceSystem.sumOf { it.code } % FALLBACK.size]
```

**`SourceBadge` composable** — wraps Material 3 `AssistChip` with `assistChipColors(containerColor = color.container, labelColor = color.label)`.

**Shared component roster** (all under `ui/components/`, all with `@Preview` siblings):

| Composable | Parameters |
|---|---|
| `SourceBadge` | `sourceSystem: String, sourceName: String? = null, modifier: Modifier = Modifier` |
| `StatCard` | `icon, label, value, subtitle?, onClick?, modifier` |
| `EmptyState` | `icon?, title, description, actionLabel?, onAction?, modifier` |
| `LoadingState` | `message: String = "Loading…", modifier` |
| `ErrorBanner` | `variant: Variant, title, content?, onDismiss?, modifier` |
| `RecordRow` | `icon, title, summary, sourceSystem, sourceName, trailingText?, onClick? = null` |
| `SyncStatusChip` | `lastSyncAt: Instant?, isSyncing: Boolean, onRefresh: () -> Unit` |
| `FilterChipRow<T>` | `options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String, icon: (T) -> ImageVector?` |

### `@Preview` discipline

Every screen composable and every shared component has at least one `@Preview` function. Screens have multiple previews covering **empty** / **loading** / **populated** states. All previews wrap content in `HealthAggregatorTheme` with sample data. Android Studio's side panel renders these — no build/run cycle required for UI iteration.

## Testing

### Unit tests (JUnit 5 + kotlin.test)

| File | What's covered |
|---|---|
| `SourceThemeTest.kt` | Locked-org mapping, hash-stable fallback, whitespace handling |
| `AbnormalLabPredicateTest.kt` | `Normal` interpretation does NOT count, `H`/`L`/`HH`/`LL`/`A` prefixes DO (case-insensitive), out-of-range numeric both above and below |
| `FhirImportServiceTest.kt` | Happy path: Patient/Observation-lab/Observation-vital-sign/DiagnosticReport/Condition/MedicationRequest/MedicationStatement/AllergyIntolerance/Encounter/DocumentReference each route to correct upsert. Vital-sign `Observation` with BP `component` array → two `VitalsObservation` rows. Unknown `resourceType` → SourceRecord only. Duplicate calls are idempotent (re-run produces same row count). |
| `SyncManagerTest.kt` | Integration: mocked `HealthConnectReader` → real in-memory Room → assert expected row counts per table, `SyncJob` audit row written, error path populates `SyncJob.error`. |

### DAO tests (in-memory Room)

One test class per DAO. Covers upsert-by-unique-index, ordering, `observeBySource` filtering, date-range queries. Room in-memory builder spins up and tears down per test.

### ViewModel tests (Turbine for Flow assertions)

- `HomeViewModelTest` — no-perm empty state → granted → populated; sync trigger transitions `isSyncing` flag; stat counts emit from mocked repository flows.
- `RecordsViewModelTest` — filter state persists across recomposition; type change re-emits filtered list from repository.
- `SettingsViewModelTest` — edit-source dialog state, reset-database confirm flow.

### UI tests — deferred to α.2

Instrumented Compose UI tests are brittle at walking-skeleton stage. Shift to α.2 when screens have settled.

### Manual device smoke (gate for "α.1 shipped")

Run on the user's actual Android 16 phone (emulator lacks the CommonHealth-populated Health Connect data):

1. Fresh install (wipe app data if needed).
2. Open app → Home shows "Grant Health Connect access" empty state.
3. Tap grant → system perm dialog → grant all 11 → returns to Home.
4. Tap "Sync now" → `SyncStatusChip` shows spinner → completes.
5. Home's stat cards populated with record counts. Sources list shows Cleveland Clinic + Summa Health rows.
6. Record counts match what shows inside CommonHealth for both orgs.
7. Records tab: filter chips work, each type's list populates, rows display date / summary / source badge.
8. Settings tab: HC status is Available, permissions are Granted, sources are listed, rename dialog works (source color updates after rename), "Sync now" triggers another sync.
9. App restart → sync does NOT auto-run (< 1h); manual refresh still works.
10. After 1+ hour app-not-opened → open app → sync auto-runs.

## Implementation order

**Phase 1 — Foundation reset** (~½ day). Scrap bridge-era Kotlin, rename package, add deps (Hilt, Room, JUnit 5, Turbine), add `@HiltAndroidApp`, `HealthAggregatorTheme`, `MainActivity` with empty three-tab `NavigationBar`. Build + launch verified.

**Phase 2 — Data layer** (~1-1.5 days). 12 `@Entity`, 12 `@Dao`, `AppDatabase`, `Converters`, Hilt provider for singleton DB. DAO unit tests against in-memory Room.

**Phase 3 — Sync pipeline** (~2 days). `HealthConnectReader`, `FhirImportService`, `SyncManager`, `MedicalDataSource` auto-mapping, permission flow. Unit tests for `FhirImportService` with canned FHIR fixtures; integration test for `SyncManager` with mocked reader + real in-memory Room.

**Phase 4 — Screens wired** (~2 days). Home / Records / Settings screens + ViewModels + shared components, all with `@Preview` siblings. Pull-to-refresh, stat-card navigation to Records-filtered, source-edit dialog.

**Phase 5 — Polish + device smoke** (~½ day). Every screen/component has `@Preview`, manual device smoke against user's Android 16 phone, README install instructions.

Total: ~5-7 focused days.

## Files changed summary

### New files

- `healthaggregator-android/app/src/main/java/com/healthaggregator/` — entire package tree per directory layout above (~50 Kotlin source files for code + tests)
- `healthaggregator-android/README.md` — install + device-smoke instructions
- `healthaggregator-android/app/src/test/java/com/healthaggregator/` — test tree (DAOs, FhirImportService, utilities, ViewModels)

### Modified files

- `healthaggregator-android/settings.gradle.kts` — unchanged from scaffold
- `healthaggregator-android/build.gradle.kts` — add Hilt + KSP plugin aliases
- `healthaggregator-android/gradle/libs.versions.toml` — add Hilt, Hilt-Navigation-Compose, Room (runtime + ktx + compiler), KSP, JUnit 5, Mockk, Turbine, Compose Navigation
- `healthaggregator-android/app/build.gradle.kts` — apply Hilt + KSP plugins, add dependencies
- `healthaggregator-android/app/src/main/AndroidManifest.xml` — replace placeholder `.MainActivity` reference after package rename (package is `com.healthaggregator`)

### Deleted files

- `healthaggregator-android/app/src/main/java/com/healthaggregator/bridge/Prefs.kt`
- `healthaggregator-android/app/src/main/java/com/healthaggregator/bridge/HealthConnectReader.kt` (bridge version — new one goes under `com.healthaggregator.sync`)
- `healthaggregator-android/app/src/main/java/com/healthaggregator/bridge/UploadClient.kt`

### Cross-repo notes

- Web app on `main` (directories `HealthAggregator.Api/`, `HealthAggregator.Core/`, `HealthAggregator.Data/`, `HealthAggregator.Tests/`, `healthaggregator-web/`) **stays untouched** by α.1. It remains on `main` as prior art + reference. If it's ever genuinely unneeded, delete in a separate commit — not under this spec.
- `Docs/Research/2026-04-14-gemini-health-data-platform-research.md` — still the canonical research backdrop.
- `Docs/specs/2026-04-14-ui-foundation-design.md` — prior spec (web app), not modified by α.1.
- `Docs/epic-app-approval-playbook.md` / `epic-app-brief.md` / `epic-followup-cadence.md` — no longer needed (CommonHealth obsoletes the Epic per-org approval path) but kept in-repo as reference. Delete at convenience.
- `CLAUDE.md` — will need an update post-α.1 to reflect stack pivot (Kotlin + Compose + Room replaces .NET + SvelteKit as the *active* stack). Deferred to a housekeeping commit after α.1 ships; out of α.1's own scope.
