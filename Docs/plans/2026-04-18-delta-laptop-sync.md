# Android Stream δ Implementation Plan — Laptop Sync

> **For agentic workers:** REQUIRED SUB-SKILL — use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans`. Steps use checkbox (`- [ ]`) syntax. **Every subagent dispatch must include the spec at `Docs/specs/2026-04-18-laptop-sync-design.md` as context.**

**Goal:** Bidirectional sync between the Android phone's Room SQLite database and an identical SQLite file on the laptop. Phone presses "Sync now"; a Python/FastAPI daemon bound to Tailscale receives push and serves pull. INSERT OR IGNORE keyed on FHIR natural keys (immutable rows); last-writer-wins on `chat_conversations.updatedAt` (editable titles). Laptop is an append-only superset: rows arrive from the phone and from local Claude Code sessions, never leave.

**Architecture:** Phone SyncClient (OkHttp) talks to a daemon at `~/HealthAggregatorData/` over Tailscale with Bearer token auth. Schema migrations move from Kotlin `execSQL(...)` strings to `app/src/main/assets/migrations/N_to_M.sql` files; phone ships migration SQL to the daemon on demand when versions drift. Daemon is stateless about schema — receives SQL over HTTP, applies in a transaction, bumps `PRAGMA user_version`. No watermarks, no cursors: each sync ships every row in every syncable table both ways, merge deduplicates by natural keys. Stateless and self-healing — retries always converge.

**Tech Stack (Android):** Kotlin 2.0.21, Jetpack Compose, Material 3, Hilt, Room 2.6.1 (KSP), OkHttp 4.12.0, kotlinx-serialization-json 1.7.3, `androidx.security:security-crypto` 1.1.0-alpha06, JUnit 5 Jupiter (pure Kotlin), JUnit 4 + Robolectric 4.14 (`@Config(sdk = [35])`) for Android-dependent tests, MockWebServer for HTTP tests, MigrationTestHelper for schema tests.

**Tech Stack (Laptop Daemon):** Python 3.11+, FastAPI, uvicorn, stdlib sqlite3, pytest with `fastapi.testclient.TestClient`. Project lives at `healthaggregator-laptop-daemon/` as a sibling to `healthaggregator-android/` inside the parent HealthAggregator git repo.

**Build discipline:**
- Tabs for indentation matching existing `.kt` files.
- Never use `--no-verify`.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` is the Android gate.
- `cd healthaggregator-laptop-daemon && make test` is the Python gate.
- Commit messages: imperative, no Co-Authored-By line (per parent `CLAUDE.md`).
- Feature branch: `feat/delta-laptop-sync` off `main` (commit `65b0b18` which contains the spec).

---

## File change map

### New — Android production

```
app/src/main/assets/migrations/1_to_2.sql            Extracted from existing Kotlin execSQL strings
app/src/main/assets/migrations/2_to_3.sql            Same
app/src/main/assets/migrations/3_to_4.sql            Same
app/src/main/assets/migrations/4_to_5.sql            Same (vitals dedup fix)
app/src/main/java/com/healthaggregator/data/MigrationFactory.kt    Loads asset SQL and returns Migration objects
app/src/main/java/com/healthaggregator/sync/SyncableTables.kt      Enum + MergeStrategy sealed interface
app/src/main/java/com/healthaggregator/sync/SyncModels.kt          Kotlinx Serialization DTOs for wire protocol
app/src/main/java/com/healthaggregator/sync/RowSerializer.kt       Entity ↔ JSON row mapping for each syncable table
app/src/main/java/com/healthaggregator/sync/SyncClient.kt          OkHttp wrapper for 5 HTTP routes
app/src/main/java/com/healthaggregator/sync/SyncRepository.kt      Orchestrates handshake → migrate → push → pull
app/src/main/java/com/healthaggregator/di/SyncModule.kt            Hilt bindings for SyncClient, SyncRepository
app/src/main/java/com/healthaggregator/ui/settings/LaptopPairingScreen.kt   Hostname + token entry
app/src/main/java/com/healthaggregator/ui/settings/SyncLogScreen.kt         Per-table deltas from last sync
app/src/main/java/com/healthaggregator/ui/settings/LaptopSyncSection.kt     Embedded in SettingsScreen
```

### Modified — Android

```
app/src/main/java/com/healthaggregator/data/AppDatabaseMigrations.kt   One-liners calling MigrationFactory
app/src/main/java/com/healthaggregator/di/DatabaseModule.kt            Wire factory
app/src/main/java/com/healthaggregator/util/SecureStorage.kt           Add laptop hostname + token fields
app/src/main/java/com/healthaggregator/ui/settings/SettingsScreen.kt   Add LaptopSyncSection
app/src/main/java/com/healthaggregator/ui/nav/AppNav.kt                Add routes for pairing + sync-log screens
```

### New — Android tests

```
app/src/test/java/com/healthaggregator/data/MigrationFactoryTest.kt                JUnit 4 + Robolectric
app/src/test/java/com/healthaggregator/sync/SyncableTablesTest.kt                  JUnit 5
app/src/test/java/com/healthaggregator/sync/RowSerializerTest.kt                   JUnit 4 + Robolectric
app/src/test/java/com/healthaggregator/sync/SyncModelsTest.kt                      JUnit 5
app/src/test/java/com/healthaggregator/sync/SyncClientTest.kt                      JUnit 4 + Robolectric + MockWebServer
app/src/test/java/com/healthaggregator/sync/SyncRepositoryTest.kt                  JUnit 4 + Robolectric
```

### New — Laptop daemon

```
healthaggregator-laptop-daemon/daemon.py                  FastAPI app, 5 routes
healthaggregator-laptop-daemon/sync_logic.py              Merge logic (INSERT OR IGNORE + LWW)
healthaggregator-laptop-daemon/schema.py                  Apply SQL, manage PRAGMA user_version
healthaggregator-laptop-daemon/config.py                  DB path, bind host, token, port constants
healthaggregator-laptop-daemon/row_io.py                  Read/write rows as column dicts for each syncable table
healthaggregator-laptop-daemon/requirements.txt           fastapi, uvicorn, pytest, httpx (for TestClient)
healthaggregator-laptop-daemon/Makefile                   install / run / test / install-launchd / uninstall-launchd
healthaggregator-laptop-daemon/launchd/com.healthaggregator.syncd.plist
healthaggregator-laptop-daemon/README.md                  Setup, bootstrap, pairing, troubleshooting
healthaggregator-laptop-daemon/tests/__init__.py
healthaggregator-laptop-daemon/tests/conftest.py          Shared fixtures
healthaggregator-laptop-daemon/tests/test_config.py
healthaggregator-laptop-daemon/tests/test_schema.py
healthaggregator-laptop-daemon/tests/test_sync_logic.py
healthaggregator-laptop-daemon/tests/test_row_io.py
healthaggregator-laptop-daemon/tests/test_daemon.py
healthaggregator-laptop-daemon/tests/fixtures/migrations/1_to_2.sql    Copy of Android assets for self-contained tests
healthaggregator-laptop-daemon/tests/fixtures/migrations/2_to_3.sql
healthaggregator-laptop-daemon/tests/fixtures/migrations/3_to_4.sql
healthaggregator-laptop-daemon/tests/fixtures/migrations/4_to_5.sql
healthaggregator-laptop-daemon/tests/fixtures/sample_rows.json         ~20 rows per table for round-trip tests
```

---

## Phase 1 — Migration Refactor (schema as shared source of truth)

**Goal:** Move migration SQL out of Kotlin `execSQL(...)` string literals and into plain `.sql` asset files. Kotlin `Migration` objects become thin wrappers that read the asset and execute each statement. No behavior change — existing app on v5 still boots; fresh install still runs 1→5.

This is the only Phase that touches existing code. Get it right and all later phases are additive.

### Task 1.1: Create feature branch + set up assets/migrations directory

**Files:**
- Create: `app/src/main/assets/migrations/.gitkeep` (placeholder so the directory is tracked)

- [ ] **Step 1: Create the branch off main**

```bash
cd /Users/blackcolours/dev/work/HealthAggregator
git checkout main
git pull --ff-only
git checkout -b feat/delta-laptop-sync
```

- [ ] **Step 2: Create the assets/migrations directory**

```bash
mkdir -p healthaggregator-android/app/src/main/assets/migrations
touch healthaggregator-android/app/src/main/assets/migrations/.gitkeep
```

- [ ] **Step 3: Verify directory is tracked, commit**

```bash
git add healthaggregator-android/app/src/main/assets/migrations/.gitkeep
git commit -m "Phase 1 setup: add assets/migrations directory"
```

Expected: commit lands with a single placeholder file. The `.gitkeep` will be deleted in Task 1.2 when real `.sql` files land.

### Task 1.2: Extract existing migrations to SQL asset files

**Files:**
- Create: `app/src/main/assets/migrations/1_to_2.sql`
- Create: `app/src/main/assets/migrations/2_to_3.sql`
- Create: `app/src/main/assets/migrations/3_to_4.sql`
- Create: `app/src/main/assets/migrations/4_to_5.sql`
- Delete: `app/src/main/assets/migrations/.gitkeep`

Extract SQL verbatim from `app/src/main/java/com/healthaggregator/data/AppDatabaseMigrations.kt`. Do NOT rewrite or optimize any statement — each file must contain semantically identical SQL to what the existing Kotlin migrations run today, or the instrumented migration test will fail.

- [ ] **Step 1: Create `1_to_2.sql`**

`app/src/main/assets/migrations/1_to_2.sql`:
```sql
ALTER TABLE lab_observations ADD COLUMN serviceRequestReference TEXT;
ALTER TABLE lab_observations ADD COLUMN serviceRequestDisplay TEXT;
CREATE INDEX IF NOT EXISTS index_lab_observations_serviceRequestReference ON lab_observations (serviceRequestReference);
```

- [ ] **Step 2: Create `2_to_3.sql`**

`app/src/main/assets/migrations/2_to_3.sql`:
```sql
ALTER TABLE lab_observations ADD COLUMN canonicalPanelName TEXT;
ALTER TABLE lab_observations ADD COLUMN canonicalTestName TEXT;
CREATE INDEX IF NOT EXISTS index_lab_observations_canonicalPanelName ON lab_observations (canonicalPanelName);
CREATE INDEX IF NOT EXISTS index_lab_observations_canonicalTestName ON lab_observations (canonicalTestName);
```

- [ ] **Step 3: Create `3_to_4.sql`**

`app/src/main/assets/migrations/3_to_4.sql`:
```sql
CREATE TABLE IF NOT EXISTS chat_conversations (
	id TEXT NOT NULL PRIMARY KEY,
	title TEXT NOT NULL,
	createdAt INTEGER NOT NULL,
	updatedAt INTEGER NOT NULL,
	snapshotText TEXT,
	snapshotGeneratedAt INTEGER,
	modelId TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS index_chat_conversations_updatedAt ON chat_conversations (updatedAt);
CREATE TABLE IF NOT EXISTS chat_messages (
	id TEXT NOT NULL PRIMARY KEY,
	conversationId TEXT NOT NULL,
	role TEXT NOT NULL,
	content TEXT NOT NULL,
	toolCallsJson TEXT,
	toolCallId TEXT,
	modelId TEXT,
	createdAt INTEGER NOT NULL,
	FOREIGN KEY (conversationId) REFERENCES chat_conversations(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId_createdAt ON chat_messages (conversationId, createdAt);
```

- [ ] **Step 4: Create `4_to_5.sql`**

`app/src/main/assets/migrations/4_to_5.sql`:
```sql
CREATE TABLE IF NOT EXISTS vitals_observations_new (
	`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
	`sourceSystem` TEXT NOT NULL,
	`sourceName` TEXT NOT NULL,
	`fhirReference` TEXT NOT NULL,
	`resourceId` TEXT NOT NULL,
	`patientFhirId` TEXT,
	`loincCode` TEXT,
	`code` TEXT NOT NULL,
	`displayName` TEXT NOT NULL,
	`numericValue` REAL,
	`unit` TEXT,
	`componentCode` TEXT NOT NULL,
	`effectiveAt` INTEGER,
	`importedAt` INTEGER NOT NULL
);
INSERT INTO vitals_observations_new (
	id, sourceSystem, sourceName, fhirReference, resourceId, patientFhirId,
	loincCode, code, displayName, numericValue, unit, componentCode,
	effectiveAt, importedAt
)
SELECT
	id, sourceSystem, sourceName, fhirReference, resourceId, patientFhirId,
	loincCode, code, displayName, numericValue, unit, COALESCE(componentCode, ''),
	effectiveAt, importedAt
FROM vitals_observations
WHERE id IN (
	SELECT MAX(id) FROM vitals_observations
	GROUP BY sourceSystem, fhirReference, COALESCE(componentCode, '')
);
DROP TABLE vitals_observations;
ALTER TABLE vitals_observations_new RENAME TO vitals_observations;
CREATE UNIQUE INDEX IF NOT EXISTS index_vitals_observations_sourceSystem_fhirReference_componentCode ON vitals_observations (sourceSystem, fhirReference, componentCode);
CREATE INDEX IF NOT EXISTS index_vitals_observations_loincCode_effectiveAt ON vitals_observations (loincCode, effectiveAt);
```

- [ ] **Step 5: Delete the placeholder and commit**

```bash
rm healthaggregator-android/app/src/main/assets/migrations/.gitkeep
git add healthaggregator-android/app/src/main/assets/migrations/
git commit -m "Phase 1: extract migration SQL to assets (1_to_2 through 4_to_5)"
```

Expected: commit lands with 4 `.sql` files and no other changes. Existing app still works because `AppDatabaseMigrations.kt` hasn't changed yet.

### Task 1.3: Write MigrationFactory with tests

**Files:**
- Create: `app/src/main/java/com/healthaggregator/data/MigrationFactory.kt`
- Create: `app/src/test/java/com/healthaggregator/data/MigrationFactoryTest.kt`

`MigrationFactory` takes a `Context` at construction, and exposes `load(from: Int, to: Int): Migration` that returns a `Migration` whose `migrate()` reads `assets/migrations/{from}_to_{to}.sql`, splits the content on `;`, and executes each non-empty trimmed statement. Missing assets are a programmer error (fast fail with `IllegalStateException`).

- [ ] **Step 1: Write the test first**

`app/src/test/java/com/healthaggregator/data/MigrationFactoryTest.kt`:
```kotlin
package com.healthaggregator.data

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.database.sqlite.SQLiteDatabase
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationFactoryTest {
	private val ctx: Context = ApplicationProvider.getApplicationContext()

	@Test
	fun `load returns Migration that applies real 3_to_4 SQL`() {
		val factory = MigrationFactory(ctx)
		val migration = factory.load(3, 4)
		assertEquals(3, migration.startVersion)
		assertEquals(4, migration.endVersion)

		val dbFile = File.createTempFile("mf_test", ".db")
		val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
		try {
			val helperDb = ContextCompat(ctx).wrap(db)
			migration.migrate(helperDb)
			val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='chat_conversations'", null)
			assertTrue(cursor.moveToFirst())
			cursor.close()
		} finally {
			db.close()
			dbFile.delete()
		}
	}

	@Test
	fun `load throws IllegalStateException when asset missing`() {
		val factory = MigrationFactory(ctx)
		try {
			factory.load(99, 100)
			fail("Expected IllegalStateException")
		} catch (e: IllegalStateException) {
			assertTrue(e.message!!.contains("99_to_100.sql"))
		}
	}
}
```

Note: `ContextCompat` above is a test-only wrapper that adapts a raw `SQLiteDatabase` to `SupportSQLiteDatabase`. Simpler approach — use Room's `MigrationTestHelper` in instrumented tests for the real thing, and for Robolectric use the sqlite-framework `FrameworkSQLiteOpenHelper` directly:

Replace the test body with this cleaner version:

```kotlin
package com.healthaggregator.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationFactoryTest {
	private val ctx: Context = ApplicationProvider.getApplicationContext()
	private lateinit var helper: SupportSQLiteOpenHelper
	private lateinit var dbFile: File

	@Before
	fun setUp() {
		dbFile = File.createTempFile("mf_test", ".db").also { it.delete() }
		val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
			.name(dbFile.absolutePath)
			.callback(object : SupportSQLiteOpenHelper.Callback(1) {
				override fun onCreate(db: SupportSQLiteDatabase) {
					db.execSQL("CREATE TABLE dummy (id INTEGER PRIMARY KEY)")
				}
				override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
			})
			.build()
		helper = FrameworkSQLiteOpenHelperFactory().create(config)
	}

	@After
	fun tearDown() {
		helper.close()
		dbFile.delete()
	}

	@Test
	fun `load returns Migration with correct versions`() {
		val factory = MigrationFactory(ctx)
		val migration = factory.load(3, 4)
		assertEquals(3, migration.startVersion)
		assertEquals(4, migration.endVersion)
	}

	@Test
	fun `load executes SQL from asset file`() {
		val factory = MigrationFactory(ctx)
		val migration = factory.load(3, 4)
		migration.migrate(helper.writableDatabase)
		helper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='chat_conversations'").use {
			assertTrue(it.moveToFirst())
		}
	}

	@Test
	fun `load throws IllegalStateException when asset missing`() {
		val factory = MigrationFactory(ctx)
		try {
			factory.load(99, 100)
			fail("Expected IllegalStateException")
		} catch (e: IllegalStateException) {
			assertTrue(e.message!!.contains("99_to_100.sql"))
		}
	}
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.data.MigrationFactoryTest"`
Expected: FAIL with "unresolved reference: MigrationFactory"

