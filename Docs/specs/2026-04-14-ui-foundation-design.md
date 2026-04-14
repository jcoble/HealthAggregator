# UI Foundation & Multi-Organization Scaffolding — Design Spec

**Status:** draft (brainstorm complete, pending plan) · **Date:** 2026-04-14 · **Scope window:** Stream A+ (from `Docs/Research/2026-04-14-gemini-health-data-platform-research.md` decomposition)

---

## Why this matters

This app's ultimate purpose is **personal diagnostic assistance**. The owner is managing health issues that haven't been resolved through standard care, and has longitudinal records across two Epic-using health systems (Cleveland Clinic and Summa Health). The target end-state is an LLM-driven assistant that can ingest lab results + vitals + medications + encounter history across both organizations, cross-reference them over time (e.g., "this marker shifted six months after this medication was added"), and surface patterns that may be invisible when records are siloed per-provider. Everything in this spec — multi-org source tagging, structured per-record stores, an extensible component library for rich visualizations — is foundation for that eventual diagnostic surface. Each design choice should be evaluated against the question: *does this make the future diagnostic assistant better?*

## Problem

The HealthAggregator app has grown past the prototype stage. Today the entire UI lives on a single SvelteKit route (`src/routes/+page.svelte`, ~541 lines, 8 distinct panels across 6 sections stacked on one page). The backend already stores far more than the dashboard surfaces: `MedicationRequest`/`Statement`, `Condition`, `AllergyIntolerance`, `Encounter`, and `DocumentReference` records sit in SQLite with no controller or UI access path. Multi-organization support is conceptually baked into the schema (every data table uniquely keys on `(SourceSystem, …)` and `FhirImportService` tags every row with a source system) but no UI surfaces it, and `appsettings.json` ships with only one configured org (`epic-sandbox`).

The user has two real Epic MyChart accounts — Cleveland Clinic and Summa Health — that need to merge into one local database while preserving a clear "this row came from Cleveland" / "this row came from Summa" signal on every surface (lists, trends, citations).

This spec restructures the UI into a multi-page SvelteKit app modelled on the EdiPlatform page architecture, ports a targeted subset of EdiPlatform's component library, adds the backend controllers to match the nav, and scaffolds multi-organization source attribution end-to-end.

## Scope summary

**In scope**

- Sidebar-driven `AppShell` layout with 13 routes across 4 nav groups
- Targeted port of 16 UI primitives (with a few extended variants) and 6 app-level components from EdiPlatform, plus 8 new HealthAggregator-specific components and a new 3-file `data-table/` directory
- 5 new thin backend controllers (`Medications`, `Conditions`, `Allergies`, `Encounters`, `Documents`) + filter extensions to `LabsController` and `TimelineController`
- `DELETE /api/integrations/epic/connections/{id}` endpoint for disconnect
- `SmartConfigurationClient` helper that discovers per-org OAuth endpoints via `<FhirBaseUrl>/.well-known/smart-configuration`
- `EpicSettings.Organization` config simplified to `{ Id, Name, FhirBaseUrl, IsSandbox }` (authorize/token URLs discovered at runtime)
- Three orgs wired in `appsettings.json`: `epic-sandbox`, `cleveland-clinic`, `summa-health`
- Source attribution (`SourceBadge` primitive) on every list row, chart point, and assistant citation
- URL-synced filter state on every list page
- `+page.ts` client-side loader pattern across all routes
- Dark-only theme (no light variant yet)
- Existing dashboard panels' functionality relocated to their respective feature pages; Dashboard (`/`) becomes a strict summary view that reads from — but does not own — that functionality

**Out of scope — explicit non-goals, organized by follow-up stream**

Stream ordering updated 2026-04-14 to reflect the diagnostic-assistant end-goal: detail pages feed the assistant's drill-downs, data completeness feeds its reasoning, and the assistant itself is the payoff.

- **Stream B — Per-record detail pages.** Click a lab / medication / encounter / document / condition / allergy row → dedicated detail route (`/labs/[id]` etc.) showing the full FHIR resource, cross-source reconciliation (e.g., "the same drug recorded by both Cleveland Clinic and Summa Health"), related timeline events, and raw JSON for debugging. Row-click wiring stays inert in Stream A+ so Stream B lands without retrofitting every list page.
- **Stream C — Data completeness for diagnosis.**
  - **Vitals ingestion.** Extend `FhirImportService` to parse `Observation` resources with `category = vital-signs` into a new `VitalsObservation` entity (blood pressure, HR, weight, BMI, SpO₂, temperature, etc.). `/vitals` page stub from Stream A+ fills in with a real list + trend panel like `/labs`.
  - **LLM-driven document import.** Replace the current "stored as metadata only" fallback for PDF/CSV/XLSX/scanned images with an LLM-driven extraction pipeline. Upload → text extraction (PDF via `PdfPig`, scans via a vision model) → Claude API call with a strict tool-use schema targeting the existing entity types (`LabObservation`, `MedicationRecord`, `ConditionRecord`, `VitalsObservation`, etc.) → schema-validated upsert → "Review Queue" UI for any rows the LLM flagged as low-confidence so the user can confirm / edit before they land in the main tables. No per-format parsers written by hand.
  - Lab unit normalization (mg/dL ↔ mmol/L, etc.) and cross-source trend aggregation by LOINC.
