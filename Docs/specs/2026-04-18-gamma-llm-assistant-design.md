# Android Stream γ — LLM Assistant Design

**Date:** 2026-04-18
**Branch:** `feat/gamma-llm-assistant`
**Builds on:** α.1 + α.2 + panel-name normalization (all merged to main)

## Goal

A diagnostic-quality LLM assistant embedded in the Android app. The assistant reads Jesse's full longitudinal health dataset (labs, vitals, meds, conditions, allergies, documents), identifies trends and near-abnormal patterns across time and across organizations, and chats about them with inline citations back to the specific source records. Conversations persist across sessions and can be exported as Markdown or PDF for sharing with doctors, family, or other AI models for cross-validation.

This is **the most important single feature** of the app per Jesse's stated priorities — the reason the aggregation effort matters. Quality of reasoning takes precedence over cost or simplicity.

## Non-goals (γ.1 scope)

- **PDF import of external lab results** (Labcorp, etc.) — designed for in this spec but deferred to γ.2. Needs chat-first to ship so we can validate it's useful before building import plumbing.
- **Multi-provider picker UI** (Anthropic, Gemini) — architecture supports it, but γ.1 ships OpenAI-only. Provider swap becomes a settings-row change later.
- **Push notifications** ("new analysis ready" after sync) — nice-to-have, out of scope.
- **Proactive alerts** outside chat — cross-boundary feature, defer.
- **β stream** (daily self-measurement input) — separate stream.
- **Blood pressure unification** (currently split diastolic/systolic Observations) — data-model issue tracked in backlog; not a γ blocker. LLM is explicitly instructed to pair them at presentation.
- **Medication status inference** (current vs discontinued) — same backlog item; LLM instructed to acknowledge Medication records lack status info.

## Design findings (inform the architecture)

### Data scale today

~2,381 FHIR Observations + ~60 medications + conditions/allergies/encounters/documents from Cleveland Clinic and Summa Health. Full history spans roughly 2020–2026. This shape fits comfortably in frontier-model context windows (1M tokens for both GPT-5 and GPT-5.4).

### Model choice — hybrid, default free