- [ ] **Step 3: Implement MigrationFactory**

`app/src/main/java/com/healthaggregator/data/MigrationFactory.kt`:
```kotlin
package com.healthaggregator.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.FileNotFoundException

/**
 * Loads migration SQL from `assets/migrations/{from}_to_{to}.sql` and produces Room Migration objects.
 *
 * The SQL files are the single source of truth for schema changes, shared between the Android app
 * (this class) and the laptop sync daemon (which reads the same files from the repo, or receives
 * them over HTTP during a sync).
 *
 * Missing assets are a programmer error: a Migration is wired in AppDatabaseMigrations.kt that
 * references an asset the engineer forgot to create. Fail fast.
 */
@Singleton
class MigrationFactory @Inject constructor(@ApplicationContext private val ctx: Context) {

	fun load(from: Int, to: Int): Migration = object : Migration(from, to) {
		override fun migrate(db: SupportSQLiteDatabase) {
			val assetPath = "migrations/${from}_to_${to}.sql"
			val sql = try {
				ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
			} catch (e: FileNotFoundException) {
				throw IllegalStateException("Migration asset missing: $assetPath", e)
			}
			splitStatements(sql).forEach { db.execSQL(it) }
		}
	}

	internal fun splitStatements(sql: String): List<String> =
		sql.split(';').map { it.trim() }.filter { it.isNotEmpty() }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.data.MigrationFactoryTest"`
Expected: PASS (3 tests green)

- [ ] **Step 5: Commit**

```bash
git add healthaggregator-android/app/src/main/java/com/healthaggregator/data/MigrationFactory.kt \
        healthaggregator-android/app/src/test/java/com/healthaggregator/data/MigrationFactoryTest.kt
git commit -m "Phase 1: MigrationFactory loads SQL from assets"
```

### Task 1.4: Refactor AppDatabaseMigrations.kt to use the factory

**Files:**
- Modify: `app/src/main/java/com/healthaggregator/data/AppDatabaseMigrations.kt`
- Modify: `app/src/main/java/com/healthaggregator/di/DatabaseModule.kt`

The refactor is mechanical. Replace each hand-coded `Migration` object with a function that takes the factory and returns `factory.load(n, m)`. `DatabaseModule` injects the factory and calls each helper.

- [ ] **Step 1: Rewrite AppDatabaseMigrations.kt**

Replace entire contents of `app/src/main/java/com/healthaggregator/data/AppDatabaseMigrations.kt`:

```kotlin
package com.healthaggregator.data

import androidx.room.migration.Migration

/**
 * Migrations are defined in `app/src/main/assets/migrations/{N}_to_{M}.sql`.
 * MigrationFactory loads them at runtime. This file wires each pair to the factory.
 *
 * To add a new migration:
 *   1. Create `app/src/main/assets/migrations/{currentVersion}_to_{currentVersion+1}.sql`.
 *   2. Add a line to `allMigrations` below.
 *   3. Bump `@Database(version = ...)` in AppDatabase.kt.
 *   4. Add an `@AutoMigration` or manual SQL as appropriate, all in SQL.
 */
fun allMigrations(factory: MigrationFactory): Array<Migration> = arrayOf(
	factory.load(1, 2),
	factory.load(2, 3),
	factory.load(3, 4),
	factory.load(4, 5),
)
```

- [ ] **Step 2: Update DatabaseModule.kt**

Replace `provideDatabase` in `app/src/main/java/com/healthaggregator/di/DatabaseModule.kt`:

```kotlin
@Provides
@Singleton
fun provideDatabase(
	@ApplicationContext ctx: Context,
	factory: MigrationFactory,
): AppDatabase =
	Room.databaseBuilder(ctx, AppDatabase::class.java, "healthaggregator.db")
		.addMigrations(*allMigrations(factory))
		.build()
```

Also update the import block — remove the 4 individual `MIGRATION_*` imports since those constants no longer exist; add imports for `MigrationFactory` and `allMigrations` (both live in `com.healthaggregator.data`). Final imports should include:

```kotlin
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.MigrationFactory
import com.healthaggregator.data.allMigrations
```

- [ ] **Step 3: Build + run existing Android tests**

Run: `cd healthaggregator-android && ./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. All existing tests green. The 4 removed `MIGRATION_*` constants are only referenced from `DatabaseModule.kt` — verified via grep:

```bash
grep -rn "MIGRATION_[0-9]_[0-9]" healthaggregator-android/app/src/
```

Expected: zero hits outside this task's changes.

- [ ] **Step 4: Run instrumented migration test on connected device**

Precondition: SM-S906U connected via USB with USB debugging on.

Run:
```bash
cd healthaggregator-android
./gradlew :app:connectedDebugAndroidTest --tests "com.healthaggregator.data.AppDatabaseMigrationTest"
```

Expected: PASS. If the test fails, the refactor is not behavior-preserving — investigate before proceeding.

- [ ] **Step 5: Commit**

```bash
git add healthaggregator-android/app/src/main/java/com/healthaggregator/data/AppDatabaseMigrations.kt \
        healthaggregator-android/app/src/main/java/com/healthaggregator/di/DatabaseModule.kt
git commit -m "Phase 1: route Room migrations through MigrationFactory"
```

### Task 1.5: Phase 1 gate

- [ ] **Step 1: Full build + unit tests**

```bash
cd healthaggregator-android
./gradlew clean :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. All tests green (matching count from main pre-δ, now + 3 new tests from `MigrationFactoryTest`).

- [ ] **Step 2: Instrumented migration test**

```bash
cd healthaggregator-android
./gradlew :app:connectedDebugAndroidTest
```

Expected: PASS on SM-S906U.

- [ ] **Step 3: Manual smoke on device**

Install the built APK, launch the app. Verify:
- [ ] App opens to Home without crash.
- [ ] Records tab shows existing labs / vitals.
- [ ] Assistant tab opens prior conversations.
- [ ] Pressing "Sync now" on a data source works (no regression in existing sync).

- [ ] **Step 4: Phase 1 complete — summary commit**

```bash
git commit --allow-empty -m "Phase 1 gate: migration refactor complete — assets + factory"
```

Phase 1 ships the foundation. No new user-visible feature yet; schema v5 behavior is exactly preserved. Later phases add the laptop sync daemon and wire the phone to it.

---

**Test scope note:** This is a personal-use app. Tests in later phases are deliberately minimal — one representative test per component, not exhaustive matrices. Smoke-level wire-format coverage is enough; skip defensive "what if null" tests and per-route auth-error permutations. TDD discipline (write test → fail → implement → pass → commit) stays, but the test itself is small.

---

## Phase 2 — Laptop Daemon Skeleton

**Goal:** Stand up `healthaggregator-laptop-daemon/` as a Python/FastAPI project with the two lowest-risk routes (`/sync/health` and `/sync/version`), bearer-token auth middleware, and a `schema.py` module that can apply arbitrary SQL to a target SQLite file. No sync logic yet — just the scaffold that all later phases build on.

### Task 2.1: Daemon project scaffold

**Files:**
- Create: `healthaggregator-laptop-daemon/requirements.txt`
- Create: `healthaggregator-laptop-daemon/Makefile`
- Create: `healthaggregator-laptop-daemon/config.py`
- Create: `healthaggregator-laptop-daemon/tests/__init__.py`
- Create: `healthaggregator-laptop-daemon/tests/conftest.py`
- Create: `healthaggregator-laptop-daemon/.gitignore`

- [ ] **Step 1: requirements.txt**

`healthaggregator-laptop-daemon/requirements.txt`:
```
fastapi>=0.115,<0.116
uvicorn[standard]>=0.30,<0.31
pytest>=8,<9
httpx>=0.27,<0.28
```

- [ ] **Step 2: Makefile**

`healthaggregator-laptop-daemon/Makefile`:
```make
.PHONY: install run test install-launchd uninstall-launchd

VENV := .venv
PYTHON := $(VENV)/bin/python
PIP := $(VENV)/bin/pip
PYTEST := $(VENV)/bin/pytest
UVICORN := $(VENV)/bin/uvicorn

install:
	python3 -m venv $(VENV)
	$(PIP) install --upgrade pip
	$(PIP) install -r requirements.txt

run:
	$(UVICORN) daemon:app --host 0.0.0.0 --port 8719

test:
	$(PYTEST) -v tests/

install-launchd:
	@echo "(Phase 7 will wire this up.)"

uninstall-launchd:
	@echo "(Phase 7 will wire this up.)"
```

- [ ] **Step 3: config.py**

`healthaggregator-laptop-daemon/config.py`:
```python
"""Runtime configuration loaded from environment variables with sensible defaults."""
from __future__ import annotations

import os
import secrets
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Config:
    data_dir: Path
    db_path: Path
    token_path: Path
    bind_host: str
    bind_port: int
    daemon_version: str

    @staticmethod
    def from_env() -> "Config":
        data_dir = Path(os.environ.get("HA_DATA_DIR", str(Path.home() / "HealthAggregatorData")))
        return Config(
            data_dir=data_dir,
            db_path=data_dir / "healthaggregator.db",
            token_path=data_dir / "sync.token",
            bind_host=os.environ.get("HA_BIND_HOST", "0.0.0.0"),
            bind_port=int(os.environ.get("HA_BIND_PORT", "8719")),
            daemon_version="0.1.0",
        )


def load_or_create_token(token_path: Path) -> str:
    """Return the persisted token, creating it (and the parent directory) if absent."""
    if token_path.exists():
        return token_path.read_text().strip()
    token_path.parent.mkdir(parents=True, exist_ok=True)
    token = secrets.token_urlsafe(32)
    token_path.write_text(token)
    token_path.chmod(0o600)
    return token
```

- [ ] **Step 4: tests/__init__.py (empty) + tests/conftest.py**

`healthaggregator-laptop-daemon/tests/__init__.py`: empty file.

`healthaggregator-laptop-daemon/tests/conftest.py`:
```python
"""Shared pytest fixtures."""
from __future__ import annotations

import os
from pathlib import Path

import pytest

FIXTURES_DIR = Path(__file__).parent / "fixtures"


@pytest.fixture
def tmp_data_dir(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Path:
    """Isolated HA_DATA_DIR per test."""
    monkeypatch.setenv("HA_DATA_DIR", str(tmp_path))
    return tmp_path
```

- [ ] **Step 5: .gitignore**

`healthaggregator-laptop-daemon/.gitignore`:
```
.venv/
__pycache__/
*.pyc
.pytest_cache/
.coverage
*.db
*.db-journal
```

- [ ] **Step 6: Install + commit**

```bash
cd healthaggregator-laptop-daemon
make install
```

Expected: `.venv/` created, deps installed, no errors.

```bash
git add healthaggregator-laptop-daemon/
git commit -m "Phase 2: daemon scaffold — requirements, Makefile, config, .gitignore"
```

### Task 2.2: Token and health endpoints with auth middleware

**Files:**
- Create: `healthaggregator-laptop-daemon/daemon.py`
- Create: `healthaggregator-laptop-daemon/tests/test_daemon.py`

- [ ] **Step 1: Write the core test first**

`healthaggregator-laptop-daemon/tests/test_daemon.py`:
```python
from pathlib import Path

from fastapi.testclient import TestClient


def _make_client(tmp_data_dir: Path) -> TestClient:
    from daemon import make_app
    app = make_app()
    return TestClient(app)


def test_health_returns_ok(tmp_data_dir: Path) -> None:
    client = _make_client(tmp_data_dir)
    response = client.get("/sync/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"


def test_version_requires_auth(tmp_data_dir: Path) -> None:
    client = _make_client(tmp_data_dir)
    response = client.get("/sync/version")
    assert response.status_code == 401


def test_version_with_valid_token_returns_payload(tmp_data_dir: Path) -> None:
    from config import Config, load_or_create_token
    cfg = Config.from_env()
    token = load_or_create_token(cfg.token_path)
    client = _make_client(tmp_data_dir)
    response = client.get("/sync/version", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200
    body = response.json()
    assert "schema_version" in body
    assert body["daemon_version"] == "0.1.0"
```

- [ ] **Step 2: Run tests and watch them fail**

Run: `cd healthaggregator-laptop-daemon && make test`
Expected: tests fail with `ModuleNotFoundError: No module named 'daemon'`.

- [ ] **Step 3: Implement daemon.py**

