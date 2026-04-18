# Android Stream δ — Laptop Sync Design

**Date:** 2026-04-18
**Branch:** `feat/delta-laptop-sync`
**Builds on:** γ.1 (LLM Assistant, merged to `main` 2026-04-18) + vitals dedup hotfix (schema v5)

## Goal

A bidirectional, local-first sync between the Android phone's Room SQLite database and an identical SQLite file living on Jesse's laptop. The laptop database is backed by a small HTTP daemon bound to Tailscale so the phone can sync from anywhere without USB, and Claude Code sessions on the laptop — including `/remote-control` — can read and write the same database directly with `sqlite3`.

This unlocks two concrete workflows:

1. **LabCorp ingestion via laptop.** CommonHealth has LabCorp data in its per-source drilldown but does not publish it to Health Connect's PHR surface (verified 2026-04-18 via `HASync` diagnostic log + direct inspection in Health Connect). The Android app cannot see LabCorp at all. A one-time manual ingest on the laptop — Claude Code mapping the CommonHealth export into the Room schema — plus this sync feature lands those rows on the phone.
2. **Claude Max on the laptop for in-depth data conversations.** The phone's γ assistant is bounded by gpt-5's free-tier context and rate limits. On the laptop, Claude Code reads the same dataset directly and conversations can be arbitrarily long. The phone keeps γ for on-the-go questions; the laptop adds a higher-leverage environment for deep analysis.

This is also a future insurance policy: the laptop database functions as an append-only backup of the phone.

## Non-goals (δ scope)

- **LabCorp PDF ingest pipeline.** Out of scope. The CommonHealth export is already on the laptop. Ingestion is a one-time Claude Code session — `sqlite3` + ad-hoc mapping — not a standing tool.
- **Future β daily-measurements sync with deletions.** δ ships append-only merge. If β ever needs deletes to propagate, a `sync_tombstones` journal is a cheap follow-on.
- **Laptop-side web UI / .NET+SvelteKit visualization.** The existing .NET+SvelteKit stack may read this same SQLite file in the future. Out of scope here; zero changes needed on the sync side when that happens.
- **Conflict resolution for arbitrary edits.** FHIR rows are immutable post-insert. The only edit case is `chat_conversations.title` (drawer rename), handled as a small, specified exception below.
- **Multi-device / multi-phone sync.** Single phone ↔ single laptop only. Tokens and pairing assume one-of-each.
- **Cloud sync / Tailscale-less fallback.** Tailscale is a hard dependency. If Tailscale is down, sync is down — acceptable, same as if the laptop is off.

## Design findings

### The "laptop is an append-only backup" principle

Laptop-side rows are never removed by the sync process, even when the phone deletes them. This is what makes the laptop a backup rather than a mirror. Consequences:

- **Deletes on phone do NOT propagate to laptop.** A deleted chat on the phone survives on the laptop. A fat-fingered β measurement the user deletes on the phone is preserved on the laptop.
- **Phone can be freely wiped and repopulated from the laptop.** A full `/sync/pull` after a reinstall gets the phone back to the laptop's state.
- **Laptop-side rows added by Claude Code (LabCorp rows, annotations) flow to the phone on next sync.** This is the primary value prop.

The principle is a deliberate asymmetry: phone is the active, mutable device; laptop is the passive, growing reservoir.

### Merge semantics

Two strategies, both idempotent:

- **`INSERT OR IGNORE` keyed on natural keys.** Used for every syncable table except `chat_conversations`. The natural key is the table's existing unique index (for labs/vitals: `(sourceSystem, fhirReference[, componentCode])`; for chat_messages: `id`). Repeated syncs of identical data are no-ops.
- **`INSERT OR REPLACE WHERE incoming.updatedAt > existing.updatedAt`.** Used only for `chat_conversations` (titles are editable via the drawer rename feature). One special case, everything else stays simple.

Both strategies guarantee safe retry: a sync that partially fails can be re-pressed and converges to the same final state.