- **Stream D — Diagnostic assistant (the payoff).**
  - Replace the current keyword-matcher `ReadOnlyAssistantService` with a Claude-API-backed agent that has structured tool access to the local DB (`query_labs`, `get_lab_series`, `get_vitals_series`, `get_medications`, `get_timeline`, `get_abnormal_labs`, `reconcile_medications`, `get_visit_prep`, `save_analysis`, `save_note`).
  - Diagnostic-specific reasoning: medication-timeline correlation with lab shifts, cross-source trend synthesis, anomaly flagging grounded in the user's own baselines rather than population norms.
  - `Analyses` and `PersonalNotes` persistence tables — writable surfaces where the agent stores its findings and the user stores annotations, without ever modifying the immutable clinical truths.
  - Human-in-the-loop draft outbound communications (Communication / MessageHeader FHIR resources back to the provider).
- **Stream E — Later / speculative.** Dedicated abnormal-labs dashboard beyond a filter, visit-prep and visit-diff pre-appointment tooling, FHIR Subscription-based proactive alerts, `open.epic.com` Brands Bundle dynamic org discovery, light theme / `mode-watcher`, E2E / component testing infrastructure, `arkiv` export format, sql.js in-browser DB, Capacitor mobile.

**Cross-cutting constraint — data ownership.** Nothing in this spec changes the schema or deletes records. Schema migrations are not required. The existing dev SQLite DB at `~/Library/Application Support/HealthAggregator/healthaggregator-dev.db` should survive this spec without reset.

## Design

### Architecture

**Backend** — no architectural change. Four-project solution stays: `HealthAggregator.Api → .Core + .Data; .Data → .Core`. Additions are strictly additive:

- `HealthAggregator.Api/Services/SmartConfigurationClient.cs` — GETs `<fhir-base>/.well-known/smart-configuration`, caches per-org `(authorize, token)` tuples in a private `ConcurrentDictionary<string, SmartConfig>` for the process lifetime. Wired in DI via `AddHttpClient<SmartConfigurationClient>()`. On fetch failure (non-200, timeout, or malformed JSON missing `authorization_endpoint` / `token_endpoint`), the client throws a `SmartConfigurationException` that `EpicFhirClient.BuildConnectUrlAsync` / `CompleteCallbackAsync` convert into an `EpicConnectResult.Failed("Epic smart-configuration unavailable for <org>: <reason>")` so the error surfaces in the standard UI error path, not a 500.
- `HealthAggregator.Api/Controllers/MedicationsController.cs`, `ConditionsController.cs`, `AllergiesController.cs`, `EncountersController.cs`, `DocumentsController.cs` — each ~50-80 lines, `AsNoTracking` reads, query-param filters, capped at 500 rows ordered newest-first.
- `LabsController` extended with `?source`, `?loinc`, `?abnormal` params. **Abnormal defined precisely:** a lab counts as abnormal if `Interpretation` starts (case-insensitively) with one of `H`, `L`, `A`, `HH`, `LL` **OR** both `NumericValue` and `ReferenceLow`/`ReferenceHigh` are present and `NumericValue < ReferenceLow` or `NumericValue > ReferenceHigh`. An `Interpretation` of `"Normal"` does **not** count as abnormal. `GetSeries` accepts an additional `?source` param.
- `TimelineController` extended with `?kind` (lab | report | condition | medication | allergy | encounter | document), `?source`, `?from`, `?to` server-side filters — and additionally **grows to aggregate `AllergyRecord` and `EncounterRecord`**, which the current controller does not query. The existing client-side filtering of in-memory results is replaced by EF-level filtering. Kind enum values match the `Kind` strings emitted by the existing `TimelineItem` record plus the two new kinds.
- `EpicIntegrationsController` extended with `DELETE /api/integrations/epic/connections/{id}` that removes the `EpicConnection` row but preserves all ingested data.
- `EpicSettings.Organization` config shape simplified: `{ Id, Name, FhirBaseUrl, IsSandbox }`. The previous `AuthorizationEndpoint` / `TokenEndpoint` fields are removed. This requires changes in **three places** that type the organization: the `EpicOrganization` record in `HealthAggregator.Core/Models/EpicOrganization.cs` (positional record parameters drop from six to four), the `EpicSettings.Organizations` list typing in `HealthAggregator.Api/Configuration/EpicSettings.cs`, and the frontend `EpicOrganization` TypeScript type in `healthaggregator-web/src/lib/api.ts` (fields `authorizationEndpoint` and `tokenEndpoint` removed). `EpicFhirClient.BuildConnectUrlAsync` and `CompleteCallbackAsync` are updated to call `SmartConfigurationClient` for those endpoints at connect/callback time.
- `appsettings.json` `Epic.Organizations` grows to three entries:

```json
"Organizations": [
  { "Id": "epic-sandbox",      "Name": "Epic Sandbox",      "FhirBaseUrl": "https://fhir.epic.com/interconnect-fhir-oauth/api/FHIR/R4", "IsSandbox": true },
  { "Id": "cleveland-clinic",  "Name": "Cleveland Clinic",  "FhirBaseUrl": "",  "IsSandbox": false },
  { "Id": "summa-health",      "Name": "Summa Health",      "FhirBaseUrl": "",  "IsSandbox": false }
]
```