`healthaggregator-laptop-daemon/daemon.py`:
```python
"""FastAPI app exposing the sync API.

Phase 2 scope: /sync/health (unauthenticated) and /sync/version (authenticated).
Later phases add /sync/push, /sync/pull, /sync/migrate.
"""
from __future__ import annotations

import sqlite3
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, Request, status

from config import Config, load_or_create_token


def _read_schema_version(db_path: Path) -> int:
    if not db_path.exists():
        return 0
    with sqlite3.connect(db_path) as conn:
        row = conn.execute("PRAGMA user_version").fetchone()
        return int(row[0]) if row else 0


def make_app() -> FastAPI:
    cfg = Config.from_env()
    token = load_or_create_token(cfg.token_path)
    app = FastAPI(title="HealthAggregator Sync Daemon", version=cfg.daemon_version)
    app.state.config = cfg
    app.state.token = token

    def require_token(request: Request) -> None:
        header = request.headers.get("authorization", "")
        if not header.startswith("Bearer "):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="missing_bearer_token")
        supplied = header.removeprefix("Bearer ").strip()
        if supplied != app.state.token:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="bad_token")

    @app.get("/sync/health")
    def health() -> dict:
        return {
            "status": "ok",
            "db_path": str(cfg.db_path),
            "db_exists": cfg.db_path.exists(),
        }

    @app.get("/sync/version", dependencies=[Depends(require_token)])
    def version() -> dict:
        return {
            "schema_version": _read_schema_version(cfg.db_path),
            "daemon_version": cfg.daemon_version,
        }

    return app


app = make_app()
```

- [ ] **Step 4: Run tests — all pass**

Run: `cd healthaggregator-laptop-daemon && make test`
Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
git add healthaggregator-laptop-daemon/daemon.py healthaggregator-laptop-daemon/tests/test_daemon.py
git commit -m "Phase 2: /sync/health + /sync/version endpoints with bearer-token auth"
```

### Task 2.3: schema.py — apply SQL, track user_version

**Files:**
- Create: `healthaggregator-laptop-daemon/schema.py`
- Create: `healthaggregator-laptop-daemon/tests/test_schema.py`
- Create: `healthaggregator-laptop-daemon/tests/fixtures/migrations/{1_to_2,2_to_3,3_to_4,4_to_5}.sql` (copy of the Android assets)

The daemon needs to apply SQL migrations when the phone is ahead. This module is used by Phase 5's `/sync/migrate` route. Testing now keeps it decoupled.

- [ ] **Step 1: Copy Android migration assets into test fixtures**

```bash
mkdir -p healthaggregator-laptop-daemon/tests/fixtures/migrations
cp healthaggregator-android/app/src/main/assets/migrations/*.sql \
   healthaggregator-laptop-daemon/tests/fixtures/migrations/
```

These copies exist so daemon tests are self-contained. The production daemon never reads them — it receives SQL over HTTP from the phone.

- [ ] **Step 2: Write the one core test**

`healthaggregator-laptop-daemon/tests/test_schema.py`:
```python
import sqlite3
from pathlib import Path

from tests.conftest import FIXTURES_DIR


def test_apply_migrations_bumps_user_version_and_creates_tables(tmp_path: Path) -> None:
    from schema import apply_migration

    db_path = tmp_path / "test.db"
    # Seed with an empty DB at version 1 (so 1→2 is the first applicable migration).
    with sqlite3.connect(db_path) as conn:
        # Required initial tables so ALTER TABLE in 1_to_2 has something to mutate.
        conn.execute(
            """CREATE TABLE lab_observations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sourceSystem TEXT NOT NULL,
                fhirReference TEXT NOT NULL
            )"""
        )
        conn.execute(
            """CREATE TABLE vitals_observations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sourceSystem TEXT NOT NULL,
                sourceName TEXT NOT NULL,
                fhirReference TEXT NOT NULL,
                resourceId TEXT NOT NULL,
                patientFhirId TEXT,
                loincCode TEXT,
                code TEXT NOT NULL,
                displayName TEXT NOT NULL,
                numericValue REAL,
                unit TEXT,
                componentCode TEXT,
                effectiveAt INTEGER,
                importedAt INTEGER NOT NULL
            )"""
        )
        conn.execute("PRAGMA user_version = 1")

    fixtures_dir = FIXTURES_DIR / "migrations"
    for (frm, to) in [(1, 2), (2, 3), (3, 4), (4, 5)]:
        sql = (fixtures_dir / f"{frm}_to_{to}.sql").read_text()
        apply_migration(db_path, from_version=frm, to_version=to, sql=sql)

    with sqlite3.connect(db_path) as conn:
        (version,) = conn.execute("PRAGMA user_version").fetchone()
        assert version == 5
        tables = {row[0] for row in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        assert {"lab_observations", "vitals_observations", "chat_conversations", "chat_messages"} <= tables


def test_apply_migration_rejects_version_mismatch(tmp_path: Path) -> None:
    from schema import SchemaMismatch, apply_migration

    db_path = tmp_path / "test.db"
    with sqlite3.connect(db_path) as conn:
        conn.execute("PRAGMA user_version = 3")

    try:
        apply_migration(db_path, from_version=5, to_version=6, sql="SELECT 1;")
    except SchemaMismatch as e:
        assert "expected_from=5" in str(e)
    else:
        raise AssertionError("expected SchemaMismatch")


def test_apply_migration_rolls_back_on_sql_error(tmp_path: Path) -> None:
    from schema import apply_migration

    db_path = tmp_path / "test.db"
    with sqlite3.connect(db_path) as conn:
        conn.execute("PRAGMA user_version = 1")

    try:
        apply_migration(db_path, from_version=1, to_version=2, sql="THIS IS NOT SQL;")
    except sqlite3.Error:
        pass

    with sqlite3.connect(db_path) as conn:
        (version,) = conn.execute("PRAGMA user_version").fetchone()
        assert version == 1
```

- [ ] **Step 3: Watch it fail**

Run: `cd healthaggregator-laptop-daemon && make test`
Expected: `ModuleNotFoundError: No module named 'schema'`.

- [ ] **Step 4: Implement schema.py**

`healthaggregator-laptop-daemon/schema.py`:
```python
"""Schema migration: apply SQL received from the phone, bump PRAGMA user_version."""
from __future__ import annotations

import sqlite3
from pathlib import Path


class SchemaMismatch(Exception):
    """Raised when the daemon's current user_version doesn't match the phone's expectation."""


def split_statements(sql: str) -> list[str]:
    """Split `;`-separated SQL statements, dropping comments and empty segments."""
    return [s.strip() for s in sql.split(";") if s.strip()]


def current_version(db_path: Path) -> int:
    if not db_path.exists():
        return 0
    with sqlite3.connect(db_path) as conn:
        (version,) = conn.execute("PRAGMA user_version").fetchone()
        return int(version)


def apply_migration(db_path: Path, from_version: int, to_version: int, sql: str) -> None:
    """Apply migration SQL and bump user_version atomically.

    Raises SchemaMismatch if the DB is not at `from_version`.
    Rolls back on any sqlite3 error (transaction-scoped).
    """
    with sqlite3.connect(db_path) as conn:
        (actual_from,) = conn.execute("PRAGMA user_version").fetchone()
        if int(actual_from) != from_version:
            raise SchemaMismatch(
                f"expected_from={from_version} actual_from={actual_from}"
            )
        try:
            conn.execute("BEGIN")
            for stmt in split_statements(sql):
                conn.execute(stmt)
            conn.execute(f"PRAGMA user_version = {int(to_version)}")
            conn.commit()
        except sqlite3.Error:
            conn.rollback()
            raise
```

- [ ] **Step 5: Run all tests — green**

Run: `cd healthaggregator-laptop-daemon && make test`
Expected: 6 passed.

- [ ] **Step 6: Commit**

```bash
git add healthaggregator-laptop-daemon/schema.py \
        healthaggregator-laptop-daemon/tests/test_schema.py \
        healthaggregator-laptop-daemon/tests/fixtures/migrations/
git commit -m "Phase 2: schema.py applies SQL with user_version + rollback on error"
```

### Task 2.4: Phase 2 gate

- [ ] **Step 1: Full daemon test run**

```bash
cd healthaggregator-laptop-daemon && make test
```
Expected: 6 passed.

- [ ] **Step 2: Manual daemon boot**

```bash
cd healthaggregator-laptop-daemon && make run
```

In another terminal:
```bash
curl -s http://localhost:8719/sync/health | python3 -m json.tool
```

Expected:
```json
{
    "status": "ok",
    "db_path": "/Users/blackcolours/HealthAggregatorData/healthaggregator.db",
    "db_exists": false
}
```

- [ ] **Step 3: Auth check**

```bash
TOKEN=$(cat ~/HealthAggregatorData/sync.token)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8719/sync/version | python3 -m json.tool
curl -s -i http://localhost:8719/sync/version | head -n 1
```

Expected: first curl returns `{"schema_version": 0, "daemon_version": "0.1.0"}`, second returns `HTTP/1.1 401 Unauthorized`.

Kill the daemon (Ctrl-C).

- [ ] **Step 4: Phase 2 summary commit**

```bash
git commit --allow-empty -m "Phase 2 gate: daemon responds to /health and authed /version"
```

---

## Phase 3 — Row Serialization + Push (phone → laptop)

**Goal:** Phone reads every syncable table, serializes rows to JSON, posts them to `POST /sync/push`, daemon merges with INSERT OR IGNORE (or LWW for `chat_conversations`). No pull yet, no schema-drift handling yet.

### Task 3.1: SyncableTables enum + MergeStrategy

**Files:**
- Create: `app/src/main/java/com/healthaggregator/sync/SyncableTables.kt`
- Create: `app/src/test/java/com/healthaggregator/sync/SyncableTablesTest.kt`

- [ ] **Step 1: Write a one-test smoke**

`app/src/test/java/com/healthaggregator/sync/SyncableTablesTest.kt`:
```kotlin
package com.healthaggregator.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncableTablesTest {
	@Test
	fun `all twelve syncable tables present, chat_conversations is LWW`() {
		assertEquals(12, SyncableTable.entries.size)
		val chatConv = SyncableTable.entries.first { it.tableName == "chat_conversations" }
		assertTrue(chatConv.mergeStrategy is MergeStrategy.LastWriteWinsOn)
		val lwwCols = SyncableTable.entries.count { it.mergeStrategy is MergeStrategy.LastWriteWinsOn }
		assertEquals(1, lwwCols, "exactly one table uses LWW")
	}
}
```

- [ ] **Step 2: Watch fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.sync.SyncableTablesTest"`
Expected: unresolved reference.

- [ ] **Step 3: Implement**

`app/src/main/java/com/healthaggregator/sync/SyncableTables.kt`:
```kotlin
package com.healthaggregator.sync

sealed interface MergeStrategy {
	object InsertOrIgnore : MergeStrategy
	data class LastWriteWinsOn(val timestampColumn: String) : MergeStrategy
}

/**
 * The set of tables that participate in phone↔laptop sync.
 * See Docs/specs/2026-04-18-laptop-sync-design.md §"Syncable vs non-syncable tables".
 */
enum class SyncableTable(val tableName: String, val mergeStrategy: MergeStrategy) {
	PATIENT_RECORDS("patient_records", MergeStrategy.InsertOrIgnore),
	LAB_OBSERVATIONS("lab_observations", MergeStrategy.InsertOrIgnore),
	VITALS_OBSERVATIONS("vitals_observations", MergeStrategy.InsertOrIgnore),
	CONDITION_RECORDS("condition_records", MergeStrategy.InsertOrIgnore),
	MEDICATION_RECORDS("medication_records", MergeStrategy.InsertOrIgnore),
	ALLERGY_RECORDS("allergy_records", MergeStrategy.InsertOrIgnore),
	ENCOUNTER_RECORDS("encounter_records", MergeStrategy.InsertOrIgnore),
	DOCUMENT_RECORDS("document_records", MergeStrategy.InsertOrIgnore),
	DIAGNOSTIC_REPORT_RECORDS("diagnostic_report_records", MergeStrategy.InsertOrIgnore),
	SOURCE_RECORDS("source_records", MergeStrategy.InsertOrIgnore),
	CHAT_CONVERSATIONS("chat_conversations", MergeStrategy.LastWriteWinsOn("updatedAt")),
	CHAT_MESSAGES("chat_messages", MergeStrategy.InsertOrIgnore),
}
```

- [ ] **Step 4: Pass + commit**

Run: `./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.sync.SyncableTablesTest"`
Expected: PASS.

```bash
git add healthaggregator-android/app/src/main/java/com/healthaggregator/sync/SyncableTables.kt \
        healthaggregator-android/app/src/test/java/com/healthaggregator/sync/SyncableTablesTest.kt
git commit -m "Phase 3: SyncableTable enum + MergeStrategy"
```

### Task 3.2: SyncModels — wire protocol DTOs

**Files:**
- Create: `app/src/main/java/com/healthaggregator/sync/SyncModels.kt`

No test here (trivially parsed by kotlinx.serialization, covered by SyncClient integration test later).

- [ ] **Step 1: Implement**

`app/src/main/java/com/healthaggregator/sync/SyncModels.kt`:
```kotlin
package com.healthaggregator.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Rows are serialized as `JsonObject` maps (column name → JSON primitive/null). */
typealias SyncRow = JsonObject

@Serializable
data class VersionResponse(
	val schema_version: Int,
	val daemon_version: String,
)

@Serializable
data class PushRequest(
	val batch_id: String,
	val rows_by_table: Map<String, List<SyncRow>>,
)

@Serializable
data class PushResponse(
	val inserted_by_table: Map<String, Int>,
	val ignored_by_table: Map<String, Int>,
)

@Serializable
data class PullResponse(
	val rows_by_table: Map<String, List<SyncRow>>,
)

@Serializable
data class MigrateRequest(
	val from_version: Int,
	val to_version: Int,
	val sql: String,
)

@Serializable
data class MigrateResponse(
	val applied: List<AppliedMigration>,
)

@Serializable
data class AppliedMigration(
	val from: Int,
	val to: Int,
)
```

- [ ] **Step 2: Build + commit**

```bash
cd healthaggregator-android && ./gradlew :app:compileDebugKotlin
git add healthaggregator-android/app/src/main/java/com/healthaggregator/sync/SyncModels.kt
git commit -m "Phase 3: SyncModels DTOs for wire protocol"
```

### Task 3.3: RowSerializer — entity ↔ JSON

**Files:**
- Create: `app/src/main/java/com/healthaggregator/sync/RowSerializer.kt`
- Create: `app/src/test/java/com/healthaggregator/sync/RowSerializerTest.kt`

`RowSerializer` converts between Room entities and `SyncRow` (JsonObject). One representative round-trip test — LabObservation — confirms the pattern works; other entities follow the same shape and any mistakes surface in the integration test later.

- [ ] **Step 1: Core round-trip test**

`app/src/test/java/com/healthaggregator/sync/RowSerializerTest.kt`:
```kotlin
package com.healthaggregator.sync

import com.healthaggregator.data.entities.LabObservation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RowSerializerTest {
	private val serializer = RowSerializer()

	@Test
	fun `LabObservation round-trips through JSON`() {
		val original = LabObservation(
			id = 42L,
			sourceSystem = "EpicCleveland",
			sourceName = "Cleveland Clinic",
			fhirReference = "Observation/123",
			resourceId = "123",
			patientFhirId = "Patient/5",
			loincCode = "2345-7",
			testName = "Glucose",
			numericValue = 95.0,
			unit = "mg/dL",
			referenceLow = 70.0,
			referenceHigh = 99.0,
			effectiveAt = Instant.parse("2025-11-12T10:30:00Z"),
			status = "final",
			importedAt = Instant.parse("2026-04-18T12:00:00Z"),
			canonicalTestName = "glucose_fasting",
		)
		val row = serializer.toRow(original)
		val restored = serializer.fromLabRow(row)
		assertEquals(original, restored)
	}
}
```

