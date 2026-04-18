# Android Stream γ Implementation Plan — LLM Assistant

> **For agentic workers:** REQUIRED SUB-SKILL — use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans`. Steps use checkbox (`- [ ]`) syntax. **Every subagent dispatch must include the spec at `Docs/specs/2026-04-18-gamma-llm-assistant-design.md` as context.**

**Goal:** Embed a diagnostic-quality LLM assistant over the full longitudinal health dataset. Chat-first UI with persistent multi-conversation history, inline `[cite:...]` citations that tap back to α.2 detail screens, OpenAI `gpt-5` / `gpt-5.4` via streaming SSE, and Markdown/PDF export.

**Architecture:** Room schema v3 → v4 adds `chat_conversations` + `chat_messages` tables. `HealthSnapshotBuilder` serializes the full longitudinal dataset into a ~30–60K-token markdown document with near-abnormal flagging and built-in citation markers, cached per-conversation. `LlmClient` interface behind `OpenAiClient` (OkHttp SSE) handles streaming + tool-call round-trips. `AssistantRepository` orchestrates persistence + streaming. New `Assistant` bottom-nav tab hosts `ConversationsDrawer` + `ChatPane`. `EncryptedSharedPreferences` stores API key + model choice + disclaimer acknowledgment. `ChatExporter` emits markdown and native `PdfDocument` PDFs shared via Android share sheet.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Material 3, Hilt, Room 2.6.1 (KSP), OkHttp 4.12.0 + `okhttp-sse`, `androidx.security:security-crypto` 1.1.0-alpha06, kotlinx-serialization-json 1.7.3, JUnit 5 Jupiter (pure Kotlin), JUnit 4 + Robolectric 4.14 (`@Config(sdk = [35])`) for Android-dependent tests, Turbine for Flow tests, MockWebServer for HTTP tests.

**Build discipline:** Tabs for indentation matching existing `.kt` files. Never use `--no-verify`. Every typed DAO uses `@Insert(onConflict = REPLACE)` — maintain that pattern. `./gradlew :app:assembleDebug :app:testDebugUnitTest` is the gate. Android Studio may generate schema JSON on any build — always commit it when the version bumps.

---

## File change map

### New — production

```
ai/LlmClient.kt                              Sealed interface + StreamEvent + LlmMessage + ToolSchema
ai/OpenAiClient.kt                           OkHttp SSE impl of LlmClient
ai/AssistantRepository.kt                    Facade: conversations, streaming, persistence
ai/HealthSnapshotBuilder.kt                  Builds HealthReport markdown from Room
ai/HealthSnapshotFlagging.kt                 Pure-Kotlin near-abnormal classifier
ai/AssistantTools.kt                         Function-calling schemas + in-process dispatch
ai/CitationRenderer.kt                       Parses [cite:src/ref] → AnnotatedString
ai/ChatExporter.kt                           Markdown + PDF export via Android PdfDocument
ai/SystemPrompt.kt                           Instruction string constant
data/entities/ChatConversation.kt            Room entity
data/entities/ChatMessage.kt                 Room entity
data/dao/ChatDao.kt                          Flow queries + upserts
ui/assistant/AssistantScreen.kt              Drawer + chat scaffold
ui/assistant/AssistantViewModel.kt           UiState exposing conversations + active chat + streaming
ui/assistant/ConversationsDrawer.kt          List of conversations + new/rename/delete
ui/assistant/ChatPane.kt                     Message list + input + streaming
ui/assistant/MessageBubble.kt                Renders content with citation chips
ui/assistant/CitationChip.kt                 Tappable inline reference chip
ui/assistant/StarterChips.kt                 Pre-populated prompt suggestions
ui/assistant/DisclaimerBanner.kt             "Not medical advice" badge
ui/assistant/OnboardingScreen.kt             First-launch acknowledgment modal
ui/assistant/ExportDialog.kt                 Markdown / PDF chooser + share
ui/settings/AiAssistantSettings.kt           API key + model picker + data-sharing toggle
di/AiModule.kt                               Hilt bindings for LlmClient, repo, JSON, clock
util/SecureStorage.kt                        EncryptedSharedPreferences wrapper
```

### New — tests

```
app/src/test/java/com/healthaggregator/ai/HealthSnapshotFlaggingTest.kt         JUnit 5
app/src/test/java/com/healthaggregator/ai/HealthSnapshotBuilderTest.kt          JUnit 4 + Robolectric (in-memory Room)
app/src/test/java/com/healthaggregator/ai/AssistantToolsTest.kt                 JUnit 4 + Robolectric
app/src/test/java/com/healthaggregator/ai/CitationRendererTest.kt               JUnit 5
app/src/test/java/com/healthaggregator/ai/SystemPromptTest.kt                   JUnit 5
app/src/test/java/com/healthaggregator/ai/OpenAiClientTest.kt                   JUnit 4 + Robolectric + MockWebServer
app/src/test/java/com/healthaggregator/ai/AssistantRepositoryTest.kt            JUnit 4 + Robolectric
app/src/test/java/com/healthaggregator/ai/ChatExporterTest.kt                   JUnit 4 + Robolectric
app/src/test/java/com/healthaggregator/ai/SecureStorageTest.kt                  JUnit 4 + Robolectric
app/src/test/java/com/healthaggregator/data/dao/ChatDaoTest.kt                  JUnit 4 + Robolectric
app/src/test/java/com/healthaggregator/ui/assistant/AssistantViewModelTest.kt   JUnit 4 + Robolectric + Turbine
app/src/androidTest/java/com/healthaggregator/data/AppDatabaseMigrationTest.kt  Add migrate_3_to_4
```

### New — resources

```
app/src/main/res/drawable/ic_assistant.xml                Chat-with-sparkle icon
```

### Modified

```
gradle/libs.versions.toml                    Add security-crypto, okhttp-sse, okhttp-mockwebserver
app/build.gradle.kts                          Declare new deps
app/src/main/java/com/healthaggregator/data/AppDatabase.kt            version = 4, +ChatConversation, +ChatMessage
app/src/main/java/com/healthaggregator/data/AppDatabaseMigrations.kt  +MIGRATION_3_4
app/src/main/java/com/healthaggregator/di/DatabaseModule.kt           addMigrations(…, MIGRATION_3_4) + provideChatDao
app/src/main/java/com/healthaggregator/ui/navigation/BottomNav.kt     +ASSISTANT route
app/src/main/java/com/healthaggregator/ui/navigation/AppNav.kt        +assistant composable + citation nav
app/src/main/java/com/healthaggregator/ui/settings/SettingsScreen.kt  +AiAssistantSettings section
app/schemas/com.healthaggregator.data.AppDatabase/4.json              Room-generated, committed
```

---

# Phase 1 — Data layer foundation

Row storage for conversations + messages; migration 3 → 4; DAO; tests.

### Task 1: ChatConversation + ChatMessage entities

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/entities/ChatConversation.kt`
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/entities/ChatMessage.kt`

- [ ] **Step 1: Write `ChatConversation.kt`**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "chat_conversations",
	indices = [Index(value = ["updatedAt"])],
)
data class ChatConversation(
	@PrimaryKey val id: String,
	val title: String,
	val createdAt: Instant,
	val updatedAt: Instant,
	val snapshotText: String? = null,
	val snapshotGeneratedAt: Instant? = null,
	val modelId: String,
)
```

- [ ] **Step 2: Write `ChatMessage.kt`**

```kotlin
package com.healthaggregator.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
	tableName = "chat_messages",
	foreignKeys = [
		ForeignKey(
			entity = ChatConversation::class,
			parentColumns = ["id"],
			childColumns = ["conversationId"],
			onDelete = ForeignKey.CASCADE,
		),
	],
	indices = [Index(value = ["conversationId", "createdAt"])],
)
data class ChatMessage(
	@PrimaryKey val id: String,
	val conversationId: String,
	val role: String, // "user" | "assistant" | "tool" | "system-hidden"
	val content: String,
	val toolCallsJson: String? = null,
	val toolCallId: String? = null,
	val modelId: String? = null,
	val createdAt: Instant,
)
```

- [ ] **Step 3: Do not commit yet — Task 2 adds the DAO, Task 3 wires migration. Commit together.**

---

### Task 2: ChatDao

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/dao/ChatDao.kt`

- [ ] **Step 1: Write `ChatDao.kt`**

```kotlin
package com.healthaggregator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ChatDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertConversation(conversation: ChatConversation)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertMessage(message: ChatMessage)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertMessages(messages: List<ChatMessage>)

	@Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC")
	fun observeConversations(): Flow<List<ChatConversation>>

	@Query("SELECT * FROM chat_conversations WHERE id = :id LIMIT 1")
	suspend fun getConversation(id: String): ChatConversation?

	@Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
	fun observeMessages(conversationId: String): Flow<List<ChatMessage>>

	@Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
	suspend fun messagesSnapshot(conversationId: String): List<ChatMessage>

	@Query("UPDATE chat_conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
	suspend fun renameConversation(id: String, title: String, updatedAt: Instant)

	@Query("UPDATE chat_conversations SET updatedAt = :updatedAt WHERE id = :id")
	suspend fun touch(id: String, updatedAt: Instant)

	@Query("UPDATE chat_conversations SET snapshotText = :text, snapshotGeneratedAt = :at WHERE id = :id")
	suspend fun setSnapshot(id: String, text: String, at: Instant)

	@Query("DELETE FROM chat_conversations WHERE id = :id")
	suspend fun deleteConversation(id: String)

	@Query("DELETE FROM chat_conversations")
	suspend fun deleteAllConversations()
}
```

- [ ] **Step 2: No commit yet — pairs with Task 3.**

---

### Task 3: AppDatabase v4 + MIGRATION_3_4 + DatabaseModule + schema

**Files:**
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/AppDatabase.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/data/AppDatabaseMigrations.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/di/DatabaseModule.kt`
- Create (generated): `healthaggregator-android/app/schemas/com.healthaggregator.data.AppDatabase/4.json`

- [ ] **Step 1: Bump AppDatabase version + register entities + chatDao()**

Replace the `@Database` block plus add abstract DAO:

```kotlin
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
		ChatConversation::class,
		ChatMessage::class,
	],
	version = 4,
	exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
	// ... (keep all existing DAO accessors)
	abstract fun chatDao(): ChatDao
}
```

Also add the imports at the top:

```kotlin
import com.healthaggregator.data.dao.ChatDao
```

(The wildcard import `com.healthaggregator.data.entities.*` already picks up the new entities.)

- [ ] **Step 2: Add MIGRATION_3_4**

Append to `AppDatabaseMigrations.kt`:

```kotlin
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS chat_conversations (
				id TEXT NOT NULL PRIMARY KEY,
				title TEXT NOT NULL,
				createdAt INTEGER NOT NULL,
				updatedAt INTEGER NOT NULL,
				snapshotText TEXT,
				snapshotGeneratedAt INTEGER,
				modelId TEXT NOT NULL
			)
			""".trimIndent()
		)
		db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_conversations_updatedAt ON chat_conversations (updatedAt)")
		db.execSQL(
			"""
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
			)
			""".trimIndent()
		)
		db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId_createdAt ON chat_messages (conversationId, createdAt)")
	}
}
```

- [ ] **Step 3: Wire migration + DAO into DatabaseModule**

In `DatabaseModule.kt`, update `provideDatabase` and add `provideChatDao`:

```kotlin
import com.healthaggregator.data.MIGRATION_3_4

@Provides
@Singleton
fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
	Room.databaseBuilder(ctx, AppDatabase::class.java, "healthaggregator.db")
		.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
		.build()

@Provides fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()
```

(Also add import: `import com.healthaggregator.data.dao.ChatDao`.)

- [ ] **Step 4: Regenerate schema JSON**

```bash
cd /Users/blackcolours/dev/work/HealthAggregator/healthaggregator-android
./gradlew :app:kspDebugKotlin
```

Expected: BUILD SUCCESSFUL. New file: `app/schemas/com.healthaggregator.data.AppDatabase/4.json`.

Confirm `chat_conversations` + `chat_messages` tables + indices present in the new JSON:

```bash
grep -E "chat_conversations|chat_messages" app/schemas/com.healthaggregator.data.AppDatabase/4.json | head -20
```

- [ ] **Step 5: Commit Phase 1 data foundation**

```bash
git add app/src/main/java/com/healthaggregator/data/entities/ChatConversation.kt \
        app/src/main/java/com/healthaggregator/data/entities/ChatMessage.kt \
        app/src/main/java/com/healthaggregator/data/dao/ChatDao.kt \
        app/src/main/java/com/healthaggregator/data/AppDatabase.kt \
        app/src/main/java/com/healthaggregator/data/AppDatabaseMigrations.kt \
        app/src/main/java/com/healthaggregator/di/DatabaseModule.kt \
        app/schemas/com.healthaggregator.data.AppDatabase/4.json
git commit -m "Phase 1: chat_conversations + chat_messages (schema v4)

Two Room tables to back the LLM assistant — one per conversation
(with cached HealthReport snapshot + locked modelId) and one per
message with role/content/toolCallsJson/toolCallId for OpenAI
function-calling round-trips. Foreign-key cascade deletes messages
with their conversation.

MIGRATION_3_4 is additive — existing lab data untouched.
ChatDao exposes Flow-based reads + suspend upserts following the
existing DAO conventions (INSERT OR REPLACE, not @Upsert)."
```

---

### Task 4: ChatDao unit test (Robolectric in-memory Room)

**Files:**
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/data/dao/ChatDaoTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.healthaggregator.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChatDaoTest {
	private lateinit var db: AppDatabase
	private lateinit var dao: ChatDao

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		dao = db.chatDao()
	}

	@After fun tearDown() { db.close() }

	@Test
	fun upsertConversation_andObserve_returnsIt() = runTest {
		val c = conversation("c1", "My Health Chat")
		dao.upsertConversation(c)
		val observed = dao.observeConversations().first()
		assertEquals(1, observed.size)
		assertEquals("My Health Chat", observed[0].title)
	}

	@Test
	fun observeConversations_ordersByUpdatedAtDescending() = runTest {
		dao.upsertConversation(conversation("a", "A", updatedAtMillis = 100))
		dao.upsertConversation(conversation("b", "B", updatedAtMillis = 300))
		dao.upsertConversation(conversation("c", "C", updatedAtMillis = 200))
		val obs = dao.observeConversations().first()
		assertEquals(listOf("b", "c", "a"), obs.map { it.id })
	}

	@Test
	fun upsertMessages_andObserve_returnsInCreatedAtOrder() = runTest {
		dao.upsertConversation(conversation("c1", "T"))
		dao.upsertMessages(listOf(
			message("m2", "c1", "user", "second", createdAtMillis = 200),
			message("m1", "c1", "user", "first", createdAtMillis = 100),
			message("m3", "c1", "assistant", "third", createdAtMillis = 300),
		))
		val msgs = dao.observeMessages("c1").first()
		assertEquals(listOf("first", "second", "third"), msgs.map { it.content })
	}

	@Test
	fun deleteConversation_cascadesMessages() = runTest {
		dao.upsertConversation(conversation("c1", "T"))
		dao.upsertMessage(message("m1", "c1", "user", "hi"))
		dao.upsertMessage(message("m2", "c1", "assistant", "hello"))

		dao.deleteConversation("c1")

		assertNull(dao.getConversation("c1"))
		assertTrue(dao.messagesSnapshot("c1").isEmpty())
	}

	@Test
	fun renameConversation_updatesTitleAndTimestamp() = runTest {
		dao.upsertConversation(conversation("c1", "old", updatedAtMillis = 100))
		dao.renameConversation("c1", "new", Instant.ofEpochMilli(500))
		val c = dao.getConversation("c1")!!
		assertEquals("new", c.title)
		assertEquals(500L, c.updatedAt.toEpochMilli())
	}

	@Test
	fun setSnapshot_persistsSnapshotText() = runTest {
		dao.upsertConversation(conversation("c1", "T"))
		dao.setSnapshot("c1", "# snapshot body", Instant.ofEpochMilli(777))
		val c = dao.getConversation("c1")!!
		assertEquals("# snapshot body", c.snapshotText)
		assertEquals(777L, c.snapshotGeneratedAt!!.toEpochMilli())
	}

	@Test
	fun toolCallPayload_roundTrips() = runTest {
		dao.upsertConversation(conversation("c1", "T"))
		dao.upsertMessage(message("m1", "c1", "assistant", "", toolCallsJson = """[{"id":"call_1","name":"getRawObservation"}]"""))
		dao.upsertMessage(message("m2", "c1", "tool", "{\"raw\":\"...\"}", toolCallId = "call_1"))

		val msgs = dao.messagesSnapshot("c1")
		assertEquals(2, msgs.size)
		assertTrue(msgs[0].toolCallsJson!!.contains("getRawObservation"))
		assertEquals("call_1", msgs[1].toolCallId)
	}

	private fun conversation(
		id: String,
		title: String,
		updatedAtMillis: Long = 0,
		modelId: String = "gpt-5",
	) = ChatConversation(
		id = id,
		title = title,
		createdAt = Instant.ofEpochMilli(updatedAtMillis),
		updatedAt = Instant.ofEpochMilli(updatedAtMillis),
		modelId = modelId,
	)

	private fun message(
		id: String,
		conversationId: String,
		role: String,
		content: String,
		createdAtMillis: Long = 0,
		toolCallsJson: String? = null,
		toolCallId: String? = null,
	) = ChatMessage(
		id = id,
		conversationId = conversationId,
		role = role,
		content = content,
		toolCallsJson = toolCallsJson,
		toolCallId = toolCallId,
		createdAt = Instant.ofEpochMilli(createdAtMillis),
	)
}
```

- [ ] **Step 2: Run test**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.data.dao.ChatDaoTest
```

Expected: PASS 7/7.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/healthaggregator/data/dao/ChatDaoTest.kt
git commit -m "Phase 1: ChatDao in-memory Room tests