### Schema as the shared source of truth

Room schema migrations are moved out of Kotlin `execSQL(...)` strings and into plain SQL files under `app/src/main/assets/migrations/`. The Kotlin `Migration` objects become thin wrappers that read the asset file at migration time. The phone ships this SQL to the daemon over HTTP (`POST /sync/migrate`) when the daemon is behind.

This gives us **one source of truth for schema in the Android repo** and keeps the daemon stateless about schema details:

1. Commit `app/src/main/assets/migrations/N_to_M.sql` in the Android project.
2. Build + install the new APK on the phone.
3. Next Sync-now: phone notices daemon is on N, bundles the N→M SQL from its assets, posts to `/sync/migrate`, daemon applies in transaction, then the normal push/pull proceeds.

The daemon never needs to be updated when a new migration ships — the phone is authoritative and ships SQL on demand. No dual maintenance, no risk of the two sides drifting.

### Sync granularity: full, stateless, every press

Every sync ships every row in every syncable table in both directions. Merge is deduped by natural keys. Rationale:

- Data is small. ~15-25K rows, single-digit MB of JSON. Tailscale LAN handles this in seconds.
- Stateless — no "last synced at" watermark to get out of whack. A press always converges the two sides.
- Self-healing — if one side ever gets wedged, the next press corrects it. No special-case "reset" button needed.

### Auth: shared secret over Tailscale

Tailscale already restricts reachability to Jesse's devices. A bearer token adds a second factor at zero cost and reuses the EncryptedSharedPreferences plumbing from γ. The token is generated once by the daemon at first start, persisted to `~/HealthAggregatorData/sync.token` (chmod 600), and entered on the phone via a one-time "Pair with laptop" screen.

### Daemon: Python 3 + FastAPI + stdlib sqlite3

- Stdlib `sqlite3` means no ORM, no schema coupling, direct SQL. Fits the row-serialization model where rows are just column dicts.
- FastAPI + uvicorn boot in under a second, have good defaults, and make the five routes readable.
- `pip install fastapi uvicorn pytest` is the entire dep setup. No Docker, no Homebrew packages needed beyond Python 3.11+.
- Python is also the language Claude Code drops into naturally for the one-off LabCorp ingest session — having one language on the laptop side keeps things simple.

### Syncable vs non-syncable tables

| Table | Syncs? | Rationale |
|---|---|---|
| `patient_records` | ✓ | Immutable FHIR |
| `lab_observations` | ✓ | Immutable FHIR |
| `vitals_observations` | ✓ | Immutable FHIR (post-v5 dedup fix) |
| `condition_records` | ✓ | Immutable FHIR |
| `medication_records` | ✓ | Immutable FHIR |
| `allergy_records` | ✓ | Immutable FHIR |
| `encounter_records` | ✓ | Immutable FHIR |
| `document_records` | ✓ | Immutable FHIR |
| `diagnostic_report_records` | ✓ | Immutable FHIR |
| `source_records` | ✓ | Raw FHIR JSON, immutable |
| `chat_conversations` | ✓ | LWW on `updatedAt` (rename propagation) |
| `chat_messages` | ✓ | INSERT OR IGNORE on `id` (immutable once written) |
| `medical_data_sources` | ✗ | `healthConnectSourceId` is device-local |
| `sync_jobs` | ✗ | Per-device Health Connect sync history |

## User experience

### One-time bootstrap (USB, adb)

First time setup only:

1. Plug phone into laptop via USB with USB debugging enabled.
2. Run:
   ```bash
   mkdir -p ~/HealthAggregatorData
   adb exec-out run-as com.healthaggregator cat databases/healthaggregator.db \
     > ~/HealthAggregatorData/healthaggregator.db
   cp ~/HealthAggregatorData/healthaggregator.db \
      ~/HealthAggregatorData/healthaggregator.db.bootstrap-backup
   ```
3. Unplug. Never touch adb again.

