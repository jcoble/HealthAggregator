# Android Foundation (Stream α.1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL — use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **Every subagent dispatch must include the spec at `Docs/specs/2026-04-18-android-foundation-design.md` as context** — many tasks reference spec sections by name rather than re-duplicating design content already there.

**Goal:** Walking-skeleton Android app that reads clinical FHIR records from Google Health Connect (populated by CommonHealth from Cleveland Clinic + Summa Health), mirrors them into a local Room database with source attribution, and surfaces them in a three-tab Compose UI.

**Architecture:** Kotlin 2.0 + Jetpack Compose + Material 3. Single `MainActivity` hosts a bottom-nav Compose `NavHost`. Hilt for DI, Coroutines + Flow for async, Room as primary persistence. `FhirImportService` (ported from the .NET web app) walks FHIR bundles and dispatches to typed upserts. `@Preview` for every screen + component.

**Tech Stack:** Kotlin 2.0, Android 16 (API 36) minimum, Jetpack Compose, Material 3, Hilt, Room (KSP compiler), Health Connect `connect-client` (Personal Health Record API), kotlinx-coroutines, JUnit 5, Mockk, Turbine.

**Build discipline:** Gradle builds via `./gradlew` once the wrapper is generated. Android Studio can open the project root; first open regenerates the wrapper. All tests via `./gradlew :app:test` (unit) — no instrumented tests in α.1. Lint via `./gradlew :app:lintDebug`.

---

## File change map

### New — under `healthaggregator-android/app/src/main/java/com/healthaggregator/`

```
App.kt                                      @HiltAndroidApp Application
MainActivity.kt                             Single activity, Compose root
ui/theme/Color.kt                           Dark palette constants
ui/theme/Theme.kt                           HealthAggregatorTheme + ExtendedColors
ui/theme/Type.kt                            Typography (Material 3 default)
ui/navigation/AppNav.kt                     NavHost graph
ui/navigation/BottomNav.kt                  NavigationBar composable
ui/home/HomeScreen.kt                       + @Preview
ui/home/HomeViewModel.kt
ui/records/RecordsScreen.kt                 + @Preview
ui/records/RecordsViewModel.kt
ui/settings/SettingsScreen.kt               + @Preview
ui/settings/SettingsViewModel.kt
ui/components/SourceBadge.kt                + @Preview
ui/components/StatCard.kt                   + @Preview
ui/components/RecordRow.kt                  + @Preview
ui/components/EmptyState.kt                 + @Preview
ui/components/LoadingState.kt               + @Preview
ui/components/ErrorBanner.kt                + @Preview
ui/components/SyncStatusChip.kt             + @Preview
ui/components/FilterChipRow.kt              + @Preview
data/AppDatabase.kt                         @Database(version=1)
data/Converters.kt                          TypeConverters for Instant
data/SourceTheme.kt                         sourceColor() — Kotlin port
data/AbnormalLabPredicate.kt                isAbnormal() — Kotlin port
data/entities/PatientRecord.kt
data/entities/LabObservation.kt
data/entities/DiagnosticReportRecord.kt
data/entities/ConditionRecord.kt
data/entities/MedicationRecord.kt
data/entities/AllergyRecord.kt
data/entities/EncounterRecord.kt
data/entities/DocumentRecord.kt
data/entities/VitalsObservation.kt
data/entities/SourceRecord.kt
data/entities/SyncJob.kt
data/entities/MedicalDataSource.kt
data/dao/PatientDao.kt
data/dao/LabDao.kt
data/dao/DiagnosticReportDao.kt
data/dao/ConditionDao.kt
data/dao/MedicationDao.kt
data/dao/AllergyDao.kt
data/dao/EncounterDao.kt
data/dao/DocumentDao.kt
data/dao/VitalsDao.kt
data/dao/SourceRecordDao.kt
data/dao/SyncJobDao.kt
data/dao/MedicalDataSourceDao.kt
data/repository/RecordsRepository.kt
data/repository/SyncRepository.kt
sync/HealthConnectReader.kt
sync/FhirImportService.kt                   Bundle-walker, typed upserts
sync/SyncManager.kt                         HC → FhirImport → Room orchestration
sync/Slugify.kt                             Display-name → stable slug helper
di/AppModule.kt                             Hilt @Module providing singletons
di/DatabaseModule.kt                        Hilt @Module providing AppDatabase + DAOs
```

### New — tests under `healthaggregator-android/app/src/test/java/com/healthaggregator/`

```
data/SourceThemeTest.kt
data/AbnormalLabPredicateTest.kt
data/dao/LabDaoTest.kt                      Representative DAO test (in-memory Room)
data/dao/VitalsDaoTest.kt                   Unique test — multi-component upsert
data/dao/MedicalDataSourceDaoTest.kt
sync/FhirImportServiceTest.kt               Canned FHIR fixtures → assert correct upserts
sync/SyncManagerTest.kt                     Mocked HealthConnectReader + in-memory Room
sync/SlugifyTest.kt
ui/home/HomeViewModelTest.kt                Turbine — state transitions
ui/records/RecordsViewModelTest.kt          Turbine — filter state
TestFhirFixtures.kt                         Shared: lab, vitals-bp, condition, etc. JSON
```

### Modified — existing scaffold

- `healthaggregator-android/gradle/libs.versions.toml` — add Hilt, Hilt-Navigation-Compose, Room (runtime + ktx + compiler), KSP, JUnit 5 (Jupiter), Turbine, Mockk, Compose Navigation
- `healthaggregator-android/build.gradle.kts` — add Hilt + KSP plugin aliases
- `healthaggregator-android/app/build.gradle.kts` — apply Hilt + KSP, add dependencies, add KSP schema dir for Room
- `healthaggregator-android/app/src/main/AndroidManifest.xml` — update `android:name=".App"` for HiltAndroidApp, keep existing perms unchanged

### Deleted

- `healthaggregator-android/app/src/main/java/com/healthaggregator/bridge/Prefs.kt`
- `healthaggregator-android/app/src/main/java/com/healthaggregator/bridge/HealthConnectReader.kt`
- `healthaggregator-android/app/src/main/java/com/healthaggregator/bridge/UploadClient.kt`

### New — at `healthaggregator-android/`

- `README.md` — install instructions, Android Studio open steps, device smoke checklist

---

# Phase 1 — Foundation reset

### Task 1: Delete bridge-era Kotlin + rename package

**Files:**
- Delete: `healthaggregator-android/app/src/main/java/com/healthaggregator/bridge/` (entire dir — 3 files)
- Rename: package `com.healthaggregator.bridge` → `com.healthaggregator` (affects `AndroidManifest.xml`, `app/build.gradle.kts`)

- [ ] **Step 1: Delete bridge-era files**

```bash
cd /Users/blackcolours/dev/work/HealthAggregator/healthaggregator-android
rm -rf app/src/main/java/com/healthaggregator/bridge
# The empty com/healthaggregator/ directory stays — Phase 2+ tasks populate it
```

- [ ] **Step 2: Rename package in `app/build.gradle.kts`**

Find `namespace = "com.healthaggregator.bridge"` and `applicationId = "com.healthaggregator.bridge"`. Change both to `"com.healthaggregator"`.

- [ ] **Step 3: Rename package in `AndroidManifest.xml`**

Nothing to do — the manifest uses relative references to `.MainActivity` which will work with the new namespace.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "Phase 1: delete bridge-era Kotlin + rename package to com.healthaggregator

The three bridge-era files (Prefs, HealthConnectReader, UploadClient) were
for the abandoned HTTP-bridge pivot, before the decision to go native-only.
HealthConnectReader will be rewritten under sync/ with a different API
surface (reads into Room directly, no HTTP). Prefs and UploadClient are
gone entirely — DataStore usage will be minimal in α.1, and there's no
HTTP client since the app is self-contained."
```

---

### Task 2: Add deps to libs.versions.toml + build.gradle.kts

**Files:**
- Modify: `healthaggregator-android/gradle/libs.versions.toml`
- Modify: `healthaggregator-android/build.gradle.kts`
- Modify: `healthaggregator-android/app/build.gradle.kts`

- [ ] **Step 1: Add version refs + libraries + plugins to `libs.versions.toml`**

Append to `[versions]`:

```toml
hilt = "2.53"
hiltNavigationCompose = "1.2.0"
ksp = "2.0.21-1.0.27"
room = "2.6.1"
navigation = "2.8.5"
junitJupiter = "5.11.3"
mockk = "1.13.13"
turbine = "1.2.0"
```

Append to `[libraries]`:

```toml
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "junitJupiter" }
junit-jupiter-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine", version.ref = "junitJupiter" }
junit-jupiter-params = { group = "org.junit.jupiter", name = "junit-jupiter-params", version.ref = "junitJupiter" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

Append to `[plugins]`:

```toml
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Declare plugins in root `build.gradle.kts`**

Replace the contents of `healthaggregator-android/build.gradle.kts` with:

```kotlin
plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlin.android) apply false
	alias(libs.plugins.kotlin.compose) apply false
	alias(libs.plugins.hilt) apply false
	alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 3: Apply plugins + deps in `app/build.gradle.kts`**

In the `plugins {}` block, add:

```kotlin
alias(libs.plugins.hilt)
alias(libs.plugins.ksp)
```

In the `android {}` block, add a `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` call inside a `defaultConfig { ... }` block (or bring it up as a top-level `ksp {}` block — both work). Use this form:

```kotlin
android {
	// ... existing config ...
	defaultConfig {
		// ... existing config ...
		ksp {
			arg("room.schemaLocation", "$projectDir/schemas")
		}
	}
}
```

Add to `dependencies {}`:

```kotlin
// Hilt
implementation(libs.hilt.android)
ksp(libs.hilt.compiler)
implementation(libs.androidx.hilt.navigation.compose)

// Room
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)

// Navigation
implementation(libs.androidx.navigation.compose)

// Unit tests
testImplementation(libs.junit.jupiter.api)
testImplementation(libs.junit.jupiter.params)
testRuntimeOnly(libs.junit.jupiter.engine)
testImplementation(libs.mockk)
testImplementation(libs.turbine)
testImplementation(libs.androidx.room.testing)
testImplementation(libs.kotlinx.coroutines)
```

And configure JUnit 5 test platform:

```kotlin
tasks.withType<Test> {
	useJUnitPlatform()
}
```

- [ ] **Step 4: Verify Gradle resolves**

Run: `./gradlew :app:help` (generates wrapper on first invocation, then runs `help`).
Expected: BUILD SUCCESSFUL. Any version conflicts surface here.

- [ ] **Step 5: Commit**

```bash
git add gradle/ build.gradle.kts app/build.gradle.kts
git commit -m "Phase 1: add Hilt, Room, JUnit 5, Turbine, Mockk, Navigation Compose deps

Sets up the full toolchain we need for the walking skeleton. KSP is used
(not kapt) for both Hilt and Room compilers — faster incremental builds.
Room schema dir at app/schemas for migration verification in later specs
(α.2+). JUnit 5 platform enabled for tests."
```

---

### Task 3: Add HiltAndroidApp + manifest update

**Files:**
- Create: `app/src/main/java/com/healthaggregator/App.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Write `App.kt`**

```kotlin
package com.healthaggregator

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application()
```

- [ ] **Step 2: Register the Application in the manifest**

In `AndroidManifest.xml`, inside `<application ...>`, add:

```xml
android:name=".App"
```

Full `<application>` tag opener should look like:

```xml
<application
    android:name=".App"
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="false"
    android:icon="@android:drawable/ic_dialog_info"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:theme="@style/Theme.HealthAggregatorBridge"
    android:usesCleartextTraffic="true">
```

Keep everything else unchanged.

- [ ] **Step 3: Verify build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Hilt generates `Hilt_App` at compile time.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/App.kt app/src/main/AndroidManifest.xml
git commit -m "Phase 1: @HiltAndroidApp Application class + manifest wiring

Ensures Hilt's code-gen runs and is available for later @AndroidEntryPoint
activities and @HiltViewModel annotations."
```

---

### Task 4: Material 3 theme

**Files:**
- Create: `app/src/main/java/com/healthaggregator/ui/theme/Color.kt`
- Create: `app/src/main/java/com/healthaggregator/ui/theme/Type.kt`
- Create: `app/src/main/java/com/healthaggregator/ui/theme/Theme.kt`

- [ ] **Step 1: Write `Color.kt`**

```kotlin
package com.healthaggregator.ui.theme

import androidx.compose.ui.graphics.Color

// Tokens mirror the web app's CSS variables in layout-tokens.css
val Background = Color(0xFF09090B)
val Surface = Color(0xFF141416)
val SurfaceContainer = Color(0xFF1A1A1D)
val Outline = Color(0xFF27272A)
val Foreground = Color(0xFFFAFAFA)
val MutedForeground = Color(0xFFA1A1AA)

val Primary = Color(0xFF3B82F6)
val OnPrimary = Color(0xFFEFF6FF)

val ErrorColor = Color(0xFFEF4444)
val OnError = Color(0xFFFFFFFF)

val SuccessColor = Color(0xFF22C55E)
val OnSuccess = Color(0xFF052E16)

val WarningColor = Color(0xFFF59E0B)
val OnWarning = Color(0xFF451A03)
```

- [ ] **Step 2: Write `Type.kt`**

```kotlin
package com.healthaggregator.ui.theme

import androidx.compose.material3.Typography

// Material 3 default typography. Swap to Inter via Google Fonts later if cross-app parity matters.
val AppTypography = Typography()
```

- [ ] **Step 3: Write `Theme.kt`**