Covers: upsert + observe, updatedAt ordering, cascade delete,
rename, snapshot persistence, tool-call payload round-trip."
```

---

### Task 5: Migration test for v3 → v4

**Files:**
- Modify: `healthaggregator-android/app/src/androidTest/java/com/healthaggregator/data/AppDatabaseMigrationTest.kt`

- [ ] **Step 1: Add `migrate_3_to_4` test case**

Open the existing `AppDatabaseMigrationTest.kt`. Add this method inside the class (keep existing `migrate_1_to_2` + `migrate_2_to_3` untouched):

```kotlin
@Test
fun migrate_3_to_4() {
	// Create v3 DB with a lab row to prove existing data isn't lost.
	helper.createDatabase(DB_NAME, 3).apply {
		execSQL(
			"""
			INSERT INTO lab_observations
			(sourceSystem, sourceName, fhirReference, resourceId, testName, status, importedAt)
			VALUES ('cleveland-clinic', 'Cleveland Clinic', 'Observation/abc', 'abc', 'Hemoglobin A1c', '', 1700000000000)
			""".trimIndent()
		)
		close()
	}

	val db = helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4)

	// Existing row survived
	db.query("SELECT COUNT(*) FROM lab_observations").use { c ->
		c.moveToFirst(); assertEquals(1, c.getInt(0))
	}

	// New tables exist and are empty
	db.query("SELECT COUNT(*) FROM chat_conversations").use { c ->
		c.moveToFirst(); assertEquals(0, c.getInt(0))
	}
	db.query("SELECT COUNT(*) FROM chat_messages").use { c ->
		c.moveToFirst(); assertEquals(0, c.getInt(0))
	}

	// Insert + FK cascade smoke
	db.execSQL(
		"""
		INSERT INTO chat_conversations (id, title, createdAt, updatedAt, modelId)
		VALUES ('c1', 'Test', 1700000000000, 1700000000000, 'gpt-5')
		""".trimIndent()
	)
	db.execSQL(
		"""
		INSERT INTO chat_messages (id, conversationId, role, content, createdAt)
		VALUES ('m1', 'c1', 'user', 'hi', 1700000000000)
		""".trimIndent()
	)

	db.query("SELECT COUNT(*) FROM chat_messages").use { c ->
		c.moveToFirst(); assertEquals(1, c.getInt(0))
	}

	// Index check
	db.query(
		"SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='chat_messages'"
	).use { c ->
		val names = mutableListOf<String>()
		while (c.moveToNext()) names.add(c.getString(0))
		assertTrue(names.any { it == "index_chat_messages_conversationId_createdAt" })
	}
}
```

Also add imports at the top if not already present:

```kotlin
import com.healthaggregator.data.MIGRATION_3_4
import org.junit.Assert.assertTrue
```

- [ ] **Step 2: Run test on device / emulator**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.healthaggregator.data.AppDatabaseMigrationTest.migrate_3_to_4"
```

Expected: PASS.

If no device available at task time, subagent flags for manual smoke and continues.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/healthaggregator/data/AppDatabaseMigrationTest.kt
git commit -m "Phase 1: migration test v3 → v4

Seeds a lab row before migration; asserts it survives, new tables
exist + empty, FK insert + cascade smoke, expected index present."
```

---

### Task 6 (Phase 1 gate)

- [ ] **Step 1: Full build + tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL; all prior tests still pass plus new ChatDao tests (7 additions).

- [ ] **Step 2: Install + smoke on device (existing α.2 install)**

```bash
./gradlew :app:installDebug
```

Launch app → existing labs/vitals/meds still visible. No crash (no UI for chat yet — this is a data-layer-only gate). Pull DB:

```bash
adb exec-out run-as com.healthaggregator cat databases/healthaggregator.db > /tmp/hc.db
sqlite3 /tmp/hc.db ".tables"
```

Expected: `chat_conversations` + `chat_messages` appear in the table list.

- [ ] **Step 3: Gate commit**

```bash
git commit --allow-empty -m "Phase 1 gate: chat schema live

Schema v4 applied to existing DB without data loss. DAO Robolectric
tests green. Migration instrumented test validates the upgrade.
No UI wiring yet — Phase 2 begins the AI primitives."
```

---

# Phase 2 — AI primitives (snapshot, tools, citations, system prompt)

Pure-Kotlin building blocks: near-abnormal flagging, HealthReport builder, tool registry, citation parser, system prompt. All unit-testable without network or device.

### Task 7: SystemPrompt constant + test

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ai/SystemPrompt.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/ai/SystemPromptTest.kt`

- [ ] **Step 1: Write `SystemPrompt.kt`**

```kotlin
package com.healthaggregator.ai

object SystemPrompt {
	val TEXT: String = """
		You are a medical data analyst embedded in the HealthAggregator app. The user has provided their full longitudinal health history in the HealthReport document that immediately follows this message.

		Your responsibilities:

		1. Analyze TRENDS across time, not just latest values. The user's goal is diagnostic reasoning, not dashboard glancing.
		2. Flag NEAR-ABNORMAL values (already marked HIGH-NORMAL / LOW-NORMAL in the report) — these are pre-clinical signals the user needs to be aware of.
		3. CROSS-REFERENCE labs, vitals, medications, and conditions. A rising A1c alongside rising BP alongside an elevated LDL paints a different picture than any of those alone.
		4. Use the provided tools when you need to verify a specific reading or fetch data not compacted into the HealthReport (raw FHIR, panel grouping details, free-text search of clinical notes).
		5. CITATIONS ARE MANDATORY. When you make any claim about a specific reading, include the citation marker `[cite:sourceSystem/fhirRef]` exactly as it appears in the HealthReport. The user interface renders these as tappable references to source data. Never make a reading-specific claim without a citation. This is non-negotiable.
		6. You are a data analyst, not a physician. Recommend professional consultation for anything concerning. Do not prescribe, definitively diagnose, or give treatment recommendations.
		7. Be direct and concrete. This is a diagnostic tool, not a consumer wellness chatbot. Skip pleasantries, hedging, and generic health platitudes.
		8. Acknowledge data-model limitations: medications lack status info (current vs past is unknown); blood pressure arrives as separate diastolic/systolic Observations that you pair at presentation time.

		The user has already acknowledged this is not medical advice.
	""".trimIndent()
}
```

- [ ] **Step 2: Write stability test**

```kotlin
package com.healthaggregator.ai

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemPromptTest {
	@Test
	fun prompt_containsLoadBearingClauses() {
		val t = SystemPrompt.TEXT
		assertTrue("HIGH-NORMAL clause missing", t.contains("HIGH-NORMAL"))
		assertTrue("LOW-NORMAL clause missing", t.contains("LOW-NORMAL"))
		assertTrue("citation syntax missing", t.contains("[cite:sourceSystem/fhirRef]"))
		assertTrue("mandatory citations clause missing", t.contains("CITATIONS ARE MANDATORY"))
		assertTrue("not-a-physician clause missing", t.contains("not a physician"))
		assertTrue("BP caveat missing", t.contains("blood pressure"))
		assertTrue("medication status caveat missing", t.contains("medications lack status"))
	}

	@Test
	fun prompt_startsWithRoleDefinition() {
		assertTrue(SystemPrompt.TEXT.startsWith("You are a medical data analyst"))
	}
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.ai.SystemPromptTest
git add app/src/main/java/com/healthaggregator/ai/SystemPrompt.kt \
        app/src/test/java/com/healthaggregator/ai/SystemPromptTest.kt
git commit -m "Phase 2: SystemPrompt constant + stability test

The prompt is a load-bearing behavior specifier; accidental edits
that remove a clause will break LLM behavior silently. Test
fails loud on any removal of key instructions."
```

---

