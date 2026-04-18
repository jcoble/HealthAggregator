# HealthAggregator Android

Walking-skeleton Android app — reads clinical FHIR records from Google Health
Connect (populated by CommonHealth from Cleveland Clinic + Summa Health) and
surfaces them in a three-tab Compose UI.

## Prereqs

- Android Studio Ladybug or newer
- Android 16 (API 36) device (Health Connect's Personal Health Record API
  requires 16 stable)
- CommonHealth app installed on the device, with at least one MyChart-backed
  provider already added and records pulled

## Build

Open `healthaggregator-android/` in Android Studio. Let it regenerate the
Gradle wrapper + sync. Then:

```bash
./gradlew :app:assembleDebug
```

## Install

1. Enable USB debugging on your Android 16 device (Settings → Developer options)
2. Connect via USB
3. `./gradlew :app:installDebug`

Or click Run in Android Studio with the device selected.

## First-run walk-through

1. Launch — Home tab shows "Grant Health Connect access" empty state
2. Tap Grant → system permission dialog → grant all 12 → return
3. Tap Sync → watch counts populate
4. Verify counts match what you see in the CommonHealth app

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

## Architecture

- **Kotlin 2.0** + **Jetpack Compose** + **Material 3** (dark-only)
- **Hilt** for DI, **Room** for persistence (KSP compiler)
- **Health Connect** 1.1.0-beta02 (Personal Health Record API)
- **kotlinx-serialization-json** for FHIR parsing
- Single `MainActivity` hosts a Compose `NavHost` with 3 top-level tabs: Home, Records, Settings
- `HealthConnectReader` → `FhirImportService` → Room DAOs, orchestrated by `SyncManager`
- `RecordsRepository` + `SyncRepository` isolate ViewModels from DAOs

## Source attribution

Every record carries a `sourceSystem` slug (`cleveland-clinic`, `summa-health`, `manual-upload`, etc.) rendered as a colored `SourceBadge` pill. Colors are locked for known orgs (Cleveland Clinic = red, Summa Health = blue) with a hash-stable fallback palette for unknown sources.