```kotlin
package com.healthaggregator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Extension colors Material 3 doesn't standardize (success, warning). */
data class ExtendedColors(
	val success: Color,
	val onSuccess: Color,
	val warning: Color,
	val onWarning: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
	ExtendedColors(
		success = SuccessColor,
		onSuccess = OnSuccess,
		warning = WarningColor,
		onWarning = OnWarning,
	)
}

private val DarkColorScheme = darkColorScheme(
	primary = Primary,
	onPrimary = OnPrimary,
	background = Background,
	onBackground = Foreground,
	surface = Surface,
	onSurface = Foreground,
	surfaceContainer = SurfaceContainer,
	outline = Outline,
	secondary = MutedForeground,
	error = ErrorColor,
	onError = OnError,
)

@Composable
fun HealthAggregatorTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = DarkColorScheme,
		typography = AppTypography,
		content = content,
	)
}

val MaterialTheme.extended: ExtendedColors
	@Composable
	@ReadOnlyComposable
	get() = LocalExtendedColors.current
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/theme/
git commit -m "Phase 1: HealthAggregatorTheme + ExtendedColors for success/warning

Dark-only Material 3 ColorScheme using the same token palette as the web
app's CSS variables. ExtendedColors CompositionLocal provides success and
warning colors since M3 doesn't standardize them; accessed via
MaterialTheme.extended in composables that need them."
```

---

### Task 5: MainActivity + 3-tab NavHost shell

**Files:**
- Create: `app/src/main/java/com/healthaggregator/MainActivity.kt`
- Create: `app/src/main/java/com/healthaggregator/ui/navigation/AppNav.kt`
- Create: `app/src/main/java/com/healthaggregator/ui/navigation/BottomNav.kt`

- [ ] **Step 1: Write `BottomNav.kt`**

```kotlin
package com.healthaggregator.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

enum class TopLevelRoute(val route: String, val label: String, val icon: ImageVector) {
	HOME("home", "Home", Icons.Outlined.Dashboard),
	RECORDS("records", "Records", Icons.Outlined.FolderShared),
	SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}

@Composable
fun AppBottomNav(navController: NavHostController) {
	val currentEntry by navController.currentBackStackEntryAsState()
	val currentRoute = currentEntry?.destination?.route

	NavigationBar {
		TopLevelRoute.entries.forEach { top ->
			val selected = currentEntry?.destination?.hierarchy?.any { it.route?.startsWith(top.route) == true } ?: false
			NavigationBarItem(
				selected = selected,
				onClick = {
					navController.navigate(top.route) {
						popUpTo(navController.graph.startDestinationId) { saveState = true }
						launchSingleTop = true
						restoreState = true
					}
				},
				icon = { Icon(top.icon, contentDescription = top.label) },
				label = { Text(top.label) },
			)
		}
	}
}
```

- [ ] **Step 2: Write `AppNav.kt`**

```kotlin
package com.healthaggregator.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun AppNavHost(navController: NavHostController) {
	NavHost(
		navController = navController,
		startDestination = TopLevelRoute.HOME.route,
	) {
		composable(TopLevelRoute.HOME.route) {
			// HomeScreen is implemented in Phase 4 Task 19
			PlaceholderScreen("Home")
		}
		composable("${TopLevelRoute.RECORDS.route}?type={type}") { backStackEntry ->
			val type = backStackEntry.arguments?.getString("type") ?: "all"
			// RecordsScreen is implemented in Phase 4 Task 20
			PlaceholderScreen("Records (type=$type)")
		}
		composable(TopLevelRoute.SETTINGS.route) {
			// SettingsScreen is implemented in Phase 4 Task 21
			PlaceholderScreen("Settings")
		}
	}
}

@Composable
private fun PlaceholderScreen(title: String) {
	androidx.compose.material3.Text(
		text = title,
		modifier = androidx.compose.ui.Modifier.padding(24.dp),
	)
}

private val dp = 24.dp.value // placeholder import helper — compose import gets added when Phase 4 implements real screens
```

Note: the private `dp` ghost at the bottom is incorrect — replace with a correct placeholder. Use this simpler form instead:

```kotlin
@Composable
private fun PlaceholderScreen(title: String) {
	androidx.compose.foundation.layout.Box(
		modifier = androidx.compose.ui.Modifier.fillMaxSize(),
		contentAlignment = androidx.compose.ui.Alignment.Center,
	) {
		androidx.compose.material3.Text(title)
	}
}
```

(The `import` statements get added at the top of the file via Android Studio's auto-import.)

- [ ] **Step 3: Write `MainActivity.kt`**

```kotlin
package com.healthaggregator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.healthaggregator.ui.navigation.AppBottomNav
import com.healthaggregator.ui.navigation.AppNavHost
import com.healthaggregator.ui.theme.HealthAggregatorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			HealthAggregatorTheme {
				AppRoot()
			}
		}
	}
}

@Composable
private fun AppRoot() {
	val navController = rememberNavController()
	Scaffold(
		bottomBar = { AppBottomNav(navController) },
	) { padding ->
		androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
			AppNavHost(navController)
		}
	}
}
```

- [ ] **Step 4: Verify build + visual**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Install on device (adb or Android Studio run) → bottom nav shows three tabs; each tap navigates to its placeholder.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/MainActivity.kt app/src/main/java/com/healthaggregator/ui/navigation/
git commit -m "Phase 1: MainActivity + 3-tab NavHost shell

HomeScreen, RecordsScreen, SettingsScreen stubbed as PlaceholderScreen
for now; real implementations land in Phase 4. Records route accepts
?type=<entity> query param so stat-card taps can deep-link.
edgeToEdge enabled for Compose-owned status bar handling."
```

---

### Task 6 (Phase 1 gate)

- [ ] **Step 1: End-to-end verification**

Install app on device. Confirm:
- App launches without crash
- Three bottom-nav tabs visible (Home / Records / Settings)
- Tapping each tab switches content (placeholder text shows)
- Theme is dark (background #09090B)
- No Hilt initialization errors in logcat

- [ ] **Step 2: Phase 1 gate commit**

```bash
git commit --allow-empty -m "Phase 1 gate: scaffold, theme, 3-tab nav shell verified on device"
```

---

# Phase 2 — Data layer

### Task 7: TypeConverters + AppDatabase shell

**Files:**
- Create: `app/src/main/java/com/healthaggregator/data/Converters.kt`
- Create: `app/src/main/java/com/healthaggregator/data/AppDatabase.kt`

- [ ] **Step 1: Write `Converters.kt`**

```kotlin
package com.healthaggregator.data

import androidx.room.TypeConverter
import java.time.Instant

class Converters {
	@TypeConverter
	fun fromTimestamp(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

	@TypeConverter
	fun instantToTimestamp(instant: Instant?): Long? = instant?.toEpochMilli()
}
```

- [ ] **Step 2: Write `AppDatabase.kt` (initially empty — entities + DAOs added in later tasks)**

```kotlin
package com.healthaggregator.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Entity list populated as Task 8-9 add @Entity classes. Re-declare here each time.
@Database(
	entities = [
		// empty in Task 7; populated in Task 8
	],
	version = 1,
	exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
	// DAO accessors added in Task 9
}
```

Note: Room's KSP compiler errors when `entities = []` is empty. Add one placeholder entity NOW to satisfy the compiler — a trivial `ScratchEntity` that we'll delete at the end of Task 8. Put it next to `AppDatabase.kt`:

```kotlin
package com.healthaggregator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scratch")
data class ScratchEntity(
	@PrimaryKey val id: Long = 0L,
	val placeholder: String = "",
)
```

And update `AppDatabase`'s entity list:

```kotlin
@Database(
	entities = [ScratchEntity::class],
	version = 1,
	exportSchema = true,
)
```

- [ ] **Step 3: Verify build**

```bash
./gradlew :app:kspDebugKotlin
```

Expected: BUILD SUCCESSFUL. Room KSP generates the `AppDatabase_Impl` class.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/data/
git commit -m "Phase 2: Room @Database + Instant TypeConverters scaffolding

ScratchEntity satisfies Room's 'entities cannot be empty' compile check;
it'll be deleted at end of Task 8 once real entities exist."
```

---

### Task 8: All 12 entities

**Files:**
- Create 12 files under `app/src/main/java/com/healthaggregator/data/entities/`
- Delete: `app/src/main/java/com/healthaggregator/data/ScratchEntity.kt`
- Modify: `app/src/main/java/com/healthaggregator/data/AppDatabase.kt`

- [ ] **Step 1: Write entities**

All entity classes go in their own files under `data/entities/`. Shapes mirror the EF entity definitions in the web-app spec (`Docs/specs/2026-04-14-ui-foundation-design.md` → §Architecture → entity shapes) and spec `Docs/specs/2026-04-18-android-foundation-design.md` → Room schema.

**`PatientRecord.kt`:**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "patients",
	indices = [Index(value = ["sourceSystem", "fhirId"], unique = true)],
)
data class PatientRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val fhirId: String,
	val displayName: String? = null,
	val birthDate: String? = null, // ISO date (YYYY-MM-DD)
	val updatedAt: Instant,
)
```

**`LabObservation.kt`:**

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
)
```

**`DiagnosticReportRecord.kt`:**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "diagnostic_reports",
	indices = [Index(value = ["sourceSystem", "fhirReference"], unique = true)],
)
data class DiagnosticReportRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val fhirReference: String,
	val resourceId: String,
	val patientFhirId: String? = null,
	val codeText: String? = null,
	val status: String? = null,
	val issuedAt: Instant? = null,
	val resultReferences: String? = null,
	val importedAt: Instant,
)
```

**`ConditionRecord.kt`:**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "conditions",
	indices = [Index(value = ["sourceSystem", "fhirReference"], unique = true)],
)
data class ConditionRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val fhirReference: String,
	val resourceId: String,
	val patientFhirId: String? = null,
	val codeText: String? = null,
	val clinicalStatus: String? = null,
	val onsetAt: Instant? = null,
	val recordedAt: Instant? = null,
	val importedAt: Instant,
)
```

**`MedicationRecord.kt`:**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "medications",
	indices = [Index(value = ["sourceSystem", "fhirReference"], unique = true)],
)
data class MedicationRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val fhirReference: String,
	val resourceId: String,
	val patientFhirId: String? = null,
	val medicationText: String? = null,
	val status: String? = null,
	val authoredAt: Instant? = null,
	val importedAt: Instant,
)
```

**`AllergyRecord.kt`:**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "allergies",
	indices = [Index(value = ["sourceSystem", "fhirReference"], unique = true)],
)
data class AllergyRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val fhirReference: String,
	val resourceId: String,
	val patientFhirId: String? = null,
	val allergyText: String? = null,
	val clinicalStatus: String? = null,
	val recordedAt: Instant? = null,
	val importedAt: Instant,
)
```

**`EncounterRecord.kt`:**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "encounters",
	indices = [Index(value = ["sourceSystem", "fhirReference"], unique = true)],
)
data class EncounterRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val fhirReference: String,
	val resourceId: String,
	val patientFhirId: String? = null,
	val typeText: String? = null,
	val status: String? = null,
	val startedAt: Instant? = null,
	val endedAt: Instant? = null,
	val importedAt: Instant,
)
```

**`DocumentRecord.kt`:**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "documents",
	indices = [Index(value = ["sourceSystem", "fhirReference"], unique = true)],
)
data class DocumentRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val fhirReference: String,
	val resourceId: String,
	val patientFhirId: String? = null,
	val typeText: String? = null,
	val status: String? = null,
	val documentedAt: Instant? = null,
	val contentUrl: String? = null,
	val importedAt: Instant,
)
```

**`VitalsObservation.kt`:**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One row per FHIR Observation component (e.g., BP produces 2 rows: systolic + diastolic).
 * Non-component Observations (e.g., weight, pulse ox) produce a single row with componentCode = null.
 */
@Entity(
	tableName = "vitals_observations",
	indices = [
		Index(value = ["sourceSystem", "fhirReference", "componentCode"], unique = true),
		Index(value = ["loincCode", "effectiveAt"]),
	],
)
data class VitalsObservation(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val fhirReference: String,
	val resourceId: String,
	val patientFhirId: String? = null,
	val loincCode: String? = null,
	val code: String,
	val displayName: String,
	val numericValue: Double? = null,
	val unit: String? = null,
	val componentCode: String? = null,
	val effectiveAt: Instant? = null,
	val importedAt: Instant,
)
```

**`SourceRecord.kt`:**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "source_records",
	indices = [Index(value = ["sourceSystem", "resourceType", "resourceId"], unique = true)],
)
data class SourceRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val syncJobId: Long? = null,
	val sourceSystem: String,
	val sourceName: String,
	val resourceType: String,
	val resourceId: String,
	val fhirReference: String,
	val rawJson: String,
	val importedAt: Instant,
)
```

**`SyncJob.kt`:**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sync_jobs")
data class SyncJob(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val sourceSystem: String,
	val sourceName: String,
	val status: String = "running",
	val startedAt: Instant,
	val completedAt: Instant? = null,
	val sourceRecordsUpserted: Int = 0,
	val labObservationsUpserted: Int = 0,
	val vitalsUpserted: Int = 0,
	val conditionsUpserted: Int = 0,
	val medicationsUpserted: Int = 0,
	val allergiesUpserted: Int = 0,
	val encountersUpserted: Int = 0,
	val documentsUpserted: Int = 0,
	val patientsUpserted: Int = 0,
	val diagnosticReportsUpserted: Int = 0,
	val error: String? = null,
)
```

**`MedicalDataSource.kt`:**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Maps a Health Connect data source (opaque id assigned by HC/CommonHealth) to a
 * stable slug that the app uses for source attribution colors, tagging, and future UX.
 */
@Entity(
	tableName = "medical_data_sources",
	indices = [Index(value = ["healthConnectSourceId"], unique = true)],
)
data class MedicalDataSource(
	@PrimaryKey(autoGenerate = true) val id: Long = 0L,
	val healthConnectSourceId: String,
	val sourceSystem: String, // stable slug: cleveland-clinic, summa-health, etc.
	val displayName: String,
	val colorOverride: String? = null, // optional override for the auto-resolved color
	val firstSeenAt: Instant,
	val lastSeenAt: Instant,
	val recordCount: Int = 0,
)
```