The real-org `FhirBaseUrl` values are left blank for the user to fill from the `open.epic.com/Endpoints/R4` Brands Bundle. Blank entries render as non-connectable rows on `/connections` with a small hint.

No EF Core migration is required — no entity shape changes.

**Frontend** — major restructure.

New directory layout under `healthaggregator-web/src/`:

```
src/
├── app.html, app.d.ts                  — unchanged
├── routes/
│   ├── +layout.svelte                  — AppShell + Toaster + NavigationLoader
│   ├── +layout.ts                      — no-op
│   ├── +error.svelte                   — global fallback
│   ├── +page.svelte, +page.ts          — Dashboard
│   ├── connections/+page.svelte, +page.ts
│   ├── imports/+page.svelte, +page.ts
│   ├── labs/+page.svelte, +page.ts
│   ├── vitals/+page.svelte, +page.ts
│   ├── medications/+page.svelte, +page.ts
│   ├── conditions/+page.svelte, +page.ts
│   ├── allergies/+page.svelte, +page.ts
│   ├── encounters/+page.svelte, +page.ts
│   ├── documents/+page.svelte, +page.ts
│   ├── timeline/+page.svelte, +page.ts
│   ├── assistant/+page.svelte, +page.ts
│   └── settings/+page.svelte, +page.ts
├── lib/
│   ├── api.ts                          — extended with new endpoints + optional fetch override
│   ├── components/
│   │   ├── AppShell.svelte             — new
│   │   ├── NavGroup.svelte             — new
│   │   ├── NavItem.svelte              — new
│   │   ├── TopBar.svelte               — new
│   │   ├── PageHeader.svelte           — new
│   │   ├── StatCard.svelte             — new
│   │   ├── SourceBadge.svelte          — new
│   │   ├── ErrorBanner.svelte          — new
│   │   ├── PageBreadcrumb.svelte       — ported
│   │   ├── NavigationLoader.svelte     — ported
│   │   ├── EmptyState.svelte           — ported
│   │   ├── LoadingState.svelte         — ported
│   │   ├── FieldError.svelte           — ported
│   │   ├── ConfirmDialog.svelte        — ported
│   │   ├── data-table/
│   │   │   ├── DataTable.svelte        — new (inspired by DataGrid, simplified)
│   │   │   ├── DataTableSearch.svelte  — new
│   │   │   └── types.ts                — new
│   │   └── ui/
│   │       ├── button/ {button.svelte, variants.ts, index.ts}
│   │       ├── card/ {card.svelte, card-header.svelte, card-title.svelte, card-description.svelte, card-content.svelte, card-footer.svelte, card-action.svelte, index.ts}
│   │       ├── badge/ {badge.svelte, variants.ts, index.ts}
│   │       ├── separator/ {separator.svelte, index.ts}
│   │       ├── input/ {input.svelte, index.ts}
│   │       ├── select/ {… several part files, index.ts}
│   │       ├── tabs/ {… part files, index.ts}
│   │       ├── tooltip/ {… part files, index.ts}
│   │       ├── sonner/ {sonner.svelte, index.ts}
│   │       ├── table/ {table.svelte, table-header.svelte, table-row.svelte, table-cell.svelte, table-head.svelte, index.ts}
│   │       ├── dialog/ {… part files, index.ts}
│   │       ├── dropdown-menu/ {… part files, index.ts}
│   │       ├── combobox/ {… part files, index.ts}
│   │       ├── command/ {… part files, index.ts}
│   │       ├── popover/ {… part files, index.ts}
│   │       └── checkbox/ {checkbox.svelte, index.ts}
│   ├── utils/
│   │   ├── cn.ts                       — `cn(...inputs)` = `twMerge(clsx(inputs))`
│   │   ├── source-theme.ts             — hash-stable color per sourceSystem id
│   │   ├── url-filters.ts              — `updateFilters(patch)` + `goto(..., { replaceState, keepFocus, noScroll })`
│   │   ├── empty-state.ts              — `getEmptyStateProps(entityName, rowCount, filtersActive)`
│   │   └── format.ts                   — `formatDate`, `formatRelativeTime`, `formatDateTime` (moved from inline or new)
│   ├── styles/
│   │   └── tokens.css                  — CSS variables on :root, imported by layout.css
│   └── assets/                         — existing (favicon + logo)
```

New dev deps: `bits-ui ^2.15.5`, `clsx ^2.1.1`, `tailwind-merge ^3.4.0`, `tailwind-variants ^3.2.2`, `svelte-sonner ^1.0.7`, `@tanstack/svelte-table ^9.0.0-alpha.10`, `tw-animate-css ^1.4.0`. No `mode-watcher` (dark-only). No `@tanstack/svelte-query` (raw `api.ts` + load fns cover the use cases here).

### Routing & navigation

13 routes, 4 nav groups:

```
Pinned top:   Dashboard                     (/)
Group 1:      Records
                Labs                        (/labs)
                Vitals                      (/vitals)        — stub
                Medications                 (/medications)
                Conditions                  (/conditions)
                Allergies                   (/allergies)
                Encounters                  (/encounters)
                Documents                   (/documents)
Group 2:      Insights
                Timeline                    (/timeline)
                Assistant                   (/assistant)
Group 3:      Data Sources
                Connections                 (/connections)
                Imports                     (/imports)
Pinned bottom: Settings                     (/settings)      — stub
```