The `.bootstrap-backup` copy is the safety net in case a later operation corrupts the active DB.

### Pairing (one-time, via phone Settings)

1. On the laptop: `make run` in `healthaggregator-laptop-daemon/`. Daemon starts, prints `Auth token: <uuid>` (also saved to `~/HealthAggregatorData/sync.token`).
2. On the phone: **Settings → Laptop Sync → Pair with laptop**. Enter hostname (e.g. `laptop.tail-scale-name.ts.net`) and the token.
3. Phone stores both in SecureStorage (EncryptedSharedPreferences via γ's infrastructure).
4. On success the pairing screen shows "Paired with &lt;hostname&gt;" and returns to Settings.

### Steady-state: the Sync button

New section in `SettingsScreen`:

```
┌─ Laptop Sync ───────────────────────────────┐
│                                             │
│ Status: Paired with laptop.ts.net (port 8719)│
│ Last sync: 2 minutes ago                    │
│ Last result: +8 from laptop, +47 to laptop  │
│                                             │
│ [   Sync now   ]        [   Unpair   ]      │
└─────────────────────────────────────────────┘
```

Tapping **Sync now** runs the flow (Section: Data flow). During the flow the button shows a progress indicator. On success a toast appears: *"Synced. +8 from laptop, +47 to laptop, 12.5K deduped."* On failure an error banner shows with a Retry button.

Tapping the status line opens a simple log screen showing the full per-table deltas from the most recent sync.

### Auto-start on laptop (optional)

`make install-launchd` registers `com.healthaggregator.syncd.plist` with launchd. The daemon then runs on login, binds to the Tailscale interface, restarts on crash, and logs to `~/HealthAggregatorData/daemon.log`. Removing the autostart: `make uninstall-launchd`. Users who prefer manual control can skip this and just `make run` when needed.

## Architecture

```
┌─────────────────────────────┐              ┌──────────────────────────────────┐
│ Android phone (Room v5+)    │              │ Laptop (~/HealthAggregatorData/) │
│                             │              │                                  │
│ Settings → "Sync now" btn   │◄───HTTPS────►│ Python/FastAPI daemon            │
│                             │ (Tailscale)  │ bound to Tailscale interface     │
│ SyncClient (OkHttp)         │              │                                  │
│   - handshake               │              │ healthaggregator.db              │
│   - schema migrate          │              │   (same Room schema)             │
│   - push / pull             │              │                                  │
│                             │              │ Stateless re: migrations —       │
│ Bundles migration SQL       │              │  receives SQL from phone over    │
│  from local assets as       │              │  /sync/migrate as needed.        │
│  needed                     │              │                                  │
│                             │              │                                  │
│ Bearer token in             │              │                                  │
│ EncryptedSharedPreferences  │              │                                  │
└─────────────────────────────┘              └──────────────────────────────────┘
                                                         ▲
                                                         │ sqlite3 CLI / Read tool
                                                ┌────────┴──────────┐
                                                │ Claude Code       │
                                                │ (local or /remote)│
                                                └───────────────────┘
```

## Data flow: the Sync-now sequence

1. **Handshake.** Phone → `GET /sync/version` → `{ schema_version, daemon_version }`. Validates the daemon is reachable and responding.
2. **Schema check.** Phone compares daemon's `schema_version` to its own. Three outcomes:
   - Equal: proceed to push.
   - Daemon behind: phone bundles each required `.sql` file from assets (e.g. for a 5→6 gap, one file) and `POST /sync/migrate { from, to, sql }`. Daemon applies in transaction, bumps `PRAGMA user_version`. Retry on network failure is safe — the daemon checks current version before applying.
   - Daemon ahead: reject with a typed error. This shouldn't happen unless the user hand-edited the laptop DB; recovery is manual.
3. **Push phone → laptop.** Phone reads every syncable table, serializes via `RowSerializer`, posts `POST /sync/push { batch_id, rows_by_table }`. Daemon runs one transaction: `INSERT OR IGNORE` per row (or LWW for chat_conversations), returns `{ inserted_by_table, ignored_by_table }`.
4. **Pull laptop → phone.** Phone → `GET /sync/pull` → `{ rows_by_table }`. Phone applies the same merge logic locally inside its own Room transaction.
5. **Summary.** Phone computes deltas, persists last-sync metadata (timestamp, per-table deltas) in SharedPreferences, shows toast.

## Components

### Repo layout

```
HealthAggregator/
├── healthaggregator-android/
│   └── app/src/main/
│       ├── assets/migrations/          [NEW — shared source of truth]
│       │   ├── 1_to_2.sql              [extracted from existing Kotlin]
│       │   ├── 2_to_3.sql
│       │   ├── 3_to_4.sql
│       │   └── 4_to_5.sql
│       └── java/com/healthaggregator/
│           ├── data/MigrationFactory.kt         [NEW — load SQL from assets]
│           ├── data/AppDatabaseMigrations.kt    [MODIFIED — one-liners via factory]
│           ├── sync/                            [NEW package]
│           │   ├── SyncClient.kt                [OkHttp wrapper, 5 routes]
│           │   ├── SyncRepository.kt            [orchestrator]
│           │   ├── SyncModels.kt                [Kotlinx Serialization DTOs]
│           │   ├── SyncableTables.kt            [enum with MergeStrategy]
│           │   └── RowSerializer.kt             [entity ↔ JSON row]
│           ├── di/SyncModule.kt                 [NEW — Hilt bindings]
│           └── ui/settings/
│               ├── SettingsScreen.kt            [MODIFIED — Laptop Sync section]
│               ├── LaptopPairingScreen.kt       [NEW — hostname + token entry]
│               └── SyncLogScreen.kt             [NEW — per-table deltas]
│
└── healthaggregator-laptop-daemon/              [NEW project]
    ├── daemon.py                                [FastAPI, 5 routes]
    ├── sync_logic.py                            [merge logic]
    ├── schema.py                                [apply SQL over HTTP, bump user_version]
    ├── config.py                                [paths, bind host, token]
    ├── tests/
    │   ├── fixtures/                            [sample migration SQL for tests]
    │   ├── test_sync_logic.py
    │   ├── test_schema.py
    │   └── test_daemon.py
    ├── requirements.txt
    ├── Makefile                                 [install / run / test / install-launchd]
    ├── launchd/
    │   └── com.healthaggregator.syncd.plist
    └── README.md
```

### Android-side types (sketch)

```kotlin
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

sealed interface MergeStrategy {
    object InsertOrIgnore : MergeStrategy
    data class LastWriteWinsOn(val timestampColumn: String) : MergeStrategy
}

data class SyncResult(
    val pushedRowsByTable: Map<String, Int>,
    val pulledRowsByTable: Map<String, Int>,
    val ignoredRowsByTable: Map<String, Int>,
    val migrationsApplied: List<Pair<Int, Int>>,
    val durationMs: Long,
)

class SyncRepository @Inject constructor(
    private val client: SyncClient,
    private val db: AppDatabase,
    private val secureStorage: SecureStorage,
    @ApplicationContext private val context: Context,
) {
    suspend fun syncNow(): Result<SyncResult>
    suspend fun pair(hostname: String, token: String): Result<Unit>
    suspend fun unpair()
    fun isPaired(): Boolean
}
```

### Daemon routes

```
GET  /sync/version     -> {"schema_version": int, "daemon_version": str}
POST /sync/migrate     -> {"applied": [{"from": int, "to": int}], "errors": []}
POST /sync/push        -> {"inserted_by_table": {...}, "ignored_by_table": {...}}
GET  /sync/pull        -> {"rows_by_table": {...}}
GET  /sync/health      -> {"status": "ok", "db_path": str, "rows_total": int}
```

All routes except `/sync/health` require `Authorization: Bearer <token>`.

## Error handling

| Condition | Behavior |
|---|---|
| Daemon unreachable | Phone shows *"Can't reach laptop at &lt;hostname&gt;. Is it online on Tailscale?"* with Retry |
| Auth fails (401) | *"Bad token — re-pair with laptop."* |
| Daemon schema ahead of phone | Typed error, banner explains manual recovery path |
| Migration SQL errors | Daemon rolls back transaction, returns 500 with stderr, phone shows it verbatim |
| Push partial failure | Daemon rolls back the batch, phone retry is safe (INSERT OR IGNORE is idempotent) |
| Pull response malformed | Phone surfaces parse error, no merge performed, no state corrupted |
| Network times out mid-pull | Phone shows timeout, retry is safe |

No silent state corruption is possible: every transaction on both sides is all-or-nothing, and idempotent merge means retries don't double-apply.

## Testing

### Android side (reuses γ test infrastructure)

**Unit tests (JUnit 4 + Robolectric `@Config(sdk = [35])`):**

- `MigrationFactoryTest` — loads a synthetic asset SQL, verifies it applies cleanly.
- `SyncRepositoryTest` — fake `SyncClient`, in-memory Room DB:
  - empty phone + full laptop payload → all rows land
  - duplicate push is idempotent (counts stable on re-sync)
  - `chat_conversations` LWW: older `updatedAt` loses, newer wins
  - partial merge (some tables empty in payload) doesn't crash
  - schema mismatch bubbles up as a typed error
- `RowSerializerTest` — round-trip each of 12 entity types: entity → JSON → entity equals original.
- `SyncClientTest` — MockWebServer, one test per route including auth header, 401 handling, 500 handling, timeout.

**Instrumented test (on-device, SM-S906U):** one end-to-end test that runs the real Android side against a local Python daemon booted in the test harness. Validates the whole stack end-to-end. Gated behind `RUN_E2E_SYNC=true` env var so normal CI stays fast.

### Daemon side (pytest)

- `test_sync_logic.py` — INSERT OR IGNORE dedupe, LWW comparison for chat_conversations, transactional rollback on injected error.
- `test_schema.py` — fresh DB at v1, apply every migration in order, `PRAGMA user_version` ends at 5. Also: injected bad SQL rolls back cleanly.
- `test_daemon.py` — FastAPI `TestClient`, each route happy path + auth failure + malformed payload.

### Shared fixtures

`shared/test-fixtures/sample-rows.json` — ~100 representative rows across all 12 syncable tables. Used by both sides to validate round-tripping.

### Manual smoke checklist (per phase gate)

- [ ] Bootstrap via adb works end-to-end.
- [ ] Daemon starts, binds to Tailscale interface (`lsof -i :8719` confirms).
- [ ] `curl -H "Authorization: Bearer <token>" http://<tailscale-host>:8719/sync/health` returns 200.
- [ ] Pairing on phone succeeds, stores credentials.
- [ ] First sync on paired phone reports row counts matching the DB.
- [ ] Second sync immediately after reports 0 new rows.
- [ ] Kill daemon mid-push → phone shows error → restart daemon → retry succeeds with no duplication.
- [ ] Ingest a test row via sqlite3 on laptop, press Sync on phone, row appears in phone app.
- [ ] Delete a chat on phone, press Sync, laptop still has it.
- [ ] Rename a chat on phone, press Sync, laptop title updates (LWW).

## Phased delivery

Phases mirror the γ structure — each ends with a gate (build green, tests pass, hand-exercised). Phase boundaries are commit-group boundaries.

### Phase 1 — Migration refactor (foundation)

- Create `app/src/main/assets/migrations/{1_to_2,2_to_3,3_to_4,4_to_5}.sql` by extracting existing SQL from `AppDatabaseMigrations.kt`.
- Add `MigrationFactory` that reads an asset and runs each `;`-split statement.
- Replace existing Kotlin `Migration` objects with factory calls.
- **Gate:** existing app on v5 still boots clean on SM-S906U; instrumented migration test still passes; fresh install runs 1→5 via factory.

### Phase 2 — Daemon skeleton

- Create `healthaggregator-laptop-daemon/` with FastAPI app, `config.py`, auth middleware, `/sync/version` and `/sync/health` routes.
- `schema.py` — applies arbitrary SQL received over HTTP to a target DB, in a transaction, and bumps `PRAGMA user_version`. Test fixtures (checked-in sample migration SQL) live in `tests/fixtures/`.
- `pytest test_schema.py` — fresh DB → apply fixture migrations 1→5 → `PRAGMA user_version == 5`.
- **Gate:** `make run` boots daemon; `curl /sync/health` returns 200; `curl /sync/version` returns `{"schema_version": 5}`.

### Phase 3 — Row serialization + push (phone → laptop)

- `RowSerializer.kt` on phone: per-entity → JSON row, round-trip tested for all 12 entity types.
- `SyncClient.kt` with a `push()` method.
- `POST /sync/push` route + `sync_logic.py` merge (INSERT OR IGNORE for all 11 non-chat-conversation tables, LWW for chat_conversations).
- Unit tests on both sides using shared fixtures.
- **Gate:** integration test — phone harness sends a known payload, daemon returns expected inserted/ignored counts.

### Phase 4 — Pull (laptop → phone) + full round-trip

- `GET /sync/pull` route.
- Phone-side merge mirrors daemon's (same enum-driven strategies).
- `SyncRepository.syncNow()` wires push → pull → result.
- In-memory full round-trip test.
- **Gate:** `SyncRepository.syncNow()` callable from a test harness, produces expected deltas end-to-end.

### Phase 5 — Schema-drift handling

- Phone detects daemon behind, bundles needed `.sql` assets, `POST /sync/migrate`.
- Daemon applies in transaction, bumps `user_version`, returns confirmation.
- Failure paths: daemon ahead of phone (reject typed); migration SQL errors (rollback).
- **Gate:** synthetic test — daemon at v4, phone at v5, sync, daemon ends at v5 with data intact. Negative test — bad SQL rolls back.

### Phase 6 — Pairing UI + Settings integration

- `LaptopPairingScreen.kt` — hostname + token entry, stores in SecureStorage.
- `SettingsScreen` Laptop Sync section — status line, Sync now button, Unpair.
- Result toast, `SyncLogScreen.kt` for per-table deltas.
- Device smoke on SM-S906U.
- **Gate:** manual pairing works; Sync button round-trips on real Tailscale network.

### Phase 7 — Polish + auto-start + ship gate

- `launchd` plist + `make install-launchd` / `make uninstall-launchd`.
- Logging improvements on daemon (request log, row counts).
- README with bootstrap instructions, troubleshooting.
- Full manual smoke checklist executed end-to-end.
- **Gate:** merge `feat/delta-laptop-sync` → `main`.

## Scope estimate

~25-35 implementation commits across 7 phases. Roughly 1500 new lines of code: ~500 phone-side, ~600 daemon-side, ~400 test. Comparable to γ in scope but with less novel design work — most pieces (Room + OkHttp on phone, FastAPI + sqlite3 on daemon) are well-understood.

## Open questions / future work

- **β daily-measurements deletion propagation.** When β ships and the user can delete measurement rows, we add a `sync_tombstones(table_name, natural_key, deleted_at)` journal. Out of scope for δ.
- **Multi-source-system auditing.** Future nice-to-have: a `sync_audit` table on the laptop that logs each sync event. Would let `/remote-control` Claude sessions see sync history. Out of scope for δ.
- **Laptop-side web UI.** If the .NET+SvelteKit stack gets brought onto the laptop for data visualization, it reads the same `~/HealthAggregatorData/healthaggregator.db` file directly. Sync side needs no changes.
- **Compression.** At current data scale (~2-5 MB JSON per sync), network overhead is negligible. If data grows 10x, gzip on `/sync/pull` would be a trivial add.