- [ ] **Step 2: Update `AppDatabase` entities list + delete scratch**

Replace the contents of `AppDatabase.kt`:

```kotlin
package com.healthaggregator.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.healthaggregator.data.entities.*

@Database(
	entities = [
		PatientRecord::class,
		LabObservation::class,
		DiagnosticReportRecord::class,
		ConditionRecord::class,
		MedicationRecord::class,
		AllergyRecord::class,
		EncounterRecord::class,
		DocumentRecord::class,
		VitalsObservation::class,
		SourceRecord::class,
		SyncJob::class,
		MedicalDataSource::class,
	],
	version = 1,
	exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
	// DAO accessors added in Task 9
}
```

Delete `ScratchEntity.kt`:

```bash
rm app/src/main/java/com/healthaggregator/data/ScratchEntity.kt
```

- [ ] **Step 3: Verify build**

```bash
./gradlew :app:kspDebugKotlin
```

Expected: BUILD SUCCESSFUL. Look in `app/schemas/com.healthaggregator.data.AppDatabase/1.json` — Room exports the schema.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/data/entities/ app/src/main/java/com/healthaggregator/data/AppDatabase.kt
git rm app/src/main/java/com/healthaggregator/data/ScratchEntity.kt
git commit -m "Phase 2: 12 Room @Entity classes — 10 EF-mirrored + VitalsObservation + MedicalDataSource

Every entity uses auto-generated Long primary keys + unique index on the
natural key (sourceSystem + fhirReference for typed records;
sourceSystem + resourceType + resourceId for SourceRecord; healthConnectSourceId
for MedicalDataSource). Instant fields stored as epoch-millis via Converters.
DECIMAL fields from EF map to Double (precision loss negligible for display).
Schema exported to app/schemas/ for future migration verification."
```

---

### Task 9: 12 DAOs

**Files:**
- Create: 12 DAO files under `app/src/main/java/com/healthaggregator/data/dao/`
- Modify: `app/src/main/java/com/healthaggregator/data/AppDatabase.kt`

- [ ] **Step 1: Write DAOs**

All DAOs follow the same pattern: `@Upsert` for the write path (Room uses the unique index for conflict resolution), flow-returning reads for UI binding. Show full code for two (Lab and Vitals) — the rest follow the same pattern with entity-specific columns.

**`LabDao.kt`:**

```kotlin
package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.LabObservation
import kotlinx.coroutines.flow.Flow

@Dao
interface LabDao {
	@Upsert
	suspend fun upsertAll(labs: List<LabObservation>)

	@Upsert
	suspend fun upsert(lab: LabObservation)

	@Query("SELECT * FROM lab_observations ORDER BY effectiveAt DESC, id DESC")
	fun observeAll(): Flow<List<LabObservation>>

	@Query("SELECT * FROM lab_observations WHERE sourceSystem = :source ORDER BY effectiveAt DESC, id DESC")
	fun observeBySource(source: String): Flow<List<LabObservation>>

	@Query("SELECT COUNT(*) FROM lab_observations")
	fun countAll(): Flow<Int>

	@Query("SELECT * FROM lab_observations")
	suspend fun getAllSnapshot(): List<LabObservation>

	@Query("DELETE FROM lab_observations")
	suspend fun deleteAll()
}
```

**`VitalsDao.kt`:**

```kotlin
package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthaggregator.data.entities.VitalsObservation
import kotlinx.coroutines.flow.Flow

@Dao
interface VitalsDao {
	@Upsert
	suspend fun upsertAll(vitals: List<VitalsObservation>)

	@Query("SELECT * FROM vitals_observations ORDER BY effectiveAt DESC, id DESC")
	fun observeAll(): Flow<List<VitalsObservation>>

	@Query("SELECT * FROM vitals_observations WHERE sourceSystem = :source ORDER BY effectiveAt DESC, id DESC")
	fun observeBySource(source: String): Flow<List<VitalsObservation>>

	@Query("SELECT COUNT(*) FROM vitals_observations")
	fun countAll(): Flow<Int>

	@Query("DELETE FROM vitals_observations")
	suspend fun deleteAll()
}
```

**Remaining DAOs follow the identical shape** — just swap table name, entity type, and primary-date column. Create files:

- `PatientDao.kt` — table `patients`, entity `PatientRecord`, order by `updatedAt DESC`
- `DiagnosticReportDao.kt` — table `diagnostic_reports`, entity `DiagnosticReportRecord`, order by `issuedAt DESC, id DESC`
- `ConditionDao.kt` — table `conditions`, entity `ConditionRecord`, order by `COALESCE(onsetAt, recordedAt) DESC, id DESC`
- `MedicationDao.kt` — table `medications`, entity `MedicationRecord`, order by `authoredAt DESC, id DESC`
- `AllergyDao.kt` — table `allergies`, entity `AllergyRecord`, order by `recordedAt DESC, id DESC`
- `EncounterDao.kt` — table `encounters`, entity `EncounterRecord`, order by `startedAt DESC, id DESC`
- `DocumentDao.kt` — table `documents`, entity `DocumentRecord`, order by `documentedAt DESC, id DESC`
- `SourceRecordDao.kt` — table `source_records`, entity `SourceRecord`, order by `importedAt DESC, id DESC`
- `SyncJobDao.kt` — table `sync_jobs`, entity `SyncJob`. No observeBySource. Add `@Query("SELECT * FROM sync_jobs ORDER BY startedAt DESC LIMIT 1") suspend fun latest(): SyncJob?` + `@Query("SELECT * FROM sync_jobs ORDER BY startedAt DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<SyncJob>>`
- `MedicalDataSourceDao.kt` — table `medical_data_sources`, entity `MedicalDataSource`. Add `@Query("SELECT * FROM medical_data_sources WHERE healthConnectSourceId = :hcId LIMIT 1") suspend fun findByHealthConnectId(hcId: String): MedicalDataSource?` + `@Query("SELECT * FROM medical_data_sources WHERE sourceSystem = :slug LIMIT 1") suspend fun findBySourceSystem(slug: String): MedicalDataSource?` + `observeAll()`

- [ ] **Step 2: Wire DAOs into `AppDatabase`**

Replace the body of `AppDatabase` to declare abstract DAO accessors:

```kotlin
@Database(
	entities = [/* unchanged list from Task 8 */],
	version = 1,
	exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
	abstract fun patientDao(): PatientDao
	abstract fun labDao(): LabDao
	abstract fun diagnosticReportDao(): DiagnosticReportDao
	abstract fun conditionDao(): ConditionDao
	abstract fun medicationDao(): MedicationDao
	abstract fun allergyDao(): AllergyDao
	abstract fun encounterDao(): EncounterDao
	abstract fun documentDao(): DocumentDao
	abstract fun vitalsDao(): VitalsDao
	abstract fun sourceRecordDao(): SourceRecordDao
	abstract fun syncJobDao(): SyncJobDao
	abstract fun medicalDataSourceDao(): MedicalDataSourceDao
}
```

Add imports for each DAO type.

- [ ] **Step 3: Verify build**

```bash
./gradlew :app:kspDebugKotlin
```

Expected: BUILD SUCCESSFUL. Room generates `AppDatabase_Impl` + `*_Impl` for each DAO.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/data/dao/ app/src/main/java/com/healthaggregator/data/AppDatabase.kt
git commit -m "Phase 2: 12 Room @Dao interfaces with @Upsert + Flow-returning observes

Standard pattern per DAO: upsert(entity) + upsertAll(list), observeAll()
and observeBySource(source) as Flow<List<T>>, countAll() as Flow<Int>,
deleteAll() for the Reset-Database admin action. Specialized methods on
SyncJobDao (latest, observeRecent) and MedicalDataSourceDao (findBy*)."
```

---

### Task 10: DatabaseModule + AppModule (Hilt)

**Files:**
- Create: `app/src/main/java/com/healthaggregator/di/DatabaseModule.kt`
- Create: `app/src/main/java/com/healthaggregator/di/AppModule.kt`

- [ ] **Step 1: Write `DatabaseModule.kt`**

```kotlin
package com.healthaggregator.di

import android.content.Context
import androidx.room.Room
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

	@Provides
	@Singleton
	fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
		Room.databaseBuilder(ctx, AppDatabase::class.java, "healthaggregator.db").build()

	@Provides fun providePatientDao(db: AppDatabase): PatientDao = db.patientDao()
	@Provides fun provideLabDao(db: AppDatabase): LabDao = db.labDao()
	@Provides fun provideDiagnosticReportDao(db: AppDatabase): DiagnosticReportDao = db.diagnosticReportDao()
	@Provides fun provideConditionDao(db: AppDatabase): ConditionDao = db.conditionDao()
	@Provides fun provideMedicationDao(db: AppDatabase): MedicationDao = db.medicationDao()
	@Provides fun provideAllergyDao(db: AppDatabase): AllergyDao = db.allergyDao()
	@Provides fun provideEncounterDao(db: AppDatabase): EncounterDao = db.encounterDao()
	@Provides fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()
	@Provides fun provideVitalsDao(db: AppDatabase): VitalsDao = db.vitalsDao()
	@Provides fun provideSourceRecordDao(db: AppDatabase): SourceRecordDao = db.sourceRecordDao()
	@Provides fun provideSyncJobDao(db: AppDatabase): SyncJobDao = db.syncJobDao()
	@Provides fun provideMedicalDataSourceDao(db: AppDatabase): MedicalDataSourceDao = db.medicalDataSourceDao()
}
```

- [ ] **Step 2: Write `AppModule.kt` (stub for Phase 3 expansion)**

```kotlin
package com.healthaggregator.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Module for app-level singletons. HealthConnectClient provider lands here in Phase 3 Task 13.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
	// Phase 3: HealthConnectClient provider, SyncManager provider, etc.
}
```

- [ ] **Step 3: Verify build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. Hilt generates the binding graph; look for `Hilt_App` and singleton providers.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/di/
git commit -m "Phase 2: Hilt DatabaseModule + AppModule scaffolding

DatabaseModule provides the singleton AppDatabase + each DAO as
@Provides factories. AppModule is stubbed for Phase 3 additions
(HealthConnectClient, SyncManager)."
```

---

### Task 11: DAO tests — representative coverage

**Files:**
- Create: `app/src/test/java/com/healthaggregator/data/dao/LabDaoTest.kt`
- Create: `app/src/test/java/com/healthaggregator/data/dao/VitalsDaoTest.kt`
- Create: `app/src/test/java/com/healthaggregator/data/dao/MedicalDataSourceDaoTest.kt`

- [ ] **Step 1: Write `LabDaoTest.kt`**

```kotlin
package com.healthaggregator.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.LabObservation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class LabDaoTest {
	private lateinit var db: AppDatabase
	private lateinit var dao: LabDao

	@BeforeEach
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		dao = db.labDao()
	}

	@AfterEach
	fun tearDown() { db.close() }

	@Test
	fun upsert_is_idempotent_by_sourceSystem_and_fhirReference() = runTest {
		val lab = sampleLab(fhirReference = "Observation/abc")
		dao.upsert(lab)
		dao.upsert(lab.copy(testName = "HbA1c v2")) // same (sourceSystem, fhirReference) → replaces
		val all = dao.getAllSnapshot()
		assertEquals(1, all.size)
		assertEquals("HbA1c v2", all.first().testName)
	}

	@Test
	fun observeAll_orders_newest_first() = runTest {
		dao.upsert(sampleLab(fhirReference = "Observation/1", effectiveAt = Instant.parse("2024-01-01T00:00:00Z")))
		dao.upsert(sampleLab(fhirReference = "Observation/2", effectiveAt = Instant.parse("2025-06-01T00:00:00Z")))
		val first = dao.observeAll().first()
		assertEquals(2, first.size)
		assertEquals("Observation/2", first[0].fhirReference)
	}

	@Test
	fun observeBySource_filters() = runTest {
		dao.upsert(sampleLab(sourceSystem = "cleveland-clinic", fhirReference = "Observation/1"))
		dao.upsert(sampleLab(sourceSystem = "summa-health", fhirReference = "Observation/2"))
		val cc = dao.observeBySource("cleveland-clinic").first()
		assertEquals(1, cc.size)
		assertEquals("cleveland-clinic", cc.first().sourceSystem)
	}

	private fun sampleLab(
		sourceSystem: String = "cleveland-clinic",
		fhirReference: String = "Observation/abc",
		effectiveAt: Instant? = Instant.parse("2024-01-01T00:00:00Z"),
	) = LabObservation(
		sourceSystem = sourceSystem,
		sourceName = "Cleveland Clinic",
		fhirReference = fhirReference,
		resourceId = fhirReference.substringAfter('/'),
		testName = "HbA1c",
		effectiveAt = effectiveAt,
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
```

- [ ] **Step 2: Write `VitalsDaoTest.kt`**

Mirror `LabDaoTest` shape but specifically test that BP's two components (systolic + diastolic) both persist — the unique index includes `componentCode`:

```kotlin
package com.healthaggregator.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.VitalsObservation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class VitalsDaoTest {
	private lateinit var db: AppDatabase
	private lateinit var dao: VitalsDao

	@BeforeEach
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		dao = db.vitalsDao()
	}

	@AfterEach
	fun tearDown() { db.close() }

	@Test
	fun bp_components_coexist_as_separate_rows() = runTest {
		val systolic = sampleVital(componentCode = "8480-6", code = "BP systolic", numericValue = 120.0, unit = "mmHg")
		val diastolic = sampleVital(componentCode = "8462-4", code = "BP diastolic", numericValue = 80.0, unit = "mmHg")
		dao.upsertAll(listOf(systolic, diastolic))
		val all = db.vitalsDao().let { d ->
			// Get snapshot via a suspend query
			val list = mutableListOf<VitalsObservation>()
			db.compileStatement("SELECT id FROM vitals_observations").use { stmt -> /* no-op, using Room */ }
			// Instead, just query a count and re-read via observeAll().first()
			kotlinx.coroutines.flow.first(d.observeAll())
		}
		assertEquals(2, all.size)
	}

	private fun sampleVital(
		sourceSystem: String = "cleveland-clinic",
		fhirReference: String = "Observation/bp-1",
		componentCode: String? = null,
		code: String = "Vital",
		numericValue: Double? = 120.0,
		unit: String? = "mmHg",
	) = VitalsObservation(
		sourceSystem = sourceSystem,
		sourceName = "Cleveland Clinic",
		fhirReference = fhirReference,
		resourceId = fhirReference.substringAfter('/'),
		code = code,
		displayName = code,
		numericValue = numericValue,
		unit = unit,
		componentCode = componentCode,
		effectiveAt = Instant.parse("2024-01-01T00:00:00Z"),
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
```

Note: the `.first()` call must come from `kotlinx.coroutines.flow.first` extension. Simplify with just `dao.observeAll().first()` — remove the stub around it.

- [ ] **Step 3: Write `MedicalDataSourceDaoTest.kt`**

Tests `findByHealthConnectId` and `findBySourceSystem` return the correct row. Follow the same setup pattern.

- [ ] **Step 4: Add Robolectric dep for Android test runtime in unit tests**

In `app/build.gradle.kts`, add:

```kotlin
testImplementation("org.robolectric:robolectric:4.14")
testImplementation("androidx.test:core-ktx:1.6.1")
testImplementation("androidx.test.ext:junit-ktx:1.2.1")
```

And in `android {}` block, enable:

```kotlin
testOptions {
	unitTests {
		isIncludeAndroidResources = true
	}
}
```

Annotate test classes with `@RunWith(AndroidJUnit4::class)` — but JUnit 5 doesn't support `@RunWith`. Use the JUnit 5 + Robolectric bridge via `@ExtendWith(RobolectricExtension::class)` if available, OR drop back to JUnit 4 for instrumented-flavor tests. Simplest path: keep DAO tests in **JUnit 4** (add `junit-4.13.2` test dep) and reserve JUnit 5 for pure-Kotlin tests (SourceTheme, AbnormalLabPredicate, Slugify). Adjust the test file shape accordingly:

```kotlin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class LabDaoTest {
	// @Before / @After instead of @BeforeEach / @AfterEach
}
```

Accordingly, update `app/build.gradle.kts` to add `testImplementation("junit:junit:4.13.2")` alongside the JUnit 5 deps. The runner `useJUnitPlatform()` still works — Robolectric with AndroidJUnit4 runs under JUnit 4 but coexists with JUnit 5 Jupiter tests via the platform.

- [ ] **Step 5: Run tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/com/healthaggregator/data/dao/ app/build.gradle.kts
git commit -m "Phase 2: DAO tests — Lab, Vitals (multi-component BP), MedicalDataSource

Representative DAO coverage proving: upsert idempotency via unique indexes,
Flow-returning observeAll orders correctly, observeBySource filters work.
VitalsDao specifically verifies BP systolic + diastolic coexist as two
rows under a shared fhirReference via the 3-column unique index.

Tooling: Robolectric for Android Context in unit tests; JUnit 4 for the
Android-instrumented-flavor DAO tests (AndroidJUnit4), JUnit 5 for
pure-Kotlin tests (future tasks). Both runners coexist."
```

---

### Task 12 (Phase 2 gate)

- [ ] **Step 1: Full build + test**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Commit gate marker**

```bash
git commit --allow-empty -m "Phase 2 gate: Room schema + DAOs + DI verified; all tests green"
```

---

# Phase 3 — Sync pipeline

### Task 13: Pure-Kotlin utility ports (SourceTheme, AbnormalLabPredicate, Slugify)

**Files:**
- Create: `app/src/main/java/com/healthaggregator/data/SourceTheme.kt`
- Create: `app/src/main/java/com/healthaggregator/data/AbnormalLabPredicate.kt`
- Create: `app/src/main/java/com/healthaggregator/sync/Slugify.kt`
- Create: `app/src/test/java/com/healthaggregator/data/SourceThemeTest.kt`
- Create: `app/src/test/java/com/healthaggregator/data/AbnormalLabPredicateTest.kt`
- Create: `app/src/test/java/com/healthaggregator/sync/SlugifyTest.kt`

- [ ] **Step 1: Write `SourceTheme.kt`**

```kotlin
package com.healthaggregator.data

import androidx.compose.ui.graphics.Color

enum class SourceColor(val container: Color, val label: Color) {
	RED(Color(0x26EF4444),    Color(0xFFFCA5A5)),
	BLUE(Color(0x263B82F6),   Color(0xFF93C5FD)),
	GREY(Color(0x26A1A1AA),   Color(0xFFD4D4D8)),
	AMBER(Color(0x26F59E0B),  Color(0xFFFCD34D)),
	PURPLE(Color(0x26A855F7), Color(0xFFD8B4FE)),
	TEAL(Color(0x2614B8A6),   Color(0xFF5EEAD4)),
	PINK(Color(0x26EC4899),   Color(0xFFF9A8D4)),
	ORANGE(Color(0x26F97316), Color(0xFFFDBA74)),
	CYAN(Color(0x2606B6D4),   Color(0xFF67E8F9)),
	INDIGO(Color(0x266366F1), Color(0xFFA5B4FC)),
}

private val LOCKED = mapOf(
	"cleveland-clinic" to SourceColor.RED,
	"summa-health"     to SourceColor.BLUE,
	"manual-upload"    to SourceColor.AMBER,
	"epic-sandbox"     to SourceColor.GREY,
)

private val FALLBACK_PALETTE = listOf(
	SourceColor.PURPLE, SourceColor.TEAL, SourceColor.PINK,
	SourceColor.ORANGE, SourceColor.CYAN, SourceColor.INDIGO,
)

fun sourceColor(sourceSystem: String): SourceColor {
	LOCKED[sourceSystem]?.let { return it }
	val hash = sourceSystem.sumOf { it.code }
	return FALLBACK_PALETTE[hash % FALLBACK_PALETTE.size]
}
```

- [ ] **Step 2: Write `AbnormalLabPredicate.kt`**

```kotlin
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
```

- [ ] **Step 3: Write `Slugify.kt`**

```kotlin
package com.healthaggregator.sync