**`AppShell.svelte` structure.** 260px sidebar + content column. Sidebar: brand block → pinned Dashboard → three `NavGroup`s → pinned Settings. Sidebar group open/closed state persisted in `localStorage` under `healthaggregator.nav.groups`, auto-expanded if the current route is inside the group.

**Responsive breakpoints.** ≥1024px: full sidebar always visible. 768-1023px: icon-only rail (~72px) with tooltips on hover; clicking a group icon pops out the full sidebar as an overlay. <768px: hamburger icon in TopBar, sidebar hidden; tap → drawer with backdrop.

**TopBar.** Left: `PageBreadcrumb` derived from `$page.url.pathname` against a route→label map. Right: `{#snippet actions()}` slot that each page can inject into via Svelte context.

**Lucide icon mapping** (per nav item):
- Dashboard → `LayoutDashboard`
- Records group → `FolderHeart`; Labs → `Activity`, Vitals → `HeartPulse`, Medications → `Pill`, Conditions → `Stethoscope`, Allergies → `ShieldAlert`, Encounters → `CalendarClock`, Documents → `FileText`
- Insights group → `Sparkles`; Timeline → `History`, Assistant → `Bot`
- Data Sources group → `Plug`; Connections → `Link`, Imports → `Upload`
- Settings → `Settings`
- Brand mark → existing `HeartPulse`

**Epic OAuth callback handling.** Current flow redirects to `/?epic=connected` or `/?epic=error&reason=…`. To avoid wasting a Dashboard data fetch for a page the user won't see, callback detection lives in `src/routes/+layout.svelte`'s `onMount` (or equivalently a small `src/hooks.client.ts`) and runs *before* the Dashboard `+page.ts` loader completes — it reads the query param, fires the appropriate `toast.success` / `toast.error`, then calls `goto('/connections', { replaceState: true })`. The `?epic=*` params are dropped from the URL by the `goto`. If the redirect happens fast enough, the Dashboard loader's in-flight fetches are cancelled by SvelteKit's navigation logic.

### Component library

**Design tokens** extracted from current `layout.css` :root block into `src/lib/styles/tokens.css`. Token values unchanged. Tokens used by primitives via Tailwind's arbitrary-value syntax: `bg-[var(--card)]`, `border-[var(--border)]`, `text-[var(--foreground)]`.

**`cn()` utility** — standard shadcn pattern:

```ts
import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';
export function cn(...inputs: ClassValue[]) { return twMerge(clsx(inputs)); }
```

**Primitive variants** composed with `tailwind-variants` (`tv()`). Example for `Button`:

```ts
export const buttonVariants = tv({
  base: "inline-flex items-center justify-center rounded-md font-medium transition focus-visible:ring-2 focus-visible:ring-[var(--primary)] disabled:opacity-50",
  variants: {
    variant: {
      default:     "bg-[var(--primary)] text-[var(--primary-foreground)] hover:bg-[var(--primary)]/90",
      secondary:   "bg-[var(--muted)] text-[var(--foreground)] hover:bg-[var(--muted)]/80",
      outline:     "border border-[var(--border)] hover:bg-[var(--muted)]",
      ghost:       "hover:bg-[var(--muted)]",
      destructive: "bg-[var(--danger)] text-white hover:bg-[var(--danger)]/90",
      link:        "underline-offset-4 hover:underline text-[var(--primary)]"
    },
    size: { sm: "h-8 px-3 text-xs", default: "h-9 px-4 text-sm", lg: "h-10 px-6", icon: "h-9 w-9" }
  },
  defaultVariants: { variant: "default", size: "default" }
});
```

**UI primitive roster** — 16 primitives in `src/lib/components/ui/`:

| Primitive | Variants | First use |
|---|---|---|
| `button` | default / secondary / outline / ghost / destructive / link; sm / default / lg / icon | Everywhere |
| `card` | parts: Root, Header, Title, Description, Content, Footer, Action | Dashboard, Settings, every page with sectioned content |
| `badge` | **ported base:** default / secondary / destructive / outline. **Extended in-spec:** `success` (emerald) and `warning` (amber) variants added to `variants.ts` on top of the ported base — the spec's "Flag" column on `/labs`, the `stored-file-metadata` Kind badge on `/imports`, and the kind-coloured timeline badges all depend on these two extra variants. Extensions reuse existing `--success` / `--warning` CSS tokens. | Row-level status indicators, `SourceBadge` wraps |
| `separator` | — | Between nav groups, between card sections |
| `input` | — | Search inputs, Settings forms |
| `select` | — | Status filters, kind filters |
| `tabs` | — | Settings page |
| `tooltip` | — | Nav-rail icons in icon-only mode; abbreviated column headers |
| `sonner` | wrapper around `svelte-sonner` | Root `<Toaster />` in `+layout.svelte` |
| `table` | parts: Root, Header, Row, Cell, Head | DataTable internals |
| `dialog` | — | ConfirmDialog, future settings modals |
| `dropdown-menu` | — | Per-row action menu on DataTable |
| `combobox` | — | Source filter, LOINC filter on `/labs` |
| `command` | — | Transitive dep of combobox; available for future ⌘K palette |
| `popover` | — | Transitive dep; standalone use available |
| `checkbox` | — | Abnormal-only toggle, future multi-select |

**App-level components** in `src/lib/components/` — 8 new + 6 ported, plus a 3-file `data-table/` directory (2 components + 1 types file):