- [ ] **Step 2: Watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.sync.RowSerializerTest"`
Expected: unresolved reference.

- [ ] **Step 3: Implement RowSerializer**

`app/src/main/java/com/healthaggregator/sync/RowSerializer.kt`:
```kotlin
package com.healthaggregator.sync

import com.healthaggregator.data.entities.AllergyRecord
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import com.healthaggregator.data.entities.ConditionRecord
import com.healthaggregator.data.entities.DiagnosticReportRecord
import com.healthaggregator.data.entities.DocumentRecord
import com.healthaggregator.data.entities.EncounterRecord
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.entities.MedicationRecord
import com.healthaggregator.data.entities.PatientRecord
import com.healthaggregator.data.entities.SourceRecord
import com.healthaggregator.data.entities.VitalsObservation
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

/**
 * Converts Room entities to JSON row maps and back.
 *
 * Strategy: reflective mapping over data-class properties. Each primitive is wrapped as a
 * JsonPrimitive; `Instant` becomes an epoch-millis Long; null fields emit JsonNull.
 *
 * Entity-specific `fromXxxRow` helpers exist because we need to know the target type.
 * Call sites (SyncRepository.mergePull) dispatch on SyncableTable.
 */
@Singleton
class RowSerializer @Inject constructor() {

	fun toRow(entity: Any): JsonObject = buildJsonObject {
		entity::class.memberProperties.forEach { prop ->
			@Suppress("UNCHECKED_CAST")
			val value = (prop as kotlin.reflect.KProperty1<Any, *>).get(entity)
			put(prop.name, toJsonValue(value))
		}
	}

	private fun toJsonValue(v: Any?): kotlinx.serialization.json.JsonElement = when (v) {
		null -> JsonNull
		is String -> JsonPrimitive(v)
		is Boolean -> JsonPrimitive(v)
		is Int -> JsonPrimitive(v)
		is Long -> JsonPrimitive(v)
		is Double -> JsonPrimitive(v)
		is Float -> JsonPrimitive(v.toDouble())
		is Instant -> JsonPrimitive(v.toEpochMilli())
		else -> JsonPrimitive(v.toString())
	}

	private fun JsonObject.str(name: String): String? =
		(get(name) as? JsonPrimitive)?.takeIf { it != JsonNull }?.jsonPrimitive?.content

	private fun JsonObject.strReq(name: String): String =
		str(name) ?: error("missing non-null String field '$name' in row")

	private fun JsonObject.long(name: String): Long? =
		(get(name) as? JsonPrimitive)?.longOrNull

	private fun JsonObject.longReq(name: String): Long =
		long(name) ?: error("missing non-null Long field '$name' in row")

	private fun JsonObject.double(name: String): Double? =
		(get(name) as? JsonPrimitive)?.doubleOrNull

	private fun JsonObject.bool(name: String): Boolean? =
		(get(name) as? JsonPrimitive)?.booleanOrNull

	private fun JsonObject.instant(name: String): Instant? =
		long(name)?.let { Instant.ofEpochMilli(it) }

	fun fromLabRow(r: JsonObject) = LabObservation(
		id = r.long("id") ?: 0L,
		sourceSystem = r.strReq("sourceSystem"),
		sourceName = r.strReq("sourceName"),
		fhirReference = r.strReq("fhirReference"),
		resourceId = r.strReq("resourceId"),
		patientFhirId = r.str("patientFhirId"),
		diagnosticReportReference = r.str("diagnosticReportReference"),
		loincCode = r.str("loincCode"),
		testName = r.strReq("testName"),
		numericValue = r.double("numericValue"),
		textValue = r.str("textValue"),
		unit = r.str("unit"),
		referenceLow = r.double("referenceLow"),
		referenceHigh = r.double("referenceHigh"),
		referenceText = r.str("referenceText"),
		interpretation = r.str("interpretation"),
		effectiveAt = r.instant("effectiveAt"),
		status = r.str("status") ?: "",
		importedAt = r.instant("importedAt") ?: Instant.EPOCH,
		serviceRequestReference = r.str("serviceRequestReference"),
		serviceRequestDisplay = r.str("serviceRequestDisplay"),
		canonicalPanelName = r.str("canonicalPanelName"),
		canonicalTestName = r.str("canonicalTestName"),
	)

	fun fromVitalsRow(r: JsonObject) = VitalsObservation(
		id = r.long("id") ?: 0L,
		sourceSystem = r.strReq("sourceSystem"),
		sourceName = r.strReq("sourceName"),
		fhirReference = r.strReq("fhirReference"),
		resourceId = r.strReq("resourceId"),
		patientFhirId = r.str("patientFhirId"),
		loincCode = r.str("loincCode"),
		code = r.strReq("code"),
		displayName = r.strReq("displayName"),
		numericValue = r.double("numericValue"),
		unit = r.str("unit"),
		componentCode = r.str("componentCode") ?: "",
		effectiveAt = r.instant("effectiveAt"),
		importedAt = r.instant("importedAt") ?: Instant.EPOCH,
	)

	fun fromChatConversationRow(r: JsonObject) = ChatConversation(
		id = r.strReq("id"),
		title = r.strReq("title"),
		createdAt = r.instant("createdAt") ?: Instant.EPOCH,
		updatedAt = r.instant("updatedAt") ?: Instant.EPOCH,
		snapshotText = r.str("snapshotText"),
		snapshotGeneratedAt = r.instant("snapshotGeneratedAt"),
		modelId = r.strReq("modelId"),
	)

	fun fromChatMessageRow(r: JsonObject) = ChatMessage(
		id = r.strReq("id"),
		conversationId = r.strReq("conversationId"),
		role = r.strReq("role"),
		content = r.strReq("content"),
		toolCallsJson = r.str("toolCallsJson"),
		toolCallId = r.str("toolCallId"),
		modelId = r.str("modelId"),
		createdAt = r.instant("createdAt") ?: Instant.EPOCH,
	)

	// Source/patient/condition/medication/allergy/encounter/document/diagnosticReport:
	// these follow the same pattern as fromLabRow. Implement alongside the pull path in Phase 4
	// when the concrete shape is needed. Phase 3 only pushes via `toRow(entity)`.
}
```

- [ ] **Step 4: Pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.sync.RowSerializerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add healthaggregator-android/app/src/main/java/com/healthaggregator/sync/RowSerializer.kt \
        healthaggregator-android/app/src/test/java/com/healthaggregator/sync/RowSerializerTest.kt
git commit -m "Phase 3: RowSerializer reflective toRow + typed fromXRow helpers"
```

### Task 3.4: sync_logic.py — merge on daemon side

**Files:**
- Create: `healthaggregator-laptop-daemon/sync_logic.py`
- Create: `healthaggregator-laptop-daemon/tests/test_sync_logic.py`

- [ ] **Step 1: One core test covering both merge strategies**

`healthaggregator-laptop-daemon/tests/test_sync_logic.py`:
```python
import sqlite3
from pathlib import Path


def _setup_db(db_path: Path) -> None:
    with sqlite3.connect(db_path) as conn:
        conn.execute(
            """CREATE TABLE lab_observations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sourceSystem TEXT NOT NULL,
                fhirReference TEXT NOT NULL,
                testName TEXT NOT NULL,
                status TEXT NOT NULL,
                importedAt INTEGER NOT NULL,
                UNIQUE(sourceSystem, fhirReference)
            )"""
        )
        conn.execute(
            """CREATE TABLE chat_conversations (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                modelId TEXT NOT NULL
            )"""
        )


def test_insert_or_ignore_dedupes_on_natural_key(tmp_path: Path) -> None:
    from sync_logic import merge_rows, MergeStrategy

    db_path = tmp_path / "test.db"
    _setup_db(db_path)

    row = {
        "sourceSystem": "EpicCleveland",
        "fhirReference": "Observation/abc",
        "testName": "Glucose",
        "status": "final",
        "importedAt": 1_700_000_000_000,
    }
    with sqlite3.connect(db_path) as conn:
        inserted1, ignored1 = merge_rows(
            conn, "lab_observations", [row], MergeStrategy.INSERT_OR_IGNORE,
            natural_key=("sourceSystem", "fhirReference"),
        )
        inserted2, ignored2 = merge_rows(
            conn, "lab_observations", [row], MergeStrategy.INSERT_OR_IGNORE,
            natural_key=("sourceSystem", "fhirReference"),
        )
    assert inserted1 == 1 and ignored1 == 0
    assert inserted2 == 0 and ignored2 == 1


def test_chat_conversations_lww_replaces_when_newer(tmp_path: Path) -> None:
    from sync_logic import merge_rows, MergeStrategy

    db_path = tmp_path / "test.db"
    _setup_db(db_path)

    old = {
        "id": "c1", "title": "Old title", "createdAt": 1_700_000_000_000,
        "updatedAt": 1_700_000_100_000, "modelId": "gpt-5",
    }
    new = {
        "id": "c1", "title": "New title", "createdAt": 1_700_000_000_000,
        "updatedAt": 1_700_000_200_000, "modelId": "gpt-5",
    }
    older_stale = {
        "id": "c1", "title": "Stale title", "createdAt": 1_700_000_000_000,
        "updatedAt": 1_700_000_050_000, "modelId": "gpt-5",
    }
    with sqlite3.connect(db_path) as conn:
        merge_rows(conn, "chat_conversations", [old], MergeStrategy.LWW_UPDATED_AT, natural_key=("id",))
        merge_rows(conn, "chat_conversations", [new], MergeStrategy.LWW_UPDATED_AT, natural_key=("id",))
        merge_rows(conn, "chat_conversations", [older_stale], MergeStrategy.LWW_UPDATED_AT, natural_key=("id",))
        (title,) = conn.execute("SELECT title FROM chat_conversations WHERE id='c1'").fetchone()
    assert title == "New title"
```

- [ ] **Step 2: Watch fail**

Run: `cd healthaggregator-laptop-daemon && make test`
Expected: `ModuleNotFoundError: No module named 'sync_logic'`.

- [ ] **Step 3: Implement sync_logic.py**

`healthaggregator-laptop-daemon/sync_logic.py`:
```python
"""Row merge logic — called by /sync/push and internally by /sync/pull.

INSERT OR IGNORE is used for append-only tables (all FHIR + chat_messages).
LWW (last-write-wins on updatedAt) is used for chat_conversations, where the title
can be edited via the drawer rename feature on the phone.
"""
from __future__ import annotations

import sqlite3
from enum import Enum


class MergeStrategy(Enum):
    INSERT_OR_IGNORE = "insert_or_ignore"
    LWW_UPDATED_AT = "lww_updated_at"


def merge_rows(
    conn: sqlite3.Connection,
    table_name: str,
    rows: list[dict],
    strategy: MergeStrategy,
    natural_key: tuple[str, ...],
) -> tuple[int, int]:
    """Merge rows into `table_name`. Returns (inserted_count, ignored_count)."""
    if not rows:
        return 0, 0

    inserted = 0
    ignored = 0

    if strategy is MergeStrategy.INSERT_OR_IGNORE:
        for row in rows:
            # Strip auto-increment primary key (named 'id' and Int) so SQLite assigns a fresh one.
            # Tables with textual 'id' PKs (chat_messages) keep their id.
            payload = _strip_autoincrement_id(table_name, row)
            cols = list(payload.keys())
            placeholders = ",".join(["?"] * len(cols))
            col_list = ",".join(f'"{c}"' for c in cols)
            sql = f'INSERT OR IGNORE INTO "{table_name}" ({col_list}) VALUES ({placeholders})'
            cur = conn.execute(sql, tuple(payload[c] for c in cols))
            if cur.rowcount == 1:
                inserted += 1
            else:
                ignored += 1
        conn.commit()
        return inserted, ignored

    # LWW_UPDATED_AT
    for row in rows:
        key_clause = " AND ".join([f'"{k}" = ?' for k in natural_key])
        key_values = tuple(row[k] for k in natural_key)
        existing = conn.execute(
            f'SELECT updatedAt FROM "{table_name}" WHERE {key_clause}', key_values
        ).fetchone()
        if existing is None:
            payload = _strip_autoincrement_id(table_name, row)
            cols = list(payload.keys())
            placeholders = ",".join(["?"] * len(cols))
            col_list = ",".join(f'"{c}"' for c in cols)
            conn.execute(
                f'INSERT INTO "{table_name}" ({col_list}) VALUES ({placeholders})',
                tuple(payload[c] for c in cols),
            )
            inserted += 1
        else:
            if int(row["updatedAt"]) > int(existing[0]):
                update_cols = [c for c in row if c not in natural_key]
                set_clause = ",".join(f'"{c}" = ?' for c in update_cols)
                conn.execute(
                    f'UPDATE "{table_name}" SET {set_clause} WHERE {key_clause}',
                    tuple(row[c] for c in update_cols) + key_values,
                )
                inserted += 1
            else:
                ignored += 1
    conn.commit()
    return inserted, ignored


_AUTOINCREMENT_TABLES = {
    "patient_records", "lab_observations", "vitals_observations",
    "condition_records", "medication_records", "allergy_records",
    "encounter_records", "document_records", "diagnostic_report_records",
    "source_records",
}


def _strip_autoincrement_id(table_name: str, row: dict) -> dict:
    """Drop the `id` field for tables with autoincrement PKs so SQLite assigns locally."""
    if table_name not in _AUTOINCREMENT_TABLES:
        return row
    return {k: v for k, v in row.items() if k != "id"}


def natural_key_for(table_name: str) -> tuple[str, ...]:
    """Return the natural-key columns for a syncable table. See spec §'Syncable tables'."""
    return {
        "patient_records": ("sourceSystem", "fhirReference"),
        "lab_observations": ("sourceSystem", "fhirReference"),
        "vitals_observations": ("sourceSystem", "fhirReference", "componentCode"),
        "condition_records": ("sourceSystem", "fhirReference"),
        "medication_records": ("sourceSystem", "fhirReference"),
        "allergy_records": ("sourceSystem", "fhirReference"),
        "encounter_records": ("sourceSystem", "fhirReference"),
        "document_records": ("sourceSystem", "fhirReference"),
        "diagnostic_report_records": ("sourceSystem", "fhirReference"),
        "source_records": ("sourceSystem", "resourceType", "resourceId"),
        "chat_conversations": ("id",),
        "chat_messages": ("id",),
    }[table_name]


def strategy_for(table_name: str) -> MergeStrategy:
    if table_name == "chat_conversations":
        return MergeStrategy.LWW_UPDATED_AT
    return MergeStrategy.INSERT_OR_IGNORE
```

- [ ] **Step 4: Pass + commit**

Run: `cd healthaggregator-laptop-daemon && make test`
Expected: 8 passed (6 from Phase 2 + 2 new).

```bash
git add healthaggregator-laptop-daemon/sync_logic.py \
        healthaggregator-laptop-daemon/tests/test_sync_logic.py