**Default:** `gpt-5` (eligible for OpenAI's data-sharing free-token tier — 250K tokens/day on frontier models). [VERIFIED against OpenAI help center, 2026-04-18]

**Upgrade path:** `gpt-5.4` (March 2026 release, 33% fewer factual errors vs 5.2, 1M context) available via Settings picker. Paid tier — cost dominated by input tokens; projected monthly usage ~$2–5 for diagnostic-style sessions.

**Also available in picker:** `gpt-5.4-pro`, `gpt-5.4-mini`, `gpt-5.4-nano`, `gpt-5-mini`, `gpt-5-nano` — covers fallback if user hits rate limits on their preferred model.

**Privacy posture:** Jesse explicitly consents to data sharing with LLM providers; same-tier trust as Epic and Google Health Connect already in the data flow.

### Data-access strategy — "full compact context + tools"

**Rejected:** pure tool-calling. LLM starts blind, wastes a round-trip per session discovering the data shape.

**Rejected:** raw-JSON context-stuffing. 2,381 FHIR Observations as JSON ≈ 200K tokens — blows free tier budget and includes massive FHIR boilerplate the LLM doesn't need.

**Chosen:** purpose-built **HealthReport** markdown context (~30–60K tokens) that serializes the full longitudinal dataset into an LLM-optimized format, plus function-calling tools for targeted drill-down. OpenAI prompt caching automatically discounts the repeated HealthReport prefix on every turn after the first in a conversation, keeping per-turn cost low.

Near-abnormal values (within 10% of reference-range boundaries) are flagged inline so the LLM can recognize "trending toward trouble" before thresholds are crossed — critical for diagnostic reasoning.

### Conversation shape — chat-first, no separate "report" concept

A "generate report" feature would just be a pre-populated prompt. Collapsed into: chat-first UI with starter-prompt chips for discovery ("What's abnormal?", "Summarize my recent lab trends").

### Citations — inline markers, chip rendering

LLM instructed via system prompt to emit `[cite:sourceSystem/fhirRef]` markers whenever making a claim about a specific reading. The UI parses these and renders each as a tappable chip showing `source · date`. Tap navigates to the α.2 detail screen (`LabDetailScreen` for lab observations, `RecordDetailScreen` for other types). This makes claims **verifiable** — the core trust requirement for a diagnostic tool.

## User experience

### Tab placement

New fourth bottom-nav tab: **Assistant** (alongside Home, Records, Settings). Icon: chat bubble with sparkle overlay. First-time tap shows onboarding + disclaimer acknowledgment.

### First-launch onboarding

Full-screen modal on first Assistant tab open. Content:

- Heading: "AI Health Assistant"
- Body: "This assistant analyzes your health data to help you spot trends and ask informed questions. It is not medical advice. Always verify findings with your doctor. Conversations are sent to OpenAI for processing."
- Link: "Learn more about data sharing"
- Required acknowledgment checkbox
- "Get started" button — dismisses, persists acknowledgment in EncryptedSharedPreferences

### Main Assistant screen layout

Two-pane design on wide screens, single-pane with drawer on phones:

- **Conversations pane** (drawer on phones, left column on tablets): list of past conversations with titles and last-message timestamps. "New conversation" button at top. Long-press for rename/delete. Most recent at top.
- **Chat pane**: message list (user right, assistant left, tool calls as collapsed chips), streaming token renderer, input at bottom with starter-prompt chips above when conversation is empty.

### Starter-prompt chips (empty conversation only)

Eight suggested prompts, tap to pre-fill the input:

1. Summarize my recent lab trends
2. What's abnormal or near-abnormal right now?
3. How's my A1c trending over the years?
4. Are any of my labs concerning when considered together?
5. What patterns do you see in my vitals?
6. Cross-reference my labs with my medications
7. Help me prepare questions for my next appointment
8. What should I ask my doctor about?

(Exact list tunable in code, not spec-binding.)

### Message bubble rendering

- **User messages**: right-aligned, light surface color, plain text.
- **Assistant messages**: left-aligned, darker surface, markdown-rendered. Citation markers `[cite:...]` replaced with small chips showing source icon + date — tap navigates. "Not medical advice" badge in the bottom-right corner of every assistant bubble.
- **Tool calls**: collapsed by default, showing "Looked up A1c trend" etc. Tap to expand full tool request + response JSON (for debugging/verification).
- **Streaming**: assistant tokens appear live; citation chips materialize when closing `]` arrives.

### Export actions

Long-press on a conversation title (in drawer) or overflow menu in chat pane → Export → **Markdown** or **PDF**:

- Header includes: title, date range, disclaimer, source badges.
- Body: full conversation transcript. Citations resolved to inline text: `[A1c 6.3% on 2026-02-12, Summa Health]`.
- Footer: "Generated by HealthAggregator AI Assistant. Not medical advice. Verify with a licensed clinician."
- PDF rendered via Android PdfDocument API (native, no Compose-to-PDF bridge needed — we write plain text styled via PdfDocument.Page).
- Share via Android native share sheet (intent `ACTION_SEND` with `application/pdf` or `text/markdown` MIME).

### Settings changes

Settings screen gains an **AI Assistant** section:

- "OpenAI API Key" — masked input, persistent. Placeholder text links to where to get one.
- "Model" — dropdown picker:
  - `gpt-5 (free-tier eligible)` — **default**
  - `gpt-5.4 (latest, paid)`
  - `gpt-5.4-pro (best quality, paid)`
  - `gpt-5.4-mini (fast, cheap)`
  - `gpt-5.4-nano (cheapest)`
  - `gpt-5-mini`, `gpt-5-nano` (fallback)
- "Data sharing opt-in" — toggle, explanation. Required for `gpt-5` free tier.
- "Clear all chat history" — destructive action, confirmation dialog.

## Architecture

### Component layout

```
healthaggregator-android/app/src/main/java/com/healthaggregator/
├── ai/
│   ├── LlmClient.kt                  # Sealed interface — providers implement
│   ├── OpenAiClient.kt               # Ktor + SSE streaming, tool-call handling
│   ├── AssistantRepository.kt        # Facade: conversations, streaming, persistence
│   ├── HealthSnapshotBuilder.kt      # Builds HealthReport markdown from Room
│   ├── AssistantTools.kt             # Function-call schemas + in-process dispatch
│   ├── CitationRenderer.kt           # Parses [cite:src/ref] → annotated spans
│   ├── ChatExporter.kt               # Markdown + PDF output
│   └── SystemPrompt.kt               # The instruction string
├── data/
│   ├── entities/ChatConversation.kt
│   ├── entities/ChatMessage.kt
│   ├── dao/ChatDao.kt
│   └── AppDatabaseMigrations.kt      # MIGRATION_3_4 adds two tables
├── ui/
│   ├── assistant/
│   │   ├── AssistantScreen.kt        # Drawer + chat pane scaffold
│   │   ├── AssistantViewModel.kt
│   │   ├── ConversationsDrawer.kt
│   │   ├── ChatPane.kt
│   │   ├── MessageBubble.kt
│   │   ├── CitationChip.kt
│   │   ├── StarterChips.kt
│   │   ├── DisclaimerBanner.kt
│   │   ├── OnboardingScreen.kt
│   │   └── ExportDialog.kt
│   ├── settings/
│   │   └── AiAssistantSettings.kt    # API key + model picker section
│   └── navigation/
│       └── AppNav.kt                 # Adds "assistant" route + nav item
├── di/AiModule.kt                    # Hilt bindings for LlmClient, repo, ChatDao
└── util/
    └── SecureStorage.kt              # EncryptedSharedPreferences wrapper
```

### Data flow — one chat turn

1. User types → `AssistantViewModel.sendMessage(text)`.
2. ViewModel inserts user `ChatMessage` via `ChatDao`; UI updates from Flow.
3. ViewModel invokes `AssistantRepository.stream(conversationId, text)`:
   - If this is the first message in the conversation:
     - `HealthSnapshotBuilder.build()` queries Room for all domains → emits `HealthReport` markdown (~30–60K tokens).
     - Persists the snapshot to `ChatConversation.snapshotText`.
   - Otherwise: reuses cached `snapshotText` from the row.
4. Repo assembles the request body:
   - `messages`: `[system_prompt, health_report, ...prior_messages, new_user_message]`
   - `tools`: `getRawObservation`, `getPanelComponents`, `searchFreeText`
   - `stream: true`
5. `OpenAiClient.stream(request)` opens SSE connection → emits `Flow<StreamEvent>`:
   - `TokenDelta(text)` → append to in-progress assistant message.
   - `ToolCallRequest(name, args)` → pause streaming, dispatch via `AssistantTools`, send tool result back, resume.
   - `Done` → finalize message.
6. Repo persists the completed assistant `ChatMessage` (including any `toolCallsJson`).
7. `CitationRenderer` parses `[cite:...]` markers in `MessageBubble` render to annotated `AnnotatedString`, replacing with clickable `CitationChip` composables.
8. Citation tap → lookup target in `LabObservation` / other entity tables → navigate to `LabDetailScreen` or `RecordDetailScreen`.

### HealthReport format

Plain markdown, LLM-optimized for density:

```markdown
# Jesse Coble — Full Health Data Context

**Generated:** 2026-04-18T10:30:00Z
**Data span:** 2020-01-15 → 2026-04-18
**Sources:** Cleveland Clinic, Summa Health

## Data-model caveats the LLM must know

- Blood pressure is stored as two separate Observations (diastolic + systolic) with distinct LOINC codes (8462-4, 8480-6). Pair them at presentation by matching effectiveAt timestamps.
- Medication records are bare `Medication` resources without status — assume "was taken at some point" unless the user specifies otherwise. Do not assume current use.
- Reference ranges vary by lab org; per-row `refLow/refHigh` are authoritative.

## Labs — full history, grouped by canonical test

### Hemoglobin A1c (LOINC 4548-4, 17856-6) | Ref: 4.0–5.6 %
- 2020-01-15: 5.2 (Cleveland Clinic) [cite:cleveland-clinic/Observation/abc123]
- 2022-03-20: 5.5 HIGH-NORMAL (Cleveland Clinic) [cite:cleveland-clinic/Observation/def456]
- 2024-06-10: 6.1 HIGH (Summa Health) [cite:summa-health/Observation/ghi789]
- 2026-02-12: 6.3 HIGH (Summa Health) [cite:summa-health/Observation/jkl012]

### Glucose, Fasting (LOINC 1558-6) | Ref: 70–99 mg/dL
- [each reading, compact line format]

[all canonical tests alphabetically]

## Vitals — full history
### Heart Rate (LOINC 8867-4) | Typical: 60–100 bpm
- [readings]

### Blood Pressure — PAIRED (diastolic 8462-4 / systolic 8480-6)
- 2024-06-10: 138/89 HIGH (Summa Health) [cite:summa-health/Observation/...sys, ...dia]

## Medications (note: status not available — assume historical)
- Metformin 500mg — [cite:...]
- Lisinopril 10mg — [cite:...]

## Conditions
- Essential hypertension (ICD-10 I10) — active 2024-06-10 [cite:...]

## Allergies
- [entries]

## Recent clinical documents
- 2026-02-12: Annual Physical Summary (Summa Health) [cite:...]
```

**Near-abnormal logic:** a numeric value is flagged `HIGH-NORMAL` if `refHigh != null && value > refHigh × 0.9 && value <= refHigh`. `LOW-NORMAL` if `refLow != null && value < refLow × 1.1 && value >= refLow`. Outside-range values get plain `HIGH` / `LOW`. Non-numeric values pass through without flagging.

**Built-in citations:** every data row in the HealthReport already includes `[cite:...]` markers. This pre-populates the LLM with the exact syntax and shows it that citations are expected — the LLM then echoes them in its answers by imitation.

**Size cap:** if the full report exceeds 200K tokens (uncommon but possible after years of accumulation), oldest readings are dropped from each test (keeping at least 20 most recent readings per test, with a note `[...N older readings omitted]`). Configurable in code.

### System prompt

```
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
```

(Exact wording tunable in code; this is the shape.)

### Tools (γ.1)

All tools are Kotlin functions that query the existing Room database. Schemas registered via OpenAI's function-calling JSON schema.

| Tool | Parameters | Returns |
|---|---|---|
| `getRawObservation` | `sourceSystem: String, fhirRef: String` | Raw FHIR JSON string via `RecordsRepository.findRawJson`. For deep verification. |
| `getPanelComponents` | `serviceRequestRef: String` | JSON array of components — name, value, unit, range, abnormal flag. Via existing `observeLabsByServiceRequest`. |
| `searchFreeText` | `query: String, limit: Int = 20` | JSON array of `SourceRecord` matches — for finding notes/impressions in `DocumentReference` and other narrative fields. Case-insensitive `LIKE %query%` over `rawJson` column. |

Tool schemas live in `AssistantTools.kt` as one `ToolRegistry` object. Adding tools later (e.g., `getVitalsTrend`, `getDailyMeasurements`) requires only adding to the registry.

### Data model — schema v4

MIGRATION_3_4 adds two tables:

```kotlin
@Entity(
    tableName = "chat_conversations",
    indices = [Index("updatedAt")]
)
data class ChatConversation(
    @PrimaryKey val id: String,            // UUID
    val title: String,                     // auto-generated from first message; editable
    val createdAt: Instant,
    val updatedAt: Instant,
    val snapshotText: String? = null,      // cached HealthReport for this conversation
    val snapshotGeneratedAt: Instant? = null,
    val modelId: String,                   // e.g. "gpt-5" or "gpt-5.4" — locked per-conversation for reproducibility
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatConversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversationId", "createdAt")]
)
data class ChatMessage(
    @PrimaryKey val id: String,            // UUID
    val conversationId: String,
    val role: String,                      // "user" | "assistant" | "tool"
    val content: String,
    val toolCallsJson: String? = null,     // JSON array of tool_calls (assistant messages)
    val toolCallId: String? = null,        // For role="tool" — which call this responds to
    val modelId: String? = null,           // which model generated this (null for user messages)
    val createdAt: Instant,
)
```

Migration:

```sql
CREATE TABLE IF NOT EXISTS chat_conversations (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL,
    snapshotText TEXT,
    snapshotGeneratedAt INTEGER,
    modelId TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS index_chat_conversations_updatedAt ON chat_conversations(updatedAt);

CREATE TABLE IF NOT EXISTS chat_messages (
    id TEXT PRIMARY KEY NOT NULL,
    conversationId TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    toolCallsJson TEXT,
    toolCallId TEXT,
    modelId TEXT,
    createdAt INTEGER NOT NULL,
    FOREIGN KEY (conversationId) REFERENCES chat_conversations(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId_createdAt ON chat_messages(conversationId, createdAt);
```

### LlmClient interface

```kotlin
interface LlmClient {
    fun stream(
        messages: List<LlmMessage>,
        tools: List<ToolSchema>,
        modelId: String,
    ): Flow<StreamEvent>
}

data class LlmMessage(
    val role: String,  // system | user | assistant | tool
    val content: String,
    val toolCalls: List<ToolCallRequest>? = null,
    val toolCallId: String? = null,
)

sealed interface StreamEvent {
    data class TokenDelta(val text: String) : StreamEvent
    data class ToolCallRequest(val id: String, val name: String, val argsJson: String) : StreamEvent
    data class ToolCallComplete(val id: String) : StreamEvent
    data object Done : StreamEvent
    data class Error(val message: String, val retryable: Boolean) : StreamEvent
}
```

`OpenAiClient` implements this using Ktor HTTP client with SSE parsing. Content-type handling, exponential backoff on retryable errors, and tool-call result injection are all inside the client — the repo just forwards user messages and drains the flow.

### Secure storage

`SecureStorage` wraps `EncryptedSharedPreferences` (AndroidX Security library, AES-256-GCM, Android Keystore-backed key). Stores:

- `openai_api_key` — the API key
- `data_sharing_enabled` — Boolean, required-true for `gpt-5` free-tier
- `selected_model` — String, default `"gpt-5"`
- `disclaimer_acknowledged` — Boolean, set on onboarding

### Citations — render pipeline

`CitationRenderer.parse(text: String): AnnotatedString`:

1. Regex `\[cite:([^/]+)/([^\]]+)\]` captures `source` and `fhirRef`.
2. Builds `AnnotatedString` replacing each match with a placeholder (Compose `inlineContent` slot).
3. `MessageBubble` renders the `AnnotatedString` with `inlineContent` mapping placeholder IDs to `CitationChip` composables.
4. `CitationChip` shows source name + date (looked up from `LabObservation`/other tables via repository, cached in ViewModel). Tap → `onCitationClick(source, fhirRef)` → navigation.

Navigation target lookup:
- If a `LabObservation` row matches `(sourceSystem, fhirReference)` → nav to `lab/{source}/{fhirRef}`.
- Otherwise → nav to `record/{source}/{fhirRef}`.

(Both routes already exist from α.2.)

### Exporter

`ChatExporter.exportMarkdown(conversationId): String` and `ChatExporter.exportPdf(conversationId): ByteArray`:

- Loads conversation + messages.
- Resolves `[cite:...]` markers to inline text: `[A1c 6.3% on 2026-02-12, Summa Health]` by looking up `LabObservation.effectiveAt` etc.
- Markdown: standard markdown with a front-matter header (title, date range, sources, disclaimer).
- PDF: Android `PdfDocument` API. Fixed-width monospace for technical fidelity; one page per ~50 lines; header on every page.
- Output handed to caller (ViewModel) which writes to temp file + fires `Intent.ACTION_SEND` with FileProvider URI.

## Error handling

| Situation | UI response | Technical |
|---|---|---|
| No API key configured | Assistant tab shows centered "Set up your API key" card linking to Settings. Tab badge hides chat. | `AssistantRepository.stream` short-circuits with `StreamEvent.Error` if key missing. |
| Data sharing not opted-in + `gpt-5` selected | Warning banner: "Enable data sharing in Settings to use free tier" with deep link. | Repo checks opt-in for free-tier-eligible models before calling API. |
| Network unreachable | Offline banner at top of chat. User message preserved with failed state + retry button. | Ktor throws → caught, emitted as `StreamEvent.Error(retryable=true)`. |
| Rate limit (429) | Banner: "Free tier limit reached — resets midnight UTC. Switch to paid model?" with one-tap model picker. | OpenAI 429 → specific `RateLimitError` → UI offers model switch. |
| Auth error (401) | Error card: "API key invalid or revoked" → Settings link. | OpenAI 401 → emitted as non-retryable error. |
| Timeout (>60s) | Failed message state, retry button. | Ktor request timeout 60s. |
| Tool call failure | LLM receives `{"error": "..."}` as tool result, continues. | `AssistantTools.dispatch` catches per-tool, returns structured error. |
| Snapshot > 200K tokens | Warning appended to HealthReport, oldest readings per test dropped to stay under cap. | `HealthSnapshotBuilder` enforces cap with per-test recent-N fallback. |
| API outage (5xx) | Error card with raw error text, retry button. | 5xx responses emitted as retryable errors. |
| Corrupt `toolCallsJson` in DB | Message skipped on load, logged. | `ChatDao` deserialization uses `runCatching`. |

Never silently swallow errors. Every failure path surfaces something actionable in the UI.

## Testing strategy

Follows the α.2 pattern: JUnit 4 + Robolectric for Android-dependent tests, JUnit 5 Jupiter for pure-Kotlin logic, Room `MigrationTestHelper` for schema changes.

### Unit tests (Jupiter, pure Kotlin)

- **`HealthSnapshotBuilderTest`** — given seeded in-memory Room, verify:
  - Full history is serialized (no truncation in normal size).
  - Near-abnormal logic: `HIGH-NORMAL` / `LOW-NORMAL` flags applied at 90% / 110% of boundaries.
  - Canonical test grouping joins multi-org readings under one heading.
  - BP-pairing instruction appears in caveats section when BP data exists.
  - Citation markers embedded on every row.
  - Size cap kicks in past 200K tokens with per-test recent-N fallback.
- **`CitationRendererTest`** — regex parsing:
  - Single citation in a sentence.
  - Multiple citations in one message.
  - Citations adjacent (`[cite:a/b][cite:c/d]`) parse independently.
  - Malformed `[cite:...` with no closing bracket left as-is.
  - Non-citation `[brackets]` unaffected.
- **`AssistantToolsTest`** — schema shape + dispatch:
  - Each tool schema serializes to valid OpenAI function spec JSON.
  - `getRawObservation` dispatches to `RecordsRepository.findRawJson` with correct args.
  - `getPanelComponents` returns serialized components.
  - `searchFreeText` returns JSON array.
  - Unknown tool name returns structured error.
- **`ChatExporterTest`** — golden-file regression:
  - Markdown format matches committed `expected.md`.
  - PDF byte-length in expected range; PDF header bytes valid.
  - Citations resolved to inline text.
- **`SystemPromptTest`** — stable string check (catches accidental edits that break LLM behavior).

### Android/Robolectric tests (JUnit 4)

- **`ChatDaoTest`** (Robolectric, in-memory Room):
  - Insert conversation + messages, observe via Flow.
  - Foreign key cascade: deleting a conversation deletes its messages.
  - Ordering: messages returned by `createdAt` ascending.
- **`AppDatabaseMigrationTest.migrate_3_to_4`** (instrumented — runs with `MigrationTestHelper`):
  - Create v3 schema, add fixture rows from existing tables.
  - Run migration to v4, assert both new tables exist with correct columns + indices.
  - Assert no existing data lost.
- **`OpenAiClientTest`** (Robolectric, `MockEngine` for Ktor):
  - Streaming SSE tokens produce `TokenDelta` events in order.
  - Tool call mid-stream produces `ToolCallRequest`, pauses, accepts injected result, resumes.
  - 429 response → `RateLimitError` emitted, retry metadata present.
  - 401 → non-retryable error.
  - Network error → retryable error.
  - Timeout enforced at 60s.

### Schema snapshot

`app/schemas/com.healthaggregator.data.AppDatabase/4.json` committed.

### No real API calls in tests

Every test mocks the HTTP layer. Real OpenAI calls only happen via on-device smoke after each phase gate.

## Phase plan (locks in the implementation plan's shape)

### Phase 1 — Data layer foundation
- `ChatConversation` + `ChatMessage` entities, `ChatDao`, `MIGRATION_3_4`, schema v4.json, DAO tests, migration test.
- Gate: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedAndroidTest` green.

### Phase 2 — HealthSnapshotBuilder + tools
- `HealthSnapshotBuilder` with near-abnormal logic, BP-pairing caveat, citation-embedding, size cap.
- `AssistantTools` registry with three tools.
- `CitationRenderer` with full regex parsing.
- `SystemPrompt` constant.
- All Jupiter tests pass.
- Gate: build + test pass.

### Phase 3 — LlmClient + OpenAiClient
- `LlmClient` interface + sealed `StreamEvent`.
- `OpenAiClient` with Ktor + SSE + tool-call handling + error mapping.
- `SecureStorage` wrapper around `EncryptedSharedPreferences`.
- MockEngine tests for all paths.
- Gate: build + test pass.

### Phase 4 — AssistantRepository + ViewModel wiring
- `AssistantRepository` facade: create/load/stream/persist conversations.
- Hilt module binding.
- `AssistantViewModel` with UiState exposing conversations + active chat + streaming state.
- No UI yet — repo+VM exercised via unit tests.
- Gate: build + test pass.

### Phase 5 — UI shell + Settings
- `AssistantScreen` + `ChatPane` + `ConversationsDrawer` + `MessageBubble` + `CitationChip` + `StarterChips` + `DisclaimerBanner` + `OnboardingScreen`.
- `AiAssistantSettings` section added to Settings screen.
- AppNav route + 4th bottom-nav tab.
- Citation-tap navigation wired.
- Gate: build + test pass; manual smoke on device — onboard, enter key, send message, see response, tap citation.

### Phase 6 — Export
- `ChatExporter` for markdown + PDF.
- `ExportDialog` UI with share-sheet integration.
- Golden-file tests for export format.
- Gate: build + test pass; device smoke — export a real conversation, share to Files/Email.

### Phase 7 — Polish + ship
- On-device smoke across multiple conversation lengths, rate limit simulation (via mock), no-API-key empty state, network-off behavior.
- Merge to main.

## Success criteria

At phase-7 ship gate, Jesse should be able to:

1. Open Assistant tab → see onboarding → acknowledge → land in chat.
2. Enter API key + enable data sharing in Settings.
3. Start a conversation. See HealthReport context get built silently. Send "What's abnormal or near-abnormal?" and receive a streamed answer with inline citation chips.
4. Tap a citation → land on `LabDetailScreen` for that Observation.
5. Ask a follow-up that requires tool use ("show me raw FHIR for my last A1c") and watch the tool-call chip appear then resolve.
6. Open conversation drawer → see the conversation in the list → create a new conversation → switch back.
7. Export the conversation as PDF → share to Email or Messages.
8. Hit rate limit (simulated by switching to `gpt-5.4` with no billing) → see banner with one-tap fallback to `gpt-5-mini`.
9. Reopen app → conversation still there → send new message → LLM responds using cached snapshot (verifiable via network inspector showing cached-prefix-hit token count).

If any of those flows don't work, the stream isn't done.

## Open questions deferred to the implementation plan

- **Snapshot chunking for very long conversations**: the HealthReport grows over years. Specific strategy for the 200K cap (keep most-recent N per test, truncate oldest) is in-code; tunable numbers in the plan.
- **Citation chip visual design**: source-icon inclusion, date format, chip size. Figure out during phase 5 polish on device.
- **Starter-chip content**: exact eight prompts subject to iteration post-smoke.
- **Model picker default behavior when API key changes**: reset model choice? preserve? — default to preserve.

None of the above blocks implementation; each is an in-code tuning decision.