| Component | Source | Responsibility |
|---|---|---|
| `AppShell.svelte` | new | 260px sidebar + responsive drawer + topbar slot; renders `<slot />` main content |
| `NavGroup.svelte` | new | Collapsible sidebar section (icon + title + chevron + children); state persisted in localStorage |
| `NavItem.svelte` | new | One navigable row (href + icon + label + active state); reads `$page.url.pathname` |
| `TopBar.svelte` | new | Breadcrumb left + actions slot right |
| `PageHeader.svelte` | new | Per-page title + subtitle + `{#snippet actions()}` slot |
| `StatCard.svelte` | new | Dashboard stat tile: icon + label + value + subtitle + color accent |
| `SourceBadge.svelte` | new | Small coloured badge showing `SourceName`; colour stable per org via `source-theme.ts` |
| `ErrorBanner.svelte` | new | In-page warning/error banner with variant + dismissible + action slot |
| `PageBreadcrumb.svelte` | ported | Pathname → labelled breadcrumb using a route map |
| `NavigationLoader.svelte` | ported | Top progress bar on navigation |
| `EmptyState.svelte` | ported | Icon + title + description + optional action |
| `LoadingState.svelte` | ported | Centered spinner + message |
| `FieldError.svelte` | ported | Red error text beneath form fields |
| `ConfirmDialog.svelte` | ported | `dialog` wrapper with title + message + confirm/cancel |
| `data-table/DataTable.svelte` | new | Stripped-down DataGrid: paginated, sortable columns, search slot, snippet cells |
| `data-table/DataTableSearch.svelte` | new | Debounced search input with URL param sync |
| `data-table/types.ts` | new | `Column<T>`, `DataTableProps<T>` |

**Source theming — `src/lib/utils/source-theme.ts`.** Hash-stable colour per `sourceSystem` id:

```ts
const LOCKED: Record<string, string> = {
  'cleveland-clinic': 'red',
  'summa-health':     'blue',
  'epic-sandbox':     'grey',
  'manual-upload':    'amber',
};
const FALLBACK_PALETTE = ['purple', 'teal', 'pink', 'orange', 'cyan', 'indigo'];
export function sourceColor(sourceSystem: string): string {
  if (LOCKED[sourceSystem]) return LOCKED[sourceSystem];
  const hash = [...sourceSystem].reduce((a, c) => a + c.charCodeAt(0), 0);
  return FALLBACK_PALETTE[hash % FALLBACK_PALETTE.length];
}
```

Locked colours are reserved up front so the hash fallback can never collide with them. Unknown sources stable-hash into the `FALLBACK_PALETTE`.

### Dashboard (`/`)

Summary view. Loads in parallel: `api.getOrganizations`, `api.getConnections`, `api.getLabs`, `api.getTimeline`. Computes stats client-side. Renders:

1. `PageHeader`: "Overview" + "Your health records at a glance" + Refresh action (calls `invalidateAll()`).
2. 4-tile stat row (`sm:grid-cols-2 lg:grid-cols-4`): Labs total, Abnormal labs, Timeline events, Sources `X / Y`.
3. 2-column grid (`lg:grid-cols-2`) below:
   - Left: `Connection Status` card — one row per configured org with status dot + `SourceBadge` + last sync + per-row Connect/Sync button + `[Manage →]` footer.
   - Right: `Recent Labs` card — top 8 by `EffectiveAt`, columns: test name, value+unit, abnormal chip, `SourceBadge`, relative date. `[View all →]` footer to `/labs`.
4. Full-width `Recent Timeline` card — top 10 from `/api/timeline`, kind badge + title + `SourceBadge` + relative date. `[Open full timeline →]` footer to `/timeline`.

**Empty state.** If `orgs.length === 0 || connections.length === 0`: collapse stats + grid, render a single large `EmptyState` centered with "Connect your first health record" message and a primary CTA `[Connect Epic MyChart →]` linking to `/connections`.

### Records pages

**`/labs`** — full-featured list page:

- `PageHeader` with total count + source count subtitle + Refresh action.
- Filters bar (Card): search input, from/to date inputs, source combobox, abnormal-only checkbox, clear button. Every filter change calls `updateFilters` which `goto`s with patched URL → triggers loader → refetches.
- 2-column grid (3fr / 2fr at `lg:`): left DataTable, right Trend panel.
- DataTable columns: Date (`formatDate`), Test (name + small LOINC beneath), Value (number+unit or text), Ref Range, Flag (Badge `warning` if abnormal), Source (`SourceBadge`). Ref Range column `hideOn: 'md'`.
- Trend panel: a combobox to select a test from currently-loaded labs, then an SVG sparkline from `/api/labs/series`, points colour-coded by source (legend below).
- Empty states from `getEmptyStateProps`: "No labs yet" (no filters, 0 rows) or "No labs match your filters" (filters active, 0 rows).

**`/medications`, `/conditions`, `/allergies`, `/encounters`, `/documents`** — uniform pattern:

- `PageHeader` with count + source count + Refresh.
- Filters bar: search + source combobox (+ status combobox where applicable).
- DataTable: 4-5 entity-specific columns + Source column.
- Standard empty-state branching.

Per-page column sets:

| Page | Columns (in order) |
|---|---|
| `/medications` | Authored date · Medication · Status · **Source** |
| `/conditions` | Onset/Recorded date · Condition · Clinical status · **Source** |
| `/allergies` | Recorded date · Allergen · Clinical status · **Source** |
| `/encounters` | Started · Type · Status · Ended · **Source** |
| `/documents` | Documented date · Type · Status · Content URL · **Source** |

**Null primary-date handling.** When a row's primary date column (`AuthoredAt`, `OnsetAt ?? RecordedAt`, `RecordedAt`, `StartedAt`, `DocumentedAt`, `EffectiveAt`) is null, the DataTable renders an em-dash (`—`) in the cell and sorts those rows to the bottom of the date order. Rows without a date are still included — they're not filtered out.

**`/vitals`** — true stub, filled in by Stream C:
- `PageHeader`: "Vitals".
- `EmptyState`: icon `HeartPulse`, title "Vitals coverage coming in Stream C", description explaining vital-sign `Observation` resources are already being pulled from Epic and stored in raw `SourceRecord` rows, but the structured `VitalsObservation` entity + ingestion path + list/trend UI lands in the next spec. Vitals + medication + lab time-correlation is load-bearing for the diagnostic assistant goal.

### Insights pages

**`/timeline`** — chronological feed:
- `PageHeader` with count across sources.
- Filters: search, kind combobox (all/lab/report/condition/medication/encounter/document), source combobox, from/to dates.
- Virtual-scrollable list (not DataTable — more narrative format).
- Each row: kind icon + date + kind Badge + title + `SourceBadge` + chevron.
- Loads 300 on first render; `[Load more]` fetches the next 300.
- Backend `TimelineController` gains server-side filter params (`kind`, `source`, `from`, `to`) — filters execute on the server, not client-side.

**`/assistant`** — chat + citations:
- `PageHeader`: "Assistant" + "Ask about your labs" + `[New conversation]` action.
- Chat-style layout: messages container + sticky input at bottom.
- Empty state: centered prompt with 4 suggested questions as `Button variant="outline"` chips.
- Each assistant message renders: prose answer + `AssistantCitationList` showing each citation as a row with test name + value + relative date + `SourceBadge` + reference. Citation rows are keyed by `fhirReference`.
- No DB persistence of threads yet — component-local state. `[New conversation]` clears state.
- No loader work; `+page.ts` returns `{}`.

### Data sources pages

**`/connections`** — multi-org connect + per-connection sync:
- `PageHeader` with "X/Y sources connected" subtitle.
- If `Epic:ClientId` not set: full-width `ErrorBanner` variant `warning` with user-secrets command instructions; all Connect buttons disabled.
- `Available Sources` card: one row per configured org (regardless of whether connected). Row content: status dot + `SourceBadge` + org name + last sync + record count + action button (Connect / Sync). Per-row `dropdown-menu`: View details · Re-sync · Disconnect.
- `Recent Sync Details` collapsible card: per-resource breakdown from the most recent `EpicSyncSummary` (resource type, request path, records upserted).
- **Disconnect flow:** `ConfirmDialog` ("Disconnect from {org}? Stored records remain; tokens are cleared.") → `DELETE /api/integrations/epic/connections/{id}` → `toast.success` + `invalidate`.
- **Orgs with blank `FhirBaseUrl`** (Cleveland Clinic, Summa Health until user fills config) render their row with a muted hint: "Configure FHIR base URL in appsettings.json to enable" and the action button disabled.

**`/imports`** — upload + history:
- `PageHeader` with "X manual imports" subtitle.
- 2-column grid (stacks on tablet): left `Upload` card (drag-or-click zone, file types listed), right `History` DataTable.
- On upload success: `toast.success` + `invalidate('/api/imports')`. Most recent summary rendered as a collapsible detail card below the history.
- DataTable columns: Started (date) · File (name) · Kind (Badge: `fhir-json` default, `stored-file-metadata` warning with tooltip "Stored as metadata only — structured parsing for this format isn't wired yet") · Status · Records upserted · Labs upserted.

### Settings page (`/settings`) — stub tabs

Tabs (`tabs` primitive): General · Epic · Data · About.

- **General tab:** `EmptyState` "Preferences coming later".
- **Epic tab:** Card with Client ID set/not-set badge, Callback URL, configured organizations list. `EmptyState` for "Change client id" pointing at the `user-secrets` CLI command.
- **Data tab:** Card showing DB path + size on disk. `EmptyState` for "Export / Archive — Coming in a later update."
- **About tab:** Card with version, repo URL, license, brief description.

Only the Epic tab shows real data (from `/api/integrations/epic/organizations`). Everything else is structural placeholder using `EmptyState`.

### Data loading contract

**API helpers.** `src/lib/api.ts` refactored so every function accepts an optional `FetchLike` first argument defaulting to `fetch`:

```ts
export type FetchLike = typeof fetch;
export const api = {
  baseUrl: API_BASE,
  getLabs: (f: FetchLike = fetch, params?: LabsQuery) =>
    fetchJson<LabObservation[]>(buildUrl('/api/labs', params), undefined, f),
  // ... one per endpoint
  disconnectConnection: (id: number) =>
    fetchJson<{ id: number }>(`/api/integrations/epic/connections/${id}`, { method: 'DELETE' }),
};
```

`+page.ts` loaders pass the SvelteKit-enhanced `fetch`; in-page handlers use the default.