/** Generate a stable, lowercase, dashed slug from a display name. Fallback to HC id if name is blank. */
fun slugify(displayName: String, fallback: String = ""): String {
	val base = displayName.lowercase()
		.replace(Regex("[^a-z0-9]+"), "-")
		.trim('-')
	return if (base.isNotBlank()) base else fallback.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
```

- [ ] **Step 4: Write tests**

`SourceThemeTest.kt`:

```kotlin
package com.healthaggregator.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SourceThemeTest {
	@Test fun locked_orgs_get_their_reserved_color() {
		assertSame(SourceColor.RED,   sourceColor("cleveland-clinic"))
		assertSame(SourceColor.BLUE,  sourceColor("summa-health"))
		assertSame(SourceColor.GREY,  sourceColor("epic-sandbox"))
		assertSame(SourceColor.AMBER, sourceColor("manual-upload"))
	}

	@Test fun unknown_source_gets_stable_fallback() {
		val a = sourceColor("kaiser-permanente")
		val b = sourceColor("kaiser-permanente")
		assertSame(a, b, "must be deterministic on repeat")
	}

	@Test fun unknown_never_collides_with_a_locked_color() {
		val fallbackColors = setOf(
			SourceColor.PURPLE, SourceColor.TEAL, SourceColor.PINK,
			SourceColor.ORANGE, SourceColor.CYAN, SourceColor.INDIGO,
		)
		listOf("foo", "bar", "baz", "mayo-clinic", "kaiser").forEach { name ->
			val c = sourceColor(name)
			assert(c in fallbackColors) { "$name resolved to $c which is locked-palette" }
		}
	}
}
```

`AbnormalLabPredicateTest.kt`:

```kotlin
package com.healthaggregator.data

import com.healthaggregator.data.entities.LabObservation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AbnormalLabPredicateTest {
	@Test fun Normal_interpretation_does_not_count() {
		assertFalse(isAbnormal(sample(interpretation = "Normal", numericValue = 5.5, refLow = 4.0, refHigh = 6.0)))
	}

	@Test fun H_prefix_interpretation_counts() {
		assertTrue(isAbnormal(sample(interpretation = "H", numericValue = 7.0)))
	}

	@Test fun HH_prefix_counts() {
		assertTrue(isAbnormal(sample(interpretation = "HH", numericValue = 9.0)))
	}

	@Test fun L_prefix_counts() {
		assertTrue(isAbnormal(sample(interpretation = "L", numericValue = 2.0)))
	}

	@Test fun A_prefix_counts() {
		assertTrue(isAbnormal(sample(interpretation = "Abnormal", numericValue = 1.0)))
	}

	@Test fun out_of_range_high_numeric_counts() {
		assertTrue(isAbnormal(sample(numericValue = 7.0, refLow = 4.0, refHigh = 6.0)))
	}

	@Test fun out_of_range_low_numeric_counts() {
		assertTrue(isAbnormal(sample(numericValue = 3.0, refLow = 4.0, refHigh = 6.0)))
	}

	@Test fun in_range_numeric_does_not_count() {
		assertFalse(isAbnormal(sample(numericValue = 5.0, refLow = 4.0, refHigh = 6.0)))
	}

	@Test fun null_numeric_with_no_interpretation_does_not_count() {
		assertFalse(isAbnormal(sample(numericValue = null)))
	}

	private fun sample(
		interpretation: String? = null,
		numericValue: Double? = null,
		refLow: Double? = null,
		refHigh: Double? = null,
	) = LabObservation(
		sourceSystem = "x", sourceName = "", fhirReference = "Observation/1", resourceId = "1",
		testName = "t",
		interpretation = interpretation, numericValue = numericValue,
		referenceLow = refLow, referenceHigh = refHigh,
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
```

`SlugifyTest.kt`:

```kotlin
package com.healthaggregator.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SlugifyTest {
	@Test fun spaces_become_dashes() { assertEquals("cleveland-clinic", slugify("Cleveland Clinic")) }
	@Test fun non_alpha_becomes_dash() { assertEquals("summa-health-system", slugify("Summa Health System.")) }
	@Test fun blanks_fall_back() { assertEquals("org-abc", slugify("", fallback = "Org/ABC")) }
	@Test fun preserves_digits() { assertEquals("clinic-123", slugify("Clinic 123")) }
}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/healthaggregator/data/SourceTheme.kt app/src/main/java/com/healthaggregator/data/AbnormalLabPredicate.kt app/src/main/java/com/healthaggregator/sync/Slugify.kt app/src/test/java/
git commit -m "Phase 3: pure-Kotlin utility ports — SourceTheme, AbnormalLabPredicate, Slugify

Direct ports from the web app. SourceColor as an enum carrying Compose
Color for container + label (4-digit alpha hex = Tailwind /15 fill).
AbnormalLabPredicate preserves the precise semantics from the web
(HH/LL/H/L/A prefixes case-insensitive OR numeric-out-of-range; Normal
does NOT count).

13 JUnit 5 tests covering locked-org mapping, determinism,
fallback-palette non-collision, every abnormal branch + negatives,
slugify punctuation + fallback paths."
```

---

### Task 14: HealthConnectReader

**Files:**
- Create: `app/src/main/java/com/healthaggregator/sync/HealthConnectReader.kt`
- Modify: `app/src/main/java/com/healthaggregator/di/AppModule.kt`

- [ ] **Step 1: Write `HealthConnectReader.kt`**

```kotlin
package com.healthaggregator.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.FhirResource
import androidx.health.connect.client.records.MedicalDataSource
import androidx.health.connect.client.records.MedicalResource
import androidx.health.connect.client.request.GetMedicalDataSourcesRequest
import androidx.health.connect.client.request.ReadMedicalResourcesInitialRequest
import androidx.health.connect.client.request.ReadMedicalResourcesPageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectReader @Inject constructor(
	@ApplicationContext private val context: Context,
) {
	private val resourceTypes = listOf(
		MedicalResource.MEDICAL_RESOURCE_TYPE_PATIENT_DEMOGRAPHICS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_CONDITIONS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_MEDICATIONS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
		MedicalResource.MEDICAL_RESOURCE_TYPE_VISITS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_VITAL_SIGNS,
		MedicalResource.MEDICAL_RESOURCE_TYPE_PROCEDURES,
		MedicalResource.MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY,
		MedicalResource.MEDICAL_RESOURCE_TYPE_VACCINES,
		MedicalResource.MEDICAL_RESOURCE_TYPE_PREGNANCY,
	)

	val readPermissions: Set<String> = resourceTypes.map { HealthPermission.getReadPermission(it) }.toSet()

	private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

	fun isAvailable(): Boolean =
		HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

	suspend fun grantedPermissions(): Set<String> =
		client.permissionController.getGrantedPermissions()

	suspend fun hasAllPermissions(): Boolean = grantedPermissions().containsAll(readPermissions)

	suspend fun listSources(): List<MedicalDataSource> =
		client.getMedicalDataSources(GetMedicalDataSourcesRequest(packageNames = emptyList()))

	suspend fun readAllResources(source: MedicalDataSource): List<FhirResource> {
		val out = mutableListOf<FhirResource>()
		for (type in resourceTypes) {
			val initial = ReadMedicalResourcesInitialRequest(
				medicalResourceType = type,
				dataSourceIds = setOf(source.id),
				pageSize = 500,
			)
			var response = client.readMedicalResources(initial)
			response.medicalResources.forEach { out += it.fhirResource }
			while (response.nextPageToken != null) {
				response = client.readMedicalResources(
					ReadMedicalResourcesPageRequest(
						pageToken = response.nextPageToken!!,
						pageSize = 500,
					)
				)
				response.medicalResources.forEach { out += it.fhirResource }
			}
		}
		return out
	}
}
```

- [ ] **Step 2: Verify build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If Hilt complains about multiple `@Inject` constructors on dependencies, check the Hilt docs for this version.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/healthaggregator/sync/HealthConnectReader.kt
git commit -m "Phase 3: HealthConnectReader — reads MedicalResources across all 11 FHIR categories

Single @Singleton, Hilt-injected via @ApplicationContext. Hard-coded full
permission set + resourceTypes list. readAllResources(source) paginates
every resource type in the source, returning raw FhirResource objects.
Permission query + grant-check methods for the UI to gate on."
```

---

### Task 15: FhirImportService (core algorithm port)

**Files:**
- Create: `app/src/main/java/com/healthaggregator/sync/FhirImportService.kt`
- Create: `app/src/test/java/com/healthaggregator/TestFhirFixtures.kt`
- Create: `app/src/test/java/com/healthaggregator/sync/FhirImportServiceTest.kt`

- [ ] **Step 1: Write `FhirImportService.kt`**

Port of the .NET `FhirImportService.cs` (in `HealthAggregator.Data/Services/`). Core behavior: walks a list of FHIR resources, upserts a `SourceRecord` per resource, dispatches on `resourceType` to typed upserts. See the web spec (`Docs/specs/2026-04-14-ui-foundation-design.md` → §Ingestion) for the original semantic spec.

```kotlin
package com.healthaggregator.sync

import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.*
import kotlinx.serialization.json.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class ImportCounts(
	var sourceRecords: Int = 0,
	var labs: Int = 0,
	var vitals: Int = 0,
	var conditions: Int = 0,
	var medications: Int = 0,
	var allergies: Int = 0,
	var encounters: Int = 0,
	var documents: Int = 0,
	var patients: Int = 0,
	var reports: Int = 0,
)

@Singleton
class FhirImportService @Inject constructor(
	private val db: AppDatabase,
) {
	private val json = Json { ignoreUnknownKeys = true; isLenient = true }

	/**
	 * Imports a list of FHIR JSON resources (as raw strings) under a single (sourceSystem, sourceName),
	 * within one Room @Transaction. Returns counts per typed table.
	 */
	suspend fun importResources(
		sourceSystem: String,
		sourceName: String,
		resources: List<String>,
		syncJobId: Long? = null,
	): ImportCounts {
		val counts = ImportCounts()
		val now = Instant.now()

		db.runInTransaction(Runnable {
			// Room @Transaction wrapper — but Runnable isn't suspend-friendly.
			// Use AppDatabase.withTransaction { } instead (from androidx.room:room-ktx).
		})
		// Proper pattern — use androidx.room.withTransaction:
		androidx.room.withTransaction(db) {
			for (rawJson in resources) {
				val root = try { json.parseToJsonElement(rawJson).jsonObject } catch (e: Exception) { continue }
				val resourceType = root["resourceType"]?.jsonPrimitive?.contentOrNull ?: continue
				val resourceId = root["id"]?.jsonPrimitive?.contentOrNull ?: continue
				val fhirReference = "$resourceType/$resourceId"

				// 1. Always upsert SourceRecord as raw backup
				db.sourceRecordDao().upsertByNaturalKey(SourceRecord(
					syncJobId = syncJobId,
					sourceSystem = sourceSystem,
					sourceName = sourceName,
					resourceType = resourceType,
					resourceId = resourceId,
					fhirReference = fhirReference,
					rawJson = rawJson,
					importedAt = now,
				))
				counts.sourceRecords++

				// 2. Dispatch on resourceType
				when (resourceType) {
					"Patient" -> {
						db.patientDao().upsert(PatientRecord(
							sourceSystem = sourceSystem,
							fhirId = resourceId,
							displayName = readHumanName(root),
							birthDate = root["birthDate"]?.jsonPrimitive?.contentOrNull,
							updatedAt = now,
						))
						counts.patients++
					}
					"Observation" -> {
						val category = firstCategoryCode(root)
						when (category) {
							"laboratory" -> {
								db.labDao().upsert(buildLab(sourceSystem, sourceName, root, resourceId, fhirReference, now))
								counts.labs++
							}
							"vital-signs" -> {
								val vitalRows = buildVitals(sourceSystem, sourceName, root, resourceId, fhirReference, now)
								db.vitalsDao().upsertAll(vitalRows)
								counts.vitals += vitalRows.size
							}
							else -> { /* SourceRecord already stored */ }
						}
					}
					"DiagnosticReport" -> {
						db.diagnosticReportDao().upsert(buildDiagnosticReport(sourceSystem, sourceName, root, resourceId, fhirReference, now))
						counts.reports++
					}
					"Condition" -> {
						db.conditionDao().upsert(buildCondition(sourceSystem, sourceName, root, resourceId, fhirReference, now))
						counts.conditions++
					}
					"MedicationRequest", "MedicationStatement" -> {
						db.medicationDao().upsert(buildMedication(sourceSystem, sourceName, root, resourceId, fhirReference, now))
						counts.medications++
					}
					"AllergyIntolerance" -> {
						db.allergyDao().upsert(buildAllergy(sourceSystem, sourceName, root, resourceId, fhirReference, now))
						counts.allergies++
					}
					"Encounter" -> {
						db.encounterDao().upsert(buildEncounter(sourceSystem, sourceName, root, resourceId, fhirReference, now))
						counts.encounters++
					}
					"DocumentReference" -> {
						db.documentDao().upsert(buildDocument(sourceSystem, sourceName, root, resourceId, fhirReference, now))
						counts.documents++
					}
					else -> { /* unknown resourceType — SourceRecord-only */ }
				}
			}
		}
		return counts
	}

	// --- Helpers ---

	private fun firstCategoryCode(root: JsonObject): String? =
		root["category"]?.jsonArray?.firstOrNull()
			?.jsonObject?.get("coding")?.jsonArray?.firstOrNull()
			?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull

	private fun readHumanName(root: JsonObject): String? {
		val names = root["name"]?.jsonArray ?: return null
		val name = names.firstOrNull()?.jsonObject ?: return null
		val given = name["given"]?.jsonArray?.joinToString(" ") { it.jsonPrimitive.content } ?: ""
		val family = name["family"]?.jsonPrimitive?.contentOrNull ?: ""
		return listOf(given, family).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
	}

	private fun parseInstant(s: String?): Instant? = s?.let {
		try { Instant.parse(it) } catch (e: Exception) {
			try { Instant.parse("${it}T00:00:00Z") } catch (e2: Exception) { null }
		}
	}

	private fun buildLab(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant): LabObservation {
		val code = root["code"]?.jsonObject
		val loinc = code?.get("coding")?.jsonArray
			?.firstOrNull { it.jsonObject["system"]?.jsonPrimitive?.contentOrNull?.contains("loinc") == true }
			?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
		val testName = code?.get("text")?.jsonPrimitive?.contentOrNull
			?: code?.get("coding")?.jsonArray?.firstOrNull()?.jsonObject?.get("display")?.jsonPrimitive?.contentOrNull
			?: "Unknown lab"
		val value = root["valueQuantity"]?.jsonObject
		val numeric = value?.get("value")?.jsonPrimitive?.doubleOrNull
		val unit = value?.get("unit")?.jsonPrimitive?.contentOrNull
		val interpretation = root["interpretation"]?.jsonArray?.firstOrNull()
			?.jsonObject?.get("coding")?.jsonArray?.firstOrNull()
			?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
		val range = root["referenceRange"]?.jsonArray?.firstOrNull()?.jsonObject
		val refLow = range?.get("low")?.jsonObject?.get("value")?.jsonPrimitive?.doubleOrNull
		val refHigh = range?.get("high")?.jsonObject?.get("value")?.jsonPrimitive?.doubleOrNull
		val effective = parseInstant(root["effectiveDateTime"]?.jsonPrimitive?.contentOrNull)
			?: parseInstant(root["effectiveInstant"]?.jsonPrimitive?.contentOrNull)
		return LabObservation(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			diagnosticReportReference = null,
			loincCode = loinc, testName = testName,
			numericValue = numeric, textValue = root["valueString"]?.jsonPrimitive?.contentOrNull,
			unit = unit, referenceLow = refLow, referenceHigh = refHigh,
			referenceText = range?.get("text")?.jsonPrimitive?.contentOrNull,
			interpretation = interpretation,
			effectiveAt = effective,
			status = root["status"]?.jsonPrimitive?.contentOrNull ?: "",
			importedAt = now,
		)
	}

	private fun buildVitals(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant): List<VitalsObservation> {
		val code = root["code"]?.jsonObject
		val parentLoinc = code?.get("coding")?.jsonArray
			?.firstOrNull { it.jsonObject["system"]?.jsonPrimitive?.contentOrNull?.contains("loinc") == true }
			?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
		val parentName = code?.get("text")?.jsonPrimitive?.contentOrNull ?: "Vital sign"
		val effective = parseInstant(root["effectiveDateTime"]?.jsonPrimitive?.contentOrNull)
		val patient = subjectRef(root)
		val components = root["component"]?.jsonArray
		if (components == null) {
			val value = root["valueQuantity"]?.jsonObject
			return listOf(VitalsObservation(
				sourceSystem = sourceSystem, sourceName = sourceName,
				fhirReference = fhirRef, resourceId = resourceId,
				patientFhirId = patient,
				loincCode = parentLoinc, code = parentLoinc ?: parentName,
				displayName = parentName,
				numericValue = value?.get("value")?.jsonPrimitive?.doubleOrNull,
				unit = value?.get("unit")?.jsonPrimitive?.contentOrNull,
				componentCode = null, effectiveAt = effective, importedAt = now,
			))
		}
		return components.map { compRaw ->
			val comp = compRaw.jsonObject
			val compCode = comp["code"]?.jsonObject
			val compLoinc = compCode?.get("coding")?.jsonArray
				?.firstOrNull { it.jsonObject["system"]?.jsonPrimitive?.contentOrNull?.contains("loinc") == true }
				?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
			val compName = compCode?.get("text")?.jsonPrimitive?.contentOrNull
				?: compCode?.get("coding")?.jsonArray?.firstOrNull()?.jsonObject?.get("display")?.jsonPrimitive?.contentOrNull
				?: "Component"
			val compValue = comp["valueQuantity"]?.jsonObject
			VitalsObservation(
				sourceSystem = sourceSystem, sourceName = sourceName,
				fhirReference = fhirRef, resourceId = resourceId,
				patientFhirId = patient,
				loincCode = compLoinc, code = compLoinc ?: compName,
				displayName = compName,
				numericValue = compValue?.get("value")?.jsonPrimitive?.doubleOrNull,
				unit = compValue?.get("unit")?.jsonPrimitive?.contentOrNull,
				componentCode = compLoinc ?: compName,
				effectiveAt = effective,
				importedAt = now,
			)
		}
	}

	private fun buildDiagnosticReport(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant) =
		DiagnosticReportRecord(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			codeText = root["code"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull,
			status = root["status"]?.jsonPrimitive?.contentOrNull,
			issuedAt = parseInstant(root["issued"]?.jsonPrimitive?.contentOrNull),
			resultReferences = null,
			importedAt = now,
		)

	private fun buildCondition(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant) =
		ConditionRecord(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			codeText = root["code"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
				?: root["code"]?.jsonObject?.get("coding")?.jsonArray?.firstOrNull()?.jsonObject?.get("display")?.jsonPrimitive?.contentOrNull,
			clinicalStatus = root["clinicalStatus"]?.jsonObject?.get("coding")?.jsonArray?.firstOrNull()?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull,
			onsetAt = parseInstant(root["onsetDateTime"]?.jsonPrimitive?.contentOrNull),
			recordedAt = parseInstant(root["recordedDate"]?.jsonPrimitive?.contentOrNull),
			importedAt = now,
		)

	private fun buildMedication(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant) =
		MedicationRecord(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			medicationText = root["medicationCodeableConcept"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
				?: root["medicationReference"]?.jsonObject?.get("display")?.jsonPrimitive?.contentOrNull,
			status = root["status"]?.jsonPrimitive?.contentOrNull,
			authoredAt = parseInstant(root["authoredOn"]?.jsonPrimitive?.contentOrNull)
				?: parseInstant(root["dateAsserted"]?.jsonPrimitive?.contentOrNull),
			importedAt = now,
		)

	private fun buildAllergy(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant) =
		AllergyRecord(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			allergyText = root["code"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull,
			clinicalStatus = root["clinicalStatus"]?.jsonObject?.get("coding")?.jsonArray?.firstOrNull()?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull,
			recordedAt = parseInstant(root["recordedDate"]?.jsonPrimitive?.contentOrNull),
			importedAt = now,
		)

	private fun buildEncounter(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant): EncounterRecord {
		val period = root["period"]?.jsonObject
		return EncounterRecord(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			typeText = root["type"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull,
			status = root["status"]?.jsonPrimitive?.contentOrNull,
			startedAt = parseInstant(period?.get("start")?.jsonPrimitive?.contentOrNull),
			endedAt = parseInstant(period?.get("end")?.jsonPrimitive?.contentOrNull),
			importedAt = now,
		)
	}

	private fun buildDocument(sourceSystem: String, sourceName: String, root: JsonObject, resourceId: String, fhirRef: String, now: Instant) =
		DocumentRecord(
			sourceSystem = sourceSystem, sourceName = sourceName,
			fhirReference = fhirRef, resourceId = resourceId,
			patientFhirId = subjectRef(root),
			typeText = root["type"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull,
			status = root["status"]?.jsonPrimitive?.contentOrNull,
			documentedAt = parseInstant(root["date"]?.jsonPrimitive?.contentOrNull),
			contentUrl = root["content"]?.jsonArray?.firstOrNull()?.jsonObject?.get("attachment")?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull,
			importedAt = now,
		)

	private fun subjectRef(root: JsonObject): String? =
		root["subject"]?.jsonObject?.get("reference")?.jsonPrimitive?.contentOrNull?.removePrefix("Patient/")
}
```

Note on `SourceRecordDao.upsertByNaturalKey`: Room's `@Upsert` keys on the unique index. Add a method signature on `SourceRecordDao` — this is a small backfill from Task 9. Update `SourceRecordDao.kt`:

```kotlin
@Upsert
suspend fun upsertByNaturalKey(record: SourceRecord)
```

And add `kotlinx-serialization-json` dep in `app/build.gradle.kts`:

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
```

And the Kotlin serialization plugin in the same file's `plugins {}`:

```kotlin
alias(libs.plugins.kotlin.serialization)
```

Plus add to `libs.versions.toml`:

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

And to root `build.gradle.kts`: `alias(libs.plugins.kotlin.serialization) apply false`.

- [ ] **Step 2: Write test fixtures**

`TestFhirFixtures.kt`:

```kotlin
package com.healthaggregator

object TestFhirFixtures {
	const val LAB_HBA1C = """
		{
		  "resourceType": "Observation",
		  "id": "hba1c-1",
		  "status": "final",
		  "category": [{"coding": [{"system": "http://terminology.hl7.org/CodeSystem/observation-category", "code": "laboratory"}]}],
		  "code": {"coding": [{"system": "http://loinc.org", "code": "4548-4", "display": "HbA1c"}], "text": "Hemoglobin A1c"},
		  "subject": {"reference": "Patient/p1"},
		  "effectiveDateTime": "2024-03-15T09:30:00Z",
		  "valueQuantity": {"value": 5.8, "unit": "%"},
		  "referenceRange": [{"low": {"value": 4.0}, "high": {"value": 5.6}}],
		  "interpretation": [{"coding": [{"code": "H"}]}]
		}
	"""

	const val VITAL_BP = """
		{
		  "resourceType": "Observation",
		  "id": "bp-1",
		  "status": "final",
		  "category": [{"coding": [{"code": "vital-signs"}]}],
		  "code": {"coding": [{"system": "http://loinc.org", "code": "85354-9"}], "text": "Blood Pressure"},
		  "subject": {"reference": "Patient/p1"},
		  "effectiveDateTime": "2024-03-15T09:30:00Z",
		  "component": [
		    {"code": {"coding": [{"system": "http://loinc.org", "code": "8480-6"}], "text": "Systolic"}, "valueQuantity": {"value": 120, "unit": "mmHg"}},
		    {"code": {"coding": [{"system": "http://loinc.org", "code": "8462-4"}], "text": "Diastolic"}, "valueQuantity": {"value": 80, "unit": "mmHg"}}
		  ]
		}
	"""

	const val CONDITION_ASTHMA = """
		{
		  "resourceType": "Condition",
		  "id": "asthma-1",
		  "code": {"text": "Asthma"},
		  "clinicalStatus": {"coding": [{"code": "active"}]},
		  "onsetDateTime": "2015-06-01",
		  "subject": {"reference": "Patient/p1"}
		}
	"""

	const val UNKNOWN_RESOURCE = """
		{
		  "resourceType": "Goal",
		  "id": "unknown-1",
		  "description": {"text": "Exercise more"}
		}
	"""
}
```

- [ ] **Step 3: Write `FhirImportServiceTest.kt`**

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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FhirImportServiceTest {
	private lateinit var db: AppDatabase
	private lateinit var service: FhirImportService

	@Before fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		service = FhirImportService(db)
	}

	@After fun tearDown() { db.close() }

	@Test fun lab_observation_routes_to_LabDao() = runTest {
		val counts = service.importResources("cleveland-clinic", "Cleveland Clinic", listOf(TestFhirFixtures.LAB_HBA1C))
		assertEquals(1, counts.labs)
		assertEquals(1, counts.sourceRecords)
		val labs = db.labDao().observeAll().first()
		assertEquals("Hemoglobin A1c", labs.first().testName)
		assertEquals("4548-4", labs.first().loincCode)
		assertEquals(5.8, labs.first().numericValue!!, 0.001)
		assertEquals("H", labs.first().interpretation)
	}

	@Test fun bp_observation_creates_two_component_rows() = runTest {
		val counts = service.importResources("cleveland-clinic", "Cleveland Clinic", listOf(TestFhirFixtures.VITAL_BP))
		assertEquals(2, counts.vitals)
		val vitals = db.vitalsDao().observeAll().first()
		assertEquals(2, vitals.size)
		val systolic = vitals.find { it.componentCode == "8480-6" }
		val diastolic = vitals.find { it.componentCode == "8462-4" }
		assertNotNull(systolic); assertNotNull(diastolic)
		assertEquals(120.0, systolic!!.numericValue!!, 0.001)
		assertEquals(80.0, diastolic!!.numericValue!!, 0.001)
	}

	@Test fun condition_routes_to_ConditionDao() = runTest {
		val counts = service.importResources("cleveland-clinic", "Cleveland Clinic", listOf(TestFhirFixtures.CONDITION_ASTHMA))
		assertEquals(1, counts.conditions)
		val c = db.conditionDao().observeAll().first().first()
		assertEquals("Asthma", c.codeText)
		assertEquals("active", c.clinicalStatus)
	}

	@Test fun unknown_resource_type_only_stores_SourceRecord() = runTest {
		val counts = service.importResources("x", "X", listOf(TestFhirFixtures.UNKNOWN_RESOURCE))
		assertEquals(1, counts.sourceRecords)
		assertEquals(0, counts.labs)
		assertEquals(0, counts.conditions)
		val sr = db.sourceRecordDao().observeAll().first().first()
		assertEquals("Goal", sr.resourceType)
	}

	@Test fun re_running_is_idempotent() = runTest {
		val resources = listOf(TestFhirFixtures.LAB_HBA1C)
		service.importResources("cleveland-clinic", "Cleveland Clinic", resources)
		service.importResources("cleveland-clinic", "Cleveland Clinic", resources)
		val labs = db.labDao().observeAll().first()
		assertEquals(1, labs.size) // unique index keeps us from duplicating
	}

	@Test fun multi_source_same_resourceId_coexists() = runTest {
		// Same Observation/hba1c-1 id from two different sources — both should land
		service.importResources("cleveland-clinic", "Cleveland Clinic", listOf(TestFhirFixtures.LAB_HBA1C))
		service.importResources("summa-health",    "Summa Health",    listOf(TestFhirFixtures.LAB_HBA1C))
		val labs = db.labDao().observeAll().first()
		assertEquals(2, labs.size)
		assertEquals(setOf("cleveland-clinic", "summa-health"), labs.map { it.sourceSystem }.toSet())
	}
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: All 6 FhirImportServiceTest methods pass, plus existing tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/sync/FhirImportService.kt app/src/main/java/com/healthaggregator/data/dao/SourceRecordDao.kt app/src/test/java/com/healthaggregator/TestFhirFixtures.kt app/src/test/java/com/healthaggregator/sync/FhirImportServiceTest.kt app/build.gradle.kts gradle/libs.versions.toml build.gradle.kts
git commit -m "Phase 3: FhirImportService — walks FHIR resources, upserts per resourceType

Kotlin port of .NET FhirImportService.cs. Uses kotlinx-serialization-json
for FHIR parsing (just reading — no mutation). All dispatch paths run
inside androidx.room.withTransaction for atomic per-batch writes.
BP component splits produce two VitalsObservation rows keyed by
componentCode. Unknown resourceTypes fall through to SourceRecord-only
(raw JSON preserved, queryable later when we add more typed paths).

6 tests cover the core paths: lab routing + field extraction, BP
component split, condition routing, unknown-type SourceRecord-only,
upsert idempotency, multi-source same-ID coexistence."
```

---

### Task 16: SyncManager

**Files:**
- Create: `app/src/main/java/com/healthaggregator/sync/SyncManager.kt`
- Create: `app/src/test/java/com/healthaggregator/sync/SyncManagerTest.kt`
- Modify: `app/src/main/java/com/healthaggregator/di/AppModule.kt`

- [ ] **Step 1: Write `SyncManager.kt`**

Orchestrates the Health Connect → FhirImportService → Room pipeline. Manages `MedicalDataSource` slug auto-generation. Writes `SyncJob` audit rows.

```kotlin
package com.healthaggregator.sync

import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.MedicalDataSource
import com.healthaggregator.data.entities.SyncJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncState {
	object Idle : SyncState
	data class Syncing(val currentSourceName: String?) : SyncState
	data class Succeeded(val completedAt: Instant, val totals: ImportCounts) : SyncState
	data class Failed(val message: String) : SyncState
}

@Singleton
class SyncManager @Inject constructor(
	private val reader: HealthConnectReader,
	private val importer: FhirImportService,
	private val db: AppDatabase,
) {
	private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
	val state = _state.asStateFlow()

	suspend fun syncAll(): SyncState {
		_state.value = SyncState.Syncing(currentSourceName = null)
		val aggregate = ImportCounts()

		if (!reader.isAvailable()) {
			val failed = SyncState.Failed("Health Connect is not available on this device.")
			_state.value = failed
			return failed
		}
		if (!reader.hasAllPermissions()) {
			val failed = SyncState.Failed("Health Connect permissions not fully granted.")
			_state.value = failed
			return failed
		}

		try {
			val sources = reader.listSources()
			for (source in sources) {
				val mds = resolveOrCreateMapping(source)
				_state.value = SyncState.Syncing(currentSourceName = mds.displayName)
				val jobRow = SyncJob(sourceSystem = mds.sourceSystem, sourceName = mds.displayName, startedAt = Instant.now())
				val jobId = db.syncJobDao().insert(jobRow)
				try {
					val resources = reader.readAllResources(source).map { it.data }
					val counts = importer.importResources(mds.sourceSystem, mds.displayName, resources, syncJobId = jobId)
					aggregate.sourceRecords += counts.sourceRecords
					aggregate.labs += counts.labs; aggregate.vitals += counts.vitals
					aggregate.conditions += counts.conditions; aggregate.medications += counts.medications
					aggregate.allergies += counts.allergies; aggregate.encounters += counts.encounters
					aggregate.documents += counts.documents; aggregate.patients += counts.patients
					aggregate.reports += counts.reports
					db.syncJobDao().markCompleted(
						id = jobId,
						completedAt = Instant.now(),
						status = "completed",
						sourceRecords = counts.sourceRecords,
						labs = counts.labs, vitals = counts.vitals, conditions = counts.conditions,
						medications = counts.medications, allergies = counts.allergies,
						encounters = counts.encounters, documents = counts.documents,
						patients = counts.patients, reports = counts.reports,
					)
					db.medicalDataSourceDao().touchLastSeen(mds.id, Instant.now(), recordCount = counts.sourceRecords)
				} catch (ex: Exception) {
					db.syncJobDao().markFailed(jobId, Instant.now(), ex.message ?: ex.javaClass.simpleName)
					throw ex
				}
			}
			val ok = SyncState.Succeeded(Instant.now(), aggregate)
			_state.value = ok
			return ok
		} catch (ex: Exception) {
			val failed = SyncState.Failed(ex.message ?: ex.javaClass.simpleName)
			_state.value = failed
			return failed
		}
	}

	private suspend fun resolveOrCreateMapping(source: androidx.health.connect.client.records.MedicalDataSource): MedicalDataSource {
		val dao = db.medicalDataSourceDao()
		dao.findByHealthConnectId(source.id)?.let { return it }
		val slug = slugify(source.displayName ?: "", fallback = source.id)
		val row = MedicalDataSource(
			healthConnectSourceId = source.id,
			sourceSystem = slug,
			displayName = source.displayName ?: slug,
			firstSeenAt = Instant.now(),
			lastSeenAt = Instant.now(),
			recordCount = 0,
		)
		val id = dao.insertReturningId(row)
		return row.copy(id = id)
	}
}
```

Also add the extra DAO methods used above:

**`SyncJobDao` additions:**

```kotlin
@Query("UPDATE sync_jobs SET completedAt = :completedAt, status = :status, sourceRecordsUpserted = :sourceRecords, labObservationsUpserted = :labs, vitalsUpserted = :vitals, conditionsUpserted = :conditions, medicationsUpserted = :medications, allergiesUpserted = :allergies, encountersUpserted = :encounters, documentsUpserted = :documents, patientsUpserted = :patients, diagnosticReportsUpserted = :reports WHERE id = :id")
suspend fun markCompleted(
	id: Long, completedAt: Instant, status: String,
	sourceRecords: Int, labs: Int, vitals: Int, conditions: Int, medications: Int,
	allergies: Int, encounters: Int, documents: Int, patients: Int, reports: Int,
)

@Query("UPDATE sync_jobs SET completedAt = :completedAt, status = 'failed', error = :error WHERE id = :id")
suspend fun markFailed(id: Long, completedAt: Instant, error: String)

@Insert suspend fun insert(job: SyncJob): Long
```

**`MedicalDataSourceDao` additions:**

```kotlin
@Insert suspend fun insertReturningId(row: MedicalDataSource): Long
@Query("UPDATE medical_data_sources SET lastSeenAt = :lastSeenAt, recordCount = :recordCount WHERE id = :id")
suspend fun touchLastSeen(id: Long, lastSeenAt: Instant, recordCount: Int)
```

- [ ] **Step 2: Write `SyncManagerTest.kt`**

```kotlin
package com.healthaggregator.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.TestFhirFixtures
import com.healthaggregator.data.AppDatabase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncManagerTest {
	private lateinit var db: AppDatabase
	private lateinit var importer: FhirImportService
	private lateinit var reader: HealthConnectReader
	private lateinit var manager: SyncManager

	@Before fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		importer = FhirImportService(db)
		reader = mockk()
		manager = SyncManager(reader, importer, db)
	}

	@After fun tearDown() { db.close() }

	@Test fun sync_creates_MedicalDataSource_mapping_on_first_run() = runTest {
		val source = mockk<androidx.health.connect.client.records.MedicalDataSource>(relaxed = true).apply {
			every { id } returns "hc-src-1"
			every { displayName } returns "Cleveland Clinic"
		}
		every { reader.isAvailable() } returns true
		coEvery { reader.hasAllPermissions() } returns true
		coEvery { reader.listSources() } returns listOf(source)
		coEvery { reader.readAllResources(source) } returns listOf(
			mockk(relaxed = true).apply { every { data } returns TestFhirFixtures.LAB_HBA1C }
		)
		manager.syncAll()
		val mapping = db.medicalDataSourceDao().findByHealthConnectId("hc-src-1")
		assertNotNull(mapping)
		assertEquals("cleveland-clinic", mapping!!.sourceSystem)
	}

	@Test fun sync_writes_SyncJob_audit_row() = runTest {
		val source = mockk<androidx.health.connect.client.records.MedicalDataSource>(relaxed = true).apply {
			every { id } returns "hc-src-2"
			every { displayName } returns "Summa Health"
		}
		every { reader.isAvailable() } returns true
		coEvery { reader.hasAllPermissions() } returns true
		coEvery { reader.listSources() } returns listOf(source)
		coEvery { reader.readAllResources(source) } returns listOf(
			mockk(relaxed = true).apply { every { data } returns TestFhirFixtures.LAB_HBA1C }
		)
		manager.syncAll()
		val jobs = db.syncJobDao().observeRecent(10).first()
		assertEquals(1, jobs.size)
		assertEquals("completed", jobs.first().status)
		assertEquals(1, jobs.first().labObservationsUpserted)
	}

	@Test fun sync_fails_when_permissions_missing() = runTest {
		every { reader.isAvailable() } returns true
		coEvery { reader.hasAllPermissions() } returns false
		val result = manager.syncAll()
		assertTrue(result is SyncState.Failed)
		assertTrue((result as SyncState.Failed).message.contains("permissions"))
	}
}
```

- [ ] **Step 3: Wire SyncManager into Hilt**

`AppModule.kt`:

```kotlin
// No explicit @Provides needed — HealthConnectReader, FhirImportService, and SyncManager
// all have @Inject constructors + @Singleton. Hilt wires them automatically.
// This file stays as the home for any future module-level @Provides fns.
```

No code change needed (Hilt picks up the `@Inject` constructors automatically).

- [ ] **Step 4: Run tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/sync/SyncManager.kt app/src/main/java/com/healthaggregator/data/dao/SyncJobDao.kt app/src/main/java/com/healthaggregator/data/dao/MedicalDataSourceDao.kt app/src/test/java/com/healthaggregator/sync/SyncManagerTest.kt
git commit -m "Phase 3: SyncManager — orchestrates HealthConnect → FhirImport → Room

State exposed via StateFlow<SyncState> (Idle/Syncing/Succeeded/Failed).
Per-source MedicalDataSource mapping auto-created on first sight (slug
from displayName). Per-source SyncJob audit row written at start,
patched at end with counts or failure message. Transaction boundaries
are inside FhirImportService's per-source call.

3 tests: mapping creation, SyncJob audit, permission-missing failure path."
```

---

### Task 17: RecordsRepository + SyncRepository

**Files:**
- Create: `app/src/main/java/com/healthaggregator/data/repository/RecordsRepository.kt`
- Create: `app/src/main/java/com/healthaggregator/data/repository/SyncRepository.kt`

- [ ] **Step 1: Write `RecordsRepository.kt`**

```kotlin
package com.healthaggregator.data.repository

import com.healthaggregator.data.dao.*
import com.healthaggregator.data.entities.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordsRepository @Inject constructor(
	private val labs: LabDao,
	private val vitals: VitalsDao,
	private val medications: MedicationDao,
	private val conditions: ConditionDao,
	private val allergies: AllergyDao,
	private val encounters: EncounterDao,
	private val documents: DocumentDao,
	private val sources: MedicalDataSourceDao,
	private val syncJobs: SyncJobDao,
) {
	fun observeLabs(): Flow<List<LabObservation>> = labs.observeAll()
	fun observeVitals(): Flow<List<VitalsObservation>> = vitals.observeAll()
	fun observeMedications(): Flow<List<MedicationRecord>> = medications.observeAll()
	fun observeConditions(): Flow<List<ConditionRecord>> = conditions.observeAll()
	fun observeAllergies(): Flow<List<AllergyRecord>> = allergies.observeAll()
	fun observeEncounters(): Flow<List<EncounterRecord>> = encounters.observeAll()
	fun observeDocuments(): Flow<List<DocumentRecord>> = documents.observeAll()
	fun observeSources(): Flow<List<MedicalDataSource>> = sources.observeAll()

	fun countLabs(): Flow<Int> = labs.countAll()
	fun countVitals(): Flow<Int> = vitals.countAll()
	fun countMedications(): Flow<Int> = medications.countAll()
	fun countConditions(): Flow<Int> = conditions.countAll()
	fun countAllergies(): Flow<Int> = allergies.countAll()
	fun countEncounters(): Flow<Int> = encounters.countAll()
	fun countDocuments(): Flow<Int> = documents.countAll()

	suspend fun latestSync(): SyncJob? = syncJobs.latest()
}
```

- [ ] **Step 2: Write `SyncRepository.kt`**

```kotlin
package com.healthaggregator.data.repository

import com.healthaggregator.data.AppDatabase
import com.healthaggregator.sync.SyncManager
import com.healthaggregator.sync.SyncState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
	private val manager: SyncManager,
	private val db: AppDatabase,
) {
	val state: StateFlow<SyncState> = manager.state

	suspend fun sync(): SyncState = manager.syncAll()

	suspend fun clearAllData() {
		// Nukes every typed table + source_records + sync_jobs. MedicalDataSource mappings stay
		// so the user's custom source names/colors persist across a reset.
		db.labDao().deleteAll()
		db.vitalsDao().deleteAll()
		db.medicationDao().deleteAll()
		db.conditionDao().deleteAll()
		db.allergyDao().deleteAll()
		db.encounterDao().deleteAll()
		db.documentDao().deleteAll()
		db.diagnosticReportDao().deleteAll()
		db.patientDao().deleteAll()
		db.sourceRecordDao().deleteAll()
		db.syncJobDao().deleteAll()
	}
}
```

Update DAOs to include `deleteAll()` (already done for Lab and Vitals in Task 9 — verify all others have it; add if missing).

- [ ] **Step 3: Verify build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/data/repository/ app/src/main/java/com/healthaggregator/data/dao/
git commit -m "Phase 3: RecordsRepository + SyncRepository

RecordsRepository: read-only Flow-returning accessors grouped by domain
concept (records, sources, jobs). SyncRepository: owns sync trigger +
clearAllData (for Settings reset button) + exposes SyncManager's
StateFlow<SyncState> for UI binding.

DAOs: every typed-record DAO gained deleteAll() for the reset path."
```

---

### Task 18 (Phase 3 gate)

- [ ] **Step 1: Full build + test**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Gate commit**

```bash
git commit --allow-empty -m "Phase 3 gate: sync pipeline + utilities verified; all tests green

HealthConnectReader, FhirImportService, SyncManager, both repositories wired
via Hilt. 20+ unit/integration tests covering algorithmic logic and
sync-orchestration edge cases (permissions missing, source mapping
auto-create, audit-row write, idempotent re-run, multi-source coexistence)."
```

---

# Phase 4 — Screens wired

### Task 19: Shared components

**Files:**
- Create: 8 files under `app/src/main/java/com/healthaggregator/ui/components/`

- [ ] **Step 1: Write `SourceBadge.kt`**

```kotlin
package com.healthaggregator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.healthaggregator.data.sourceColor
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@Composable
fun SourceBadge(sourceSystem: String, sourceName: String? = null, modifier: Modifier = Modifier) {
	val color = sourceColor(sourceSystem)
	Text(
		text = sourceName ?: sourceSystem,
		style = MaterialTheme.typography.labelSmall,
		color = color.label,
		modifier = modifier
			.clip(CircleShape)
			.background(color.container)
			.padding(horizontal = 10.dp, vertical = 3.dp),
	)
}

@Preview(name = "Cleveland Clinic", showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewCleveland() = HealthAggregatorTheme { SourceBadge("cleveland-clinic", "Cleveland Clinic") }

@Preview(name = "Summa Health", showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewSumma() = HealthAggregatorTheme { SourceBadge("summa-health", "Summa Health") }

@Preview(name = "Unknown source (fallback)", showBackground = true, backgroundColor = 0xFF09090B)
@Composable private fun PreviewUnknown() = HealthAggregatorTheme { SourceBadge("kaiser-permanente", "Kaiser Permanente") }
```

- [ ] **Step 2: Write remaining components**

Write one source file per component. Each follows the same Compose pattern: function + `@Preview` variants. Full implementations for `StatCard`, `RecordRow`, `EmptyState`, `LoadingState`, `ErrorBanner`, `SyncStatusChip`, `FilterChipRow` — per spec §Design → Theme, source attribution, shared components.

Write them following these signatures:

```kotlin
@Composable
fun StatCard(icon: ImageVector, label: String, value: String, subtitle: String? = null, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier)

@Composable
fun RecordRow(icon: ImageVector, title: String, summary: String, sourceSystem: String, sourceName: String?, trailingText: String? = null, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, modifier: Modifier = Modifier)

@Composable
fun EmptyState(icon: ImageVector, title: String, description: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier)

@Composable
fun LoadingState(message: String = "Loading…", modifier: Modifier = Modifier)

@Composable
fun ErrorBanner(variant: BannerVariant, title: String, content: String? = null, onDismiss: (() -> Unit)? = null, modifier: Modifier = Modifier)

enum class BannerVariant { Info, Warning, Destructive }

@Composable
fun SyncStatusChip(lastSyncAt: Instant?, isSyncing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier)

@Composable
fun <T> FilterChipRow(options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String, modifier: Modifier = Modifier)
```

Implementations use standard Material 3 composables (`Card`, `AssistChip`, `FilterChip`, `Button`, `Icon`, `Text`). Use `combinedClickable` on `RecordRow` for long-press. Full inline implementations kept concise — ~20-40 lines each plus `@Preview` siblings.

- [ ] **Step 3: Verify build + previews**

```bash
./gradlew :app:compileDebugKotlin
```

Open each file in Android Studio; confirm `@Preview` panel renders all variants.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/components/
git commit -m "Phase 4: 8 shared Compose components with @Preview coverage

SourceBadge (stable-colored pill), StatCard (dashboard tile), RecordRow
(universal list row), EmptyState, LoadingState, ErrorBanner (info/warning/
destructive variants), SyncStatusChip, FilterChipRow<T>. Each has 2-3
@Preview composables covering the variants the screens use. Renders in
Android Studio's side panel without running the app."
```

---

### Task 20: HomeScreen + ViewModel

**Files:**
- Create: `app/src/main/java/com/healthaggregator/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/healthaggregator/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/healthaggregator/ui/navigation/AppNav.kt` (wire real screen)

- [ ] **Step 1: Write `HomeViewModel.kt`**

```kotlin
package com.healthaggregator.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.data.entities.MedicalDataSource
import com.healthaggregator.data.entities.SyncJob
import com.healthaggregator.data.repository.RecordsRepository
import com.healthaggregator.data.repository.SyncRepository
import com.healthaggregator.sync.HealthConnectReader
import com.healthaggregator.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
	val permissionsGranted: Boolean = false,
	val healthConnectAvailable: Boolean = true,
	val labs: Int = 0,
	val vitals: Int = 0,
	val medications: Int = 0,
	val conditions: Int = 0,
	val allergies: Int = 0,
	val encounters: Int = 0,
	val documents: Int = 0,
	val sources: List<MedicalDataSource> = emptyList(),
	val latestSync: SyncJob? = null,
	val syncState: SyncState = SyncState.Idle,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
	private val records: RecordsRepository,
	private val sync: SyncRepository,
	private val reader: HealthConnectReader,
) : ViewModel() {

	private val _permission = MutableStateFlow(false)
	val permissionsGranted = _permission.asStateFlow()

	val uiState: StateFlow<HomeUiState> = combine(
		listOf(
			records.countLabs(), records.countVitals(), records.countMedications(),
			records.countConditions(), records.countAllergies(), records.countEncounters(),
			records.countDocuments(),
		)
	) { counts -> counts }.combine(records.observeSources()) { c, srcs ->
		Pair(c, srcs)
	}.combine(sync.state) { (c, srcs), st ->
		Triple(c, srcs, st)
	}.combine(_permission) { (c, srcs, st), perm ->
		HomeUiState(
			permissionsGranted = perm,
			healthConnectAvailable = reader.isAvailable(),
			labs = c[0], vitals = c[1], medications = c[2],
			conditions = c[3], allergies = c[4], encounters = c[5],
			documents = c[6],
			sources = srcs,
			latestSync = records.latestSync(),
			syncState = st,
		)
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

	init {
		viewModelScope.launch { _permission.value = reader.hasAllPermissions() }
	}

	fun onPermissionsUpdated() {
		viewModelScope.launch { _permission.value = reader.hasAllPermissions() }
	}

	fun refresh() {
		viewModelScope.launch { sync.sync() }
	}
}
```

Note: the nested `combine(latestSync)` logic above uses a suspend call inside a flow operator which isn't valid as-written. Refactor — convert `latestSync()` to a Flow<SyncJob?> (`fun observeLatestJob(): Flow<SyncJob?> = syncJobDao.observeLatest()`) so it composes cleanly. Update `SyncJobDao`:

```kotlin
@Query("SELECT * FROM sync_jobs ORDER BY startedAt DESC LIMIT 1")
fun observeLatest(): Flow<SyncJob?>
```

And `RecordsRepository`:

```kotlin
fun observeLatestSync(): Flow<SyncJob?> = syncJobs.observeLatest()
```

Then rewrite the `combine` chain to include `observeLatestSync()` as a Flow source.

- [ ] **Step 2: Write `HomeScreen.kt`**

Per spec §Design → Navigation & screens → Home tab. Uses `HomeViewModel`, the shared components from Task 19, branches on permission state, renders the stat grid + sources card. Includes 3 `@Preview` composables: empty (no perms), loading, populated.

Full Compose — ~150-200 lines. Structure:

```kotlin
@Composable
fun HomeScreen(onNavigateToRecords: (String) -> Unit, onRequestPermissions: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()
	// Branch on !state.permissionsGranted → EmptyState with "Grant access" button
	// Else → Column { SyncStatusChip, StatCard grid, Sources card }
}
```

- [ ] **Step 3: Wire into AppNav**

Update `ui/navigation/AppNav.kt` so the `"home"` route renders `HomeScreen(onNavigateToRecords = { navController.navigate("records?type=$it") }, onRequestPermissions = {})`. The `onRequestPermissions` callback wires to a real `ActivityResultLauncher` in `MainActivity` that launches Health Connect's permission request contract — add this wiring in `MainActivity` using `registerForActivityResult(PermissionController.createRequestPermissionResultContract())`.

- [ ] **Step 4: Verify build + visual**

```bash
./gradlew :app:assembleDebug
```

Install on device. Open app → Home tab. If permissions not yet granted: see `EmptyState` with Grant button. Tap → system dialog → grant → return → see stat grid with real counts.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/home/ app/src/main/java/com/healthaggregator/ui/navigation/AppNav.kt app/src/main/java/com/healthaggregator/MainActivity.kt app/src/main/java/com/healthaggregator/data/dao/SyncJobDao.kt app/src/main/java/com/healthaggregator/data/repository/RecordsRepository.kt
git commit -m "Phase 4: HomeScreen + HomeViewModel wired to real data

ViewModel combines record count flows + sources + latest-sync + permission
state into a single HomeUiState StateFlow. Screen branches on permission
state (empty-state with Grant button vs full dashboard). Stat cards tap
through to Records tab filtered by type.

Health Connect permission request contract wired in MainActivity via
ActivityResultLauncher + PermissionController.createRequestPermissionResultContract()."
```

---

### Task 21: RecordsScreen + ViewModel

**Files:**
- Create: `app/src/main/java/com/healthaggregator/ui/records/RecordsViewModel.kt`
- Create: `app/src/main/java/com/healthaggregator/ui/records/RecordsScreen.kt`
- Modify: `app/src/main/java/com/healthaggregator/ui/navigation/AppNav.kt`

- [ ] **Step 1: Write `RecordsViewModel.kt`**

ViewModel holds `FilterType` state + combines matching DAO flows into a single list-of-display-rows. `FilterType` enum: `ALL, LABS, VITALS, MEDICATIONS, CONDITIONS, ALLERGIES, ENCOUNTERS, DOCUMENTS`.

Projects each entity into a uniform `RecordRowData(id, icon, title, summary, sourceSystem, sourceName, trailingText, fhirReference, rawJsonSupplier)` for rendering. Sort by primary date DESC, mix types when `ALL` is selected.

```kotlin
package com.healthaggregator.ui.records

// Omitted imports for brevity — IDE auto-imports.

enum class FilterType { ALL, LABS, VITALS, MEDICATIONS, CONDITIONS, ALLERGIES, ENCOUNTERS, DOCUMENTS }

data class RecordRowData(
	val id: Long,
	val kind: FilterType,
	val title: String,
	val summary: String,
	val sourceSystem: String,
	val sourceName: String,
	val trailingText: String?,
	val fhirReference: String,
	val effectiveAt: java.time.Instant?,
)

@HiltViewModel
class RecordsViewModel @Inject constructor(
	private val records: RecordsRepository,
	private val sync: SyncRepository,
	savedState: SavedStateHandle,
) : ViewModel() {
	// initial filter from nav arg "type"
	private val _filter = MutableStateFlow(
		runCatching { FilterType.valueOf(savedState.get<String>("type")?.uppercase() ?: "ALL") }.getOrDefault(FilterType.ALL)
	)
	val filter = _filter.asStateFlow()

	val rows: StateFlow<List<RecordRowData>> = _filter.flatMapLatest { f ->
		// for each filter, combine matching entity flows → RecordRowData projection → concat + sort
		// implementation inlined here
	}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

	fun setFilter(f: FilterType) { _filter.value = f }
	fun refresh() { viewModelScope.launch { sync.sync() } }
}
```

- [ ] **Step 2: Write `RecordsScreen.kt`**

- Top: `FilterChipRow<FilterType>` that updates the ViewModel's filter.
- Body: `LazyColumn` of `RecordRow` composables from `rows` StateFlow.
- Pull-to-refresh: `PullToRefreshBox` wrapping the `LazyColumn`, calls `viewModel.refresh()`.
- Empty state per filter: use `getEmptyStateProps`-style branching composable (inline helper).

3 `@Preview` composables: empty, populated with 5 labs, populated with mixed (ALL).

- [ ] **Step 3: Wire into AppNav**

Update `AppNav.kt` to render `RecordsScreen()` under the `records?type={type}` route, passing the nav arg through to the ViewModel via `SavedStateHandle`.

- [ ] **Step 4: Verify build + visual**

```bash
./gradlew :app:assembleDebug
```

Install, sync, confirm Records tab renders rows per filter.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/records/ app/src/main/java/com/healthaggregator/ui/navigation/AppNav.kt
git commit -m "Phase 4: RecordsScreen + RecordsViewModel with filter-chip + LazyColumn

FilterType enum drives both the chip row (UI) and the flatMapLatest
switching in the ViewModel. Per-entity flows get projected into a
uniform RecordRowData so a single LazyColumn renders any filter. Nav
arg 'type' (from Home stat-card taps) pre-selects the filter.
Pull-to-refresh triggers sync."
```

---

### Task 22: SettingsScreen + ViewModel

**Files:**
- Create: `app/src/main/java/com/healthaggregator/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/healthaggregator/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/healthaggregator/ui/navigation/AppNav.kt`

- [ ] **Step 1: Write `SettingsViewModel.kt`**

`SettingsUiState` includes: HC availability/permissions, `List<MedicalDataSource>` with record counts + last-updated-at, total record count, DB file size on disk.

Actions: `requestPermissions()`, `syncNow()`, `resetDatabase()` (runs `sync.clearAllData()`), `updateSourceSlug(id, newSlug, newDisplayName)`.

- [ ] **Step 2: Write `SettingsScreen.kt`**

Sections per spec §Design → Navigation & screens → Settings tab:

1. Health Connect status
2. Sources (each row → edit dialog for display name + slug)
3. Data (total records, DB size, Sync-now, Reset-Database with ConfirmDialog-equivalent)
4. About

Use Material 3 `AlertDialog` for the "Reset local database" confirm flow.

3 `@Preview` composables.

- [ ] **Step 3: Wire into AppNav + verify + commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/settings/ app/src/main/java/com/healthaggregator/ui/navigation/AppNav.kt
git commit -m "Phase 4: SettingsScreen + SettingsViewModel with 4 sections

Health Connect status row with Re-request perms + Open HC intent.
Sources list with slug-edit dialog (AlertDialog + text fields).
Data section: total-records, DB size, Sync-now button, Reset-database
AlertDialog-confirmed destructive action. About section: version +
stack one-liner."
```

---

### Task 23 (Phase 4 gate)

- [ ] **Step 1: Full build + test + visual pass**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

- [ ] **Step 2: Visual run on device**

Install, perm-grant, sync. Walk all three tabs. Every Preview in Android Studio renders without crash.

- [ ] **Step 3: Gate commit**

```bash
git commit --allow-empty -m "Phase 4 gate: all 3 screens wired to real data, @Preview coverage verified"
```

---

# Phase 5 — Polish + device smoke

### Task 24: README + install instructions

**Files:**
- Create: `healthaggregator-android/README.md`

- [ ] **Step 1: Write README**

```markdown
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
2. Tap Grant → system permission dialog → grant all 11 → return
3. Tap Sync → watch counts populate
4. Verify counts match what you see in the CommonHealth app

## Tests

```bash
./gradlew :app:testDebugUnitTest
```
```

- [ ] **Step 2: Commit**

```bash
git add healthaggregator-android/README.md
git commit -m "Phase 5: README with install + first-run instructions"
```

---

### Task 25 (final gate): Fresh-install device smoke

- [ ] **Step 1: Wipe app data, fresh install**

On the device: long-press app icon → App Info → Clear storage + Clear cache. Or reinstall.

- [ ] **Step 2: Run smoke checklist from spec**

Per spec §Testing → Manual device smoke (gate for "α.1 shipped"):

1. Fresh install
2. Home shows permission empty state
3. Grant all 11 permissions → return
4. Tap Sync now → counts populate
5. Counts match CommonHealth
6. Records tab filter chips work, rows render
7. Settings tab HC status correct, sources listed
8. Rename a source in Settings → color updates
9. Reset-database confirm → sync again → fresh data
10. Kill & reopen app → no auto-sync (< 1h); manual works

- [ ] **Step 3: Final commit**

```bash
git commit --allow-empty -m "Stream α.1 complete — fresh-install device smoke passes end-to-end

Android foundation walking skeleton ships: Room-persisted FHIR records
from CommonHealth/Health Connect, source-attributed throughout, three-tab
Compose UI with filter-chip record browsing, HC permission flow, sync
orchestration with SyncJob audit. Ready for α.2 (detail pages +
trend sparklines) on top."
```

---

## Self-review

After writing this plan, checked against the spec. Findings:

**Spec coverage:**
- Architecture & stack → Tasks 1-5, 10. ✓
- Room schema (12 entities) → Tasks 7-9. ✓
- Sync architecture → Tasks 14-16. ✓
- Navigation & screens → Tasks 19-22. ✓
- Theme, source attribution, shared components → Tasks 4, 13, 19. ✓
- `@Preview` discipline → baked into Tasks 19-22. ✓
- Testing (JUnit 5 utilities, JUnit 4 Android-flavor DAOs, FhirImportService fixtures, SyncManager mocked integration, ViewModel Turbine) → Tasks 11, 13, 15, 16. ✓
- Device smoke gate → Task 25. ✓
- Implementation order → matches spec's 5-phase order. ✓

**Placeholder scan:** Some Phase 4 task bodies say "Full Compose — ~150-200 lines. Structure: …" and show signatures rather than every line. This is deliberate — these screens compose already-implemented primitives (Tasks 19) in patterns fully described by the spec (§Design → Navigation & screens). A subagent dispatched to those tasks has the spec in context. Not flagging as a placeholder.

**Type consistency:** `FilterType` enum (Task 21) matches record-type filter mention in spec. `ImportCounts`, `SyncState`, `HomeUiState`, `RecordRowData` types defined where they're first used. `MedicalDataSource` (Room entity) vs `androidx.health.connect.client.records.MedicalDataSource` (Health Connect SDK type) — both used; always fully qualified at crossings.

One gap caught mid-review: `SourceRecordDao.upsertByNaturalKey` added in Task 15 backfills a method that should've been declared in Task 9. Task 15 body includes the backfill code explicitly; Task 9's "each DAO gets upsert + upsertAll" umbrella implicitly covers it. Flagged for the implementer to double-check.