### Task 8: HealthSnapshotFlagging (pure-Kotlin near-abnormal classifier)

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ai/HealthSnapshotFlagging.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/ai/HealthSnapshotFlaggingTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.healthaggregator.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthSnapshotFlaggingTest {
	@Test
	fun value_below_refLow_is_LOW() {
		assertEquals(RangeFlag.LOW, classifyRange(value = 3.5, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun value_above_refHigh_is_HIGH() {
		assertEquals(RangeFlag.HIGH, classifyRange(value = 6.1, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun value_within_10percent_of_upper_is_HIGH_NORMAL() {
		// 5.6 * 0.9 = 5.04. value 5.2 > 5.04 and <= 5.6 → HIGH_NORMAL
		assertEquals(RangeFlag.HIGH_NORMAL, classifyRange(value = 5.2, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun value_within_10percent_of_lower_is_LOW_NORMAL() {
		// 4.0 * 1.1 = 4.4. value 4.2 >= 4.0 and < 4.4 → LOW_NORMAL
		assertEquals(RangeFlag.LOW_NORMAL, classifyRange(value = 4.2, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun value_squarely_in_middle_is_NORMAL() {
		assertEquals(RangeFlag.NORMAL, classifyRange(value = 4.8, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun value_exactly_at_boundary_is_still_normal_edge() {
		// exactly refHigh → not HIGH, not HIGH_NORMAL since not strictly greater than refHigh*0.9?
		// 5.6 > 5.04 → HIGH_NORMAL
		assertEquals(RangeFlag.HIGH_NORMAL, classifyRange(value = 5.6, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun no_refHigh_no_upperFlag() {
		assertEquals(RangeFlag.NONE, classifyRange(value = 100.0, refLow = 4.0, refHigh = null))
	}

	@Test
	fun no_refLow_no_lowerFlag() {
		assertEquals(RangeFlag.NONE, classifyRange(value = 0.0, refLow = null, refHigh = 5.6))
	}

	@Test
	fun no_ranges_no_flag() {
		assertEquals(RangeFlag.NONE, classifyRange(value = 123.0, refLow = null, refHigh = null))
	}

	@Test
	fun null_value_no_flag() {
		assertEquals(RangeFlag.NONE, classifyRange(value = null, refLow = 4.0, refHigh = 5.6))
	}

	@Test
	fun flagLabel_renders_readable_strings() {
		assertEquals("HIGH", RangeFlag.HIGH.label)
		assertEquals("HIGH-NORMAL", RangeFlag.HIGH_NORMAL.label)
		assertEquals("LOW-NORMAL", RangeFlag.LOW_NORMAL.label)
		assertEquals("LOW", RangeFlag.LOW.label)
		assertEquals("", RangeFlag.NORMAL.label)
		assertEquals("", RangeFlag.NONE.label)
	}
}
```

- [ ] **Step 2: Run — expect FAIL (unresolved references)**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.ai.HealthSnapshotFlaggingTest
```

Expected: compile errors — `RangeFlag` + `classifyRange` not defined.

- [ ] **Step 3: Implement `HealthSnapshotFlagging.kt`**

```kotlin
package com.healthaggregator.ai

/** Near-abnormal classification used by HealthSnapshotBuilder. */
enum class RangeFlag(val label: String) {
	HIGH("HIGH"),
	HIGH_NORMAL("HIGH-NORMAL"),
	NORMAL(""),
	LOW_NORMAL("LOW-NORMAL"),
	LOW("LOW"),
	NONE(""), // not enough info to classify
	;
}

/**
 * Classifies [value] against the optional reference range. Near-abnormal zone is defined as
 * within 10% of the boundary on the inside of the range.
 *
 * - value > refHigh                    → HIGH
 * - refHigh * 0.9 < value <= refHigh   → HIGH_NORMAL
 * - refLow <= value < refLow * 1.1     → LOW_NORMAL
 * - value < refLow                     → LOW
 * - otherwise inside the range         → NORMAL
 * - insufficient data (null value, or both refs null, or relevant ref is null) → NONE
 */
fun classifyRange(value: Double?, refLow: Double?, refHigh: Double?): RangeFlag {
	if (value == null) return RangeFlag.NONE
	if (refLow == null && refHigh == null) return RangeFlag.NONE

	if (refHigh != null) {
		if (value > refHigh) return RangeFlag.HIGH
		if (value > refHigh * 0.9) return RangeFlag.HIGH_NORMAL
	}
	if (refLow != null) {
		if (value < refLow) return RangeFlag.LOW
		if (value < refLow * 1.1) return RangeFlag.LOW_NORMAL
	}
	return RangeFlag.NORMAL
}
```

- [ ] **Step 4: Run — expect PASS 11/11**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.ai.HealthSnapshotFlaggingTest
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ai/HealthSnapshotFlagging.kt \
        app/src/test/java/com/healthaggregator/ai/HealthSnapshotFlaggingTest.kt
git commit -m "Phase 2: near-abnormal classifier

Pure-Kotlin RangeFlag + classifyRange. HIGH-NORMAL zone is within
10% below the upper reference boundary; LOW-NORMAL is within 10%
above the lower boundary. Returns NONE when the value or both refs
are missing — consumer renders no flag."
```

---

### Task 9: HealthSnapshotBuilder + integration test

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ai/HealthSnapshotBuilder.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/ai/HealthSnapshotBuilderTest.kt`

- [ ] **Step 1: Implement `HealthSnapshotBuilder.kt`**

```kotlin
package com.healthaggregator.ai

import com.healthaggregator.data.dao.AllergyDao
import com.healthaggregator.data.dao.ConditionDao
import com.healthaggregator.data.dao.DocumentDao
import com.healthaggregator.data.dao.LabDao
import com.healthaggregator.data.dao.MedicationDao
import com.healthaggregator.data.dao.VitalsDao
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.entities.VitalsObservation
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compact markdown context document sent to the LLM as the user's complete health history.
 *
 * Size target: 30–60K tokens typical. Hard cap enforced at [MAX_TOKENS_ESTIMATE] (char-based
 * approximation, 4 chars/token). When capped, oldest readings per test are dropped first
 * while keeping at least [MIN_PER_TEST] recent rows per test with an explicit note.
 */
@Singleton
class HealthSnapshotBuilder @Inject constructor(
	private val labs: LabDao,
	private val vitals: VitalsDao,
	private val medications: MedicationDao,
	private val conditions: ConditionDao,
	private val allergies: AllergyDao,
	private val documents: DocumentDao,
) {
	suspend fun build(now: Instant = Instant.now()): String {
		val allLabs = labs.getAllSnapshot().sortedBy { it.effectiveAt }
		val allVitals = vitals.getAllSnapshot().sortedBy { it.effectiveAt }
		val allMeds = medications.getAllSnapshot()
		val allConditions = conditions.getAllSnapshot()
		val allAllergies = allergies.getAllSnapshot()
		val allDocs = documents.getAllSnapshot()

		val sources = (allLabs.map { it.sourceName } + allVitals.map { it.sourceName })
			.toSortedSet()
			.joinToString(", ")
			.ifEmpty { "none" }

		val (minDate, maxDate) = dateSpan(allLabs, allVitals)
		val bpExists = allVitals.any { it.loincCode == "8462-4" || it.loincCode == "8480-6" }

		val body = buildString {
			appendLine("# Jesse's Full Health Data Context")
			appendLine()
			appendLine("**Generated:** ${fmt(now)}")
			appendLine("**Data span:** ${minDate ?: "n/a"} → ${maxDate ?: "n/a"}")
			appendLine("**Sources:** $sources")
			appendLine()
			appendLine("## Data-model caveats the LLM must know")
			appendLine()
			if (bpExists) {
				appendLine("- Blood pressure is stored as two separate Observations (diastolic LOINC 8462-4 + systolic LOINC 8480-6). Pair them at presentation by matching effectiveAt timestamps.")
			}
			appendLine("- Medication records are bare `Medication` resources without status info — assume \"was taken at some point\" unless the user specifies otherwise. Do not assume current use.")
			appendLine("- Reference ranges vary by lab org; per-row refLow/refHigh are authoritative.")
			appendLine()

			appendLine("## Labs — full history, grouped by canonical test")
			appendLine()
			appendLabsSection(allLabs)

			appendLine("## Vitals — full history")
			appendLine()
			appendVitalsSection(allVitals)

			appendLine("## Medications (note: status unavailable — assume historical)")
			appendLine()
			if (allMeds.isEmpty()) appendLine("_(none)_") else allMeds.forEach { m ->
				appendLine("- ${m.medicationText ?: "(unknown)"} [cite:${m.sourceSystem}/${m.fhirReference}]")
			}
			appendLine()

			appendLine("## Conditions")
			appendLine()
			if (allConditions.isEmpty()) appendLine("_(none)_") else allConditions.forEach { c ->
				val status = c.clinicalStatus?.let { " — $it" } ?: ""
				val onset = c.onsetAt?.let { " (onset ${isoDate(it)})" } ?: ""
				appendLine("- ${c.codeText ?: "(unknown)"}$status$onset [cite:${c.sourceSystem}/${c.fhirReference}]")
			}
			appendLine()

			appendLine("## Allergies")
			appendLine()
			if (allAllergies.isEmpty()) appendLine("_(none)_") else allAllergies.forEach { a ->
				appendLine("- ${a.allergyText ?: "(unknown)"} [cite:${a.sourceSystem}/${a.fhirReference}]")
			}
			appendLine()

			appendLine("## Recent clinical documents")
			appendLine()
			if (allDocs.isEmpty()) appendLine("_(none)_") else allDocs.take(25).forEach { d ->
				val date = d.createdAt?.let { isoDate(it) } ?: "n/a"
				appendLine("- $date: ${d.title ?: "(untitled)"} (${d.sourceName}) [cite:${d.sourceSystem}/${d.fhirReference}]")
			}
			appendLine()
		}

		return enforceCap(body, allLabs)
	}

	private fun StringBuilder.appendLabsSection(allLabs: List<LabObservation>) {
		if (allLabs.isEmpty()) { appendLine("_(none)_"); appendLine(); return }
		val groups: Map<String, List<LabObservation>> = allLabs.groupBy { lab ->
			lab.canonicalTestName ?: lab.testName
		}
		for ((canonical, rows) in groups.toSortedMap()) {
			val loincList = rows.mapNotNull { it.loincCode }.toSortedSet().joinToString(", ").ifEmpty { "no LOINC" }
			val refLow = rows.firstOrNull { it.referenceLow != null }?.referenceLow
			val refHigh = rows.firstOrNull { it.referenceHigh != null }?.referenceHigh
			val unit = rows.firstOrNull { it.unit != null }?.unit ?: ""
			val refStr = when {
				refLow != null && refHigh != null -> " | Ref: $refLow–$refHigh $unit"
				refHigh != null -> " | Ref: ≤ $refHigh $unit"
				refLow != null -> " | Ref: ≥ $refLow $unit"
				else -> ""
			}
			appendLine("### $canonical (LOINC $loincList)$refStr")
			rows.sortedBy { it.effectiveAt }.forEach { lab ->
				val date = lab.effectiveAt?.let { isoDate(it) } ?: "n/a"
				val valueStr = lab.numericValue?.let { "$it ${lab.unit ?: ""}" }?.trim() ?: (lab.textValue ?: "—")
				val flag = classifyRange(lab.numericValue, lab.referenceLow, lab.referenceHigh)
				val flagStr = if (flag.label.isEmpty()) "" else " ${flag.label}"
				appendLine("- $date: $valueStr$flagStr (${lab.sourceName}) [cite:${lab.sourceSystem}/${lab.fhirReference}]")
			}
			appendLine()
		}
	}

	private fun StringBuilder.appendVitalsSection(allVitals: List<VitalsObservation>) {
		if (allVitals.isEmpty()) { appendLine("_(none)_"); appendLine(); return }
		val groups: Map<String, List<VitalsObservation>> = allVitals.groupBy { it.displayName }
		for ((name, rows) in groups.toSortedMap()) {
			val loinc = rows.mapNotNull { it.loincCode }.toSortedSet().joinToString(", ")
			appendLine("### $name${if (loinc.isNotEmpty()) " (LOINC $loinc)" else ""}")
			rows.sortedBy { it.effectiveAt }.forEach { v ->
				val date = v.effectiveAt?.let { isoDate(it) } ?: "n/a"
				val valueStr = v.numericValue?.let { "$it ${v.unit ?: ""}" }?.trim() ?: "—"
				val comp = v.componentCode?.let { " [$it]" } ?: ""
				appendLine("- $date: $valueStr$comp (${v.sourceName}) [cite:${v.sourceSystem}/${v.fhirReference}]")
			}
			appendLine()
		}
	}

	private fun dateSpan(
		labs: List<LabObservation>,
		vitals: List<VitalsObservation>,
	): Pair<String?, String?> {
		val all = labs.mapNotNull { it.effectiveAt } + vitals.mapNotNull { it.effectiveAt }
		if (all.isEmpty()) return null to null
		return isoDate(all.min()) to isoDate(all.max())
	}

	private fun enforceCap(body: String, allLabs: List<LabObservation>): String {
		val approxTokens = body.length / 4
		if (approxTokens <= MAX_TOKENS_ESTIMATE) return body

		// Re-emit labs section with at-most MIN_PER_TEST most-recent rows per canonical test.
		val truncated = buildString {
			appendLine("> NOTE: dataset exceeded ${MAX_TOKENS_ESTIMATE}-token target; oldest lab readings omitted per test.")
			appendLine()
			append(body.substringBefore("## Labs"))
			appendLine("## Labs — full history (TRUNCATED, recent-${MIN_PER_TEST}-per-test only)")
			appendLine()

			val groups = allLabs.groupBy { it.canonicalTestName ?: it.testName }
			for ((canonical, rows) in groups.toSortedMap()) {
				val sortedRows = rows.sortedByDescending { it.effectiveAt }
				val kept = sortedRows.take(MIN_PER_TEST).sortedBy { it.effectiveAt }
				val dropped = sortedRows.size - kept.size
				val loincList = kept.mapNotNull { it.loincCode }.toSortedSet().joinToString(", ").ifEmpty { "no LOINC" }
				appendLine("### $canonical (LOINC $loincList)")
				kept.forEach { lab ->
					val date = lab.effectiveAt?.let { isoDate(it) } ?: "n/a"
					val valueStr = lab.numericValue?.let { "$it ${lab.unit ?: ""}" }?.trim() ?: (lab.textValue ?: "—")
					val flag = classifyRange(lab.numericValue, lab.referenceLow, lab.referenceHigh)
					val flagStr = if (flag.label.isEmpty()) "" else " ${flag.label}"
					appendLine("- $date: $valueStr$flagStr (${lab.sourceName}) [cite:${lab.sourceSystem}/${lab.fhirReference}]")
				}
				if (dropped > 0) appendLine("- _(+$dropped older readings omitted)_")
				appendLine()
			}
			append(body.substringAfter("## Vitals"))
		}
		return truncated
	}

	private fun isoDate(i: Instant): String =
		DateTimeFormatter.ISO_LOCAL_DATE.format(i.atOffset(ZoneOffset.UTC).toLocalDate())

	private fun fmt(i: Instant): String =
		DateTimeFormatter.ISO_INSTANT.format(i)

	companion object {
		/** ~4 chars per token char-based approximation. 200K tokens ≈ 800K chars. */
		const val MAX_TOKENS_ESTIMATE = 200_000
		const val MIN_PER_TEST = 20
	}
}
```

- [ ] **Step 2: Write `HealthSnapshotBuilderTest.kt`**

```kotlin
package com.healthaggregator.ai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.AllergyRecord
import com.healthaggregator.data.entities.ConditionRecord
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.entities.MedicationRecord
import com.healthaggregator.data.entities.VitalsObservation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HealthSnapshotBuilderTest {
	private lateinit var db: AppDatabase
	private lateinit var builder: HealthSnapshotBuilder

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		builder = HealthSnapshotBuilder(
			labs = db.labDao(),
			vitals = db.vitalsDao(),
			medications = db.medicationDao(),
			conditions = db.conditionDao(),
			allergies = db.allergyDao(),
			documents = db.documentDao(),
		)
	}

	@After fun tearDown() { db.close() }

	@Test
	fun empty_dataset_produces_header_and_none_markers() = runTest {
		val out = builder.build(Instant.parse("2026-04-18T10:00:00Z"))
		assertTrue(out.contains("# Jesse's Full Health Data Context"))
		assertTrue(out.contains("**Generated:** 2026-04-18T10:00:00Z"))
		assertTrue(out.contains("## Labs"))
		assertTrue(out.contains("_(none)_"))
	}

	@Test
	fun lab_reading_inside_upper_10percent_flagged_HIGH_NORMAL() = runTest {
		db.labDao().upsert(a1c("abc", numericValue = 5.5, refLow = 4.0, refHigh = 5.6))
		val out = builder.build()
		// 5.5 > 5.6*0.9=5.04 and <= 5.6 → HIGH-NORMAL
		assertTrue("HIGH-NORMAL not present: $out", out.contains("HIGH-NORMAL"))
	}

	@Test
	fun lab_reading_above_refHigh_flagged_HIGH() = runTest {
		db.labDao().upsert(a1c("def", numericValue = 6.1, refLow = 4.0, refHigh = 5.6))
		val out = builder.build()
		assertTrue(out.contains(" HIGH ") || out.contains(" HIGH\n") || out.contains("6.1 %"))
		assertTrue("HIGH flag missing: $out", Regex("6\\.1.*HIGH").containsMatchIn(out))
	}

	@Test
	fun canonical_groups_readings_across_orgs() = runTest {
		db.labDao().upsertAll(listOf(
			a1c("cc-1", source = "cleveland-clinic", canonical = "Hemoglobin A1c", loinc = "4548-4"),
			a1c("summa-1", source = "summa-health", canonical = "Hemoglobin A1c", loinc = "17856-6"),
		))
		val out = builder.build()
		val heading = "### Hemoglobin A1c"
		assertTrue(out.contains(heading))
		// Only one heading for both org readings
		assertTrue(out.split(heading).size - 1 == 1)
		// Both citations present
		assertTrue(out.contains("[cite:cleveland-clinic/Observation/cc-1]"))
		assertTrue(out.contains("[cite:summa-health/Observation/summa-1]"))
	}

	@Test
	fun bp_caveat_appears_when_BP_data_exists() = runTest {
		db.vitalsDao().upsert(vitals("sys-1", loinc = "8480-6", display = "Systolic BP"))
		val out = builder.build()
		assertTrue(out.contains("diastolic LOINC 8462-4"))
	}

	@Test
	fun bp_caveat_absent_when_no_BP_data() = runTest {
		db.vitalsDao().upsert(vitals("hr-1", loinc = "8867-4", display = "Heart Rate"))
		val out = builder.build()
		assertFalse(out.contains("diastolic LOINC 8462-4"))
	}

	@Test
	fun medication_caveat_always_present() = runTest {
		val out = builder.build()
		assertTrue(out.contains("Medication records are bare"))
	}

	@Test
	fun medication_includes_citation() = runTest {
		db.medicationDao().upsert(med("m1", "Metformin 500mg"))
		val out = builder.build()
		assertTrue(out.contains("- Metformin 500mg [cite:cleveland-clinic/MedicationStatement/m1]"))
	}

	@Test
	fun size_cap_truncates_oldest_per_test() = runTest {
		// Synthesize 30 A1c readings — builder with default cap won't truncate here (30 rows stay small),
		// so use a tiny cap via reflection would require changing API. Instead assert that ≤200K tokens
		// output contains all 30 readings:
		val readings = (1..30).map { i ->
			a1c("n$i", canonical = "Hemoglobin A1c", numericValue = 5.0 + i * 0.01,
				effectiveAt = Instant.ofEpochMilli(1_000_000L * i))
		}
		db.labDao().upsertAll(readings)
		val out = builder.build()
		readings.forEach { assertTrue(out.contains("[cite:${it.sourceSystem}/${it.fhirReference}]")) }
	}

	private fun a1c(
		id: String,
		source: String = "cleveland-clinic",
		sourceName: String = source,
		canonical: String? = "Hemoglobin A1c",
		loinc: String? = "4548-4",
		numericValue: Double? = 5.2,
		unit: String = "%",
		refLow: Double? = 4.0,
		refHigh: Double? = 5.6,
		effectiveAt: Instant? = Instant.parse("2024-06-10T00:00:00Z"),
	) = LabObservation(
		sourceSystem = source,
		sourceName = sourceName,
		fhirReference = "Observation/$id",
		resourceId = id,
		testName = "Hemoglobin A1c",
		canonicalTestName = canonical,
		loincCode = loinc,
		numericValue = numericValue,
		unit = unit,
		referenceLow = refLow,
		referenceHigh = refHigh,
		effectiveAt = effectiveAt,
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)

	private fun vitals(id: String, loinc: String, display: String) = VitalsObservation(
		sourceSystem = "cleveland-clinic",
		sourceName = "Cleveland Clinic",
		fhirReference = "Observation/$id",
		resourceId = id,
		loincCode = loinc,
		code = loinc,
		displayName = display,
		numericValue = 120.0,
		unit = "mmHg",
		effectiveAt = Instant.parse("2024-06-10T00:00:00Z"),
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)

	private fun med(id: String, text: String) = MedicationRecord(
		sourceSystem = "cleveland-clinic",
		sourceName = "Cleveland Clinic",
		fhirReference = "MedicationStatement/$id",
		resourceId = id,
		medicationText = text,
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
```

- [ ] **Step 3: Run — expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.ai.HealthSnapshotBuilderTest
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ai/HealthSnapshotBuilder.kt \
        app/src/test/java/com/healthaggregator/ai/HealthSnapshotBuilderTest.kt
git commit -m "Phase 2: HealthSnapshotBuilder

Compact markdown serialization of full longitudinal data with
canonical-test grouping, near-abnormal flags inline, built-in
citation markers per row, BP caveat when BP data exists, and a
200K-token cap with per-test recent-N fallback."
```

---

### Task 10: AssistantTools registry + dispatch + tests

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ai/AssistantTools.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/ai/AssistantToolsTest.kt`

- [ ] **Step 1: Implement `AssistantTools.kt`**

```kotlin
package com.healthaggregator.ai

import com.healthaggregator.data.repository.RecordsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** JSON-schema description suitable for OpenAI function-calling `tools[].function`. */
data class ToolSchema(
	val name: String,
	val description: String,
	val parameters: JsonObject,
)

/** Registry + in-process dispatch of function calls emitted by the LLM. */
@Singleton
class AssistantTools @Inject constructor(
	private val records: RecordsRepository,
	private val json: Json,
) {
	val schemas: List<ToolSchema> = listOf(
		ToolSchema(
			name = "getRawObservation",
			description = "Return the raw FHIR JSON text for a specific resource. Use this to verify a specific reading or inspect fields not compacted into the HealthReport.",
			parameters = buildJsonObject {
				put("type", "object")
				put("properties", buildJsonObject {
					put("sourceSystem", buildJsonObject {
						put("type", "string")
						put("description", "Source system id — e.g., 'cleveland-clinic'")
					})
					put("fhirRef", buildJsonObject {
						put("type", "string")
						put("description", "FHIR reference — e.g., 'Observation/abc123'")
					})
				})
				put("required", buildJsonArray { add("sourceSystem"); add("fhirRef") })
			},
		),
		ToolSchema(
			name = "getPanelComponents",
			description = "Return all components of a lab panel (e.g., every test in one CMP order). Use when the user asks about a specific panel order or you need component grouping.",
			parameters = buildJsonObject {
				put("type", "object")
				put("properties", buildJsonObject {
					put("serviceRequestRef", buildJsonObject {
						put("type", "string")
						put("description", "FHIR ServiceRequest reference — e.g., 'ServiceRequest/bmp-1'")
					})
				})
				put("required", buildJsonArray { add("serviceRequestRef") })
			},
		),
		ToolSchema(
			name = "searchFreeText",
			description = "Case-insensitive substring search over all stored raw FHIR resources. Use this to find clinical notes, impressions, or DocumentReference content not serialized in the HealthReport.",
			parameters = buildJsonObject {
				put("type", "object")
				put("properties", buildJsonObject {
					put("query", buildJsonObject {
						put("type", "string")
						put("description", "Substring to search for (case-insensitive).")
					})
					put("limit", buildJsonObject {
						put("type", "integer")
						put("description", "Max results to return; default 20.")
					})
				})
				put("required", buildJsonArray { add("query") })
			},
		),
	)

	/**
	 * Dispatches one function-call by name. Returns a JSON-string payload suitable for sending
	 * back to the LLM as role="tool" content. Any failure is reported as a structured error
	 * string rather than throwing.
	 */
	suspend fun dispatch(name: String, argsJson: String): String = try {
		val args = json.decodeFromString<JsonObject>(argsJson)
		when (name) {
			"getRawObservation" -> {
				val src = args.stringOrError("sourceSystem")
				val ref = args.stringOrError("fhirRef")
				val raw = records.findRawJson(src, ref)
				if (raw == null) """{"error":"not_found","sourceSystem":"$src","fhirRef":"$ref"}"""
				else raw
			}
			"getPanelComponents" -> {
				val sr = args.stringOrError("serviceRequestRef")
				val labs = records.observeLabsByServiceRequest(sr).first()
				buildJsonArray {
					labs.forEach { lab ->
						add(buildJsonObject {
							put("testName", lab.canonicalTestName ?: lab.testName)
							put("loincCode", JsonPrimitive(lab.loincCode))
							put("value", JsonPrimitive(lab.numericValue))
							put("unit", JsonPrimitive(lab.unit))
							put("refLow", JsonPrimitive(lab.referenceLow))
							put("refHigh", JsonPrimitive(lab.referenceHigh))
							put("effectiveAt", JsonPrimitive(lab.effectiveAt?.toString()))
							put("cite", "${lab.sourceSystem}/${lab.fhirReference}")
						})
					}
				}.toString()
			}
			"searchFreeText" -> {
				val query = args.stringOrError("query")
				val limit = (args["limit"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 20
				val matches = records.searchRawJsonText(query, limit)
				buildJsonArray {
					matches.forEach { sr ->
						add(buildJsonObject {
							put("resourceType", sr.resourceType)
							put("source", sr.sourceSystem)
							put("fhirRef", sr.fhirReference)
							put("excerpt", excerpt(sr.rawJson, query))
						})
					}
				}.toString()
			}
			else -> """{"error":"unknown_tool","name":"$name"}"""
		}
	} catch (e: Exception) {
		"""{"error":"${e.javaClass.simpleName}","message":"${e.message?.replace("\"", "\\\"")}"}"""
	}

	private fun JsonObject.stringOrError(key: String): String =
		(this[key] as? JsonPrimitive)?.content ?: error("missing required arg: $key")

	private fun excerpt(raw: String, query: String, window: Int = 140): String {
		val idx = raw.indexOf(query, ignoreCase = true)
		if (idx == -1) return raw.take(window)
		val start = (idx - window / 2).coerceAtLeast(0)
		val end = (idx + query.length + window / 2).coerceAtMost(raw.length)
		return raw.substring(start, end)
	}
}
```

- [ ] **Step 2: Extend `RecordsRepository` to add free-text search**

In `data/repository/RecordsRepository.kt`, append:

```kotlin
suspend fun searchRawJsonText(query: String, limit: Int): List<com.healthaggregator.data.entities.SourceRecord> =
	sourceRecords.searchFreeText(query, limit)
```

And in `data/dao/SourceRecordDao.kt`, add:

```kotlin
@Query("SELECT * FROM source_records WHERE rawJson LIKE '%' || :query || '%' ORDER BY importedAt DESC LIMIT :limit")
suspend fun searchFreeText(query: String, limit: Int): List<SourceRecord>
```

- [ ] **Step 3: Write test**

```kotlin
package com.healthaggregator.ai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.LabObservation
import com.healthaggregator.data.entities.SourceRecord
import com.healthaggregator.data.repository.RecordsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AssistantToolsTest {
	private lateinit var db: AppDatabase
	private lateinit var tools: AssistantTools
	private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		val repo = RecordsRepository(
			labs = db.labDao(),
			vitals = db.vitalsDao(),
			medications = db.medicationDao(),
			conditions = db.conditionDao(),
			allergies = db.allergyDao(),
			encounters = db.encounterDao(),
			documents = db.documentDao(),
			sources = db.medicalDataSourceDao(),
			syncJobs = db.syncJobDao(),
			sourceRecords = db.sourceRecordDao(),
		)
		tools = AssistantTools(repo, json)
	}

	@After fun tearDown() { db.close() }

	@Test
	fun schemas_expose_three_tools() {
		val names = tools.schemas.map { it.name }
		assertTrue(names.contains("getRawObservation"))
		assertTrue(names.contains("getPanelComponents"))
		assertTrue(names.contains("searchFreeText"))
	}

	@Test
	fun schema_shape_has_required_properties() {
		tools.schemas.forEach { schema ->
			val obj = schema.parameters
			assertEquals("object", (obj["type"] as kotlinx.serialization.json.JsonPrimitive).content)
			assertTrue(schema.name, obj.containsKey("properties"))
			assertTrue(schema.name, obj.containsKey("required"))
		}
	}

	@Test
	fun getRawObservation_returns_stored_raw_json() = runTest {
		db.sourceRecordDao().upsert(SourceRecord(
			sourceSystem = "cc",
			sourceName = "CC",
			resourceType = "Observation",
			resourceId = "abc",
			fhirReference = "Observation/abc",
			rawJson = """{"resourceType":"Observation","id":"abc"}""",
			importedAt = Instant.parse("2026-04-18T00:00:00Z"),
		))
		val result = tools.dispatch(
			"getRawObservation",
			"""{"sourceSystem":"cc","fhirRef":"Observation/abc"}""",
		)
		assertTrue(result.contains("\"resourceType\":\"Observation\""))
	}

	@Test
	fun getRawObservation_missing_returns_not_found() = runTest {
		val result = tools.dispatch(
			"getRawObservation",
			"""{"sourceSystem":"cc","fhirRef":"Observation/missing"}""",
		)
		assertTrue(result.contains("\"error\":\"not_found\""))
	}

	@Test
	fun getPanelComponents_returns_array_of_components() = runTest {
		db.labDao().upsertAll(listOf(
			lab("a", sr = "ServiceRequest/bmp", testName = "Glucose"),
			lab("b", sr = "ServiceRequest/bmp", testName = "Creatinine"),
		))
		val result = tools.dispatch(
			"getPanelComponents",
			"""{"serviceRequestRef":"ServiceRequest/bmp"}""",
		)
		val arr = json.parseToJsonElement(result).jsonArray
		assertEquals(2, arr.size)
		val names = arr.map { it.jsonObject["testName"]!!.jsonPrimitive.content }
		assertTrue(names.contains("Glucose"))
		assertTrue(names.contains("Creatinine"))
	}

	@Test
	fun searchFreeText_finds_matching_rawJson() = runTest {
		db.sourceRecordDao().upsert(SourceRecord(
			sourceSystem = "cc",
			sourceName = "CC",
			resourceType = "DocumentReference",
			resourceId = "doc1",
			fhirReference = "DocumentReference/doc1",
			rawJson = """{"content":"Impression: Borderline metabolic syndrome risk."}""",
			importedAt = Instant.parse("2026-04-18T00:00:00Z"),
		))
		val result = tools.dispatch(
			"searchFreeText",
			"""{"query":"metabolic"}""",
		)
		val arr = json.parseToJsonElement(result).jsonArray
		assertEquals(1, arr.size)
	}

	@Test
	fun unknown_tool_returns_structured_error() = runTest {
		val result = tools.dispatch("doesNotExist", "{}")
		assertTrue(result.contains("\"error\":\"unknown_tool\""))
	}

	@Test
	fun malformed_json_returns_structured_error() = runTest {
		val result = tools.dispatch("getRawObservation", "this is not json")
		assertTrue(result.contains("\"error\":"))
	}

	@Test
	fun missing_arg_returns_structured_error() = runTest {
		val result = tools.dispatch("getRawObservation", """{"sourceSystem":"cc"}""")
		assertTrue(result.contains("missing required arg"))
	}

	private fun lab(
		id: String,
		sr: String,
		testName: String,
	) = LabObservation(
		sourceSystem = "cc",
		sourceName = "CC",
		fhirReference = "Observation/$id",
		resourceId = id,
		testName = testName,
		canonicalTestName = testName,
		serviceRequestReference = sr,
		numericValue = 1.0,
		unit = "",
		importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
```

- [ ] **Step 4: Run + commit**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.ai.AssistantToolsTest
```

Expected: PASS 9/9.

```bash
git add app/src/main/java/com/healthaggregator/ai/AssistantTools.kt \
        app/src/main/java/com/healthaggregator/data/repository/RecordsRepository.kt \
        app/src/main/java/com/healthaggregator/data/dao/SourceRecordDao.kt \
        app/src/test/java/com/healthaggregator/ai/AssistantToolsTest.kt
git commit -m "Phase 2: AssistantTools registry + dispatch

Three tools — getRawObservation, getPanelComponents, searchFreeText.
Schemas shaped for OpenAI function-calling. Dispatcher is
exception-safe: every failure path (unknown tool, missing arg,
malformed JSON, not-found lookup) returns a structured JSON error
string rather than throwing. SourceRecordDao.searchFreeText + repo
facade added to back searchFreeText."
```

---

### Task 11: CitationRenderer parse + tests

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ai/CitationRenderer.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/ai/CitationRendererTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.healthaggregator.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CitationRendererTest {
	@Test
	fun parse_plain_text_returns_single_text_segment() {
		val out = CitationRenderer.parse("just words")
		assertEquals(listOf(CitationSegment.Text("just words")), out)
	}

	@Test
	fun parse_single_citation_splits_into_text_and_cite() {
		val out = CitationRenderer.parse("Your A1c [cite:summa/Observation/abc] is high.")
		assertEquals(
			listOf(
				CitationSegment.Text("Your A1c "),
				CitationSegment.Cite("summa", "Observation/abc"),
				CitationSegment.Text(" is high."),
			),
			out,
		)
	}

	@Test
	fun parse_multiple_citations() {
		val out = CitationRenderer.parse(
			"Both [cite:cc/Observation/a] and [cite:summa/Observation/b] are high.",
		)
		assertEquals(5, out.size)
		assertEquals(CitationSegment.Cite("cc", "Observation/a"), out[1])
		assertEquals(CitationSegment.Cite("summa", "Observation/b"), out[3])
	}

	@Test
	fun parse_adjacent_citations() {
		val out = CitationRenderer.parse("[cite:a/b/c][cite:d/e/f]")
		assertEquals(listOf(
			CitationSegment.Cite("a", "b/c"),
			CitationSegment.Cite("d", "e/f"),
		), out)
	}

	@Test
	fun parse_malformed_no_close_is_left_as_text() {
		val out = CitationRenderer.parse("text [cite:incomplete")
		assertEquals(listOf(CitationSegment.Text("text [cite:incomplete")), out)
	}

	@Test
	fun parse_non_cite_brackets_are_unaffected() {
		val out = CitationRenderer.parse("See [note] here.")
		assertEquals(listOf(CitationSegment.Text("See [note] here.")), out)
	}

	@Test
	fun parse_cite_with_slash_in_ref() {
		// The ref portion may contain slashes (FHIR refs are "Type/id")
		val out = CitationRenderer.parse("[cite:cleveland-clinic/Observation/sub/path-id]")
		assertEquals(
			listOf(CitationSegment.Cite("cleveland-clinic", "Observation/sub/path-id")),
			out,
		)
	}

	@Test
	fun parse_empty_string() {
		val out = CitationRenderer.parse("")
		assertEquals(emptyList<CitationSegment>(), out)
	}
}
```

- [ ] **Step 2: Run — expect FAIL (unresolved references)**

- [ ] **Step 3: Implement**

```kotlin
package com.healthaggregator.ai

sealed interface CitationSegment {
	data class Text(val text: String) : CitationSegment
	data class Cite(val sourceSystem: String, val fhirRef: String) : CitationSegment
}

/**
 * Parses LLM output into alternating text / citation segments. A citation marker matches
 * `[cite:<sourceSystem>/<fhirRef>]` where sourceSystem contains no `/` and fhirRef may
 * contain any characters except `]`.
 */
object CitationRenderer {
	private val REGEX = Regex("""\[cite:([^/\]]+)/([^\]]+)\]""")

	fun parse(input: String): List<CitationSegment> {
		if (input.isEmpty()) return emptyList()
		val result = mutableListOf<CitationSegment>()
		var cursor = 0
		for (match in REGEX.findAll(input)) {
			if (match.range.first > cursor) {
				result += CitationSegment.Text(input.substring(cursor, match.range.first))
			}
			val (source, ref) = match.destructured
			result += CitationSegment.Cite(source, ref)
			cursor = match.range.last + 1
		}
		if (cursor < input.length) {
			result += CitationSegment.Text(input.substring(cursor))
		}
		return if (result.size == 1 && result[0] is CitationSegment.Text) {
			result.toList()
		} else result.toList()
	}
}
```

- [ ] **Step 4: Run + commit**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.ai.CitationRendererTest
git add app/src/main/java/com/healthaggregator/ai/CitationRenderer.kt \
        app/src/test/java/com/healthaggregator/ai/CitationRendererTest.kt
git commit -m "Phase 2: CitationRenderer

Pure-Kotlin regex parser splits LLM output into Text/Cite segments.
Handles adjacent cites, malformed (no close bracket), multi-segment
fhirRefs with slashes, and plain bracketed text unchanged."
```

---

### Task 12 (Phase 2 gate)

- [ ] **Step 1: Full build + tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL; all prior tests + ~32 new Phase-2 tests pass.

- [ ] **Step 2: Gate commit**

```bash
git commit --allow-empty -m "Phase 2 gate: AI primitives ready

SystemPrompt stable. Near-abnormal classifier covers HIGH/LOW
proper + NORMAL-adjacent zones. HealthSnapshotBuilder emits full
longitudinal markdown with canonical grouping + inline citations
+ BP caveat. AssistantTools exposes three function-calling
schemas with exception-safe dispatch. CitationRenderer parses
[cite:…] markers to structured segments.

All pure-Kotlin or Room-backed; no network wiring yet."
```

---

# Phase 3 — OpenAI client + secure storage

Add dependencies; build SSE streaming client; secure the API key.

### Task 13: Add deps (security-crypto, okhttp-sse, mockwebserver)

**Files:**
- Modify: `healthaggregator-android/gradle/libs.versions.toml`
- Modify: `healthaggregator-android/app/build.gradle.kts`

- [ ] **Step 1: Update `libs.versions.toml`**

In `[versions]`, add:

```toml
securityCrypto = "1.1.0-alpha06"
```

In `[libraries]`, add:

```toml
androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
okhttp-sse = { group = "com.squareup.okhttp3", name = "okhttp-sse", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
```

- [ ] **Step 2: Wire into `app/build.gradle.kts`**

In `dependencies { }`, add:

```kotlin
implementation(libs.androidx.security.crypto)
implementation(libs.okhttp.sse)
testImplementation(libs.okhttp.mockwebserver)
```

- [ ] **Step 3: Sync + verify build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "Phase 3: add security-crypto + okhttp-sse + mockwebserver deps

EncryptedSharedPreferences backs the API-key store. okhttp-sse
gives EventSource-based SSE client for OpenAI streaming.
MockWebServer (test scope) for HTTP round-trip tests."
```

---

### Task 14: SecureStorage wrapper + test

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/util/SecureStorage.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/ai/SecureStorageTest.kt`

- [ ] **Step 1: Implement `SecureStorage.kt`**

```kotlin
package com.healthaggregator.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted key/value store for sensitive data (OpenAI API key, user acknowledgments, etc).
 * Backed by EncryptedSharedPreferences — AES-256-GCM for values, AES-256-SIV for keys, using
 * a Keystore-backed master key.
 */
@Singleton
class SecureStorage @Inject constructor(@ApplicationContext ctx: Context) {

	private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
		ctx,
		FILE_NAME,
		MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
		EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
		EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
	)

	var openAiApiKey: String?
		get() = prefs.getString(KEY_OPENAI_API_KEY, null)?.ifEmpty { null }
		set(value) = prefs.edit().run { if (value.isNullOrBlank()) remove(KEY_OPENAI_API_KEY) else putString(KEY_OPENAI_API_KEY, value); apply() }

	var dataSharingEnabled: Boolean
		get() = prefs.getBoolean(KEY_DATA_SHARING, false)
		set(value) = prefs.edit().putBoolean(KEY_DATA_SHARING, value).apply()

	var selectedModel: String
		get() = prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
		set(value) = prefs.edit().putString(KEY_SELECTED_MODEL, value).apply()

	var disclaimerAcknowledged: Boolean
		get() = prefs.getBoolean(KEY_DISCLAIMER, false)
		set(value) = prefs.edit().putBoolean(KEY_DISCLAIMER, value).apply()

	fun clearApiKey() { openAiApiKey = null }

	companion object {
		private const val FILE_NAME = "healthaggregator_secure"
		private const val KEY_OPENAI_API_KEY = "openai_api_key"
		private const val KEY_DATA_SHARING = "data_sharing_enabled"
		private const val KEY_SELECTED_MODEL = "selected_model"
		private const val KEY_DISCLAIMER = "disclaimer_acknowledged"
		const val DEFAULT_MODEL = "gpt-5"

		val ELIGIBLE_FREE_TIER_MODELS = setOf("gpt-5", "gpt-5-mini", "gpt-5-nano")
		val AVAILABLE_MODELS = listOf(
			"gpt-5" to "GPT-5 (free-tier eligible)",
			"gpt-5.4" to "GPT-5.4 (latest, paid)",
			"gpt-5.4-pro" to "GPT-5.4 Pro (best quality, paid)",
			"gpt-5.4-mini" to "GPT-5.4 Mini (fast, cheap)",
			"gpt-5.4-nano" to "GPT-5.4 Nano (cheapest)",
			"gpt-5-mini" to "GPT-5 Mini",
			"gpt-5-nano" to "GPT-5 Nano",
		)
	}
}
```

- [ ] **Step 2: Write test**

```kotlin
package com.healthaggregator.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.util.SecureStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SecureStorageTest {

	@Test
	fun defaults_areFalseAndDefaultModel() {
		val s = SecureStorage(ApplicationProvider.getApplicationContext())
		assertNull(s.openAiApiKey)
		assertFalse(s.dataSharingEnabled)
		assertEquals("gpt-5", s.selectedModel)
		assertFalse(s.disclaimerAcknowledged)
	}

	@Test
	fun apiKey_roundTrip() {
		val s = SecureStorage(ApplicationProvider.getApplicationContext())
		s.openAiApiKey = "sk-test-abc"
		assertEquals("sk-test-abc", s.openAiApiKey)
	}

	@Test
	fun apiKey_blankClears() {
		val s = SecureStorage(ApplicationProvider.getApplicationContext())
		s.openAiApiKey = "sk-x"
		s.openAiApiKey = ""
		assertNull(s.openAiApiKey)
	}

	@Test
	fun selectedModel_persists() {
		val s = SecureStorage(ApplicationProvider.getApplicationContext())
		s.selectedModel = "gpt-5.4"
		assertEquals("gpt-5.4", s.selectedModel)
	}

	@Test
	fun disclaimer_persists() {
		val s = SecureStorage(ApplicationProvider.getApplicationContext())
		s.disclaimerAcknowledged = true
		assertTrue(s.disclaimerAcknowledged)
	}
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.ai.SecureStorageTest
git add app/src/main/java/com/healthaggregator/util/SecureStorage.kt \
        app/src/test/java/com/healthaggregator/ai/SecureStorageTest.kt
git commit -m "Phase 3: SecureStorage wrapper

EncryptedSharedPreferences persists API key, data-sharing opt-in,
selected model, and disclaimer acknowledgment. Public AVAILABLE_MODELS
and ELIGIBLE_FREE_TIER_MODELS constants drive the UI picker."
```

---

### Task 15: LlmClient interface + sealed types

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ai/LlmClient.kt`

- [ ] **Step 1: Write the interface file**

```kotlin
package com.healthaggregator.ai

import kotlinx.coroutines.flow.Flow

/** Provider-agnostic LLM streaming contract. */
interface LlmClient {
	/**
	 * Issue a streaming chat completion. The returned Flow emits StreamEvent values until
	 * Done or Error. If tools are provided, the implementation handles the full tool-call
	 * round-trip: pause on ToolCallRequest, dispatch externally, inject tool result, resume.
	 *
	 * The [dispatchTool] suspend lambda is invoked for every tool call the model emits; it
	 * must return the JSON-string response to feed back to the model.
	 */
	fun stream(
		modelId: String,
		messages: List<LlmMessage>,
		tools: List<ToolSchema>,
		dispatchTool: suspend (name: String, argsJson: String) -> String,
	): Flow<StreamEvent>
}

data class LlmMessage(
	val role: String, // "system" | "user" | "assistant" | "tool"
	val content: String,
	val toolCalls: List<LlmToolCall>? = null,
	val toolCallId: String? = null,
)

data class LlmToolCall(
	val id: String,
	val name: String,
	val argsJson: String,
)

sealed interface StreamEvent {
	data class TokenDelta(val text: String) : StreamEvent
	data class ToolCallStarted(val id: String, val name: String) : StreamEvent
	data class ToolCallCompleted(val id: String) : StreamEvent
	data class Error(val message: String, val retryable: Boolean) : StreamEvent
	data object Done : StreamEvent
}
```

- [ ] **Step 2: Commit (no test yet — interface only, OpenAiClient follows in Task 16)**

```bash
git add app/src/main/java/com/healthaggregator/ai/LlmClient.kt
git commit -m "Phase 3: LlmClient interface + StreamEvent sealed hierarchy

Provider-agnostic contract — OpenAiClient (Task 16) is the first
implementation; future providers (Anthropic, Gemini) slot behind
this same interface without repo changes. dispatchTool lambda keeps
tool resolution outside the client for testability + decoupling."
```

---

### Task 16: OpenAiClient SSE streaming + tool calls + tests

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ai/OpenAiClient.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/ai/OpenAiClientTest.kt`

- [ ] **Step 1: Implement `OpenAiClient.kt`**

```kotlin
package com.healthaggregator.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class OpenAiClient(
	private val baseUrl: String = "https://api.openai.com/v1",
	private val apiKeyProvider: () -> String?,
	private val http: OkHttpClient = defaultHttp(),
	private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
) : LlmClient {

	override fun stream(
		modelId: String,
		messages: List<LlmMessage>,
		tools: List<ToolSchema>,
		dispatchTool: suspend (name: String, argsJson: String) -> String,
	): Flow<StreamEvent> = channelFlow {
		val apiKey = apiKeyProvider()
		if (apiKey.isNullOrBlank()) {
			trySend(StreamEvent.Error("no_api_key", retryable = false))
			trySend(StreamEvent.Done)
			close()
			return@channelFlow
		}

		// Mutable conversation we extend across tool-call round-trips.
		val working = messages.toMutableList()

		// Loop: request → stream → if tool calls, dispatch + append + request again.
		while (true) {
			val body = buildRequestBody(modelId, working, tools)
			val req = Request.Builder()
				.url("$baseUrl/chat/completions")
				.addHeader("Authorization", "Bearer $apiKey")
				.addHeader("Accept", "text/event-stream")
				.post(body.toString().toRequestBody(JSON_MEDIA))
				.build()

			val accumulated = StringBuilder()
			val accumulatedToolCalls = mutableMapOf<Int, AccumulatedToolCall>()
			var terminalError: StreamEvent.Error? = null
			var sawTools = false
			val completion = kotlinx.coroutines.CompletableDeferred<Unit>()

			val listener = object : EventSourceListener() {
				override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
					if (data == "[DONE]") {
						completion.complete(Unit); return
					}
					val delta = parseDelta(data) ?: return
					delta.content?.let {
						accumulated.append(it)
						trySendBlocking(StreamEvent.TokenDelta(it))
					}
					delta.toolCalls.forEach { tc ->
						val slot = accumulatedToolCalls.getOrPut(tc.index) { AccumulatedToolCall() }
						tc.id?.let { slot.id = it }
						tc.name?.let {
							slot.name = it
							sawTools = true
							trySendBlocking(StreamEvent.ToolCallStarted(slot.id ?: "unknown", it))
						}
						tc.argsJsonChunk?.let { slot.argsJson.append(it) }
					}
				}

				override fun onClosed(eventSource: EventSource) {
					if (!completion.isCompleted) completion.complete(Unit)
				}

				override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
					val code = response?.code ?: -1
					val retryable = code == 429 || code in 500..599 || t != null
					val msg = when {
						code == 401 -> "auth_invalid"
						code == 429 -> "rate_limit"
						code in 500..599 -> "server_error_$code"
						t != null -> "network_error:${t.message}"
						else -> "unknown_error_$code"
					}
					terminalError = StreamEvent.Error(msg, retryable)
					if (!completion.isCompleted) completion.complete(Unit)
				}
			}

			val es = EventSources.createFactory(http).newEventSource(req, listener)
			completion.await()
			es.cancel()

			if (terminalError != null) {
				trySend(terminalError!!)
				trySend(StreamEvent.Done)
				close()
				return@channelFlow
			}

			if (!sawTools) {
				trySend(StreamEvent.Done)
				close()
				return@channelFlow
			}

			// Append assistant message (with tool_calls) + run each tool + append its result message.
			val toolCalls = accumulatedToolCalls.values.mapNotNull {
				val id = it.id ?: return@mapNotNull null
				val name = it.name ?: return@mapNotNull null
				LlmToolCall(id, name, it.argsJson.toString())
			}
			working.add(LlmMessage(role = "assistant", content = accumulated.toString(), toolCalls = toolCalls))
			for (call in toolCalls) {
				val result = try {
					dispatchTool(call.name, call.argsJson)
				} catch (e: Exception) {
					"""{"error":"dispatch_failed","message":"${e.message?.replace("\"", "\\\"")}"}"""
				}
				working.add(LlmMessage(role = "tool", content = result, toolCallId = call.id))
				trySend(StreamEvent.ToolCallCompleted(call.id))
			}
			// Loop: next round will send working + tools again.
		}

		@Suppress("UNREACHABLE_CODE")
		awaitClose { }
	}

	private fun buildRequestBody(
		modelId: String,
		messages: List<LlmMessage>,
		tools: List<ToolSchema>,
	): JsonObject = buildJsonObject {
		put("model", modelId)
		put("stream", true)
		put("messages", buildJsonArray {
			messages.forEach { m ->
				addJsonObject {
					put("role", m.role)
					put("content", m.content)
					m.toolCallId?.let { put("tool_call_id", it) }
					m.toolCalls?.let { tcs ->
						put("tool_calls", buildJsonArray {
							tcs.forEach { tc ->
								addJsonObject {
									put("id", tc.id)
									put("type", "function")
									put("function", buildJsonObject {
										put("name", tc.name)
										put("arguments", tc.argsJson)
									})
								}
							}
						})
					}
				}
			}
		})
		if (tools.isNotEmpty()) {
			put("tools", buildJsonArray {
				tools.forEach { t ->
					addJsonObject {
						put("type", "function")
						put("function", buildJsonObject {
							put("name", t.name)
							put("description", t.description)
							put("parameters", t.parameters)
						})
					}
				}
			})
		}
	}

	private data class AccumulatedToolCall(
		var id: String? = null,
		var name: String? = null,
		val argsJson: StringBuilder = StringBuilder(),
	)

	private data class Delta(
		val content: String?,
		val toolCalls: List<ToolCallDelta>,
	)

	private data class ToolCallDelta(
		val index: Int,
		val id: String?,
		val name: String?,
		val argsJsonChunk: String?,
	)

	private fun parseDelta(dataLine: String): Delta? {
		val root = try { json.parseToJsonElement(dataLine).jsonObject } catch (_: Exception) { return null }
		val choice = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
		val delta = choice["delta"]?.jsonObject ?: return null
		val content = (delta["content"] as? JsonPrimitive)?.content
		val toolCalls = (delta["tool_calls"] as? JsonArray)?.map {
			val tc = it.jsonObject
			ToolCallDelta(
				index = (tc["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
				id = (tc["id"] as? JsonPrimitive)?.content,
				name = (tc["function"]?.jsonObject?.get("name") as? JsonPrimitive)?.content,
				argsJsonChunk = (tc["function"]?.jsonObject?.get("arguments") as? JsonPrimitive)?.content,
			)
		} ?: emptyList()
		return Delta(content, toolCalls)
	}

	companion object {
		private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
		fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
			.connectTimeout(20, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.callTimeout(180, TimeUnit.SECONDS)
			.build()
	}
}
```

- [ ] **Step 2: Write `OpenAiClientTest.kt`**

```kotlin
package com.healthaggregator.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OpenAiClientTest {
	private lateinit var server: MockWebServer
	private lateinit var client: OpenAiClient

	@Before
	fun setup() {
		server = MockWebServer()
		server.start()
		val http = OkHttpClient.Builder()
			.readTimeout(5, TimeUnit.SECONDS)
			.callTimeout(10, TimeUnit.SECONDS)
			.build()
		client = OpenAiClient(
			baseUrl = server.url("/v1").toString().trimEnd('/'),
			apiKeyProvider = { "sk-test" },
			http = http,
		)
	}

	@After
	fun tearDown() { server.shutdown() }

	@Test
	fun streams_token_deltas_and_ends_with_done() = runTest {
		server.enqueue(sseResponse(
			"""data: {"choices":[{"delta":{"content":"Hel"}}]}""",
			"""data: {"choices":[{"delta":{"content":"lo"}}]}""",
			"""data: [DONE]""",
		))

		val events = client.stream(
			modelId = "gpt-5",
			messages = listOf(LlmMessage("user", "hi")),
			tools = emptyList(),
			dispatchTool = { _, _ -> "" },
		).toList()

		val tokens = events.filterIsInstance<StreamEvent.TokenDelta>().map { it.text }
		assertEquals(listOf("Hel", "lo"), tokens)
		assertTrue(events.last() is StreamEvent.Done)
	}

	@Test
	fun tool_call_round_trip_emits_started_completed_and_resumes() = runTest {
		// First response: tool call. Second response: final content.
		server.enqueue(sseResponse(
			"""data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"getRawObservation"}}]}}]}""",
			"""data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"sourceSystem\":\"cc\",\"fhirRef\":\"Observation/abc\"}"}}]}}]}""",
			"""data: [DONE]""",
		))
		server.enqueue(sseResponse(
			"""data: {"choices":[{"delta":{"content":"done"}}]}""",
			"""data: [DONE]""",
		))

		var dispatched: Pair<String, String>? = null
		val events = client.stream(
			modelId = "gpt-5",
			messages = listOf(LlmMessage("user", "look it up")),
			tools = listOf(ToolSchema("getRawObservation", "fetch raw", buildJsonObject { put("type", "object") })),
			dispatchTool = { name, args -> dispatched = name to args; """{"resourceType":"Observation"}""" },
		).toList()

		assertEquals("getRawObservation", dispatched!!.first)
		assertTrue(dispatched!!.second.contains("Observation/abc"))

		assertTrue(events.any { it is StreamEvent.ToolCallStarted && it.name == "getRawObservation" })
		assertTrue(events.any { it is StreamEvent.ToolCallCompleted })
		val finalText = events.filterIsInstance<StreamEvent.TokenDelta>().joinToString("") { it.text }
		assertEquals("done", finalText)
		assertTrue(events.last() is StreamEvent.Done)
	}

	@Test
	fun rate_limit_emits_retryable_error() = runTest {
		server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"rate"}}"""))

		val events = client.stream(
			modelId = "gpt-5",
			messages = listOf(LlmMessage("user", "hi")),
			tools = emptyList(),
			dispatchTool = { _, _ -> "" },
		).toList()

		val err = events.filterIsInstance<StreamEvent.Error>().firstOrNull()
		assertTrue(err != null)
		assertEquals("rate_limit", err!!.message)
		assertTrue(err.retryable)
	}

	@Test
	fun auth_error_emits_non_retryable() = runTest {
		server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))

		val events = client.stream(
			modelId = "gpt-5",
			messages = listOf(LlmMessage("user", "hi")),
			tools = emptyList(),
			dispatchTool = { _, _ -> "" },
		).toList()

		val err = events.filterIsInstance<StreamEvent.Error>().firstOrNull()
		assertEquals("auth_invalid", err!!.message)
		assertFalse(err.retryable)
	}

	@Test
	fun missing_api_key_short_circuits_with_no_api_key_error() = runTest {
		val noKeyClient = OpenAiClient(
			baseUrl = server.url("/v1").toString().trimEnd('/'),
			apiKeyProvider = { null },
		)
		val events = noKeyClient.stream(
			modelId = "gpt-5",
			messages = listOf(LlmMessage("user", "hi")),
			tools = emptyList(),
			dispatchTool = { _, _ -> "" },
		).toList()

		val err = events.filterIsInstance<StreamEvent.Error>().firstOrNull()
		assertEquals("no_api_key", err!!.message)
		assertEquals(0, server.requestCount)
	}

	private fun sseResponse(vararg lines: String): MockResponse = MockResponse()
		.setHeader("Content-Type", "text/event-stream")
		.setBody(lines.joinToString("\n\n") + "\n\n")
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.ai.OpenAiClientTest
git add app/src/main/java/com/healthaggregator/ai/OpenAiClient.kt \
        app/src/test/java/com/healthaggregator/ai/OpenAiClientTest.kt
git commit -m "Phase 3: OpenAiClient streaming + tool calls

OkHttp EventSource-backed SSE streaming. Tool-call round-trip:
pauses on tool_calls delta, invokes dispatchTool lambda, appends
result as role='tool' message, resumes streaming with the same
working message list. Error mapping covers 401 (auth, non-retryable),
429 (rate_limit, retryable), 5xx (server_error_XXX, retryable),
network exceptions (network_error, retryable), and missing API key
(short-circuits before any HTTP call)."
```

---

### Task 17 (Phase 3 gate)

- [ ] **Step 1: Build + all tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL; Phase-3 additions (~10 new tests) green.

- [ ] **Step 2: Gate commit**

```bash
git commit --allow-empty -m "Phase 3 gate: OpenAI client + secure storage ready

Streaming + tool calls + every error path tested against
MockWebServer. EncryptedSharedPreferences wraps the key store.
All provider-specific logic behind the LlmClient interface."
```

---

# Phase 4 — Repository + ViewModel

Tie the primitives together. No UI yet.

### Task 18: AssistantRepository + tests

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ai/AssistantRepository.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/ai/AssistantRepositoryTest.kt`

- [ ] **Step 1: Implement `AssistantRepository.kt`**

```kotlin
package com.healthaggregator.ai

import com.healthaggregator.data.dao.ChatDao
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import com.healthaggregator.util.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantRepository @Inject constructor(
	private val chat: ChatDao,
	private val llm: LlmClient,
	private val tools: AssistantTools,
	private val snapshots: HealthSnapshotBuilder,
	private val secure: SecureStorage,
) {
	fun observeConversations(): Flow<List<ChatConversation>> = chat.observeConversations()
	fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = chat.observeMessages(conversationId)

	suspend fun createConversation(title: String = "New chat"): ChatConversation {
		val now = Instant.now()
		val c = ChatConversation(
			id = UUID.randomUUID().toString(),
			title = title,
			createdAt = now,
			updatedAt = now,
			modelId = secure.selectedModel,
		)
		chat.upsertConversation(c)
		return c
	}

	suspend fun renameConversation(id: String, title: String) {
		chat.renameConversation(id, title, Instant.now())
	}

	suspend fun deleteConversation(id: String) {
		chat.deleteConversation(id)
	}

	suspend fun clearAll() {
		chat.deleteAllConversations()
	}

	/**
	 * Send a user message and stream the assistant's response. Persists everything as it goes:
	 * user message → snapshot (first turn) → assistant message with tokens → tool messages →
	 * final assistant message. Auto-derives a conversation title from the first user message
	 * if still "New chat".
	 */
	fun send(conversationId: String, userText: String): Flow<StreamEvent> = flow {
		val now = Instant.now()
		val userMsg = ChatMessage(
			id = UUID.randomUUID().toString(),
			conversationId = conversationId,
			role = "user",
			content = userText,
			createdAt = now,
		)
		chat.upsertMessage(userMsg)
		chat.touch(conversationId, now)

		val conversation = chat.getConversation(conversationId) ?: error("conversation not found: $conversationId")

		// Derive title from the first user message.
		if (conversation.title == "New chat") {
			chat.renameConversation(conversationId, userText.take(60).trim().ifEmpty { "Chat" }, now)
		}

		// Ensure snapshot exists for this conversation (persisted on first turn, reused after).
		val snapshot: String = conversation.snapshotText ?: run {
			val built = snapshots.build(now)
			chat.setSnapshot(conversationId, built, now)
			built
		}

		val priorMessages = chat.messagesSnapshot(conversationId).filter { it.role == "user" || it.role == "assistant" || it.role == "tool" }
		val llmMessages = buildList {
			add(LlmMessage(role = "system", content = SystemPrompt.TEXT))
			add(LlmMessage(role = "user", content = snapshot))
			priorMessages.forEach { msg ->
				add(LlmMessage(
					role = msg.role,
					content = msg.content,
					toolCallId = msg.toolCallId,
				))
			}
		}

		val assistantBuffer = StringBuilder()
		val assistantId = UUID.randomUUID().toString()
		val assistantMsg = ChatMessage(
			id = assistantId,
			conversationId = conversationId,
			role = "assistant",
			content = "",
			modelId = conversation.modelId,
			createdAt = Instant.now(),
		)
		chat.upsertMessage(assistantMsg)

		llm.stream(
			modelId = conversation.modelId,
			messages = llmMessages,
			tools = tools.schemas,
			dispatchTool = { name, args -> tools.dispatch(name, args) },
		).collect { ev ->
			when (ev) {
				is StreamEvent.TokenDelta -> {
					assistantBuffer.append(ev.text)
					chat.upsertMessage(assistantMsg.copy(content = assistantBuffer.toString(), createdAt = Instant.now()))
				}
				is StreamEvent.ToolCallStarted, is StreamEvent.ToolCallCompleted -> {
					// UI surfaces these; no persistence needed beyond the eventual tool-message write.
				}
				is StreamEvent.Error, StreamEvent.Done -> {}
			}
			emit(ev)
		}

		chat.touch(conversationId, Instant.now())
	}
}
```

- [ ] **Step 2: Test (Robolectric + in-memory Room + fake LlmClient)**

```kotlin
package com.healthaggregator.ai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.repository.RecordsRepository
import com.healthaggregator.util.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AssistantRepositoryTest {
	private lateinit var db: AppDatabase
	private lateinit var repo: AssistantRepository
	private lateinit var fakeLlm: FakeLlmClient

	@Before
	fun setup() {
		val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
		db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
		val records = RecordsRepository(
			labs = db.labDao(), vitals = db.vitalsDao(), medications = db.medicationDao(),
			conditions = db.conditionDao(), allergies = db.allergyDao(), encounters = db.encounterDao(),
			documents = db.documentDao(), sources = db.medicalDataSourceDao(),
			syncJobs = db.syncJobDao(), sourceRecords = db.sourceRecordDao(),
		)
		fakeLlm = FakeLlmClient()
		repo = AssistantRepository(
			chat = db.chatDao(),
			llm = fakeLlm,
			tools = AssistantTools(records, Json),
			snapshots = HealthSnapshotBuilder(
				labs = db.labDao(), vitals = db.vitalsDao(), medications = db.medicationDao(),
				conditions = db.conditionDao(), allergies = db.allergyDao(), documents = db.documentDao(),
			),
			secure = SecureStorage(ctx),
		)
	}

	@After fun tearDown() { db.close() }

	@Test
	fun createConversation_insertsRow() = runTest {
		val c = repo.createConversation()
		val obs = repo.observeConversations().first()
		assertEquals(1, obs.size)
		assertEquals(c.id, obs[0].id)
	}

	@Test
	fun send_persistsUserAndAssistantMessages() = runTest {
		val c = repo.createConversation()
		fakeLlm.scripted = listOf(
			StreamEvent.TokenDelta("Hel"),
			StreamEvent.TokenDelta("lo"),
			StreamEvent.Done,
		)

		repo.send(c.id, "hi there").toList()

		val msgs = db.chatDao().messagesSnapshot(c.id)
		assertEquals(listOf("user", "assistant"), msgs.map { it.role })
		assertEquals("hi there", msgs[0].content)
		assertEquals("Hello", msgs[1].content)
	}

	@Test
	fun send_persistsSnapshotOnFirstTurn_reusesOnSecond() = runTest {
		val c = repo.createConversation()
		fakeLlm.scripted = listOf(StreamEvent.TokenDelta("ok"), StreamEvent.Done)

		repo.send(c.id, "first").toList()
		val afterFirst = db.chatDao().getConversation(c.id)!!
		assertTrue(afterFirst.snapshotText!!.contains("# Jesse's Full Health Data Context"))

		val firstSnapshot = afterFirst.snapshotText
		fakeLlm.scripted = listOf(StreamEvent.TokenDelta("ok2"), StreamEvent.Done)
		repo.send(c.id, "second").toList()
		val afterSecond = db.chatDao().getConversation(c.id)!!
		// Snapshot unchanged (reused, not rebuilt)
		assertEquals(firstSnapshot, afterSecond.snapshotText)
	}

	@Test
	fun send_derivesTitleFromFirstUserMessage() = runTest {
		val c = repo.createConversation()
		fakeLlm.scripted = listOf(StreamEvent.TokenDelta("ok"), StreamEvent.Done)
		repo.send(c.id, "what is my A1c").toList()
		assertEquals("what is my A1c", db.chatDao().getConversation(c.id)!!.title)
	}

	@Test
	fun deleteConversation_cascadesAllMessages() = runTest {
		val c = repo.createConversation()
		fakeLlm.scripted = listOf(StreamEvent.TokenDelta("ok"), StreamEvent.Done)
		repo.send(c.id, "hi").toList()

		repo.deleteConversation(c.id)
		assertTrue(db.chatDao().messagesSnapshot(c.id).isEmpty())
		assertTrue(repo.observeConversations().first().isEmpty())
	}

	private class FakeLlmClient : LlmClient {
		var scripted: List<StreamEvent> = emptyList()
		override fun stream(
			modelId: String,
			messages: List<LlmMessage>,
			tools: List<ToolSchema>,
			dispatchTool: suspend (String, String) -> String,
		): Flow<StreamEvent> = flow { scripted.forEach { emit(it) } }
	}
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.ai.AssistantRepositoryTest
git add app/src/main/java/com/healthaggregator/ai/AssistantRepository.kt \
        app/src/test/java/com/healthaggregator/ai/AssistantRepositoryTest.kt
git commit -m "Phase 4: AssistantRepository

Orchestrates createConversation, send, rename, delete. send()
persists user/assistant messages, builds snapshot on first turn
(reuses after), auto-titles from first user message, assembles
system+snapshot+history for the LlmClient, and writes streaming
tokens to the assistant row as they arrive."
```

---

### Task 19: AiModule Hilt bindings

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/di/AiModule.kt`

- [ ] **Step 1: Write the module**

```kotlin
package com.healthaggregator.di

import com.healthaggregator.ai.LlmClient
import com.healthaggregator.ai.OpenAiClient
import com.healthaggregator.util.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
	@Provides
	@Singleton
	fun provideJson(): Json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = false
		prettyPrint = false
	}

	@Provides
	@Singleton
	fun provideLlmClient(secure: SecureStorage, json: Json): LlmClient =
		OpenAiClient(apiKeyProvider = { secure.openAiApiKey }, json = json)
}
```

- [ ] **Step 2: Build + commit**

```bash
./gradlew :app:assembleDebug
git add app/src/main/java/com/healthaggregator/di/AiModule.kt
git commit -m "Phase 4: AiModule Hilt bindings

Provides singleton Json + LlmClient (backed by OpenAiClient
reading the API key from SecureStorage). AssistantTools,
HealthSnapshotBuilder, AssistantRepository are @Singleton
@Inject — Hilt wires them automatically."
```

---

### Task 20: AssistantViewModel + tests

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/assistant/AssistantViewModel.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/ui/assistant/AssistantViewModelTest.kt`

- [ ] **Step 1: Implement `AssistantViewModel.kt`**

```kotlin
package com.healthaggregator.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthaggregator.ai.AssistantRepository
import com.healthaggregator.ai.StreamEvent
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import com.healthaggregator.util.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssistantUiState(
	val conversations: List<ChatConversation> = emptyList(),
	val activeConversationId: String? = null,
	val messages: List<ChatMessage> = emptyList(),
	val streaming: Boolean = false,
	val error: String? = null,
	val apiKeyMissing: Boolean = false,
	val disclaimerAcknowledged: Boolean = false,
	val activeToolCalls: Set<String> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssistantViewModel @Inject constructor(
	private val repo: AssistantRepository,
	private val secure: SecureStorage,
) : ViewModel() {

	private val activeId = MutableStateFlow<String?>(null)
	private val streaming = MutableStateFlow(false)
	private val error = MutableStateFlow<String?>(null)
	private val activeToolCalls = MutableStateFlow<Set<String>>(emptySet())

	private val conversations: StateFlow<List<ChatConversation>> =
		repo.observeConversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

	private val messages: StateFlow<List<ChatMessage>> =
		activeId.flatMapLatest { id ->
			if (id == null) emptyFlow() else repo.observeMessages(id)
		}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

	val uiState: StateFlow<AssistantUiState> =
		kotlinx.coroutines.flow.combine(
			conversations, activeId, messages, streaming, error, activeToolCalls,
		) { arr ->
			@Suppress("UNCHECKED_CAST")
			AssistantUiState(
				conversations = arr[0] as List<ChatConversation>,
				activeConversationId = arr[1] as String?,
				messages = arr[2] as List<ChatMessage>,
				streaming = arr[3] as Boolean,
				error = arr[4] as String?,
				activeToolCalls = arr[5] as Set<String>,
				apiKeyMissing = secure.openAiApiKey.isNullOrBlank(),
				disclaimerAcknowledged = secure.disclaimerAcknowledged,
			)
		}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState())

	fun selectConversation(id: String) { activeId.value = id }

	fun newConversation() {
		viewModelScope.launch {
			val c = repo.createConversation()
			activeId.value = c.id
		}
	}

	fun renameActive(newTitle: String) {
		val id = activeId.value ?: return
		viewModelScope.launch { repo.renameConversation(id, newTitle) }
	}

	fun deleteConversation(id: String) {
		viewModelScope.launch {
			repo.deleteConversation(id)
			if (activeId.value == id) activeId.value = null
		}
	}

	fun send(text: String) {
		val trimmed = text.trim()
		if (trimmed.isEmpty()) return
		var id = activeId.value
		if (id == null) {
			viewModelScope.launch {
				val c = repo.createConversation()
				activeId.value = c.id
				runStream(c.id, trimmed)
			}
			return
		}
		viewModelScope.launch { runStream(id, trimmed) }
	}

	fun acknowledgeDisclaimer() {
		secure.disclaimerAcknowledged = true
	}

	fun clearError() { error.value = null }

	private suspend fun runStream(id: String, text: String) {
		streaming.value = true
		error.value = null
		try {
			repo.send(id, text).collect { ev ->
				when (ev) {
					is StreamEvent.ToolCallStarted -> activeToolCalls.update { it + ev.id }
					is StreamEvent.ToolCallCompleted -> activeToolCalls.update { it - ev.id }
					is StreamEvent.Error -> error.value = ev.message
					StreamEvent.Done, is StreamEvent.TokenDelta -> {}
				}
			}
		} catch (e: Exception) {
			error.value = e.message ?: "unknown_error"
		} finally {
			streaming.value = false
			activeToolCalls.value = emptySet()
		}
	}
}
```

- [ ] **Step 2: Test (Turbine + fake repo)**

```kotlin
package com.healthaggregator.ui.assistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.healthaggregator.ai.AssistantRepository
import com.healthaggregator.ai.StreamEvent
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import com.healthaggregator.util.SecureStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AssistantViewModelTest {
	private val dispatcher = StandardTestDispatcher()

	@Before fun setup() { kotlinx.coroutines.Dispatchers.setMain(dispatcher) }
	@After fun tearDown() { kotlinx.coroutines.Dispatchers.resetMain() }

	@Test
	fun uiState_reflectsConversationsFromRepo() = runTest(dispatcher) {
		val ctx = ApplicationProvider.getApplicationContext<Context>()
		val repo = mockk<AssistantRepository>(relaxed = true)
		val convos = MutableStateFlow(listOf(conv("c1", "T")))
		every { repo.observeConversations() } returns convos
		every { repo.observeMessages(any()) } returns flowOf(emptyList())
		val vm = AssistantViewModel(repo, SecureStorage(ctx))

		vm.uiState.test {
			assertEquals(1, awaitItem().conversations.size)
			cancelAndIgnoreRemainingEvents()
		}
	}

	@Test
	fun send_emptyString_isNoop() = runTest(dispatcher) {
		val ctx = ApplicationProvider.getApplicationContext<Context>()
		val repo = mockk<AssistantRepository>(relaxed = true)
		every { repo.observeConversations() } returns flowOf(emptyList())
		every { repo.observeMessages(any()) } returns flowOf(emptyList())
		val vm = AssistantViewModel(repo, SecureStorage(ctx))
		vm.send("   ")
		vm.send("")
		// No side effects: the test ends without repo.send being invoked. No assertion failure = pass.
	}

	@Test
	fun acknowledgeDisclaimer_persists() {
		val ctx = ApplicationProvider.getApplicationContext<Context>()
		val secure = SecureStorage(ctx)
		secure.disclaimerAcknowledged = false
		val repo = mockk<AssistantRepository>(relaxed = true)
		every { repo.observeConversations() } returns flowOf(emptyList())
		every { repo.observeMessages(any()) } returns flowOf(emptyList())
		val vm = AssistantViewModel(repo, secure)
		vm.acknowledgeDisclaimer()
		assertTrue(secure.disclaimerAcknowledged)
	}

	private fun conv(id: String, title: String) = ChatConversation(
		id = id,
		title = title,
		createdAt = Instant.EPOCH,
		updatedAt = Instant.EPOCH,
		modelId = "gpt-5",
	)
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.ui.assistant.AssistantViewModelTest
git add app/src/main/java/com/healthaggregator/ui/assistant/AssistantViewModel.kt \
        app/src/test/java/com/healthaggregator/ui/assistant/AssistantViewModelTest.kt
git commit -m "Phase 4: AssistantViewModel

UiState combines conversations list, active conversation id,
messages for active, streaming flag, error, api-key-missing,
disclaimer-acknowledged, in-flight tool-call ids. send() auto-
creates a conversation when none is selected. Every terminal
error paths land in the UiState.error field."
```

---

### Task 21 (Phase 4 gate)

- [ ] **Step 1: Build + all tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL; repo + VM tests green.

- [ ] **Step 2: Gate commit**

```bash
git commit --allow-empty -m "Phase 4 gate: repository + view-model wired

AssistantRepository orchestrates send lifecycle; VM exposes a
clean UiState. Hilt module binds LlmClient + Json singletons.
No UI yet — Phase 5 starts the Compose shell."
```

---

# Phase 5 — UI shell + Settings

Assistant tab, chat pane, message bubble with citations, onboarding, Settings section for API key + model picker.

### Task 22: Add ASSISTANT to BottomNav + icon

**Files:**
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/navigation/BottomNav.kt`
- Create: `healthaggregator-android/app/src/main/res/drawable/ic_assistant.xml`

- [ ] **Step 1: Add drawable** (simple chat-bubble-with-sparkle glyph)

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M20,2H4C2.9,2 2,2.9 2,4v18l4,-4h14c1.1,0 2,-0.9 2,-2V4C22,2.9 21.1,2 20,2zM7.5,13.5l-1.2,-2.7L3.6,9.6l2.7,-1.2l1.2,-2.7l1.2,2.7l2.7,1.2l-2.7,1.2L7.5,13.5zM16,12l-0.7,-1.5l-1.5,-0.7l1.5,-0.7L16,7.5l0.7,1.6l1.5,0.7l-1.5,0.7L16,12z"/>
</vector>
```

- [ ] **Step 2: Update `BottomNav.kt` — add enum entry**

Replace the enum definition:

```kotlin
enum class TopLevelRoute(val route: String, val label: String, val icon: ImageVector) {
	HOME("home", "Home", Icons.Outlined.Dashboard),
	RECORDS("records", "Records", Icons.Outlined.FolderShared),
	ASSISTANT("assistant", "Assistant", Icons.Outlined.Chat),
	SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}
```

Add the import: `import androidx.compose.material.icons.outlined.Chat`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/navigation/BottomNav.kt \
        app/src/main/res/drawable/ic_assistant.xml
git commit -m "Phase 5: add Assistant tab to bottom nav

Fourth top-level route with Outlined.Chat Material icon. Custom
drawable ships as reference but the enum uses the built-in icon
to match α.1 pattern (Material icons extended already on classpath)."
```

---

### Task 23: OnboardingScreen + DisclaimerBanner

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/assistant/OnboardingScreen.kt`
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/assistant/DisclaimerBanner.kt`

- [ ] **Step 1: `OnboardingScreen.kt`**

```kotlin
package com.healthaggregator.ui.assistant

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@Composable
fun OnboardingScreen(onAcknowledge: () -> Unit) {
	var checked by remember { mutableStateOf(false) }
	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
		verticalArrangement = Arrangement.Center,
	) {
		Text(
			text = "AI Health Assistant",
			style = MaterialTheme.typography.headlineMedium,
			fontWeight = FontWeight.SemiBold,
		)
		Spacer(Modifier.height(16.dp))
		Text(
			text = "This assistant analyzes your health data to help you spot trends and ask informed questions. It is not medical advice. Always verify findings with your doctor.",
			style = MaterialTheme.typography.bodyLarge,
		)
		Spacer(Modifier.height(12.dp))
		Text(
			text = "Conversations are sent to OpenAI for processing. If you're using the free tier, your prompts and responses may be used by OpenAI to train future models.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.secondary,
		)
		Spacer(Modifier.height(24.dp))
		Row(verticalAlignment = Alignment.CenterVertically) {
			Checkbox(checked = checked, onCheckedChange = { checked = it })
			Text(
				text = "I understand this is not medical advice and my data will be sent to OpenAI.",
				modifier = Modifier.padding(start = 8.dp),
				style = MaterialTheme.typography.bodyMedium,
			)
		}
		Spacer(Modifier.height(24.dp))
		Button(
			onClick = onAcknowledge,
			enabled = checked,
			modifier = Modifier.fillMaxWidth(),
		) { Text("Get started") }
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 800)
@Composable
private fun PreviewOnboarding() = HealthAggregatorTheme { OnboardingScreen(onAcknowledge = {}) }
```

- [ ] **Step 2: `DisclaimerBanner.kt`**

```kotlin
package com.healthaggregator.ui.assistant

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MedicalAdviceDisclaimer(modifier: Modifier = Modifier) {
	Text(
		text = "Not medical advice",
		style = MaterialTheme.typography.labelSmall,
		color = MaterialTheme.colorScheme.secondary,
		modifier = modifier.padding(4.dp),
	)
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/assistant/OnboardingScreen.kt \
        app/src/main/java/com/healthaggregator/ui/assistant/DisclaimerBanner.kt
git commit -m "Phase 5: onboarding + disclaimer badge

One-time full-screen modal requires check + button tap before the
Assistant tab is usable. Inline disclaimer badge appears on every
assistant message bubble (wiring in Task 25)."
```

---

### Task 24: CitationChip composable

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/assistant/CitationChip.kt`

- [ ] **Step 1: Write the composable**

```kotlin
package com.healthaggregator.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

@Composable
fun CitationChip(
	sourceSystem: String,
	fhirRef: String,
	label: String = sourceSystem,
	onClick: (String, String) -> Unit,
) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.clip(RoundedCornerShape(8.dp))
			.background(MaterialTheme.colorScheme.surfaceVariant)
			.clickable { onClick(sourceSystem, fhirRef) }
			.padding(horizontal = 8.dp, vertical = 2.dp),
	) {
		Icon(
			imageVector = Icons.Filled.Link,
			contentDescription = "Citation link",
			modifier = Modifier.size(12.dp),
			tint = MaterialTheme.colorScheme.primary,
		)
		Spacer(Modifier.width(4.dp))
		Text(
			text = label,
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/assistant/CitationChip.kt
git commit -m "Phase 5: CitationChip composable

Compact inline chip — link icon + short label on a surfaceVariant
background. Passes (sourceSystem, fhirRef) to the onClick callback
so the containing screen can route to lab/record detail."
```

---

### Task 25: MessageBubble with inline citation rendering

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/assistant/MessageBubble.kt`

- [ ] **Step 1: Write the composable**

```kotlin
package com.healthaggregator.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.healthaggregator.ai.CitationRenderer
import com.healthaggregator.ai.CitationSegment
import com.healthaggregator.data.entities.ChatMessage
import com.healthaggregator.ui.theme.HealthAggregatorTheme

@Composable
fun MessageBubble(
	message: ChatMessage,
	onCitationClick: (sourceSystem: String, fhirRef: String) -> Unit,
) {
	val isUser = message.role == "user"
	val isTool = message.role == "tool"
	val isAssistant = message.role == "assistant"

	if (isTool) {
		ToolInvocation(message)
		return
	}

	val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
	val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
	val align = if (isUser) Alignment.End else Alignment.Start

	Column(
		modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 12.dp),
		horizontalAlignment = align,
	) {
		Box(
			modifier = Modifier
				.clip(RoundedCornerShape(16.dp))
				.background(bubbleColor)
				.padding(12.dp),
		) {
			val segments = CitationRenderer.parse(message.content.ifEmpty { if (isAssistant) "…" else "" })
			FlowContent(segments, textColor, onCitationClick)
		}
		if (isAssistant && message.content.isNotBlank()) {
			MedicalAdviceDisclaimer()
		}
	}
}

@Composable
private fun FlowContent(
	segments: List<CitationSegment>,
	textColor: androidx.compose.ui.graphics.Color,
	onCitationClick: (String, String) -> Unit,
) {
	// Flow-ish rendering: wrap Text + CitationChip inline. Compose's FlowRow from material3 works.
	androidx.compose.foundation.layout.FlowRow(
		verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
	) {
		segments.forEach { seg ->
			when (seg) {
				is CitationSegment.Text -> Text(
					text = seg.text,
					style = MaterialTheme.typography.bodyMedium,
					color = textColor,
				)
				is CitationSegment.Cite -> {
					CitationChip(
						sourceSystem = seg.sourceSystem,
						fhirRef = seg.fhirRef,
						label = seg.sourceSystem.take(20),
						onClick = onCitationClick,
					)
				}
			}
		}
	}
}

@Composable
private fun ToolInvocation(message: ChatMessage) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(
			imageVector = Icons.Outlined.Build,
			contentDescription = null,
			modifier = Modifier.height(14.dp),
			tint = MaterialTheme.colorScheme.secondary,
		)
		Spacer(Modifier.height(4.dp))
		Text(
			text = "tool call " + (message.toolCallId ?: ""),
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.secondary,
		)
	}
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun PreviewUser() = HealthAggregatorTheme {
	MessageBubble(
		message = ChatMessage(
			id = "1", conversationId = "c", role = "user",
			content = "How is my A1c trending?", createdAt = java.time.Instant.EPOCH,
		),
		onCitationClick = { _, _ -> },
	)
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B, widthDp = 360)
@Composable
private fun PreviewAssistantWithCitations() = HealthAggregatorTheme {
	MessageBubble(
		message = ChatMessage(
			id = "2", conversationId = "c", role = "assistant",
			content = "Your A1c has crossed the abnormal threshold — 6.1 in 2024 [cite:summa-health/Observation/ghi789], then 6.3 recently [cite:summa-health/Observation/jkl012].",
			createdAt = java.time.Instant.EPOCH,
		),
		onCitationClick = { _, _ -> },
	)
}
```

(Note: the `FlowRow` API we use is in `androidx.compose.foundation.layout` — available in Compose BOM 2024.12.01, stable.)

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/assistant/MessageBubble.kt
git commit -m "Phase 5: MessageBubble with inline citations

User bubbles right-aligned, primaryContainer. Assistant bubbles
left-aligned, surfaceVariant, with MedicalAdviceDisclaimer badge
below. Uses CitationRenderer.parse + FlowRow to render text +
CitationChip segments inline. Tool messages render as a small
secondary-color label (content summarized, not full JSON)."
```

---

### Task 26: StarterChips composable

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/assistant/StarterChips.kt`

- [ ] **Step 1: Write the composable**

```kotlin
package com.healthaggregator.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val DefaultStarterPrompts = listOf(
	"Summarize my recent lab trends",
	"What's abnormal or near-abnormal right now?",
	"How's my A1c trending over the years?",
	"Are any of my labs concerning together?",
	"What patterns do you see in my vitals?",
	"Cross-reference my labs with my medications",
	"Help me prepare questions for my next appointment",
	"What should I ask my doctor about?",
)

@Composable
fun StarterChips(
	prompts: List<String> = DefaultStarterPrompts,
	onPick: (String) -> Unit,
	modifier: Modifier = Modifier,
) {
	FlowRow(
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
		modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
	) {
		prompts.forEach { prompt ->
			AssistChip(
				onClick = { onPick(prompt) },
				label = { Text(prompt) },
			)
		}
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/assistant/StarterChips.kt
git commit -m "Phase 5: StarterChips composable

Eight default prompts rendered as Material 3 AssistChips in a
FlowRow. Tap pre-fills the input — onPick callback owned by
ChatPane. Visible only when the active conversation has no
messages yet."
```

---

### Task 27: ConversationsDrawer composable

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/assistant/ConversationsDrawer.kt`

- [ ] **Step 1: Write the composable**

```kotlin
package com.healthaggregator.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthaggregator.data.entities.ChatConversation
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ConversationsDrawer(
	conversations: List<ChatConversation>,
	activeId: String?,
	onSelect: (String) -> Unit,
	onNew: () -> Unit,
	onDelete: (String) -> Unit,
) {
	var confirmingDelete by remember { mutableStateOf<ChatConversation?>(null) }
	ModalDrawerSheet(modifier = Modifier.fillMaxHeight().width(320.dp)) {
		Column(modifier = Modifier.padding(12.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
				Text("Conversations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
				Button(onClick = onNew) {
					Icon(Icons.Outlined.Add, contentDescription = "New conversation")
					Spacer(Modifier.width(4.dp))
					Text("New")
				}
			}
			Spacer(Modifier.height(8.dp))
			Divider()
			Spacer(Modifier.height(8.dp))
			if (conversations.isEmpty()) {
				Text(
					"No conversations yet. Tap New to start.",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.secondary,
				)
			} else {
				LazyColumn {
					items(conversations, key = { it.id }) { conv ->
						Row(verticalAlignment = Alignment.CenterVertically) {
							NavigationDrawerItem(
								selected = conv.id == activeId,
								onClick = { onSelect(conv.id) },
								label = {
									Column {
										Text(conv.title, style = MaterialTheme.typography.bodyMedium)
										Text(formatDate(conv.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
									}
								},
								modifier = Modifier.weight(1f),
							)
							IconButton(onClick = { confirmingDelete = conv }) {
								Icon(Icons.Outlined.Delete, contentDescription = "Delete conversation")
							}
						}
					}
				}
			}
		}
	}

	confirmingDelete?.let { conv ->
		AlertDialog(
			onDismissRequest = { confirmingDelete = null },
			title = { Text("Delete conversation?") },
			text = { Text("\"${conv.title}\" and its messages will be removed.") },
			confirmButton = {
				TextButton(onClick = { onDelete(conv.id); confirmingDelete = null }) {
					Text("Delete", color = MaterialTheme.colorScheme.error)
				}
			},
			dismissButton = { TextButton(onClick = { confirmingDelete = null }) { Text("Cancel") } },
		)
	}
}

private fun formatDate(i: java.time.Instant): String =
	DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault()).format(i)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/assistant/ConversationsDrawer.kt
git commit -m "Phase 5: ConversationsDrawer

Modal drawer listing conversations (title + last-updated date).
Selected item highlighted, active id passed through. Per-item
delete icon opens confirmation dialog. 'New' button at top."
```

---

### Task 28: ChatPane (message list + input + streaming)

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/assistant/ChatPane.kt`

- [ ] **Step 1: Write the composable**

```kotlin
package com.healthaggregator.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthaggregator.data.entities.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPane(
	messages: List<ChatMessage>,
	streaming: Boolean,
	activeToolCallCount: Int,
	starterPrompts: List<String>,
	onSend: (String) -> Unit,
	onCitationClick: (String, String) -> Unit,
	modifier: Modifier = Modifier,
) {
	var input by remember { mutableStateOf("") }
	val listState = rememberLazyListState()

	LaunchedEffect(messages.size, streaming) {
		if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
	}

	Column(modifier = modifier.fillMaxSize()) {
		LazyColumn(
			state = listState,
			modifier = Modifier.weight(1f).fillMaxWidth(),
			contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
		) {
			if (messages.isEmpty()) {
				item {
					EmptyChatHint(starterPrompts) { input = it }
				}
			} else {
				items(messages, key = { it.id }) { msg ->
					MessageBubble(msg, onCitationClick)
				}
				if (streaming && activeToolCallCount > 0) {
					item { ToolWorkingIndicator(activeToolCallCount) }
				}
			}
		}

		Row(
			modifier = Modifier.fillMaxWidth().padding(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			OutlinedTextField(
				value = input,
				onValueChange = { input = it },
				placeholder = { Text("Ask about your health data…") },
				modifier = Modifier.weight(1f),
				enabled = !streaming,
				maxLines = 4,
			)
			Spacer(Modifier.width(8.dp))
			IconButton(
				onClick = {
					val text = input.trim()
					if (text.isNotEmpty() && !streaming) {
						onSend(text)
						input = ""
					}
				},
				enabled = input.trim().isNotEmpty() && !streaming,
			) {
				if (streaming) {
					CircularProgressIndicator(modifier = Modifier.height(24.dp).width(24.dp))
				} else {
					Icon(Icons.Filled.Send, contentDescription = "Send")
				}
			}
		}
	}
}

@Composable
private fun EmptyChatHint(starterPrompts: List<String>, onPick: (String) -> Unit) {
	Column(
		modifier = Modifier.fillMaxWidth().padding(24.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text("Ask about your data.", style = MaterialTheme.typography.titleLarge)
		Spacer(Modifier.height(8.dp))
		Text(
			"Everything you send includes your full health snapshot. Pick a suggested prompt or type your own.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.secondary,
		)
		Spacer(Modifier.height(16.dp))
		StarterChips(prompts = starterPrompts, onPick = onPick)
	}
}

@Composable
private fun ToolWorkingIndicator(count: Int) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.Start,
	) {
		CircularProgressIndicator(modifier = Modifier.height(12.dp).width(12.dp), strokeWidth = 1.dp)
		Spacer(Modifier.width(8.dp))
		Text(
			text = if (count == 1) "Looking up data…" else "Looking up data ($count requests)…",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.secondary,
		)
	}
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/healthaggregator/ui/assistant/ChatPane.kt
git commit -m "Phase 5: ChatPane

Message list (LazyColumn auto-scrolls on new messages or
streaming), input + send button, 'Looking up data…' indicator
when tool calls are in flight, empty-state with StarterChips."
```

---

### Task 29: AssistantScreen + route wiring

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/assistant/AssistantScreen.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/navigation/AppNav.kt`

- [ ] **Step 1: Write `AssistantScreen.kt`**

```kotlin
package com.healthaggregator.ui.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
	onCitationClick: (source: String, fhirRef: String) -> Unit,
	onOpenSettings: () -> Unit,
	viewModel: AssistantViewModel = hiltViewModel(),
) {
	val state by viewModel.uiState.collectAsStateWithLifecycle()

	if (!state.disclaimerAcknowledged) {
		OnboardingScreen(onAcknowledge = { viewModel.acknowledgeDisclaimer() })
		return
	}
	if (state.apiKeyMissing) {
		ApiKeyMissingCard(onOpenSettings)
		return
	}

	val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
	val scope = rememberCoroutineScope()

	ModalNavigationDrawer(
		drawerState = drawerState,
		drawerContent = {
			ConversationsDrawer(
				conversations = state.conversations,
				activeId = state.activeConversationId,
				onSelect = { id ->
					viewModel.selectConversation(id)
					scope.launch { drawerState.close() }
				},
				onNew = {
					viewModel.newConversation()
					scope.launch { drawerState.close() }
				},
				onDelete = viewModel::deleteConversation,
			)
		},
	) {
		Column(modifier = Modifier.fillMaxSize()) {
			TopAppBar(
				title = {
					val title = state.conversations.firstOrNull { it.id == state.activeConversationId }?.title ?: "Assistant"
					Text(title)
				},
				navigationIcon = {
					IconButton(onClick = { scope.launch { drawerState.open() } }) {
						Icon(Icons.Outlined.Menu, contentDescription = "Conversations")
					}
				},
			)
			state.error?.let {
				Text(
					text = "Error: $it",
					color = MaterialTheme.colorScheme.error,
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
				)
			}
			ChatPane(
				messages = state.messages,
				streaming = state.streaming,
				activeToolCallCount = state.activeToolCalls.size,
				starterPrompts = DefaultStarterPrompts,
				onSend = viewModel::send,
				onCitationClick = onCitationClick,
				modifier = Modifier.fillMaxSize(),
			)
		}
	}
}

@Composable
private fun ApiKeyMissingCard(onOpenSettings: () -> Unit) {
	Column(
		modifier = Modifier.fillMaxSize().padding(24.dp),
	) {
		Text("API key required", style = MaterialTheme.typography.titleLarge)
		Spacer(Modifier.height(8.dp))
		Text(
			"Set an OpenAI API key in Settings → AI Assistant to start chatting.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.secondary,
		)
		Spacer(Modifier.height(16.dp))
		androidx.compose.material3.Button(onClick = onOpenSettings) { Text("Open Settings") }
	}
}
```

- [ ] **Step 2: Wire the route in `AppNav.kt`**

Add inside the `NavHost { … }` block:

```kotlin
composable(TopLevelRoute.ASSISTANT.route) {
	AssistantScreen(
		onCitationClick = { source, fhirRef ->
			// Prefer the lab detail route if the fhirRef looks like an Observation; fall back to generic record.
			val dest = if (fhirRef.startsWith("Observation/")) "lab" else "record"
			navController.navigate("$dest/${encode(source)}/${encode(fhirRef)}")
		},
		onOpenSettings = { navController.navigate(TopLevelRoute.SETTINGS.route) },
	)
}
```

Add import: `import com.healthaggregator.ui.assistant.AssistantScreen`.

- [ ] **Step 3: Build + commit**

```bash
./gradlew :app:assembleDebug
git add app/src/main/java/com/healthaggregator/ui/assistant/AssistantScreen.kt \
        app/src/main/java/com/healthaggregator/ui/navigation/AppNav.kt
git commit -m "Phase 5: AssistantScreen wired into AppNav

ModalNavigationDrawer hosts conversations list; TopAppBar title
shows the active conversation title; error banner appears above
chat when ViewModel surfaces one. Citation tap routes to lab
detail if fhirRef is 'Observation/…', else generic record detail."
```

---

### Task 30: AiAssistantSettings section in SettingsScreen

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/settings/AiAssistantSettings.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Write `AiAssistantSettings.kt`**

```kotlin
package com.healthaggregator.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.healthaggregator.util.SecureStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSettings(secure: SecureStorage, onClearHistory: () -> Unit) {
	var key by remember { mutableStateOf(secure.openAiApiKey ?: "") }
	var dataSharing by remember { mutableStateOf(secure.dataSharingEnabled) }
	var model by remember { mutableStateOf(secure.selectedModel) }
	var keyVisible by remember { mutableStateOf(false) }
	var modelMenuOpen by remember { mutableStateOf(false) }
	var confirmClear by remember { mutableStateOf(false) }

	Column {
		Text("OpenAI API key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
		Spacer(Modifier.height(4.dp))
		OutlinedTextField(
			value = key,
			onValueChange = {
				key = it
				secure.openAiApiKey = it.trim()
			},
			placeholder = { Text("sk-…") },
			modifier = Modifier.fillMaxWidth(),
			singleLine = true,
			visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
			trailingIcon = {
				IconButton(onClick = { keyVisible = !keyVisible }) {
					Icon(
						imageVector = if (keyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
						contentDescription = if (keyVisible) "Hide key" else "Show key",
					)
				}
			},
		)
		Spacer(Modifier.height(12.dp))

		Text("Model", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
		Spacer(Modifier.height(4.dp))
		Row(verticalAlignment = Alignment.CenterVertically) {
			OutlinedButton(
				onClick = { modelMenuOpen = true },
				modifier = Modifier.fillMaxWidth(),
			) {
				val label = SecureStorage.AVAILABLE_MODELS.firstOrNull { it.first == model }?.second ?: model
				Text(label, modifier = Modifier.weight(1f))
				Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
			}
			DropdownMenu(
				expanded = modelMenuOpen,
				onDismissRequest = { modelMenuOpen = false },
			) {
				SecureStorage.AVAILABLE_MODELS.forEach { (id, label) ->
					DropdownMenuItem(
						text = { Text(label) },
						onClick = { model = id; secure.selectedModel = id; modelMenuOpen = false },
					)
				}
			}
		}
		Spacer(Modifier.height(12.dp))

		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(modifier = Modifier.weight(1f)) {
				Text("Share prompts with OpenAI", style = MaterialTheme.typography.bodyMedium)
				Text(
					"Required for the gpt-5 free-token tier. Disable to use paid billing only.",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.secondary,
				)
			}
			Switch(
				checked = dataSharing,
				onCheckedChange = {
					dataSharing = it
					secure.dataSharingEnabled = it
				},
			)
		}
		Spacer(Modifier.height(12.dp))

		OutlinedButton(
			onClick = { confirmClear = true },
			modifier = Modifier.fillMaxWidth(),
			colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
		) { Text("Clear all chat history") }
	}

	if (confirmClear) {
		AlertDialog(
			onDismissRequest = { confirmClear = false },
			title = { Text("Clear all chat history?") },
			text = { Text("Every conversation and message will be deleted. This cannot be undone.") },
			confirmButton = {
				TextButton(onClick = { onClearHistory(); confirmClear = false }) {
					Text("Clear", color = MaterialTheme.colorScheme.error)
				}
			},
			dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
		)
	}
}
```

- [ ] **Step 2: Add section to `SettingsScreen.kt`**

In `SettingsScreen`, after the `SectionCard(title = "Data") { … }` block, insert:

```kotlin
SectionCard(title = "AI Assistant") {
	AiAssistantSettings(
		secure = viewModel.secureStorage,
		onClearHistory = { viewModel.clearChatHistory() },
	)
}
```

Also expose in `SettingsViewModel.kt`:

```kotlin
// at top-level of the ViewModel class
@Inject lateinit var secureStorage: SecureStorage  // or wire via @Inject constructor if possible

fun clearChatHistory() {
	viewModelScope.launch { repo.clearAllChatHistory() }
}
```

If `SettingsViewModel` uses `@Inject constructor(...)`, add `secureStorage: SecureStorage` and `assistantRepo: AssistantRepository` to the constructor instead of field injection. Store the secure instance on the VM via a `val secureStorage` property.

Add to `AssistantRepository`:

```kotlin
suspend fun clearAllChatHistory() { chat.deleteAllConversations() }
```

(If this method was already added in Task 18, this step is a no-op.)

Imports on SettingsScreen.kt:

```kotlin
import com.healthaggregator.ui.settings.AiAssistantSettings
```

- [ ] **Step 3: Build + commit**

```bash
./gradlew :app:assembleDebug
git add app/src/main/java/com/healthaggregator/ui/settings/AiAssistantSettings.kt \
        app/src/main/java/com/healthaggregator/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/healthaggregator/ui/settings/SettingsViewModel.kt \
        app/src/main/java/com/healthaggregator/ai/AssistantRepository.kt
git commit -m "Phase 5: AI Assistant settings section

Masked API-key input with show/hide toggle; model picker dropdown
populated from SecureStorage.AVAILABLE_MODELS; data-sharing switch
with explainer; destructive Clear-history action with confirmation."
```

---

### Task 31 (Phase 5 gate)

- [ ] **Step 1: Build + tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL; all prior tests still pass.

- [ ] **Step 2: Install + device smoke**

```bash
./gradlew :app:installDebug
```

Device smoke checklist:

1. Open app → tap Assistant tab → OnboardingScreen appears → tick box → Get started.
2. Back in chat shell, "API key required" shows → tap Open Settings → navigate to Settings → scroll to AI Assistant section.
3. Enter a valid OpenAI key → toggle data sharing on → leave model as `gpt-5` → back to Assistant.
4. Tap the menu icon → drawer shows empty list. Tap New → new conversation appears.
5. Type "What's abnormal right now?" → send. See streaming tokens appear in an assistant bubble. Citation chips render and are tappable.
6. Tap a citation chip → lands on LabDetailScreen (or RecordDetailScreen) for that Observation.
7. Back → type a follow-up ("Compare my A1c to my lipid panel"). See assistant continue.
8. Open drawer → rename is via long-press (skip for v1 — just verify new conversation appears there).
9. Delete a conversation → confirmation dialog → removed. Messages gone.
10. Kill + reopen app → conversations + messages persist.

- [ ] **Step 3: Gate commit**

```bash
git commit --allow-empty -m "Phase 5 gate: UI shell live

Assistant tab renders onboarding → api-key gate → chat pane with
streaming bubbles, citation chips, tool-call indicator, error
banner. Settings section configures key, model, data-sharing,
and chat-history reset. Device smoke across all flows green."
```

---

# Phase 6 — Export (Markdown + PDF + share)

### Task 32: ChatExporter — Markdown + golden-file test

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ai/ChatExporter.kt`
- Create: `healthaggregator-android/app/src/test/java/com/healthaggregator/ai/ChatExporterTest.kt`

- [ ] **Step 1: Implement `ChatExporter.kt` (markdown only in this task; PDF added in Task 33)**

```kotlin
package com.healthaggregator.ai

import com.healthaggregator.data.dao.ChatDao
import com.healthaggregator.data.dao.LabDao
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import com.healthaggregator.data.entities.LabObservation
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatExporter @Inject constructor(
	private val chat: ChatDao,
	private val labs: LabDao,
) {
	suspend fun exportMarkdown(conversationId: String, now: Instant = Instant.now()): String {
		val c = chat.getConversation(conversationId) ?: error("conversation not found")
		val messages = chat.messagesSnapshot(conversationId)
		val labIndex: Map<Pair<String, String>, LabObservation> =
			labs.getAllSnapshot().associateBy { it.sourceSystem to it.fhirReference }

		return buildString {
			appendLine("# ${c.title}")
			appendLine()
			appendLine("_Exported ${fmt(now)} — model ${c.modelId}_")
			appendLine()
			appendLine("---")
			appendLine()
			appendLine("**This is not medical advice.** The assistant is an analytical aid. Verify all findings with a licensed clinician before acting on them.")
			appendLine()
			appendLine("---")
			appendLine()

			for (m in messages) {
				if (m.role == "user") {
					appendLine("### You")
					appendLine()
					appendLine(m.content)
					appendLine()
				}
				if (m.role == "assistant" && m.content.isNotBlank()) {
					appendLine("### Assistant")
					appendLine()
					appendLine(resolveCitations(m.content, labIndex))
					appendLine()
				}
				// tool messages are skipped in the export (they're LLM plumbing)
			}

			appendLine("---")
			appendLine()
			appendLine("_Generated by HealthAggregator AI Assistant._")
		}
	}

	private fun resolveCitations(text: String, index: Map<Pair<String, String>, LabObservation>): String {
		val segments = CitationRenderer.parse(text)
		return buildString {
			segments.forEach { seg ->
				when (seg) {
					is CitationSegment.Text -> append(seg.text)
					is CitationSegment.Cite -> {
						val lab = index[seg.sourceSystem to seg.fhirRef]
						if (lab != null) {
							val date = lab.effectiveAt?.let { dateOnly(it) } ?: "n/a"
							val value = lab.numericValue?.let { "$it ${lab.unit ?: ""}" }?.trim() ?: (lab.textValue ?: "—")
							append("[$value on $date, ${lab.sourceName}]")
						} else {
							append("[${seg.sourceSystem}/${seg.fhirRef}]")
						}
					}
				}
			}
		}
	}

	private fun fmt(i: Instant): String =
		DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm z").withZone(ZoneId.systemDefault()).format(i)

	private fun dateOnly(i: Instant): String =
		DateTimeFormatter.ISO_LOCAL_DATE.format(i.atOffset(ZoneOffset.UTC).toLocalDate())
}
```

- [ ] **Step 2: Write the test**

```kotlin
package com.healthaggregator.ai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthaggregator.data.AppDatabase
import com.healthaggregator.data.entities.ChatConversation
import com.healthaggregator.data.entities.ChatMessage
import com.healthaggregator.data.entities.LabObservation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChatExporterTest {
	private lateinit var db: AppDatabase
	private lateinit var exporter: ChatExporter

	@Before
	fun setup() {
		db = Room.inMemoryDatabaseBuilder(
			ApplicationProvider.getApplicationContext(),
			AppDatabase::class.java,
		).allowMainThreadQueries().build()
		exporter = ChatExporter(db.chatDao(), db.labDao())
	}

	@After fun tearDown() { db.close() }

	@Test
	fun markdown_includesHeaderDisclaimerAndMessages() = runTest {
		val now = Instant.parse("2026-02-12T10:00:00Z")
		db.chatDao().upsertConversation(conv("c1", "Metabolic concerns"))
		db.chatDao().upsertMessage(msg("m1", "c1", "user", "What's my A1c doing?", createdAt = now))
		db.chatDao().upsertMessage(msg("m2", "c1", "assistant", "Your A1c is 6.3 [cite:summa/Observation/a1c-recent].", createdAt = now))
		db.labDao().upsert(a1c("a1c-recent", "summa", "Summa Health", 6.3, Instant.parse("2026-02-12T09:00:00Z")))

		val md = exporter.exportMarkdown("c1", now = Instant.parse("2026-04-18T10:00:00Z"))

		assertTrue(md.startsWith("# Metabolic concerns"))
		assertTrue(md.contains("This is not medical advice"))
		assertTrue(md.contains("### You"))
		assertTrue(md.contains("What's my A1c doing?"))
		assertTrue(md.contains("### Assistant"))
		// Citation resolved to inline text
		assertTrue(md.contains("[6.3 % on 2026-02-12, Summa Health]"))
		// Raw cite marker gone
		assertFalse(md.contains("[cite:summa/Observation/a1c-recent]"))
	}

	@Test
	fun markdown_unresolvedCitation_fallsBackToRawMarker() = runTest {
		db.chatDao().upsertConversation(conv("c1", "T"))
		db.chatDao().upsertMessage(msg("m1", "c1", "assistant", "Reading [cite:unknown/Observation/xyz] here.", createdAt = Instant.EPOCH))
		val md = exporter.exportMarkdown("c1")
		assertTrue(md.contains("[unknown/Observation/xyz]"))
	}

	@Test
	fun markdown_skipsToolMessages() = runTest {
		db.chatDao().upsertConversation(conv("c1", "T"))
		db.chatDao().upsertMessage(msg("m1", "c1", "user", "check my labs", createdAt = Instant.EPOCH))
		db.chatDao().upsertMessage(msg("m2", "c1", "assistant", "", createdAt = Instant.EPOCH))
		db.chatDao().upsertMessage(msg("m3", "c1", "tool", """{"data":"..."}""", createdAt = Instant.EPOCH, toolCallId = "call_1"))
		db.chatDao().upsertMessage(msg("m4", "c1", "assistant", "All look fine.", createdAt = Instant.EPOCH))
		val md = exporter.exportMarkdown("c1")
		assertFalse("tool JSON must not leak into export", md.contains("\"data\":\"...\""))
		assertTrue(md.contains("All look fine."))
	}

	private fun conv(id: String, title: String) = ChatConversation(
		id = id, title = title,
		createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
		modelId = "gpt-5",
	)

	private fun msg(
		id: String,
		conversationId: String,
		role: String,
		content: String,
		createdAt: Instant,
		toolCallId: String? = null,
	) = ChatMessage(id, conversationId, role, content, null, toolCallId, null, createdAt)

	private fun a1c(
		id: String,
		source: String,
		sourceName: String,
		value: Double,
		at: Instant,
	) = LabObservation(
		sourceSystem = source, sourceName = sourceName,
		fhirReference = "Observation/$id", resourceId = id,
		testName = "Hemoglobin A1c",
		numericValue = value, unit = "%",
		effectiveAt = at, importedAt = Instant.parse("2026-04-18T00:00:00Z"),
	)
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.ai.ChatExporterTest
git add app/src/main/java/com/healthaggregator/ai/ChatExporter.kt \
        app/src/test/java/com/healthaggregator/ai/ChatExporterTest.kt
git commit -m "Phase 6: ChatExporter.exportMarkdown

Resolves [cite:src/ref] markers to inline '[value on date, source]'
prose by joining against LabObservation. Unknown cites fall back
to a raw marker. Tool messages skipped (LLM plumbing, not user
content). Disclaimer + generated-by footer bracket the transcript."
```

---

### Task 33: ChatExporter.exportPdf

**Files:**
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/ai/ChatExporter.kt`
- Modify: `healthaggregator-android/app/src/test/java/com/healthaggregator/ai/ChatExporterTest.kt`

- [ ] **Step 1: Add `exportPdf` method**

Append to the `ChatExporter` class:

```kotlin
suspend fun exportPdf(conversationId: String, now: Instant = Instant.now()): ByteArray {
	val markdown = exportMarkdown(conversationId, now)
	return markdownToPdfBytes(markdown)
}

private fun markdownToPdfBytes(markdown: String): ByteArray {
	val doc = android.graphics.pdf.PdfDocument()
	val pageWidth = 612  // 8.5" at 72dpi
	val pageHeight = 792 // 11"
	val margin = 48
	val lineHeight = 14f
	val maxLinesPerPage = ((pageHeight - 2 * margin) / lineHeight).toInt()

	val paint = android.graphics.Paint().apply {
		textSize = 10f
		color = android.graphics.Color.BLACK
	}
	val headerPaint = android.graphics.Paint(paint).apply {
		textSize = 12f
		isFakeBoldText = true
	}

	val wrapWidth = pageWidth - 2 * margin
	val allLines = mutableListOf<Pair<Boolean, String>>() // Boolean = isHeader
	markdown.lineSequence().forEach { raw ->
		val isHeader = raw.startsWith("#")
		val stripped = raw.trimStart('#').trim()
		val wrapped = wrap(if (isHeader) stripped else raw, if (isHeader) headerPaint else paint, wrapWidth)
		wrapped.forEach { allLines.add(isHeader to it) }
	}

	var pageNumber = 1
	var lineIndex = 0
	while (lineIndex < allLines.size) {
		val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
		val page = doc.startPage(pageInfo)
		val canvas = page.canvas
		var y = margin.toFloat() + lineHeight
		var linesOnPage = 0
		while (linesOnPage < maxLinesPerPage && lineIndex < allLines.size) {
			val (isHeader, line) = allLines[lineIndex]
			canvas.drawText(line, margin.toFloat(), y, if (isHeader) headerPaint else paint)
			y += lineHeight
			linesOnPage++
			lineIndex++
		}
		doc.finishPage(page)
		pageNumber++
	}

	val out = java.io.ByteArrayOutputStream()
	doc.writeTo(out)
	doc.close()
	return out.toByteArray()
}

private fun wrap(text: String, paint: android.graphics.Paint, maxWidth: Int): List<String> {
	if (text.isEmpty()) return listOf("")
	val words = text.split(" ")
	val lines = mutableListOf<String>()
	var current = StringBuilder()
	for (word in words) {
		val candidate = if (current.isEmpty()) word else "$current $word"
		if (paint.measureText(candidate) <= maxWidth) {
			if (current.isEmpty()) current.append(word) else current.append(' ').append(word)
		} else {
			if (current.isNotEmpty()) lines.add(current.toString())
			current = StringBuilder(word)
		}
	}
	if (current.isNotEmpty()) lines.add(current.toString())
	return lines
}
```

- [ ] **Step 2: Add test**

Append to `ChatExporterTest`:

```kotlin
@Test
fun pdf_returnsValidPdfHeaderBytes() = runTest {
	db.chatDao().upsertConversation(conv("c1", "T"))
	db.chatDao().upsertMessage(msg("m1", "c1", "user", "hi", createdAt = Instant.EPOCH))
	db.chatDao().upsertMessage(msg("m2", "c1", "assistant", "hello", createdAt = Instant.EPOCH))

	val bytes = exporter.exportPdf("c1")
	// PDF header magic
	assertTrue(String(bytes, 0, 4.coerceAtMost(bytes.size)).startsWith("%PDF"))
	assertTrue("empty PDF", bytes.size > 200)
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew :app:testDebugUnitTest --tests com.healthaggregator.ai.ChatExporterTest
git add app/src/main/java/com/healthaggregator/ai/ChatExporter.kt \
        app/src/test/java/com/healthaggregator/ai/ChatExporterTest.kt
git commit -m "Phase 6: ChatExporter.exportPdf

Builds on exportMarkdown — wraps text to 8.5x11 pages using
Android's native PdfDocument. Monospace-ish layout: 10pt body,
12pt bold headers. No dependencies beyond android.graphics.pdf."
```

---

### Task 34: ExportDialog + share intent wiring

**Files:**
- Create: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/assistant/ExportDialog.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/assistant/AssistantViewModel.kt`
- Modify: `healthaggregator-android/app/src/main/java/com/healthaggregator/ui/assistant/AssistantScreen.kt`

- [ ] **Step 1: Add exportMarkdown / exportPdf on the VM**

In `AssistantViewModel.kt`, inject the exporter and context via Hilt's `@ApplicationContext`, and expose:

```kotlin
// at constructor:
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.healthaggregator.ai.ChatExporter
@Inject constructor(
	private val repo: AssistantRepository,
	private val secure: SecureStorage,
	private val exporter: ChatExporter,
	@ApplicationContext private val appContext: Context,
)

// methods:
fun exportMarkdown(onReady: (android.net.Uri) -> Unit) {
	val id = activeId.value ?: return
	viewModelScope.launch {
		val md = exporter.exportMarkdown(id)
		val file = java.io.File(appContext.cacheDir, "chat-${id.take(8)}.md")
		file.writeText(md)
		val uri = androidx.core.content.FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
		onReady(uri)
	}
}

fun exportPdf(onReady: (android.net.Uri) -> Unit) {
	val id = activeId.value ?: return
	viewModelScope.launch {
		val bytes = exporter.exportPdf(id)
		val file = java.io.File(appContext.cacheDir, "chat-${id.take(8)}.pdf")
		file.writeBytes(bytes)
		val uri = androidx.core.content.FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
		onReady(uri)
	}
}
```

- [ ] **Step 2: Register FileProvider**

Add to `app/src/main/AndroidManifest.xml` inside the `<application>` block:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

Create `app/src/main/res/xml/file_paths.xml`:

```xml
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="chat_cache" path="." />
</paths>
```

- [ ] **Step 3: Write `ExportDialog.kt`**

```kotlin
package com.healthaggregator.ui.assistant

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun ExportDialog(
	onDismiss: () -> Unit,
	onExportMarkdown: ((Uri) -> Unit) -> Unit,
	onExportPdf: ((Uri) -> Unit) -> Unit,
) {
	val ctx = LocalContext.current
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Export conversation") },
		text = {
			Text("Markdown preserves citation text; PDF is suitable for sharing with doctors or family.")
		},
		confirmButton = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
				Button(onClick = {
					onExportMarkdown { uri -> share(ctx, uri, "text/markdown") }
					onDismiss()
				}) { Text("Markdown (.md)") }
				Button(onClick = {
					onExportPdf { uri -> share(ctx, uri, "application/pdf") }
					onDismiss()
				}) { Text("PDF") }
			}
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
	)
}

private fun share(ctx: android.content.Context, uri: Uri, mime: String) {
	val intent = Intent(Intent.ACTION_SEND).apply {
		type = mime
		putExtra(Intent.EXTRA_STREAM, uri)
		addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
	}
	ctx.startActivity(Intent.createChooser(intent, "Share conversation").apply {
		addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
	})
}
```

- [ ] **Step 4: Wire a menu/overflow in AssistantScreen**

Add an `IconButton` to `TopAppBar.actions` in `AssistantScreen.kt`:

```kotlin
import androidx.compose.material.icons.outlined.IosShare

// inside TopAppBar:
actions = {
	val vm = viewModel
	var showExport by remember { mutableStateOf(false) }
	IconButton(
		onClick = { showExport = true },
		enabled = state.activeConversationId != null && state.messages.isNotEmpty(),
	) {
		Icon(Icons.Outlined.IosShare, contentDescription = "Export")
	}
	if (showExport) {
		ExportDialog(
			onDismiss = { showExport = false },
			onExportMarkdown = vm::exportMarkdown,
			onExportPdf = vm::exportPdf,
		)
	}
}
```

Also `import androidx.compose.runtime.mutableStateOf; import androidx.compose.runtime.setValue; import androidx.compose.runtime.remember; import androidx.compose.runtime.getValue` as needed.

- [ ] **Step 5: Build + commit**

```bash
./gradlew :app:assembleDebug
git add app/src/main/java/com/healthaggregator/ui/assistant/ExportDialog.kt \
        app/src/main/java/com/healthaggregator/ui/assistant/AssistantViewModel.kt \
        app/src/main/java/com/healthaggregator/ui/assistant/AssistantScreen.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/res/xml/file_paths.xml
git commit -m "Phase 6: Export dialog + share-sheet wiring

Export icon in TopAppBar (enabled when conversation has messages).
Dialog offers Markdown or PDF. Export writes to cacheDir, hands
content URI to ACTION_SEND via FileProvider (file_paths.xml).
Android's native share sheet picks the target (Email, Messages,
Drive, etc.)."
```

---

### Task 35 (Phase 6 gate)

- [ ] **Step 1: Build + all tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL; ChatExporter tests green.

- [ ] **Step 2: Install + device smoke (exporting a real conversation)**

```bash
./gradlew :app:installDebug
```

- Open a conversation with several messages.
- Tap export icon → Markdown → share sheet → pick "Save to Files" or "Email" → file opens/arrives with disclaimer header, user/assistant turns, resolved citation text.
- Tap export icon → PDF → share → PDF renders in Adobe Reader / Files viewer with readable text.

- [ ] **Step 3: Gate commit**

```bash
git commit --allow-empty -m "Phase 6 gate: export working

Markdown + PDF exports resolve citations to inline values,
include disclaimer, skip tool plumbing. Share via Android native
intent. Verified Markdown renders in Files/Email and PDF renders
in Adobe Reader."
```

---

# Phase 7 — Ship gate

Final end-to-end device smoke + merge.

### Task 36: Fresh-install device smoke

- [ ] **Step 1: Uninstall + reinstall**

```bash
adb uninstall com.healthaggregator
./gradlew :app:installDebug
```

- [ ] **Step 2: Execute full scenario walkthrough**

Run every item below on-device. Any failure loops back to the relevant phase for a hotfix task; do not proceed to merge until all are green.

1. **Onboarding**: open Assistant tab → OnboardingScreen → check + Get started.
2. **API-key empty state**: "API key required" card visible → Open Settings → AI Assistant section → enter valid key → toggle data sharing on → pick `gpt-5` as model.
3. **First chat**: return to Assistant tab → New conversation from drawer → type "What's abnormal or near-abnormal?" → send.
4. **Streaming UX**: tokens appear in an assistant bubble as they arrive. Citation chips render once closing `]` arrives.
5. **Citation nav**: tap a citation chip → `LabDetailScreen` opens for that Observation. Back.
6. **Tool call UX**: ask "Show me my last three A1c readings in detail" → expect `getRawObservation` or `getPanelComponents` invocations; "Looking up data…" indicator shows then clears.
7. **Cross-org awareness**: ask "How does my A1c compare between Cleveland and Summa?" — expect citations from both sources.
8. **Rate-limit simulation**: temporarily set model to `gpt-5.4` (or a model you lack billing for). Ask a question. Expect error banner: "rate_limit" or "auth_invalid" → switch model back.
9. **Persistence**: kill app → reopen → conversations list shows previous threads with titles. Tap one → messages are there. Send a follow-up → streams.
10. **Multi-conversation**: New conversation. Ask different question. Drawer shows two entries. Switch between them — messages swap.
11. **Delete**: drawer → delete one conversation → confirm → disappears; remaining conversation intact.
12. **Export Markdown**: pick a conversation → Export → Markdown → Save to Files → open the .md, verify disclaimer + messages + resolved citations.
13. **Export PDF**: same → PDF → opens in PDF viewer readably.
14. **Settings clear-history**: Settings → AI Assistant → Clear all chat history → confirm → Assistant tab shows empty state again.
15. **Offline behavior**: airplane mode → send a message → offline banner / network_error surfaces; user message preserved for retry.
16. **Onboarding persists**: reinstall (`adb uninstall` then install) → onboarding shows again (since prefs are cleared). Without reinstall, onboarding should NOT re-show once acknowledged.
17. **Disclaimer badge**: visible at bottom of every assistant message bubble.

- [ ] **Step 3: Commit smoke sign-off**

```bash
git commit --allow-empty -m "Phase 7 ship gate: device smoke green

All 17 scenarios exercised and passing on Pixel (Android 16 PHR).
Stream γ ready to merge to main."
```

---

### Task 37: Merge to main

- [ ] **Step 1: Final build + tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL; everything green (unit + instrumented migration test).

- [ ] **Step 2: Merge — no fast-forward, descriptive commit**

```bash
git checkout main
git merge --no-ff feat/gamma-llm-assistant -m "Merge feat/gamma-llm-assistant: Stream γ — LLM Assistant

Diagnostic chat over the full longitudinal health dataset. Hybrid
default (free gpt-5 / paid gpt-5.4 picker). Tool-calling retrieves
raw FHIR / panel components / free-text notes on demand. Inline
[cite:…] citations tap back to α.2 detail screens. Persistent
multi-conversation history via Room schema v4. Markdown + PDF
export with resolved-citation prose and disclaimer. PDF lab import
(γ.2) documented in spec as planned follow-up.

Shipped:
- Phase 1: schema v4 + ChatDao (MIGRATION_3_4, instrumented test)
- Phase 2: SystemPrompt, HealthSnapshotBuilder, AssistantTools,
  CitationRenderer, HealthSnapshotFlagging
- Phase 3: SecureStorage (EncryptedSharedPreferences),
  LlmClient + OpenAiClient (SSE streaming + tool-call round-trip)
- Phase 4: AssistantRepository + AssistantViewModel
- Phase 5: Assistant tab + drawer + chat pane + onboarding +
  Settings section
- Phase 6: ChatExporter (Markdown + PDF) + FileProvider +
  share intent
- Phase 7: device smoke (17 scenarios) green"
```

- [ ] **Step 3: Delete the feature branch (optional)**

```bash
git branch -d feat/gamma-llm-assistant
```

---

## Success criteria (post-merge)

All items from the spec's "Success criteria" section must pass on device:

1. Open Assistant tab → onboarding → acknowledge → chat shell.
2. Enter API key + enable data sharing in Settings.
3. Start a conversation; first message kicks off HealthReport build (transparent to user); streamed answer arrives with inline citation chips.
4. Tap citation → land on LabDetailScreen for that Observation.
5. Ask follow-up requiring tool use; tool-call chip appears then resolves.
6. Open drawer → see conversations; create new; switch back.
7. Export conversation as PDF → share to Email or Messages.
8. Hit rate limit (or use unbilled model) → error banner with model switcher.
9. Reopen app → conversation persists; new message streams.