**URL-synced filter params** on every list page. Filter state reads from `url.searchParams` at load, writes back via `updateFilters(patch)` in `src/lib/utils/url-filters.ts`:

```ts
export function updateFilters(patch: Record<string, string | boolean | null | undefined>) {
  const url = new URL(window.location.href);
  for (const [k, v] of Object.entries(patch)) {
    if (v === null || v === undefined || v === '' || v === false) url.searchParams.delete(k);
    else url.searchParams.set(k, String(v));
  }
  goto(url, { replaceState: true, keepFocus: true, noScroll: true });
}
```

`goto` re-triggers the loader → refetches from the API. Filter → URL → loader → data is the single linear path; no parallel `onClick` fetches.

**Ephemeral component state** (not URL-synced): sidebar group expansion (localStorage), Assistant chat messages, DataTable sort direction + current page index, selected trend test on `/labs`.

**Invalidation.** After user-initiated mutations, call `invalidate('/api/...')` to refetch the affected loaders. `invalidateAll()` for the Refresh action.

**Pagination.** Server capped at 500 rows per list endpoint (matches existing Labs behavior). DataTable paginates client-side at 50 rows per visible page. Server-side `?skip`/`?take` is future work.

### Styling & theme

- `tokens.css` imported by `layout.css`. Values unchanged from current.
- Primitives style via Tailwind utilities with arbitrary values referencing CSS variables.
- Dark-only — no `dark:` variants or `mode-watcher`. `:root` keeps `color-scheme: dark`.
- Legacy custom CSS classes in `layout.css` (`.panel`, `.grid`, `.stat`, `.button`, `.badge`, `.timeline`, `.sparkline`, `.sync-summary`, `.assistant-answer`, `.sidebar`, `.nav-item`, `.app-shell`, `.topbar`, `.eyebrow`, `.alert`, `.muted`, `.error`, `.import-list`, responsive `@media` block) kept during migration, deleted at the end of Phase 5.

### Error & loading states

**Navigation progress — `NavigationLoader`.** Registered once in `+layout.svelte`. Shows as a top progress bar during `beforeNavigate` → `afterNavigate`. While a `+page.ts` loader is resolving, the current page stays rendered and the bar signals progress. On cold initial page load it isn't mounted yet, so the browser's own loading indicator covers that moment.

**DataTable refresh skeleton.** `DataTable` accepts a `loading: boolean` prop; when true, renders 5-8 shimmering placeholder rows using `animate-pulse`. Used for in-page refresh only (not initial load).

**Button-level spinner.** Primary action buttons (Sync, Connect, Upload, Ask) accept a `loading?: boolean` prop; when true, show inline spinner + disable.

**Global fallback — `src/routes/+error.svelte`.** Catches load-function throws. Renders inside AppShell with a friendly "Something went wrong" Card, error message, and retry button (`goto(url, { invalidateAll: true })`).

**In-page `ErrorBanner`.** Non-blocking errors (sync fails but rest of page still works) rendered via a dismissible banner at top of the relevant card. Variant-driven (`warning` / `destructive` / `info`).

**Toasts — `svelte-sonner`.** Fire-and-forget feedback: upload success/failure, sync success/failure, disconnect success, assistant errors, Epic-callback outcomes.

### Multi-organization source attribution

**Already in schema** — every typed table's uniqueness tuple starts with `SourceSystem`. No changes.

**Config** — three orgs in `appsettings.json`. Real-org `FhirBaseUrl` blanks are user-filled.

**Discovery** — `SmartConfigurationClient` resolves per-org authorize/token URLs at connect time. Cached in-process.

**UI surfaces** — `SourceBadge` appears on every list row, chart point, timeline entry, and assistant citation. Colour is stable per org via `source-theme.ts`. Combobox "Source" filter on every list page, populated from `/api/integrations/epic/organizations` + "Manual Upload".

**Patient identity** — single-user assumption: every `PatientRecord` is treated as the user. No cross-org patient linking table yet.

## Testing

### Backend — xUnit

New test files under `HealthAggregator.Tests/`:

1. `MedicationsControllerTests.cs` — happy path + filter paths (`search`, `status`, `from`, `to`, `source`) + ordering + source filter.
2. `ConditionsControllerTests.cs` — same shape.
3. `AllergiesControllerTests.cs` — same shape (minus from/to).
4. `EncountersControllerTests.cs` — same shape.
5. `DocumentsControllerTests.cs` — same shape.
6. `LabsControllerTests.cs` (new file) — tests for new `?loinc`, `?source`, `?abnormal` params. Existing `?search`/`?from`/`?to` behaviour also covered.
7. `TimelineControllerTests.cs` (new file) — tests for new `?kind`, `?source`, `?from`, `?to` server-side filters.
8. `SmartConfigurationClientTests.cs` — mocked `HttpMessageHandler` returning canned JSON; verifies (authorize, token) extraction, caching, malformed-response handling.
9. `EpicIntegrationsControllerTests.cs` (new file) — disconnect test: row removed, ingested records preserved.

Existing `FhirImportServiceTests` and `EpicSyncSummaryTests` unchanged; re-run for regression.

### Frontend

- `pnpm exec svelte-check --threshold error` — 0 errors required after every task completion.
- No automated UI tests this spec. `data-testid` attributes baked in on every new interactive element for future retrofit.