git commit -m "Phase 3: sync_logic merge — INSERT OR IGNORE + LWW for chat_conversations"
```

### Task 3.5: /sync/push route

**Files:**
- Modify: `healthaggregator-laptop-daemon/daemon.py` (add route)
- Modify: `healthaggregator-laptop-daemon/tests/test_daemon.py` (add route test)

- [ ] **Step 1: Add test**

Append to `healthaggregator-laptop-daemon/tests/test_daemon.py`:
```python
def test_push_inserts_rows_into_db(tmp_data_dir) -> None:
    import sqlite3
    from config import Config, load_or_create_token

    cfg = Config.from_env()
    # Seed DB with the minimal schema /sync/push will merge into.
    with sqlite3.connect(cfg.db_path) as conn:
        conn.execute(
            """CREATE TABLE lab_observations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sourceSystem TEXT NOT NULL,
                fhirReference TEXT NOT NULL,
                testName TEXT NOT NULL,
                status TEXT NOT NULL,
                importedAt INTEGER NOT NULL,
                UNIQUE(sourceSystem, fhirReference)
            )"""
        )
    token = load_or_create_token(cfg.token_path)

    from daemon import make_app
    from fastapi.testclient import TestClient
    client = TestClient(make_app())

    payload = {
        "batch_id": "abc-1",
        "rows_by_table": {
            "lab_observations": [
                {
                    "id": 1,
                    "sourceSystem": "EpicCleveland",
                    "fhirReference": "Observation/1",
                    "testName": "Glucose",
                    "status": "final",
                    "importedAt": 1_700_000_000_000,
                }
            ]
        },
    }
    r = client.post(
        "/sync/push",
        json=payload,
        headers={"Authorization": f"Bearer {token}"},
    )
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["inserted_by_table"]["lab_observations"] == 1
    assert body["ignored_by_table"]["lab_observations"] == 0

    # Second push is a no-op dedupe.
    r2 = client.post(
        "/sync/push",
        json=payload,
        headers={"Authorization": f"Bearer {token}"},
    )
    body2 = r2.json()
    assert body2["inserted_by_table"]["lab_observations"] == 0
    assert body2["ignored_by_table"]["lab_observations"] == 1
```

- [ ] **Step 2: Add route in daemon.py**

In `healthaggregator-laptop-daemon/daemon.py`, after the `/sync/version` endpoint, add:

```python
    @app.post("/sync/push", dependencies=[Depends(require_token)])
    def push(payload: dict) -> dict:
        from sync_logic import merge_rows, natural_key_for, strategy_for

        rows_by_table = payload.get("rows_by_table", {})
        inserted: dict[str, int] = {}
        ignored: dict[str, int] = {}
        with sqlite3.connect(cfg.db_path) as conn:
            for table_name, rows in rows_by_table.items():
                i, ig = merge_rows(
                    conn,
                    table_name,
                    list(rows),
                    strategy_for(table_name),
                    natural_key_for(table_name),
                )
                inserted[table_name] = i
                ignored[table_name] = ig
        return {"inserted_by_table": inserted, "ignored_by_table": ignored}
```

- [ ] **Step 3: Run tests — pass + commit**

Run: `cd healthaggregator-laptop-daemon && make test`
Expected: 9 passed.

```bash
git add healthaggregator-laptop-daemon/daemon.py healthaggregator-laptop-daemon/tests/test_daemon.py
git commit -m "Phase 3: /sync/push route with INSERT OR IGNORE + LWW merge"
```

### Task 3.6: Android-side SyncClient.push()

**Files:**
- Create: `app/src/main/java/com/healthaggregator/sync/SyncClient.kt`
- Create: `app/src/test/java/com/healthaggregator/sync/SyncClientTest.kt`

- [ ] **Step 1: Smoke test with MockWebServer**

`app/src/test/java/com/healthaggregator/sync/SyncClientTest.kt`:
```kotlin
package com.healthaggregator.sync

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncClientTest {
	private lateinit var server: MockWebServer
	private lateinit var client: SyncClient

	@Before
	fun setUp() {
		server = MockWebServer().also { it.start() }
		val creds = SyncCredentials(baseUrl = server.url("/").toString().trimEnd('/'), token = "test-token")
		client = SyncClient(
			http = OkHttpClient(),
			json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
			credentialsProvider = { creds },
		)
	}

	@After
	fun tearDown() { server.shutdown() }

	@Test
	fun `push posts JSON and parses response`() = runBlocking {
		server.enqueue(
			MockResponse().setResponseCode(200).setBody(
				"""{"inserted_by_table":{"lab_observations":3},"ignored_by_table":{"lab_observations":0}}"""
			)
		)
		val payload = PushRequest(
			batch_id = "b1",
			rows_by_table = mapOf(
				"lab_observations" to listOf(buildJsonObject { put("fhirReference", "Observation/1") }),
			),
		)
		val response = client.push(payload)
		assertEquals(3, response.inserted_by_table["lab_observations"])
		val recorded = server.takeRequest()
		assertEquals("POST", recorded.method)
		assertEquals("/sync/push", recorded.path)
		assertEquals("Bearer test-token", recorded.getHeader("Authorization"))
	}
}
```

- [ ] **Step 2: Watch fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.sync.SyncClientTest"`
Expected: unresolved reference.

- [ ] **Step 3: Implement SyncClient**

`app/src/main/java/com/healthaggregator/sync/SyncClient.kt`:
```kotlin
package com.healthaggregator.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class SyncCredentials(val baseUrl: String, val token: String)

sealed class SyncError(message: String) : Exception(message) {
	object NotPaired : SyncError("not_paired")
	class Network(cause: Throwable) : SyncError("network_error: ${cause.message}")
	class BadAuth : SyncError("bad_token")
	class DaemonAheadOfPhone(val daemon: Int, val phone: Int)
		: SyncError("daemon_schema_$daemon > phone_schema_$phone")
	class HttpStatus(val code: Int, body: String) : SyncError("http_$code: $body")
}

@Singleton
class SyncClient @Inject constructor(
	private val http: OkHttpClient,
	private val json: Json,
	private val credentialsProvider: () -> SyncCredentials?,
) {
	companion object {
		private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
	}

	suspend fun version(): VersionResponse = get("/sync/version") { VersionResponse.serializer() }

	suspend fun push(payload: PushRequest): PushResponse =
		post("/sync/push", json.encodeToString(payload)) { PushResponse.serializer() }

	private suspend fun <T> get(path: String, deserializer: () -> kotlinx.serialization.KSerializer<T>): T = withContext(Dispatchers.IO) {
		val creds = credentialsProvider() ?: throw SyncError.NotPaired
		val req = Request.Builder()
			.url(creds.baseUrl + path)
			.addHeader("Authorization", "Bearer ${creds.token}")
			.get()
			.build()
		runCall(req, deserializer)
	}

	private suspend fun <T> post(path: String, body: String, deserializer: () -> kotlinx.serialization.KSerializer<T>): T = withContext(Dispatchers.IO) {
		val creds = credentialsProvider() ?: throw SyncError.NotPaired
		val req = Request.Builder()
			.url(creds.baseUrl + path)
			.addHeader("Authorization", "Bearer ${creds.token}")
			.post(body.toRequestBody(JSON_MEDIA))
			.build()
		runCall(req, deserializer)
	}

	private fun <T> runCall(req: Request, deserializer: () -> kotlinx.serialization.KSerializer<T>): T {
		val resp = try {
			http.newCall(req).execute()
		} catch (io: IOException) {
			throw SyncError.Network(io)
		}
		resp.use { r ->
			val bodyStr = r.body?.string() ?: ""
			if (r.code == 401) throw SyncError.BadAuth()
			if (!r.isSuccessful) throw SyncError.HttpStatus(r.code, bodyStr)
			return json.decodeFromString(deserializer(), bodyStr)
		}
	}
}
```

- [ ] **Step 4: Pass + commit**

Run: `./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.sync.SyncClientTest"`
Expected: PASS.

```bash
git add healthaggregator-android/app/src/main/java/com/healthaggregator/sync/SyncClient.kt \
        healthaggregator-android/app/src/test/java/com/healthaggregator/sync/SyncClientTest.kt
git commit -m "Phase 3: SyncClient with push() over OkHttp + bearer-token auth"
```

### Task 3.7: Phase 3 gate

- [ ] **Step 1: Both test suites pass**

```bash
cd healthaggregator-android && ./gradlew :app:testDebugUnitTest
cd ../healthaggregator-laptop-daemon && make test
```

- [ ] **Step 2: End-to-end push smoke (optional — runs daemon locally)**

```bash
cd healthaggregator-laptop-daemon && make run &
# In another shell:
TOKEN=$(cat ~/HealthAggregatorData/sync.token)
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"batch_id":"smoke","rows_by_table":{}}' \
  http://localhost:8719/sync/push | python3 -m json.tool
kill %1
```

Expected: `{"inserted_by_table": {}, "ignored_by_table": {}}`.

- [ ] **Step 3: Phase 3 summary**

```bash
git commit --allow-empty -m "Phase 3 gate: push pipeline works end-to-end"
```

---

## Phase 4 — Pull (laptop → phone) + Full Round-Trip

**Goal:** Daemon returns every row in every syncable table via `GET /sync/pull`. Phone applies the same merge logic locally. `SyncRepository.syncNow()` wires the full flow.

### Task 4.1: Daemon /sync/pull route + row_io.py

**Files:**
- Create: `healthaggregator-laptop-daemon/row_io.py`
- Modify: `healthaggregator-laptop-daemon/daemon.py`

- [ ] **Step 1: Implement row_io.py**

`healthaggregator-laptop-daemon/row_io.py`:
```python
"""Read rows from the laptop DB for /sync/pull. Each row is a column → value dict."""
from __future__ import annotations

import sqlite3
from pathlib import Path

SYNCABLE_TABLES = [
    "patient_records", "lab_observations", "vitals_observations",
    "condition_records", "medication_records", "allergy_records",
    "encounter_records", "document_records", "diagnostic_report_records",
    "source_records", "chat_conversations", "chat_messages",
]


def read_all_rows(db_path: Path) -> dict[str, list[dict]]:
    """Return {table_name: [row_dict, ...]} for every syncable table. Missing tables become []."""
    out: dict[str, list[dict]] = {}
    if not db_path.exists():
        return {t: [] for t in SYNCABLE_TABLES}
    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        existing = {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        for table in SYNCABLE_TABLES:
            if table not in existing:
                out[table] = []
                continue
            rows = [dict(r) for r in conn.execute(f'SELECT * FROM "{table}"')]
            out[table] = rows
    return out
```

- [ ] **Step 2: Add /sync/pull route**

In `healthaggregator-laptop-daemon/daemon.py`, after the `/sync/push` endpoint:

```python
    @app.get("/sync/pull", dependencies=[Depends(require_token)])
    def pull() -> dict:
        from row_io import read_all_rows
        return {"rows_by_table": read_all_rows(cfg.db_path)}
```

- [ ] **Step 3: One smoke test**

Add to `healthaggregator-laptop-daemon/tests/test_daemon.py`:
```python
def test_pull_returns_rows_for_existing_table(tmp_data_dir) -> None:
    import sqlite3
    from config import Config, load_or_create_token

    cfg = Config.from_env()
    with sqlite3.connect(cfg.db_path) as conn:
        conn.execute(
            """CREATE TABLE patient_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sourceSystem TEXT NOT NULL,
                fhirReference TEXT NOT NULL,
                UNIQUE(sourceSystem, fhirReference)
            )"""
        )
        conn.execute(
            "INSERT INTO patient_records (sourceSystem, fhirReference) VALUES (?, ?)",
            ("EpicCleveland", "Patient/99"),
        )
    token = load_or_create_token(cfg.token_path)

    from daemon import make_app
    from fastapi.testclient import TestClient
    client = TestClient(make_app())

    r = client.get("/sync/pull", headers={"Authorization": f"Bearer {token}"})
    assert r.status_code == 200
    body = r.json()
    assert "patient_records" in body["rows_by_table"]
    assert body["rows_by_table"]["patient_records"][0]["fhirReference"] == "Patient/99"
```

- [ ] **Step 4: Pass + commit**

Run: `cd healthaggregator-laptop-daemon && make test`
Expected: 10 passed.

```bash
git add healthaggregator-laptop-daemon/row_io.py \
        healthaggregator-laptop-daemon/daemon.py \
        healthaggregator-laptop-daemon/tests/test_daemon.py
git commit -m "Phase 4: /sync/pull returns rows per syncable table"
```

### Task 4.2: Android SyncClient.pull()

**Files:**
- Modify: `app/src/main/java/com/healthaggregator/sync/SyncClient.kt`

- [ ] **Step 1: Add pull() method**

In `SyncClient.kt`, add after `push()`:

```kotlin
	suspend fun pull(): PullResponse = get("/sync/pull") { PullResponse.serializer() }
```

No dedicated test — the pattern matches `push()` and the round-trip is validated by Task 4.3.

- [ ] **Step 2: Compile + commit**

```bash
cd healthaggregator-android && ./gradlew :app:compileDebugKotlin
git add healthaggregator-android/app/src/main/java/com/healthaggregator/sync/SyncClient.kt
git commit -m "Phase 4: SyncClient.pull() method"
```

### Task 4.3: SyncRepository.syncNow() orchestrator

**Files:**
- Create: `app/src/main/java/com/healthaggregator/sync/SyncRepository.kt`
- Create: `app/src/test/java/com/healthaggregator/sync/SyncRepositoryTest.kt`

- [ ] **Step 1: End-to-end test with a fake SyncClient**

`app/src/test/java/com/healthaggregator/sync/SyncRepositoryTest.kt`:
```kotlin
package com.healthaggregator.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.PatientRecord
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncRepositoryTest {
	private lateinit var db: AppDatabase
	private lateinit var fakeClient: FakeSyncClient
	private lateinit var repo: SyncRepository

	@Before
	fun setUp() {
		val ctx: Context = ApplicationProvider.getApplicationContext()
		db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
		fakeClient = FakeSyncClient()
		repo = SyncRepository(
			client = fakeClient,
			db = db,
			serializer = RowSerializer(),
			isPairedProvider = { true },
		)
	}

	@After
	fun tearDown() { db.close() }

	@Test
	fun `syncNow pushes local rows then merges pull response`() = runBlocking {
		db.patientDao().upsert(
			PatientRecord(
				id = 0L,
				sourceSystem = "EpicCleveland",
				sourceName = "Cleveland Clinic",
				fhirReference = "Patient/5",
				resourceId = "5",
				givenName = "Jesse",
				familyName = "Coble",
				birthDate = null,
				gender = "male",
				importedAt = java.time.Instant.now(),
			)
		)

		// Daemon has a different patient not on the phone.
		fakeClient.pullResponse = PullResponse(
			rows_by_table = mapOf(
				"patient_records" to listOf(buildJsonObject {
					put("id", JsonPrimitive(0))
					put("sourceSystem", JsonPrimitive("LabCorp"))
					put("sourceName", JsonPrimitive("LabCorp"))
					put("fhirReference", JsonPrimitive("Patient/lc1"))
					put("resourceId", JsonPrimitive("lc1"))
					put("givenName", JsonPrimitive("Jesse"))
					put("familyName", JsonPrimitive("Coble"))
					put("birthDate", JsonPrimitive(null as String?))
					put("gender", JsonPrimitive("male"))
					put("importedAt", JsonPrimitive(1_700_000_000_000L))
				})
			),
		)

		val result = repo.syncNow().getOrThrow()
		assertEquals(1, result.pushedRowsByTable["patient_records"] ?: 0)
		assertEquals(1, result.pulledRowsByTable["patient_records"] ?: 0)

		val allPatients = db.patientDao().getAllSnapshot()
		assertEquals(2, allPatients.size)
		assertTrue(allPatients.any { it.sourceSystem == "LabCorp" })
	}

	private class FakeSyncClient : SyncClient(okhttp3.OkHttpClient(), kotlinx.serialization.json.Json { }, { null }) {
		var pushedBatches = mutableListOf<PushRequest>()
		var pullResponse = PullResponse(rows_by_table = emptyMap())
		override suspend fun push(payload: PushRequest): PushResponse {
			pushedBatches.add(payload)
			val counts = payload.rows_by_table.mapValues { it.value.size }
			return PushResponse(inserted_by_table = counts, ignored_by_table = counts.mapValues { 0 })
		}
		override suspend fun pull(): PullResponse = pullResponse
	}
}
```

