# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

.NET 10 Web API (C#, nullable enabled) + SvelteKit 2 / Svelte 5 + Tailwind 4 + SQLite via EF Core. Frontend uses pnpm.

## Common commands

```bash
# Boot everything (API + web + Epic callback listener)
./scripts/start-dev.sh

# .NET
dotnet build                                      # builds the whole solution
dotnet test                                       # runs the xUnit suite
dotnet test --filter "FullyQualifiedName~EpicSyncSummaryTests"   # single test class
dotnet test --filter "Name=FromResources_keeps_per_resource_diagnostics_and_aggregates_totals"  # single test
dotnet run --project HealthAggregator.Api         # API only

# Web (run from healthaggregator-web/)
pnpm dev          # vite dev (HTTPS via @vitejs/plugin-basic-ssl)
pnpm build
pnpm check        # svelte-kit sync + svelte-check (typecheck)

# Epic OAuth client id (required before SMART flow)
dotnet user-secrets set "Epic:ClientId" "<id>" --project HealthAggregator.Api
```

`start-dev.sh` kills any stale processes/ports, exports `Epic__CallbackBaseUrl=https://localhost:$EPIC_CALLBACK_PORT`, then runs the API on **two** URLs (`5310` and `5010`) so the Epic sandbox can keep hitting the legacy callback port while the rest of the app talks to the isolated API port. Override with `HEALTHAGGREGATOR_API_PORT`, `HEALTHAGGREGATOR_WEB_PORT`, `HEALTHAGGREGATOR_EPIC_CALLBACK_PORT`. API logs go to `/tmp/healthaggregator-api.log`.

## Architecture

Four-project .NET solution with strict layering — **Api → Core + Data; Data → Core**. Core holds plain DTOs (`EpicOrganization`, `ImportSummary`), Data owns EF entities and the `FhirImportService`, Api adds controllers, the Epic OAuth client, and the local assistant.

Dependency wiring lives in `HealthAggregator.Api/Program.cs`. The DB path defaults to `LocalApplicationData/HealthAggregator/healthaggregator.db` but Development overrides it to `~/Library/Application Support/HealthAggregator/healthaggregator-dev.db` via `appsettings.Development.json`. Schema is applied via `db.Database.Migrate()` at startup — EF Core migrations live in `HealthAggregator.Data/Migrations/` and are the source of truth for schema. Add new migrations with:

```bash
dotnet ef migrations add <Name> --project HealthAggregator.Data --startup-project HealthAggregator.Api
```

Never hand-edit a migration after it's been applied to any environment.

### Epic SMART on FHIR flow

`EpicIntegrationsController` + `EpicFhirClient` implement the standard OAuth 2.0 PKCE dance: `GET /api/integrations/epic/connect?organizationId=…` builds the authorize URL and persists an `EpicAuthorizationState` row (state, code verifier, redirect URI, 10-minute expiry). Epic redirects back to `/api/integrations/epic/callback`; the client exchanges the code, stores tokens on `EpicConnection`, then redirects to `Epic:FrontendBaseUrl` with `?epic=connected` or `?epic=error&reason=…`. `POST /api/integrations/epic/sync?connectionId=…` pulls Patient/Observation/DiagnosticReport/Condition/MedicationRequest/AllergyIntolerance/Encounter/DocumentReference and feeds each FHIR bundle into `FhirImportService`. The redirect URI is built from `Epic:CallbackBaseUrl` if set, otherwise from the incoming request — keep this aligned with whatever URL is registered in the Epic app.

### Ingestion

`FhirImportService.ImportBundleAsync` is the single entry point for both Epic sync and manual JSON upload (`POST /api/imports`). It walks bundle entries, upserts a raw `SourceRecord` per resource (uniqueness on `SourceSystem + ResourceType + ResourceId`), then dispatches per `resourceType` to typed upserts on `LabObservation`, `DiagnosticReportRecord`, `ConditionRecord`, etc. Each typed table has a unique index on `(SourceSystem, FhirReference)` — upserts must look up by that key. Only `Observation` resources whose category includes `laboratory` flow into `LabObservation`; everything else stays in `SourceRecord` only. Non-JSON uploads (PDF/CSV/XLSX) are **stored as metadata only** under `SourceRecord` with `ResourceType = "UploadedFile"`; structured parsers are not implemented.

### Read-only assistant

`ReadOnlyAssistantService` is intentionally **not an LLM** — it's a deterministic keyword/pattern matcher over `LabObservation` rows that returns a prose answer plus structured `AssistantCitation`s pointing back at FHIR references. Treat it as a search/summarization helper, not a conversational AI.

### Frontend

Single SvelteKit page (`src/routes/+page.svelte`) talking to the API through `src/lib/api.ts`. `VITE_API_BASE_URL` (default `https://localhost:5310`) selects the backend. The dev server uses self-signed HTTPS via `@vitejs/plugin-basic-ssl`. Tailwind 4 is wired via `@tailwindcss/vite` — no postcss config. The CORS policy `"Frontend"` in `Program.cs` whitelists `localhost:5373` and `127.0.0.1:5373` over both schemes; add new dev origins there if you change ports.

## Working norms

### Build & test discipline

- **Build once, test with `--no-build`.** Run `dotnet build` once after any source change, then every subsequent `dotnet test` invocation in that session uses `--no-build`. This applies to subagents too — pass `--no-build` and `--filter` instructions explicitly so they don't rebuild on each test run.
- **Pre-commit verification (mandatory).** Before any commit, run the relevant gate and confirm 0 errors:
  - `.cs` changed → `dotnet build && dotnet test --no-build`
  - `.svelte` / `.ts` / `.js` in `healthaggregator-web/` changed → `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
  - Both → run both.
- **Test integrity — never change a test to match the code.** If a test fails, the default is "the code is wrong, fix the code." Only modify a test when the behavior was intentionally changed, the reason is clear, and you've gotten explicit user approval. Never silently flip an assertion. Golden/expected fixture files are tests too — same rules apply.
- **Commit messages: comprehensive, no Co-Authored-By line, no mention of Claude/Anthropic in PR descriptions either.**

### Reasoning discipline — verify, don't pattern-match

The default for any load-bearing claim is "I need to verify this against a primary source," not "I know this from training." Confident-sounding speculation that drives a refactor costs hours to undo; "let me check" costs minutes.

- **Tag every load-bearing claim with a confidence label** in your text (not just implied):
  - **VERIFIED** — read the actual file with the Read tool yourself this session, or fetched the actual primary doc via WebFetch, or the user said it directly. Subagent summaries do **not** count as VERIFIED for facts that drive code changes — read the file yourself.
  - **CITED** — relayed from a secondary source (research note, prior session output). Docs written by previous LLM sessions are CITED at best.
  - **INFERRED** — pattern-matched from training or general reasoning with no primary source. Treat as a hypothesis, not a fact.
  - **UNKNOWN** — say so. "I don't know, let me check" is always acceptable.
- **No LLM-on-LLM verification chains.** Two LLMs agreeing isn't evidence; they may share training data. Break out by reading the actual code, fetching the actual spec (e.g., HL7 FHIR R4, Epic developer docs, x12.org), or asking the user.
- **Read load-bearing code directly.** When a specific file/method drives a recommendation, read it with the Read tool and cite `path:line` in the claim. Subagent code maps are fine for orientation, not for facts that drive a change.
- **Inventory all dispatch sites before any refactor that touches an enum, status field, switch, or polymorphic dispatch.** Grep for every consumer (`switch.*<EnumName>`, `case <EnumName>`, the literal status string) and classify each hit before writing the plan. The 30-second grep is much cheaper than discovering a missed dispatch site mid-refactor. Red flag: any plan that says *"the only place X is enforced is Y"* without grep evidence — stop and verify.
- **Devil's advocate before architectural commits.** For decisions that look obvious, dispatch a subagent whose only job is to argue the strongest counter-case. Weak counter → robust recommendation. Strong counter → real debate to resolve before spending engineering effort.
- **AI-time, not human-developer-time.** A "3-5 day refactor" in human terms is typically 1-3 hours of AI-assisted work. Don't bolt on multi-week rollout process for what's actually a 5-hour change; keep the architectural safety (tests, feature flags, staging checks) and drop process overhead that only made sense at inflated time scales.

### Backend resilience

- **Always set timeouts on external clients.** `HttpClient.Timeout` on `EpicFhirClient`'s injected client, EF command timeouts, any future SFTP/SMTP. A client that can talk to a remote system needs a timeout — defaults are usually infinite.
- **Thread `CancellationToken` through every async I/O path** and pass it to `HttpClient.SendAsync`, EF queries, `Task.Run`. A timeout is useless if nothing checks the token.
- **`Task.Run(() => SyncMethod(), ct)` does not cancel the sync method.** The token only prevents the task from starting; once the lambda is executing, blocking calls (legacy SDKs, `Connect()`) cannot be interrupted by the token. The fix is library-level timeouts, not wrapping in `Task.Run`.
- **Never use bare `catch { }`.** Always `catch (Exception ex)` and at minimum log it. Bare catches make outages invisible.
- **Use a DB transaction when multiple save steps could partially fail.** `FhirImportService` upserts span many tables — group related upserts in one transaction so a partial failure doesn't leave half-imported records.

### Database safety

- **Never drop or destructively modify the local SQLite file (or any DB) without explicit user confirmation.** The dev DB holds imported FHIR data and Epic tokens — always ask first and offer a backup before deleting.
- **Always verify migration SQL before applying to a shared environment.** Use `dotnet ef migrations script <From> <To> --project HealthAggregator.Data --startup-project HealthAggregator.Api` and review the output.
- **Never hand-edit a migration that has already been applied** anywhere — generate a new corrective migration instead.

### Debugging & bug fixing

- **Reproduce → diagnose → fix.** Don't propose a fix until you've confirmed the root cause.
- **Write a minimal failing test first.** Run it, watch it fail for the right reason, then implement the fix. Don't skip the reproduction step.
- **For bugs in code you just wrote, check the simplest causes first** — case sensitivity, wrong endpoint, typo, missing import — before proposing a larger refactor.
- **Verify "pre-existing" failures actually pre-exist.** Stash and re-run, or check the failure on a clean tree, before claiming a test failure isn't yours.
- **Chunk large plans into stages of 3-4 tasks max.** After each stage run the relevant tests + typecheck and summarize before continuing.

### Self-improvement

When a non-obvious lesson surfaces this session — a mistake that cost more than one attempt, or a non-obvious approach that worked well — write it to memory under `memory/` (per the auto-memory rules above). Promote a memory to CLAUDE.md only when it's a universal rule for this repo, not a situational note. Check the existing memories/CLAUDE.md before writing — update existing entries rather than duplicating.