### Manual verification

End-of-phase smoke checklists:

**After Phase 2:** hit each new endpoint via browser (`https://localhost:5310/api/medications`, etc.) against a sandbox-synced DB; expect non-empty JSON arrays.

**After Phase 3:** click through every nav item; verify AppShell responsive breakpoints collapse as designed (≥1024 full, 768-1023 rail+tooltip, <768 drawer).

**After each page in Phase 4:** manual walk-through confirming the page's data displays correctly with source badges, filters update URL and reload restores, and actions work end-to-end (Connect, Sync, Upload, Disconnect, Ask).

**Final gate:** fresh DB (`rm ~/Library/Application\ Support/HealthAggregator/healthaggregator-dev.db`), run start-dev.sh, connect Epic sandbox, sync, walk all 13 pages. `dotnet build && dotnet test --no-build` green. `pnpm exec svelte-check --threshold error` zero.

## Implementation order

**Phase 1 — Foundation deps + primitive library.** New deps installed; ports + new primitives landed; tokens extracted. Verified by rendering every primitive in a throwaway `src/routes/_preview/+page.svelte` sandbox (deleted at phase end).

**Phase 2 — Backend additions.** `SmartConfigurationClient`, config simplification, 5 new controllers, extended Labs/Timeline controllers, disconnect endpoint, all xUnit tests. `dotnet test` green.

**Phase 3 — Routing skeleton + AppShell wiring.** `+layout.svelte` rewrites to AppShell; 13 route stubs created; old `+page.svelte` renamed to `+page.svelte.bak` (kept as reference). Global `+error.svelte`. Nav breakpoint verification.

**Phase 4 — Page-by-page rebuild.** One page per subagent task, in this order:
1. `/` Dashboard
2. `/connections`
3. `/labs`
4. `/timeline`
5. `/medications` · `/conditions` · `/allergies` · `/encounters` · `/documents` (one task each)
6. `/imports`
7. `/assistant`
8. `/vitals` (stub)
9. `/settings` (stub)

Each task ends with svelte-check green + manual smoke of that page.

**Phase 5 — Cleanup.** Delete `+page.svelte.bak`; delete obsolete class blocks from `layout.css`; final full-app smoke on a fresh DB. Final verification gate.

## Files changed summary

### Backend

- `HealthAggregator.Api/Program.cs` — register `SmartConfigurationClient` in DI
- `HealthAggregator.Core/Models/EpicOrganization.cs` — drop `AuthorizationEndpoint` and `TokenEndpoint` positional record parameters (six → four)
- `HealthAggregator.Api/Configuration/EpicSettings.cs` — `Organizations` list typing now matches the simplified record
- `HealthAggregator.Api/appsettings.json` — three Organizations entries
- `HealthAggregator.Api/Services/SmartConfigurationClient.cs` (new) + `SmartConfigurationException` (new, same file)
- `HealthAggregator.Api/Services/EpicFhirClient.cs` — use `SmartConfigurationClient` for endpoint discovery in both `BuildConnectUrlAsync` and `CompleteCallbackAsync`; map `SmartConfigurationException` to `EpicConnectResult.Failed`
- `HealthAggregator.Api/Controllers/EpicIntegrationsController.cs` — add `DELETE /api/integrations/epic/connections/{id}`
- `HealthAggregator.Api/Controllers/LabsController.cs` — add `source`/`loinc`/`abnormal` params with the precise abnormal semantics defined above
- `HealthAggregator.Api/Controllers/TimelineController.cs` — add server-side filter params + extend to query `AllergyRecord` and `EncounterRecord` so the `?kind=allergy` and `?kind=encounter` filters have something to match
- `HealthAggregator.Api/Controllers/MedicationsController.cs` (new)
- `HealthAggregator.Api/Controllers/ConditionsController.cs` (new)
- `HealthAggregator.Api/Controllers/AllergiesController.cs` (new)
- `HealthAggregator.Api/Controllers/EncountersController.cs` (new)
- `HealthAggregator.Api/Controllers/DocumentsController.cs` (new)
- `HealthAggregator.Tests/*.cs` — new test classes per §Testing

### Frontend

- `healthaggregator-web/package.json` — new dev deps listed in §Architecture
- `healthaggregator-web/src/routes/+layout.svelte` — rewritten to mount AppShell + Toaster + NavigationLoader
- `healthaggregator-web/src/routes/+layout.ts` (new, no-op)
- `healthaggregator-web/src/routes/+error.svelte` (new, global fallback)
- `healthaggregator-web/src/routes/+page.svelte` — replaced with Dashboard summary implementation
- `healthaggregator-web/src/routes/+page.ts` (new)
- `healthaggregator-web/src/routes/<each-feature>/+page.svelte` + `+page.ts` (new × 12)
- `healthaggregator-web/src/routes/layout.css` — legacy class blocks deleted at end of Phase 5
- `healthaggregator-web/src/lib/api.ts` — fetch-override + new endpoints + `EpicOrganization` TypeScript type drops `authorizationEndpoint` and `tokenEndpoint` fields to match the backend record change
- `healthaggregator-web/src/lib/components/...` — full roster per §Architecture
- `healthaggregator-web/src/lib/utils/{cn,source-theme,url-filters,empty-state,format}.ts` (new)
- `healthaggregator-web/src/lib/styles/tokens.css` (new)