- [ ] **Step 2: Make SyncClient methods `open`**

In `app/src/main/java/com/healthaggregator/sync/SyncClient.kt`, mark `push` and `pull` as `open`:
```kotlin
open class SyncClient @Inject constructor(...) {
	// ...
	open suspend fun push(payload: PushRequest): PushResponse = ...
	open suspend fun pull(): PullResponse = ...
}
```

Change `@Singleton class SyncClient ...` to `@Singleton open class SyncClient ...`.

- [ ] **Step 3: Implement SyncRepository**

`app/src/main/java/com/healthaggregator/sync/SyncRepository.kt`:
```kotlin
package com.healthaggregator.sync

import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.*
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
	val pushedRowsByTable: Map<String, Int>,
	val pulledRowsByTable: Map<String, Int>,
	val ignoredRowsByTable: Map<String, Int>,
	val durationMs: Long,
)

@Singleton
class SyncRepository @Inject constructor(
	private val client: SyncClient,
	private val db: AppDatabase,
	private val serializer: RowSerializer,
	private val isPairedProvider: () -> Boolean,
) {
	suspend fun syncNow(): Result<SyncResult> = runCatching {
		if (!isPairedProvider()) throw SyncError.NotPaired
		val start = System.currentTimeMillis()

		val pushPayload = buildPushPayload()
		val pushResp = client.push(pushPayload)

		val pullResp = client.pull()
		val pulled = mergePull(pullResp)

		SyncResult(
			pushedRowsByTable = pushResp.inserted_by_table,
			pulledRowsByTable = pulled,
			ignoredRowsByTable = pushResp.ignored_by_table,
			durationMs = System.currentTimeMillis() - start,
		)
	}

	private suspend fun buildPushPayload(): PushRequest {
		val rows = mutableMapOf<String, List<SyncRow>>()
		rows["patient_records"] = db.patientDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["lab_observations"] = db.labDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["vitals_observations"] = db.vitalsDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["condition_records"] = db.conditionDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["medication_records"] = db.medicationDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["allergy_records"] = db.allergyDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["encounter_records"] = db.encounterDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["document_records"] = db.documentDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["diagnostic_report_records"] = db.diagnosticReportDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["source_records"] = db.sourceRecordDao().getAllSnapshot().map { serializer.toRow(it) }
		rows["chat_conversations"] = db.chatDao().getAllConversationsSnapshot().map { serializer.toRow(it) }
		rows["chat_messages"] = db.chatDao().getAllMessagesSnapshot().map { serializer.toRow(it) }
		return PushRequest(batch_id = UUID.randomUUID().toString(), rows_by_table = rows)
	}

	private suspend fun mergePull(resp: PullResponse): Map<String, Int> {
		val counts = mutableMapOf<String, Int>()
		resp.rows_by_table.forEach { (tableName, rows) ->
			val n = when (tableName) {
				"patient_records" -> mergePatients(rows)
				"lab_observations" -> mergeLabs(rows)
				"vitals_observations" -> mergeVitals(rows)
				"chat_conversations" -> mergeChatConversations(rows)
				"chat_messages" -> mergeChatMessages(rows)
				// Remaining FHIR tables follow the same pattern. Add helpers here as needed;
				// for the initial ship, the DAOs already use onConflict=REPLACE so upsertAll
				// gives us effective INSERT OR IGNORE when combined with unique indexes.
				else -> mergeGeneric(tableName, rows)
			}
			counts[tableName] = n
		}
		return counts
	}

	private suspend fun mergePatients(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromLabRow(it) } // wrong type on purpose — compile-time catches this when the file is populated; provide the correct helper
		// NOTE for implementer: add fromPatientRow to RowSerializer and call it here.
		return rows.size
	}

	private suspend fun mergeLabs(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromLabRow(it) }
		db.labDao().upsertAll(entities) // DAO uses onConflict=REPLACE + unique(sourceSystem, fhirReference)
		return entities.size
	}

	private suspend fun mergeVitals(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromVitalsRow(it) }
		db.vitalsDao().upsertAll(entities)
		return entities.size
	}

	private suspend fun mergeChatConversations(rows: List<JsonObject>): Int {
		var applied = 0
		for (row in rows) {
			val incoming = serializer.fromChatConversationRow(row)
			val existing = db.chatDao().getConversation(incoming.id)
			if (existing == null || incoming.updatedAt.isAfter(existing.updatedAt)) {
				db.chatDao().upsertConversation(incoming)
				applied++
			}
		}
		return applied
	}

	private suspend fun mergeChatMessages(rows: List<JsonObject>): Int {
		val entities = rows.map { serializer.fromChatMessageRow(it) }
		db.chatDao().upsertMessages(entities)
		return entities.size
	}

	private suspend fun mergeGeneric(tableName: String, rows: List<JsonObject>): Int {
		// Placeholder: the implementer fills in per-DAO merge for the remaining 7 FHIR tables.
		// Each follows the pattern: val entities = rows.map { serializer.fromXRow(it) }; dao.upsertAll(entities); return entities.size.
		return 0
	}
}
```

**Implementer note for Task 4.3:** the `mergePatients`/`mergeGeneric` stubs above are marked for expansion. Fill in `fromPatientRow`, `fromConditionRow`, `fromMedicationRow`, `fromAllergyRow`, `fromEncounterRow`, `fromDocumentRow`, `fromDiagnosticReportRow`, `fromSourceRecordRow` in `RowSerializer` (all follow the same shape as `fromLabRow`) and replace `mergeGeneric` with explicit per-table merges. Verify each table's DAO has a `getAllSnapshot()` method and an `upsertAll(List<T>)` method; add them if missing (they exist for labs/vitals from γ; verify for others).

- [ ] **Step 4: Iterate until compile + test pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.healthaggregator.sync.SyncRepositoryTest"`
Expected: PASS.

If any DAO lacks `getAllSnapshot()` or `upsertAll()`, add them (one-line additions) and commit alongside.

- [ ] **Step 5: Commit**

```bash
git add healthaggregator-android/app/src/main/java/com/healthaggregator/sync/SyncRepository.kt \
        healthaggregator-android/app/src/main/java/com/healthaggregator/sync/SyncClient.kt \
        healthaggregator-android/app/src/main/java/com/healthaggregator/sync/RowSerializer.kt \
        healthaggregator-android/app/src/test/java/com/healthaggregator/sync/SyncRepositoryTest.kt \
        $(git diff --name-only healthaggregator-android/app/src/main/java/com/healthaggregator/data/dao/)
git commit -m "Phase 4: SyncRepository.syncNow() — full push+pull round-trip"
```

### Task 4.4: Phase 4 gate

- [ ] **Step 1: Both sides green**

```bash
cd healthaggregator-android && ./gradlew :app:testDebugUnitTest
cd ../healthaggregator-laptop-daemon && make test
```

- [ ] **Step 2: Full round-trip smoke (optional, laptop + simulated phone)**

Boot daemon, use two curl calls to push then pull:
```bash
cd healthaggregator-laptop-daemon && make run &
TOKEN=$(cat ~/HealthAggregatorData/sync.token)
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"batch_id":"smoke","rows_by_table":{}}' \
  http://localhost:8719/sync/push
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8719/sync/pull | python3 -m json.tool
kill %1
```

Expected: push returns empty counts; pull returns empty table map.

- [ ] **Step 3: Commit**

```bash
git commit --allow-empty -m "Phase 4 gate: round-trip push+pull validated"
```

---

## Phase 5 — Schema-Drift Handling

**Goal:** If the daemon is behind the phone, the phone bundles each needed `.sql` asset and POSTs `/sync/migrate`. Daemon applies, bumps `user_version`, normal push/pull proceeds. If the daemon is ahead (shouldn't happen in normal use), the phone aborts with a typed error.

### Task 5.1: Daemon /sync/migrate route

**Files:**
- Modify: `healthaggregator-laptop-daemon/daemon.py`

- [ ] **Step 1: One focused test**

Append to `healthaggregator-laptop-daemon/tests/test_daemon.py`:
```python
def test_migrate_applies_sql_and_bumps_user_version(tmp_data_dir) -> None:
    import sqlite3
    from config import Config, load_or_create_token

    cfg = Config.from_env()
    with sqlite3.connect(cfg.db_path) as conn:
        conn.execute("PRAGMA user_version = 4")
    token = load_or_create_token(cfg.token_path)

    from daemon import make_app
    from fastapi.testclient import TestClient
    client = TestClient(make_app())

    payload = {
        "from_version": 4,
        "to_version": 5,
        "sql": "CREATE TABLE demo_v5 (id INTEGER PRIMARY KEY);",
    }
    r = client.post(
        "/sync/migrate",
        json=payload,
        headers={"Authorization": f"Bearer {token}"},
    )
    assert r.status_code == 200, r.text

    with sqlite3.connect(cfg.db_path) as conn:
        (version,) = conn.execute("PRAGMA user_version").fetchone()
        assert version == 5
        tables = {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        assert "demo_v5" in tables


def test_migrate_rejects_version_mismatch(tmp_data_dir) -> None:
    import sqlite3
    from config import Config, load_or_create_token

    cfg = Config.from_env()
    with sqlite3.connect(cfg.db_path) as conn:
        conn.execute("PRAGMA user_version = 3")
    token = load_or_create_token(cfg.token_path)

    from daemon import make_app
    from fastapi.testclient import TestClient
    client = TestClient(make_app())

    r = client.post(
        "/sync/migrate",
        json={"from_version": 5, "to_version": 6, "sql": "SELECT 1;"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert r.status_code == 409
```

- [ ] **Step 2: Add the route**

In `daemon.py`, after `/sync/pull`:
```python
    @app.post("/sync/migrate", dependencies=[Depends(require_token)])
    def migrate(payload: dict) -> dict:
        from schema import SchemaMismatch, apply_migration

        try:
            apply_migration(
                cfg.db_path,
                from_version=int(payload["from_version"]),
                to_version=int(payload["to_version"]),
                sql=str(payload["sql"]),
            )
        except SchemaMismatch as e:
            raise HTTPException(status_code=409, detail=str(e))
        except sqlite3.Error as e:
            raise HTTPException(status_code=500, detail=f"sql_error: {e}")

        return {
            "applied": [{"from": int(payload["from_version"]), "to": int(payload["to_version"])}]
        }
```

- [ ] **Step 3: Pass + commit**

Run: `cd healthaggregator-laptop-daemon && make test`
Expected: 12 passed.

```bash
git add healthaggregator-laptop-daemon/daemon.py healthaggregator-laptop-daemon/tests/test_daemon.py
git commit -m "Phase 5: /sync/migrate applies SQL and returns 409 on version mismatch"
```

### Task 5.2: Phone-side drift detection + migrate call

**Files:**
- Modify: `app/src/main/java/com/healthaggregator/sync/SyncClient.kt`
- Modify: `app/src/main/java/com/healthaggregator/sync/SyncRepository.kt`
- Create: `app/src/main/java/com/healthaggregator/sync/MigrationLoader.kt`

- [ ] **Step 1: Add migrate method to SyncClient**

In `SyncClient.kt`, add:
```kotlin
	open suspend fun migrate(payload: MigrateRequest): MigrateResponse =
		post("/sync/migrate", json.encodeToString(payload)) { MigrateResponse.serializer() }
```

- [ ] **Step 2: MigrationLoader to read asset SQL by version pair**

`app/src/main/java/com/healthaggregator/sync/MigrationLoader.kt`:
```kotlin
package com.healthaggregator.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MigrationLoader @Inject constructor(@ApplicationContext private val ctx: Context) {
	fun load(from: Int, to: Int): String {
		val path = "migrations/${from}_to_${to}.sql"
		return try {
			ctx.assets.open(path).bufferedReader().use { it.readText() }
		} catch (e: FileNotFoundException) {
			throw IllegalStateException("Migration asset missing on phone: $path", e)
		}
	}
}
```

- [ ] **Step 3: Wire into SyncRepository**

In `SyncRepository.kt`, add constructor params and extend `syncNow`:

```kotlin
@Singleton
class SyncRepository @Inject constructor(
	private val client: SyncClient,
	private val db: AppDatabase,
	private val serializer: RowSerializer,
	private val migrationLoader: MigrationLoader,
	private val phoneSchemaVersion: Int = 5,
	private val isPairedProvider: () -> Boolean,
) {
	suspend fun syncNow(): Result<SyncResult> = runCatching {
		if (!isPairedProvider()) throw SyncError.NotPaired
		val start = System.currentTimeMillis()

		val version = client.version()
		val daemonSchema = version.schema_version
		val migrationsApplied = mutableListOf<Pair<Int, Int>>()

		if (daemonSchema > phoneSchemaVersion) {
			throw SyncError.DaemonAheadOfPhone(daemonSchema, phoneSchemaVersion)
		}
		if (daemonSchema < phoneSchemaVersion) {
			var current = if (daemonSchema == 0) 1 else daemonSchema
			while (current < phoneSchemaVersion) {
				val sql = migrationLoader.load(current, current + 1)
				client.migrate(MigrateRequest(from_version = current, to_version = current + 1, sql = sql))
				migrationsApplied.add(current to (current + 1))
				current++
			}
		}

		val pushResp = client.push(buildPushPayload())
		val pullResp = client.pull()
		val pulled = mergePull(pullResp)

		SyncResult(
			pushedRowsByTable = pushResp.inserted_by_table,
			pulledRowsByTable = pulled,
			ignoredRowsByTable = pushResp.ignored_by_table,
			durationMs = System.currentTimeMillis() - start,
		)
	}
	// ... rest unchanged
}
```

Add `val migrationsApplied: List<Pair<Int, Int>>` to `SyncResult` and populate it in the return value.

- [ ] **Step 4: Build + commit**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: existing tests still pass; compile clean. The `SyncRepositoryTest` constructor call needs a `FakeMigrationLoader` param added (returns a trivial SQL string), plus `phoneSchemaVersion = 5` and the fake client's `version()` should return `VersionResponse(5, "0.1.0")` (make it return this default). Update the test accordingly — one-line change.

```bash
git add healthaggregator-android/app/src/main/java/com/healthaggregator/sync/ \
        healthaggregator-android/app/src/test/java/com/healthaggregator/sync/SyncRepositoryTest.kt
git commit -m "Phase 5: phone detects daemon schema drift and ships migration SQL"
```

### Task 5.3: Phase 5 gate

- [ ] **Step 1: All green**

```bash
cd healthaggregator-android && ./gradlew :app:testDebugUnitTest
cd ../healthaggregator-laptop-daemon && make test
```

- [ ] **Step 2: Manual drift smoke**

Boot a fresh daemon pointing to a new empty DB, no `PRAGMA user_version` set (defaults to 0):
```bash
HA_DATA_DIR=/tmp/delta-drift-test make run &
TOKEN=$(cat /tmp/delta-drift-test/sync.token)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8719/sync/version
# Expected: {"schema_version": 0, "daemon_version": "0.1.0"}

# Simulate migrate step 1→2:
SQL=$(cat ~/dev/work/HealthAggregator/healthaggregator-android/app/src/main/assets/migrations/3_to_4.sql)
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"from_version\":0,\"to_version\":3,\"sql\":\"PRAGMA user_version=3;\"}" \
  http://localhost:8719/sync/migrate
# (Then run the 3_to_4 file in a follow-up call to advance to 4.)
kill %1
rm -rf /tmp/delta-drift-test
```

This is a manual sanity check, not a required gate. The real validation is the passing test suite.

- [ ] **Step 3: Phase 5 summary commit**

```bash
git commit --allow-empty -m "Phase 5 gate: schema drift handled phone-side"
```

---

## Phase 6 — Pairing UI + Settings Integration

**Goal:** Phone UI to pair with the laptop, show last-sync status, and press "Sync now." All the plumbing exists — this phase is the screens.

### Task 6.1: Extend SecureStorage with laptop credentials

**Files:**
- Modify: `app/src/main/java/com/healthaggregator/util/SecureStorage.kt`

- [ ] **Step 1: Add properties**

In `SecureStorage.kt`, after `disclaimerAcknowledged`:

```kotlin
	var laptopHostname: String?
		get() = prefs.getString(KEY_LAPTOP_HOSTNAME, null)?.ifEmpty { null }
		set(value) = prefs.edit().run { if (value.isNullOrBlank()) remove(KEY_LAPTOP_HOSTNAME) else putString(KEY_LAPTOP_HOSTNAME, value); apply() }

	var laptopToken: String?
		get() = prefs.getString(KEY_LAPTOP_TOKEN, null)?.ifEmpty { null }
		set(value) = prefs.edit().run { if (value.isNullOrBlank()) remove(KEY_LAPTOP_TOKEN) else putString(KEY_LAPTOP_TOKEN, value); apply() }

	var lastSyncAt: Long
		get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
		set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

	var lastSyncSummary: String?
		get() = prefs.getString(KEY_LAST_SYNC_SUMMARY, null)
		set(value) = prefs.edit().run { if (value.isNullOrBlank()) remove(KEY_LAST_SYNC_SUMMARY) else putString(KEY_LAST_SYNC_SUMMARY, value); apply() }

	fun clearLaptopPairing() {
		laptopHostname = null
		laptopToken = null
		lastSyncAt = 0L
		lastSyncSummary = null
	}
```

In the companion object, add constants:

```kotlin
		private const val KEY_LAPTOP_HOSTNAME = "laptop_hostname"
		private const val KEY_LAPTOP_TOKEN = "laptop_token"
		private const val KEY_LAST_SYNC_AT = "last_sync_at"
		private const val KEY_LAST_SYNC_SUMMARY = "last_sync_summary"
```

- [ ] **Step 2: Wire into SyncModule**

`app/src/main/java/com/healthaggregator/di/SyncModule.kt`:
```kotlin
package com.healthaggregator.di

import com.healthaggregator.sync.SyncClient
import com.healthaggregator.sync.SyncCredentials
import com.healthaggregator.util.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
	@Provides
	@Singleton
	fun provideSyncHttp(): OkHttpClient = OkHttpClient.Builder()
		.connectTimeout(10, TimeUnit.SECONDS)
		.readTimeout(60, TimeUnit.SECONDS)
		.callTimeout(120, TimeUnit.SECONDS)
		.build()

	@Provides
	@Singleton
	fun provideSyncClient(http: OkHttpClient, json: Json, secure: SecureStorage): SyncClient =
		SyncClient(http, json) {
			val host = secure.laptopHostname ?: return@SyncClient null
			val token = secure.laptopToken ?: return@SyncClient null
			val baseUrl = if (host.startsWith("http://") || host.startsWith("https://")) host else "http://$host:8719"
			SyncCredentials(baseUrl = baseUrl.trimEnd('/'), token = token)
		}
}
```

- [ ] **Step 3: Build + commit**

```bash
cd healthaggregator-android && ./gradlew :app:compileDebugKotlin
git add healthaggregator-android/app/src/main/java/com/healthaggregator/util/SecureStorage.kt \
        healthaggregator-android/app/src/main/java/com/healthaggregator/di/SyncModule.kt
git commit -m "Phase 6: SecureStorage + SyncModule provide laptop credentials"
```

### Task 6.2: LaptopPairingScreen

**Files:**
- Create: `app/src/main/java/com/healthaggregator/ui/settings/LaptopPairingScreen.kt`
- Create: `app/src/main/java/com/healthaggregator/ui/settings/LaptopPairingViewModel.kt`

- [ ] **Step 1: ViewModel**

`app/src/main/java/com/healthaggregator/ui/settings/LaptopPairingViewModel.kt`:
```kotlin
package com.healthaggregator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.sync.SyncClient
import com.healthaggregator.sync.SyncError
import com.healthaggregator.util.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairingUiState(
	val hostname: String = "",
	val token: String = "",
	val testing: Boolean = false,
	val errorMessage: String? = null,
	val success: Boolean = false,
)

@HiltViewModel
class LaptopPairingViewModel @Inject constructor(
	private val client: SyncClient,
	private val secure: SecureStorage,
) : ViewModel() {
	private val _state = MutableStateFlow(
		PairingUiState(hostname = secure.laptopHostname ?: "", token = secure.laptopToken ?: "")
	)
	val state: StateFlow<PairingUiState> = _state.asStateFlow()

	fun onHostnameChange(h: String) { _state.value = _state.value.copy(hostname = h, errorMessage = null, success = false) }
	fun onTokenChange(t: String) { _state.value = _state.value.copy(token = t, errorMessage = null, success = false) }

	fun testAndSave() {
		viewModelScope.launch {
			val s = _state.value
			if (s.hostname.isBlank() || s.token.isBlank()) {
				_state.value = s.copy(errorMessage = "Both fields required")
				return@launch
			}
			_state.value = s.copy(testing = true, errorMessage = null)
			// Stash credentials so the client can use them, then round-trip a /sync/version call.
			secure.laptopHostname = s.hostname.trim()
			secure.laptopToken = s.token.trim()
			try {
				client.version()
				_state.value = _state.value.copy(testing = false, success = true)
			} catch (e: SyncError.BadAuth) {
				secure.clearLaptopPairing()
				_state.value = _state.value.copy(testing = false, errorMessage = "Bad token — double-check you copied it correctly.")
			} catch (e: SyncError.Network) {
				secure.clearLaptopPairing()
				_state.value = _state.value.copy(testing = false, errorMessage = "Can't reach laptop. Is Tailscale up and the daemon running?")
			} catch (e: Exception) {
				secure.clearLaptopPairing()
				_state.value = _state.value.copy(testing = false, errorMessage = "Pairing failed: ${e.message}")
			}
		}
	}
}
```

- [ ] **Step 2: Compose screen**

`app/src/main/java/com/healthaggregator/ui/settings/LaptopPairingScreen.kt`:
```kotlin
package com.healthaggregator.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaptopPairingScreen(
	onBack: () -> Unit,
	onPaired: () -> Unit,
	viewModel: LaptopPairingViewModel = hiltViewModel(),
) {
	val state by viewModel.state.collectAsStateWithLifecycle()

	LaunchedEffect(state.success) {
		if (state.success) onPaired()
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Pair with laptop") },
				navigationIcon = {
					TextButton(onClick = onBack) { Text("Back") }
				},
			)
		},
	) { padding ->
		Column(
			modifier = Modifier
				.padding(padding)
				.padding(16.dp)
				.fillMaxSize(),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(
				"Enter your laptop's Tailscale hostname (e.g. laptop.tail-abc.ts.net) and the sync token shown by `make run`.",
				style = MaterialTheme.typography.bodyMedium,
			)
			OutlinedTextField(
				value = state.hostname,
				onValueChange = viewModel::onHostnameChange,
				label = { Text("Hostname") },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
			)
			OutlinedTextField(
				value = state.token,
				onValueChange = viewModel::onTokenChange,
				label = { Text("Auth token") },
				singleLine = true,
				modifier = Modifier.fillMaxWidth(),
			)
			if (state.errorMessage != null) {
				Text(
					state.errorMessage!!,
					color = MaterialTheme.colorScheme.error,
					style = MaterialTheme.typography.bodySmall,
				)
			}
			Button(
				onClick = viewModel::testAndSave,
				enabled = !state.testing && state.hostname.isNotBlank() && state.token.isNotBlank(),
				modifier = Modifier.fillMaxWidth(),
			) {
				if (state.testing) {
					CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
					Spacer(Modifier.width(8.dp))
				}
				Text("Test and save")
			}
		}
	}
}
```

- [ ] **Step 3: Build + commit**

```bash
cd healthaggregator-android && ./gradlew :app:compileDebugKotlin
git add healthaggregator-android/app/src/main/java/com/healthaggregator/ui/settings/LaptopPairingScreen.kt \
        healthaggregator-android/app/src/main/java/com/healthaggregator/ui/settings/LaptopPairingViewModel.kt
git commit -m "Phase 6: LaptopPairingScreen with test-and-save validation"
```

### Task 6.3: LaptopSyncSection embedded in SettingsScreen

**Files:**
- Create: `app/src/main/java/com/healthaggregator/ui/settings/LaptopSyncSection.kt`
- Create: `app/src/main/java/com/healthaggregator/ui/settings/LaptopSyncViewModel.kt`
- Modify: `app/src/main/java/com/healthaggregator/ui/settings/SettingsScreen.kt` (embed section)

- [ ] **Step 1: ViewModel**

`app/src/main/java/com/healthaggregator/ui/settings/LaptopSyncViewModel.kt`:
```kotlin
package com.healthaggregator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.sync.SyncError
import com.healthaggregator.sync.SyncRepository
import com.healthaggregator.sync.SyncResult
import com.healthaggregator.util.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LaptopSyncUiState(
	val paired: Boolean,
	val hostname: String?,
	val lastSyncAt: Long,
	val lastSyncSummary: String?,
	val syncing: Boolean = false,
	val errorMessage: String? = null,
)

@HiltViewModel
class LaptopSyncViewModel @Inject constructor(
	private val repo: SyncRepository,
	private val secure: SecureStorage,
) : ViewModel() {
	private val _state = MutableStateFlow(snapshot())
	val state: StateFlow<LaptopSyncUiState> = _state.asStateFlow()

	private fun snapshot() = LaptopSyncUiState(
		paired = secure.laptopHostname != null && secure.laptopToken != null,
		hostname = secure.laptopHostname,
		lastSyncAt = secure.lastSyncAt,
		lastSyncSummary = secure.lastSyncSummary,
	)

	fun refreshFromStorage() { _state.value = snapshot() }

	fun syncNow() {
		viewModelScope.launch {
			_state.value = _state.value.copy(syncing = true, errorMessage = null)
			val result = repo.syncNow()
			result.onSuccess { r ->
				secure.lastSyncAt = System.currentTimeMillis()
				secure.lastSyncSummary = summarize(r)
				_state.value = snapshot().copy(syncing = false)
			}.onFailure { e ->
				_state.value = _state.value.copy(
					syncing = false,
					errorMessage = when (e) {
						is SyncError.NotPaired -> "Not paired yet."
						is SyncError.BadAuth -> "Bad token — re-pair with laptop."
						is SyncError.Network -> "Can't reach laptop. Is Tailscale up?"
						is SyncError.DaemonAheadOfPhone -> "Daemon is on schema v${e.daemon}, phone is on v${e.phone}. Update the app."
						else -> "Sync failed: ${e.message}"
					},
				)
			}
		}
	}

	fun unpair() {
		secure.clearLaptopPairing()
		refreshFromStorage()
	}

	private fun summarize(r: SyncResult): String {
		val pushed = r.pushedRowsByTable.values.sum()
		val pulled = r.pulledRowsByTable.values.sum()
		return "+$pulled from laptop, +$pushed to laptop"
	}
}
```

- [ ] **Step 2: Composable section**

`app/src/main/java/com/healthaggregator/ui/settings/LaptopSyncSection.kt`:
```kotlin
package com.healthaggregator.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@Composable
fun LaptopSyncSection(
	onPair: () -> Unit,
	onOpenSyncLog: () -> Unit,
	viewModel: LaptopSyncViewModel = hiltViewModel(),
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	LaunchedEffect(Unit) { viewModel.refreshFromStorage() }

	Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
		Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text("Laptop Sync", style = MaterialTheme.typography.titleMedium)
			if (!state.paired) {
				Text("Not paired with a laptop yet.", style = MaterialTheme.typography.bodySmall)
				Button(onClick = onPair) { Text("Pair with laptop") }
			} else {
				Text("Paired with ${state.hostname}", style = MaterialTheme.typography.bodyMedium)
				if (state.lastSyncAt > 0) {
					val when_ = DateFormat.getDateTimeInstance().format(Date(state.lastSyncAt))
					Text("Last sync: $when_", style = MaterialTheme.typography.bodySmall)
					state.lastSyncSummary?.let {
						Text(it, style = MaterialTheme.typography.bodySmall)
					}
					TextButton(onClick = onOpenSyncLog) { Text("View sync log") }
				}
				if (state.errorMessage != null) {
					Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
				}
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Button(onClick = viewModel::syncNow, enabled = !state.syncing) {
						if (state.syncing) {
							CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
							Spacer(Modifier.width(8.dp))
						}
						Text("Sync now")
					}
					OutlinedButton(onClick = viewModel::unpair) { Text("Unpair") }
				}
			}
		}
	}
}
```

- [ ] **Step 3: Embed into SettingsScreen**

In `SettingsScreen.kt`, add two new parameters and insert the section near the top of the scrollable column:

```kotlin
@Composable
fun SettingsScreen(
	onRequestPermissions: () -> Unit,
	onPairLaptop: () -> Unit,
	onOpenSyncLog: () -> Unit,
	viewModel: SettingsViewModel = hiltViewModel(),
) {
	// ... existing state
	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
	) {
		LaptopSyncSection(onPair = onPairLaptop, onOpenSyncLog = onOpenSyncLog)
		// ... rest of existing settings content
	}
}
```

- [ ] **Step 4: Build + commit**

```bash
cd healthaggregator-android && ./gradlew :app:compileDebugKotlin
git add healthaggregator-android/app/src/main/java/com/healthaggregator/ui/settings/
git commit -m "Phase 6: LaptopSyncSection with Sync now + Unpair, embedded in Settings"
```

### Task 6.4: SyncLogScreen

**Files:**
- Create: `app/src/main/java/com/healthaggregator/ui/settings/SyncLogScreen.kt`

Minimal screen — reads the last summary from SecureStorage and displays it.

- [ ] **Step 1: Implement**

`app/src/main/java/com/healthaggregator/ui/settings/SyncLogScreen.kt`:
```kotlin
package com.healthaggregator.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncLogScreen(
	onBack: () -> Unit,
	viewModel: LaptopSyncViewModel = hiltViewModel(),
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	LaunchedEffect(Unit) { viewModel.refreshFromStorage() }

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Sync log") },
				navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
			)
		},
	) { padding ->
		Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			if (state.lastSyncAt > 0) {
				Text("Last sync: ${DateFormat.getDateTimeInstance().format(Date(state.lastSyncAt))}",
					style = MaterialTheme.typography.bodyMedium)
				Text(state.lastSyncSummary ?: "(no summary)",
					style = MaterialTheme.typography.bodySmall)
			} else {
				Text("No sync has run yet.", style = MaterialTheme.typography.bodyMedium)
			}
		}
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add healthaggregator-android/app/src/main/java/com/healthaggregator/ui/settings/SyncLogScreen.kt
git commit -m "Phase 6: SyncLogScreen shows last-sync details"
```

### Task 6.5: Navigation wiring

**Files:**
- Modify: `app/src/main/java/com/healthaggregator/ui/nav/AppNav.kt` (or wherever `NavHost` is defined — grep for `NavHost`)

- [ ] **Step 1: Locate AppNav**

```bash
grep -rn "NavHost" healthaggregator-android/app/src/main/java/com/healthaggregator/ | head
```

Open the file that contains the main `NavHost`. Add two routes and a Settings callback.

- [ ] **Step 2: Add routes**

In the `NavHost` block, add:
```kotlin
composable("laptop_pairing") {
	LaptopPairingScreen(
		onBack = { navController.popBackStack() },
		onPaired = { navController.popBackStack() },
	)
}
composable("sync_log") {
	SyncLogScreen(onBack = { navController.popBackStack() })
}
```

At the Settings `composable("settings") { ... }` block, update the `SettingsScreen(...)` call to pass:
```kotlin
onPairLaptop = { navController.navigate("laptop_pairing") },
onOpenSyncLog = { navController.navigate("sync_log") },
```

- [ ] **Step 3: Build + commit**

```bash
cd healthaggregator-android && ./gradlew :app:assembleDebug
git add healthaggregator-android/app/src/main/java/com/healthaggregator/ui/nav/
git commit -m "Phase 6: wire pairing + sync-log routes into AppNav"
```

### Task 6.6: Phase 6 gate — device smoke

Requires SM-S906U connected and a laptop daemon running on Tailscale.

- [ ] **Step 1: Laptop daemon running**

On laptop:
```bash
cd healthaggregator-laptop-daemon && make run
```

Note the Tailscale hostname (e.g. `jesse-laptop.tail-abc.ts.net`) and the token from `~/HealthAggregatorData/sync.token`.

- [ ] **Step 2: Install APK**

```bash
cd healthaggregator-android && ./gradlew :app:installDebug
```

- [ ] **Step 3: Pair**

On the phone: Settings → Pair with laptop → enter hostname + token → Test and save.

Expected: "Test and save" shows a spinner briefly, then the pairing screen closes. The Settings `LaptopSyncSection` now says "Paired with ..." and shows a Sync now button.

- [ ] **Step 4: First sync**

Tap "Sync now." Expected: spinner for a few seconds, summary line populates with "+N from laptop, +M to laptop." Tap "View sync log" → confirms last-sync summary and timestamp.

- [ ] **Step 5: Idempotence check**

Tap "Sync now" a second time immediately. Expected: "+0 from laptop, +0 to laptop." Confirms INSERT OR IGNORE is deduping correctly.

- [ ] **Step 6: Phase 6 gate commit**

```bash
git commit --allow-empty -m "Phase 6 gate: pairing + sync button round-trip over Tailscale confirmed"
```

---

## Phase 7 — Polish + Auto-Start + Ship

**Goal:** Make the daemon auto-start on login (launchd), add a short README so Jesse can set this up from scratch if he ever needs to, run the full smoke checklist, merge to main.

### Task 7.1: launchd plist + Makefile targets

**Files:**
- Create: `healthaggregator-laptop-daemon/launchd/com.healthaggregator.syncd.plist`
- Modify: `healthaggregator-laptop-daemon/Makefile` (replace the stub `install-launchd` / `uninstall-launchd` targets)

- [ ] **Step 1: Plist template**

`healthaggregator-laptop-daemon/launchd/com.healthaggregator.syncd.plist`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>Label</key>
	<string>com.healthaggregator.syncd</string>
	<key>ProgramArguments</key>
	<array>
		<string>__DAEMON_DIR__/.venv/bin/uvicorn</string>
		<string>daemon:app</string>
		<string>--host</string>
		<string>0.0.0.0</string>
		<string>--port</string>
		<string>8719</string>
	</array>
	<key>WorkingDirectory</key>
	<string>__DAEMON_DIR__</string>
	<key>RunAtLoad</key>
	<true/>
	<key>KeepAlive</key>
	<true/>
	<key>StandardOutPath</key>
	<string>__HOME__/HealthAggregatorData/daemon.log</string>
	<key>StandardErrorPath</key>
	<string>__HOME__/HealthAggregatorData/daemon.err.log</string>
</dict>
</plist>
```

- [ ] **Step 2: Update Makefile**

Replace the stub `install-launchd` and `uninstall-launchd` targets with:
```make
PLIST_NAME := com.healthaggregator.syncd.plist
PLIST_DEST := $(HOME)/Library/LaunchAgents/$(PLIST_NAME)
DAEMON_DIR := $(shell pwd)

install-launchd:
	@mkdir -p $(HOME)/Library/LaunchAgents
	@sed \
	  -e 's|__DAEMON_DIR__|$(DAEMON_DIR)|g' \
	  -e 's|__HOME__|$(HOME)|g' \
	  launchd/$(PLIST_NAME) > $(PLIST_DEST)
	@launchctl unload $(PLIST_DEST) 2>/dev/null || true
	@launchctl load $(PLIST_DEST)
	@echo "Installed $(PLIST_DEST). Daemon is now running on port 8719 and will auto-start on login."

uninstall-launchd:
	@launchctl unload $(PLIST_DEST) 2>/dev/null || true
	@rm -f $(PLIST_DEST)
	@echo "Uninstalled $(PLIST_DEST)."
```

- [ ] **Step 3: Manual test the install target (optional — only if you want auto-start)**

```bash
cd healthaggregator-laptop-daemon && make install-launchd
curl -s http://localhost:8719/sync/health
# Expected: {"status": "ok", ...}
make uninstall-launchd
```

- [ ] **Step 4: Commit**

```bash
git add healthaggregator-laptop-daemon/launchd/ healthaggregator-laptop-daemon/Makefile
git commit -m "Phase 7: launchd plist + install/uninstall Makefile targets"
```

### Task 7.2: Daemon request logging

**Files:**
- Modify: `healthaggregator-laptop-daemon/daemon.py`

Add basic request logging so Jesse can `tail -f ~/HealthAggregatorData/daemon.log` when debugging.

- [ ] **Step 1: Add middleware**

Near the top of `daemon.py`:
```python
import logging

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
log = logging.getLogger("healthaggregator.syncd")
```

Inside `make_app()`, after `app = FastAPI(...)`:
```python
    @app.middleware("http")
    async def log_requests(request, call_next):
        response = await call_next(request)
        log.info("%s %s -> %d", request.method, request.url.path, response.status_code)
        return response
```

- [ ] **Step 2: Sanity-check tests still pass**

Run: `cd healthaggregator-laptop-daemon && make test`
Expected: all pass (middleware shouldn't break tests).

- [ ] **Step 3: Commit**

```bash
git add healthaggregator-laptop-daemon/daemon.py
git commit -m "Phase 7: daemon request logging"
```

### Task 7.3: README

**Files:**
- Create: `healthaggregator-laptop-daemon/README.md`

- [ ] **Step 1: Write README**

`healthaggregator-laptop-daemon/README.md`:
```markdown
# healthaggregator-laptop-daemon

Small HTTP daemon that serves the laptop-side SQLite for phone↔laptop sync.

## One-time setup

```bash
# 1. Bootstrap the laptop DB from the phone (requires USB + adb).
mkdir -p ~/HealthAggregatorData
adb exec-out run-as com.healthaggregator cat databases/healthaggregator.db \
  > ~/HealthAggregatorData/healthaggregator.db
cp ~/HealthAggregatorData/healthaggregator.db \
   ~/HealthAggregatorData/healthaggregator.db.bootstrap-backup

# 2. Install Python deps and boot the daemon.
cd healthaggregator-laptop-daemon
make install   # creates .venv and installs fastapi/uvicorn/pytest
make run       # runs on port 8719

# 3. Read the auth token (printed by daemon on first start; also here):
cat ~/HealthAggregatorData/sync.token
```

## Auto-start on login (macOS)

```bash
make install-launchd   # registers launchd plist, daemon persists across reboots
make uninstall-launchd # removes it
```

Logs go to `~/HealthAggregatorData/daemon.log`.

## Pair the phone

Open the HealthAggregator Android app → Settings → Laptop Sync → Pair with laptop. Enter:
- **Hostname:** your laptop's Tailscale hostname (e.g. `jesse-laptop.tail-abc.ts.net`). See `tailscale status` to find it.
- **Token:** the contents of `~/HealthAggregatorData/sync.token`.

Tap Test and save. On success you can press "Sync now" from Settings anytime.

## Troubleshooting

- **"Can't reach laptop":** is Tailscale up on both devices? Is the daemon running (`ps aux | grep uvicorn`)? Is the token correct?
- **"Daemon is on schema vN, phone is on vM":** laptop is ahead. This shouldn't happen during normal use. Either install a newer APK, or re-bootstrap by copying a fresh `healthaggregator.db` from the phone over adb.
- **"Sync failed" with HTTP 500:** check `~/HealthAggregatorData/daemon.err.log` for the stack trace.

## Data location

- Active DB: `~/HealthAggregatorData/healthaggregator.db`
- Auth token: `~/HealthAggregatorData/sync.token` (chmod 600)
- Bootstrap backup: `~/HealthAggregatorData/healthaggregator.db.bootstrap-backup`
- Logs: `~/HealthAggregatorData/daemon.log`, `~/HealthAggregatorData/daemon.err.log`

Deliberately outside the git repo so it can't be accidentally committed.

## Architecture

See `Docs/specs/2026-04-18-laptop-sync-design.md` in the parent repo for the full design.

Quick summary: phone reads its Room DB, serializes every row as a column→value JSON dict, POSTs to `/sync/push`. Daemon INSERT-OR-IGNOREs rows by natural keys (LWW for `chat_conversations`). `GET /sync/pull` returns everything on the laptop; phone merges the same way. Schema migrations flow phone-authoritatively over `/sync/migrate` when the daemon is behind.
```

- [ ] **Step 2: Commit**

```bash
git add healthaggregator-laptop-daemon/README.md
git commit -m "Phase 7: daemon README with setup, pairing, troubleshooting"
```

### Task 7.4: Final manual smoke + ship gate

Run the full manual checklist from the spec's Testing section against real hardware.

- [ ] **Step 1: Run the checklist on device + laptop**

- [ ] Bootstrap via adb works end-to-end (if not already done).
- [ ] Daemon starts (`make run`), binds to Tailscale interface (`lsof -i :8719`).
- [ ] `curl -H "Authorization: Bearer <token>" http://<tailscale-host>:8719/sync/health` returns 200.
- [ ] Pairing on phone succeeds, stores credentials.
- [ ] First sync on paired phone reports row counts matching the DB.
- [ ] Second sync immediately after reports 0 new rows.
- [ ] Kill daemon mid-push → phone shows error → restart daemon → retry succeeds with no duplication.
- [ ] Ingest a test row via `sqlite3` on laptop (e.g. a synthetic lab observation), press Sync on phone, row appears in the phone's Records tab.
- [ ] Delete a chat on phone, press Sync, laptop still has it (`sqlite3 ~/HealthAggregatorData/healthaggregator.db 'select * from chat_conversations;'` still shows it).
- [ ] Rename a chat on phone, press Sync, laptop title updates (LWW).

- [ ] **Step 2: Full android test + daemon test suite**

```bash
cd healthaggregator-android && ./gradlew :app:assembleDebug :app:testDebugUnitTest
cd ../healthaggregator-laptop-daemon && make test
```

Both green.

- [ ] **Step 3: Update memory**

Update `/Users/blackcolours/.claude/projects/-Users-blackcolours-dev-work-HealthAggregator/memory/project_current_execution_state.md` to note δ is ready to merge. Example update content:
- "δ merged YYYY-MM-DD via non-ff commit ..."
- "Daemon running on port 8719 via launchd (or manual)."
- "Next: γ.2 PDF lab import on laptop side using the CommonHealth export."

- [ ] **Step 4: Merge to main**

```bash
cd /Users/blackcolours/dev/work/HealthAggregator
git checkout main
git pull --ff-only
git merge --no-ff feat/delta-laptop-sync -m "Merge feat/delta-laptop-sync: Stream δ — Laptop Sync"
```

- [ ] **Step 5: Celebrate**

δ ships. Jesse can now ingest LabCorp on the laptop via a one-time Claude Code session, press Sync on the phone, and those rows land in the Android app. Claude Max on the laptop reads the same SQLite file directly.

---

## Scope summary

- **Phase 1:** 5 tasks — migration refactor, assets + factory, no behavior change
- **Phase 2:** 4 tasks — daemon scaffold, /health + /version, schema module
- **Phase 3:** 7 tasks — enum, models, RowSerializer, sync_logic, /sync/push, SyncClient.push, gate
- **Phase 4:** 4 tasks — /sync/pull, SyncClient.pull, SyncRepository.syncNow, gate
- **Phase 5:** 3 tasks — /sync/migrate, phone drift detection, gate
- **Phase 6:** 6 tasks — SecureStorage + DI, pairing screen, LaptopSyncSection, sync log, navigation, device smoke
- **Phase 7:** 4 tasks — launchd, logging, README, ship gate

**Total: 33 tasks across 7 phases.** Estimated ~30 implementation commits + 7 phase-gate commits.

**Test philosophy (personal-app light touch):** one focused test per component, smoke-level wire-format coverage, no exhaustive error-path matrices. TDD discipline per task (write test → fail → implement → pass → commit) but the tests themselves are compact. See memory `feedback_personal_app_light_tests.md`.
