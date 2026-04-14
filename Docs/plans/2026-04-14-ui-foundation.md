# UI Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL — use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **Every subagent dispatch must include the spec at `Docs/specs/2026-04-14-ui-foundation-design.md` as context** — many tasks reference spec sections by name rather than re-duplicating code already there.

**Goal:** Restructure the single-page dashboard into a 13-route multi-page SvelteKit app with an EdiPlatform-style AppShell, add 5 thin backend controllers + filter extensions + multi-org OAuth endpoint discovery, and bake source-attribution into every list, chart, and citation.

**Architecture:** Targeted port of shadcn-svelte primitives (bits-ui backed) + 8 new HealthAggregator-specific components. Client-side `+page.ts` loaders with URL-synced filter state. Backend stays .NET 10 + EF Core SQLite with 5 new `AsNoTracking` list controllers following the existing `LabsController` pattern. No schema changes (no EF migration needed).

**Tech Stack:** .NET 10 Web API, EF Core SQLite, SvelteKit 2, Svelte 5 runes, Tailwind 4, bits-ui, tailwind-variants, svelte-sonner, xUnit.

**Build discipline:** Run `dotnet build` once at the start of each backend task, then use `dotnet test --no-build` for every subsequent test run in that task. `pnpm exec svelte-check --threshold error` must show 0 errors at the end of every frontend task.

---

## File change map

### Backend (new files)
- `HealthAggregator.Api/Services/SmartConfigurationClient.cs` (+ `SmartConfigurationException` in same file)
- `HealthAggregator.Api/Controllers/MedicationsController.cs`
- `HealthAggregator.Api/Controllers/ConditionsController.cs`
- `HealthAggregator.Api/Controllers/AllergiesController.cs`
- `HealthAggregator.Api/Controllers/EncountersController.cs`
- `HealthAggregator.Api/Controllers/DocumentsController.cs`
- `HealthAggregator.Tests/SmartConfigurationClientTests.cs`
- `HealthAggregator.Tests/MedicationsControllerTests.cs`
- `HealthAggregator.Tests/ConditionsControllerTests.cs`
- `HealthAggregator.Tests/AllergiesControllerTests.cs`
- `HealthAggregator.Tests/EncountersControllerTests.cs`
- `HealthAggregator.Tests/DocumentsControllerTests.cs`
- `HealthAggregator.Tests/LabsControllerTests.cs`
- `HealthAggregator.Tests/TimelineControllerTests.cs`
- `HealthAggregator.Tests/EpicIntegrationsControllerTests.cs`
- `HealthAggregator.Tests/TestDb.cs` (shared in-memory EF context helper)

### Backend (modified)
- `HealthAggregator.Api/Program.cs`
- `HealthAggregator.Core/Models/EpicOrganization.cs`
- `HealthAggregator.Api/Configuration/EpicSettings.cs`
- `HealthAggregator.Api/appsettings.json`
- `HealthAggregator.Api/Services/EpicFhirClient.cs`
- `HealthAggregator.Api/Controllers/LabsController.cs`
- `HealthAggregator.Api/Controllers/TimelineController.cs`
- `HealthAggregator.Api/Controllers/EpicIntegrationsController.cs`

### Frontend (new files)
- `healthaggregator-web/src/routes/+layout.ts`
- `healthaggregator-web/src/routes/+error.svelte`
- 12 route pairs: `healthaggregator-web/src/routes/<route>/+page.svelte` + `+page.ts` for `connections`, `imports`, `labs`, `vitals`, `medications`, `conditions`, `allergies`, `encounters`, `documents`, `timeline`, `assistant`, `settings`
- `healthaggregator-web/src/routes/+page.ts` (new, for Dashboard)
- `healthaggregator-web/src/lib/styles/tokens.css`
- `healthaggregator-web/src/lib/utils/{cn,source-theme,url-filters,empty-state,format}.ts`
- `healthaggregator-web/src/lib/components/{AppShell,NavGroup,NavItem,TopBar,PageHeader,StatCard,SourceBadge,ErrorBanner}.svelte`
- `healthaggregator-web/src/lib/components/{EmptyState,LoadingState,FieldError,PageBreadcrumb,NavigationLoader,ConfirmDialog}.svelte` (ported)
- `healthaggregator-web/src/lib/components/data-table/{DataTable,DataTableSearch}.svelte` + `types.ts`
- `healthaggregator-web/src/lib/components/ui/` — 16 primitive directories (each with `*.svelte` parts + `index.ts` + optional `variants.ts`)

### Frontend (modified)
- `healthaggregator-web/package.json` — new dev deps
- `healthaggregator-web/src/routes/+layout.svelte` — rewritten to mount AppShell + Toaster + NavigationLoader
- `healthaggregator-web/src/routes/+page.svelte` — replaced with Dashboard summary implementation
- `healthaggregator-web/src/routes/layout.css` — token block delegates to `tokens.css`; legacy classes deleted at Phase 5
- `healthaggregator-web/src/lib/api.ts` — fetch-override + new endpoints + `EpicOrganization` type drops 2 fields

### Frontend (deleted at Phase 5)
- Legacy class blocks inside `src/routes/layout.css` (`.panel`, `.grid-2`, `.grid-3`, `.stat`, `.button`, `.badge`, `.timeline`, `.sparkline`, `.sync-summary`, `.assistant-answer`, `.sidebar`, `.nav-item`, `.app-shell`, `.topbar`, `.eyebrow`, `.alert`, `.muted`, `.error`, `.import-list`, responsive `@media` block)
- `src/routes/+page.svelte.bak` (the renamed original monolith)

---

# Phase 1 — Foundation deps + primitive library + new components

### Task 1: Install new dev dependencies

**Files:**
- Modify: `healthaggregator-web/package.json`

- [ ] **Step 1: Install deps**

```bash
cd healthaggregator-web
pnpm add -D bits-ui@^2.15.5 clsx@^2.1.1 tailwind-merge@^3.4.0 tailwind-variants@^3.2.2 svelte-sonner@^1.0.7 @tanstack/svelte-table@^9.0.0-alpha.10 tw-animate-css@^1.4.0
```

Expected: 7 new entries in `devDependencies`; no version conflicts; `pnpm-lock.yaml` updated.

- [ ] **Step 2: Verify svelte-check still green**

Run: `pnpm exec svelte-check --threshold error`
Expected: `0 errors`.

- [ ] **Step 3: Commit**

```bash
git add healthaggregator-web/package.json healthaggregator-web/pnpm-lock.yaml
git commit -m "Install shadcn-svelte primitive deps for UI foundation work"
```

---

### Task 2: Extract design tokens + add `cn` utility

**Files:**
- Create: `healthaggregator-web/src/lib/styles/tokens.css`
- Create: `healthaggregator-web/src/lib/utils/cn.ts`
- Modify: `healthaggregator-web/src/routes/layout.css`

- [ ] **Step 1: Create `tokens.css` with the `:root` block from spec §Design → Styling & theme**

File content:

```css
:root {
	--background: #09090b;
	--foreground: #fafafa;
	--card: #141416;
	--card-foreground: #fafafa;
	--muted: #1a1a1d;
	--muted-foreground: #a1a1aa;
	--border: #27272a;
	--primary: #3b82f6;
	--primary-foreground: #eff6ff;
	--success: #22c55e;
	--warning: #f59e0b;
	--danger: #ef4444;
	--radius: 8px;
}
```

- [ ] **Step 2: Import tokens.css from layout.css**

Add `@import '$lib/styles/tokens.css';` immediately after the existing `@plugin '@tailwindcss/typography';` line in `src/routes/layout.css`. Remove the duplicate `:root` block that's currently in layout.css (lines 4-21 of current file). **Do not remove** the Inter font-family or `color-scheme: dark` line — move those to a new `:root` continuation block in layout.css that only contains presentation (font-family, color-scheme) rather than tokens.

- [ ] **Step 3: Create cn.ts**

File content (verbatim from spec §Design → Component library):

```ts
import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
	return twMerge(clsx(inputs));
}
```

- [ ] **Step 4: Verify svelte-check green**

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors.

- [ ] **Step 5: Commit**

```bash
git add healthaggregator-web/src/lib/styles/tokens.css healthaggregator-web/src/lib/utils/cn.ts healthaggregator-web/src/routes/layout.css
git commit -m "Extract design tokens + cn() utility for shadcn-style primitive composition"
```

---

### Task 3: Remaining utility files

**Files:**
- Create: `healthaggregator-web/src/lib/utils/source-theme.ts`
- Create: `healthaggregator-web/src/lib/utils/url-filters.ts`
- Create: `healthaggregator-web/src/lib/utils/empty-state.ts`
- Create: `healthaggregator-web/src/lib/utils/format.ts`

- [ ] **Step 1: Write `source-theme.ts` (verbatim from spec §Design → Component library → Source theming)**

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

/** Tailwind class for a solid coloured SourceBadge pill. */
export function sourceBadgeClasses(sourceSystem: string): string {
	const color = sourceColor(sourceSystem);
	const map: Record<string, string> = {
		red:    'bg-red-500/15 text-red-300 border-red-500/30',
		blue:   'bg-blue-500/15 text-blue-300 border-blue-500/30',
		grey:   'bg-zinc-500/15 text-zinc-300 border-zinc-500/30',
		amber:  'bg-amber-500/15 text-amber-300 border-amber-500/30',
		purple: 'bg-purple-500/15 text-purple-300 border-purple-500/30',
		teal:   'bg-teal-500/15 text-teal-300 border-teal-500/30',
		pink:   'bg-pink-500/15 text-pink-300 border-pink-500/30',
		orange: 'bg-orange-500/15 text-orange-300 border-orange-500/30',
		cyan:   'bg-cyan-500/15 text-cyan-300 border-cyan-500/30',
		indigo: 'bg-indigo-500/15 text-indigo-300 border-indigo-500/30',
	};
	return map[color] ?? map.grey;
}
```

- [ ] **Step 2: Write `url-filters.ts` (verbatim from spec §Design → Data loading contract)**

```ts
import { goto } from '$app/navigation';

export function updateFilters(patch: Record<string, string | boolean | null | undefined>) {
	const url = new URL(window.location.href);
	for (const [k, v] of Object.entries(patch)) {
		if (v === null || v === undefined || v === '' || v === false) url.searchParams.delete(k);
		else url.searchParams.set(k, String(v));
	}
	goto(url, { replaceState: true, keepFocus: true, noScroll: true });
}
```

- [ ] **Step 3: Write `empty-state.ts`**

```ts
import type { Component } from 'svelte';

export type EmptyStateProps = {
	title: string;
	description: string;
	actionLabel?: string;
	actionHref?: string;
};

/**
 * Returns Props for the EmptyState component that branch on why the list is empty.
 * entityName: singular, lower-case, e.g. "medication", "lab".
 */
export function getEmptyStateProps(
	entityName: string,
	rowCount: number,
	filtersActive: boolean
): EmptyStateProps {
	if (rowCount > 0) throw new Error('getEmptyStateProps called with non-empty list');
	if (filtersActive) {
		return {
			title: `No ${entityName} match your filters`,
			description: 'Try widening the date range or clearing a filter.',
			actionLabel: 'Clear filters',
			actionHref: '?'
		};
	}
	return {
		title: `No ${entityName} yet`,
		description: 'Sync your Epic connections to pull records.',
		actionLabel: 'Manage Connections',
		actionHref: '/connections'
	};
}
```

- [ ] **Step 4: Write `format.ts`**

```ts
export function formatDate(dateStr: string | null | undefined): string {
	if (!dateStr) return '—';
	const d = new Date(dateStr);
	if (isNaN(d.getTime())) return '—';
	return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatDateTime(dateStr: string | null | undefined): string {
	if (!dateStr) return '—';
	const d = new Date(dateStr);
	if (isNaN(d.getTime())) return '—';
	return d.toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
}

export function formatRelativeTime(dateStr: string | null | undefined): string {
	if (!dateStr) return '—';
	const d = new Date(dateStr);
	if (isNaN(d.getTime())) return '—';
	const diffMs = Date.now() - d.getTime();
	const minutes = Math.round(diffMs / 60000);
	if (minutes < 1) return 'just now';
	if (minutes < 60) return `${minutes}m ago`;
	const hours = Math.round(minutes / 60);
	if (hours < 24) return `${hours}h ago`;
	const days = Math.round(hours / 24);
	if (days < 30) return `${days}d ago`;
	return formatDate(dateStr);
}
```

- [ ] **Step 5: Verify svelte-check green**

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add healthaggregator-web/src/lib/utils/
git commit -m "Add frontend utilities: source-theme, url-filters, empty-state, format"
```

---

### Task 4: Port UI primitives batch 1 — button, card, separator

**Files:**
- Copy from `/Users/blackcolours/dev/work/EdiPlatform/ediplatform-web/src/lib/components/ui/`
- Create: `healthaggregator-web/src/lib/components/ui/button/` (entire dir), `card/` (entire dir), `separator/` (entire dir)

- [ ] **Step 1: Copy primitive directories**

```bash
mkdir -p healthaggregator-web/src/lib/components/ui
cp -R /Users/blackcolours/dev/work/EdiPlatform/ediplatform-web/src/lib/components/ui/button healthaggregator-web/src/lib/components/ui/
cp -R /Users/blackcolours/dev/work/EdiPlatform/ediplatform-web/src/lib/components/ui/card healthaggregator-web/src/lib/components/ui/
cp -R /Users/blackcolours/dev/work/EdiPlatform/ediplatform-web/src/lib/components/ui/separator healthaggregator-web/src/lib/components/ui/
```

- [ ] **Step 2: Fix import paths inside copied files**

Each copied file likely imports `$lib/utils.js` for `cn()`. Our utility lives at `$lib/utils/cn.ts` — update imports inside every copied `.svelte` and `.ts` file under those three directories. Use this one-liner:

```bash
# macOS sed requires empty string after -i
find healthaggregator-web/src/lib/components/ui/{button,card,separator} -type f \( -name '*.svelte' -o -name '*.ts' \) -exec sed -i '' "s|\\\$lib/utils\\.js|\\\$lib/utils/cn|g; s|\\\$lib/utils\"|\\\$lib/utils/cn\"|g" {} +
```

Then manually check each `index.ts` and fix any remaining imports that sed missed.

- [ ] **Step 3: Verify svelte-check green**

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors. If errors, they'll point to import paths or missing peer deps — fix individually.

- [ ] **Step 4: Commit**

```bash
git add healthaggregator-web/src/lib/components/ui/{button,card,separator}
git commit -m "Port button, card, separator primitives from EdiPlatform"
```

---

### Task 5: Port UI primitives batch 2 — badge (+extend), input, checkbox

**Files:**
- Create: `healthaggregator-web/src/lib/components/ui/badge/` (copy + extend variants)
- Create: `healthaggregator-web/src/lib/components/ui/input/` (copy)
- Create: `healthaggregator-web/src/lib/components/ui/checkbox/` (copy)

- [ ] **Step 1: Copy the three directories + fix imports**

```bash
cp -R /Users/blackcolours/dev/work/EdiPlatform/ediplatform-web/src/lib/components/ui/badge healthaggregator-web/src/lib/components/ui/
cp -R /Users/blackcolours/dev/work/EdiPlatform/ediplatform-web/src/lib/components/ui/input healthaggregator-web/src/lib/components/ui/
cp -R /Users/blackcolours/dev/work/EdiPlatform/ediplatform-web/src/lib/components/ui/checkbox healthaggregator-web/src/lib/components/ui/
find healthaggregator-web/src/lib/components/ui/{badge,input,checkbox} -type f \( -name '*.svelte' -o -name '*.ts' \) -exec sed -i '' "s|\\\$lib/utils\\.js|\\\$lib/utils/cn|g; s|\\\$lib/utils\"|\\\$lib/utils/cn\"|g" {} +
```

- [ ] **Step 2: Extend `badge/variants.ts` with `success` and `warning`**

Read the copied `badge/variants.ts`. The file uses `tailwind-variants` `tv()` with a `variants.variant` map. Add two new variants inside the `variant` object:

```ts
success:     'border-transparent bg-[var(--success)]/15 text-emerald-300 [a&]:hover:bg-[var(--success)]/25',
warning:     'border-transparent bg-[var(--warning)]/15 text-amber-300 [a&]:hover:bg-[var(--warning)]/25',
```

These follow the shadcn convention of semi-transparent tinted backgrounds with saturated text colours. Update the TypeScript `BadgeVariant` type union if the file declares one — it should now include `'success' | 'warning'`.

- [ ] **Step 3: Write a smoke test page for badge**

Create throwaway `src/routes/_preview/+page.svelte` to render every badge variant:

```svelte
<script lang="ts">
	import { Badge } from '$lib/components/ui/badge';
</script>

<div class="p-8 space-y-4">
	<h1 class="text-2xl">Badge preview</h1>
	<div class="flex gap-2 flex-wrap">
		<Badge variant="default">default</Badge>
		<Badge variant="secondary">secondary</Badge>
		<Badge variant="success">success</Badge>
		<Badge variant="warning">warning</Badge>
		<Badge variant="destructive">destructive</Badge>
		<Badge variant="outline">outline</Badge>
	</div>
</div>
```

- [ ] **Step 4: Verify in dev server + svelte-check**

Run: `./scripts/start-dev.sh` (background), open `https://localhost:5373/_preview`, visually confirm all 6 badges render with distinct colours matching the token palette (blue default, grey secondary, emerald success, amber warning, red destructive, outlined).

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors.

- [ ] **Step 5: Remove the preview page (kept only during Phase 1 verification)**

```bash
rm -rf healthaggregator-web/src/routes/_preview
```

- [ ] **Step 6: Commit**

```bash
git add healthaggregator-web/src/lib/components/ui/{badge,input,checkbox}
git commit -m "Port badge (+success/warning variants), input, checkbox primitives"
```

---

### Task 6: Port UI primitives batch 3 — select, tabs, tooltip

**Files:**
- Create: `healthaggregator-web/src/lib/components/ui/{select,tabs,tooltip}/`

- [ ] **Step 1: Copy + fix imports**

```bash
for dir in select tabs tooltip; do
  cp -R /Users/blackcolours/dev/work/EdiPlatform/ediplatform-web/src/lib/components/ui/$dir healthaggregator-web/src/lib/components/ui/
done
find healthaggregator-web/src/lib/components/ui/{select,tabs,tooltip} -type f \( -name '*.svelte' -o -name '*.ts' \) -exec sed -i '' "s|\\\$lib/utils\\.js|\\\$lib/utils/cn|g; s|\\\$lib/utils\"|\\\$lib/utils/cn\"|g" {} +
```

- [ ] **Step 2: svelte-check**

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors.

- [ ] **Step 3: Commit**

```bash
git add healthaggregator-web/src/lib/components/ui/{select,tabs,tooltip}
git commit -m "Port select, tabs, tooltip primitives"
```

---

### Task 7: Port UI primitives batch 4 — dialog, popover, dropdown-menu, command, combobox

**Files:**
- Create: `healthaggregator-web/src/lib/components/ui/{dialog,popover,dropdown-menu,command,combobox}/`

- [ ] **Step 1: Copy + fix imports**

```bash
for dir in dialog popover dropdown-menu command combobox; do
  cp -R /Users/blackcolours/dev/work/EdiPlatform/ediplatform-web/src/lib/components/ui/$dir healthaggregator-web/src/lib/components/ui/
done
find healthaggregator-web/src/lib/components/ui/{dialog,popover,dropdown-menu,command,combobox} -type f \( -name '*.svelte' -o -name '*.ts' \) -exec sed -i '' "s|\\\$lib/utils\\.js|\\\$lib/utils/cn|g; s|\\\$lib/utils\"|\\\$lib/utils/cn\"|g" {} +
```

- [ ] **Step 2: svelte-check**

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors. These primitives have transitive deps — the skill's `bits-ui` exports should cover them but any missing imports surface here.

- [ ] **Step 3: Commit**

```bash
git add healthaggregator-web/src/lib/components/ui/{dialog,popover,dropdown-menu,command,combobox}
git commit -m "Port dialog, popover, dropdown-menu, command, combobox primitives"
```

---

### Task 8: Port UI primitives batch 5 — sonner (+ wrap), table

**Files:**
- Create: `healthaggregator-web/src/lib/components/ui/{sonner,table}/`

- [ ] **Step 1: Copy + fix imports**

```bash
cp -R /Users/blackcolours/dev/work/EdiPlatform/ediplatform-web/src/lib/components/ui/sonner healthaggregator-web/src/lib/components/ui/
cp -R /Users/blackcolours/dev/work/EdiPlatform/ediplatform-web/src/lib/components/ui/table healthaggregator-web/src/lib/components/ui/
find healthaggregator-web/src/lib/components/ui/{sonner,table} -type f \( -name '*.svelte' -o -name '*.ts' \) -exec sed -i '' "s|\\\$lib/utils\\.js|\\\$lib/utils/cn|g; s|\\\$lib/utils\"|\\\$lib/utils/cn\"|g" {} +
```

- [ ] **Step 2: svelte-check**

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors.

- [ ] **Step 3: Commit**

```bash
git add healthaggregator-web/src/lib/components/ui/{sonner,table}
git commit -m "Port sonner (toast wrapper) and table primitives"
```

---

### Task 9: Port app-level components — EmptyState, LoadingState, FieldError, PageBreadcrumb, NavigationLoader, ConfirmDialog

**Files:**
- Create: `healthaggregator-web/src/lib/components/{EmptyState,LoadingState,FieldError,PageBreadcrumb,NavigationLoader,ConfirmDialog}.svelte`

- [ ] **Step 1: Copy the six ports**

```bash
for f in EmptyState.svelte LoadingState.svelte FieldError.svelte PageBreadcrumb.svelte NavigationLoader.svelte ConfirmDialog.svelte; do
  cp /Users/blackcolours/dev/work/EdiPlatform/ediplatform-web/src/lib/components/$f healthaggregator-web/src/lib/components/
done
```

- [ ] **Step 2: Fix import paths**

Each file may import from `$lib/utils.js`, `$lib/components/ui/*`, or EdiPlatform-specific helpers. Fix:

```bash
find healthaggregator-web/src/lib/components/{EmptyState,LoadingState,FieldError,PageBreadcrumb,NavigationLoader,ConfirmDialog}.svelte -type f -exec sed -i '' "s|\\\$lib/utils\\.js|\\\$lib/utils/cn|g" {} +
```

Open each file and check imports. Remove any EdiPlatform-only dependencies (e.g., SignalR hooks, customer-context stores). PageBreadcrumb should be trimmed if it references an EdiPlatform-specific route map — replace with a HealthAggregator route→label map derived from the nav tree in spec §Design → Routing & navigation (13 routes). Include this map inline in `PageBreadcrumb.svelte`:

```ts
const ROUTE_LABELS: Record<string, string> = {
	'/':             'Overview',
	'/connections':  'Connections',
	'/imports':      'Imports',
	'/labs':         'Labs',
	'/vitals':       'Vitals',
	'/medications':  'Medications',
	'/conditions':   'Conditions',
	'/allergies':    'Allergies',
	'/encounters':   'Encounters',
	'/documents':    'Documents',
	'/timeline':     'Timeline',
	'/assistant':    'Assistant',
	'/settings':     'Settings',
};
const SECTION_LABELS: Record<string, string> = {
	'/labs': 'Records', '/vitals': 'Records', '/medications': 'Records',
	'/conditions': 'Records', '/allergies': 'Records', '/encounters': 'Records',
	'/documents': 'Records',
	'/timeline': 'Insights', '/assistant': 'Insights',
	'/connections': 'Data Sources', '/imports': 'Data Sources',
};
```

Render as: `{SECTION_LABELS[path] ?? ''} / {ROUTE_LABELS[path]}` with ` / ` hidden if section is empty.

- [ ] **Step 3: svelte-check**

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors.

- [ ] **Step 4: Commit**

```bash
git add healthaggregator-web/src/lib/components/{EmptyState,LoadingState,FieldError,PageBreadcrumb,NavigationLoader,ConfirmDialog}.svelte
git commit -m "Port EmptyState, LoadingState, FieldError, PageBreadcrumb, NavigationLoader, ConfirmDialog"
```

---

### Task 10: New component — `SourceBadge`

**Files:**
- Create: `healthaggregator-web/src/lib/components/SourceBadge.svelte`

- [ ] **Step 1: Write SourceBadge**

```svelte
<script lang="ts">
	import { cn } from '$lib/utils/cn';
	import { sourceBadgeClasses } from '$lib/utils/source-theme';

	let { sourceSystem, sourceName, class: className = '' }: {
		sourceSystem: string;
		sourceName?: string;
		class?: string;
	} = $props();

	const label = $derived(sourceName ?? sourceSystem);
</script>

<span
	data-testid={`source-badge-${sourceSystem}`}
	class={cn('inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium', sourceBadgeClasses(sourceSystem), className)}
>
	{label}
</span>
```

- [ ] **Step 2: svelte-check**

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors.

- [ ] **Step 3: Commit**

```bash
git add healthaggregator-web/src/lib/components/SourceBadge.svelte
git commit -m "Add SourceBadge component for multi-org attribution on every data row"
```

---

### Task 11: New components — `ErrorBanner` and `StatCard`

**Files:**
- Create: `healthaggregator-web/src/lib/components/ErrorBanner.svelte`
- Create: `healthaggregator-web/src/lib/components/StatCard.svelte`

- [ ] **Step 1: Write ErrorBanner**

```svelte
<script lang="ts">
	import { cn } from '$lib/utils/cn';
	import { AlertTriangle, AlertCircle, Info, X } from '@lucide/svelte';
	import type { Snippet } from 'svelte';

	let { variant = 'warning', title, dismissible = false, onDismiss, children, action }: {
		variant?: 'warning' | 'destructive' | 'info';
		title: string;
		dismissible?: boolean;
		onDismiss?: () => void;
		children?: Snippet;
		action?: Snippet;
	} = $props();

	const ICONS = { warning: AlertTriangle, destructive: AlertCircle, info: Info };
	const Icon = $derived(ICONS[variant]);

	const variantClasses = {
		warning:     'bg-[var(--warning)]/10 border-[var(--warning)]/30 text-amber-200',
		destructive: 'bg-[var(--danger)]/10 border-[var(--danger)]/30 text-red-200',
		info:        'bg-[var(--primary)]/10 border-[var(--primary)]/30 text-blue-200',
	};
</script>

<div
	data-testid={`error-banner-${variant}`}
	class={cn('flex items-start gap-3 rounded-lg border px-4 py-3', variantClasses[variant])}
>
	<Icon size={18} class="mt-0.5 shrink-0" />
	<div class="flex-1 min-w-0">
		<div class="font-medium">{title}</div>
		{#if children}
			<div class="text-sm mt-1 opacity-90">{@render children()}</div>
		{/if}
	</div>
	{#if action}
		<div class="shrink-0">{@render action()}</div>
	{/if}
	{#if dismissible}
		<button
			type="button"
			onclick={onDismiss}
			data-testid="error-banner-dismiss"
			class="shrink-0 opacity-70 hover:opacity-100"
			aria-label="Dismiss"
		><X size={16} /></button>
	{/if}
</div>
```

- [ ] **Step 2: Write StatCard**

```svelte
<script lang="ts">
	import { cn } from '$lib/utils/cn';
	import * as Card from '$lib/components/ui/card';
	import type { Component } from 'svelte';

	let { icon: IconComp, label, value, subtitle, accent = 'primary', href, testid }: {
		icon: Component;
		label: string;
		value: string | number;
		subtitle?: string;
		accent?: 'primary' | 'success' | 'warning' | 'muted';
		href?: string;
		testid?: string;
	} = $props();

	const accentBg = {
		primary: 'bg-[var(--primary)]/15 text-[var(--primary)]',
		success: 'bg-[var(--success)]/15 text-emerald-300',
		warning: 'bg-[var(--warning)]/15 text-amber-300',
		muted:   'bg-[var(--muted)] text-[var(--muted-foreground)]',
	};
</script>

{#snippet body()}
	<Card.Header class="flex flex-row items-center justify-between">
		<Card.Title class="text-sm font-medium text-[var(--muted-foreground)]">{label}</Card.Title>
		<div class={cn('flex h-9 w-9 items-center justify-center rounded-xl', accentBg[accent])}>
			<IconComp size={18} />
		</div>
	</Card.Header>
	<Card.Content>
		<div class="text-2xl font-semibold text-[var(--foreground)]">{value}</div>
		{#if subtitle}<div class="text-xs text-[var(--muted-foreground)] mt-1">{subtitle}</div>{/if}
	</Card.Content>
{/snippet}

{#if href}
	<a href={href} data-testid={testid} class="block transition hover:-translate-y-0.5">
		<Card.Root>
			{@render body()}
		</Card.Root>
	</a>
{:else}
	<div data-testid={testid}>
		<Card.Root>
			{@render body()}
		</Card.Root>
	</div>
{/if}
```

- [ ] **Step 3: svelte-check + commit**

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors.

```bash
git add healthaggregator-web/src/lib/components/{ErrorBanner,StatCard}.svelte
git commit -m "Add ErrorBanner (page-level warning) and StatCard (dashboard stat tile)"
```

---

### Task 12: New component — `PageHeader`

**Files:**
- Create: `healthaggregator-web/src/lib/components/PageHeader.svelte`

- [ ] **Step 1: Write PageHeader**

```svelte
<script lang="ts">
	import type { Snippet } from 'svelte';

	let { title, subtitle, actions }: {
		title: string;
		subtitle?: string;
		actions?: Snippet;
	} = $props();
</script>

<header data-testid="page-header" class="flex flex-wrap items-start justify-between gap-4 mb-6">
	<div class="min-w-0">
		<h1 data-testid="page-header-title" class="text-2xl font-semibold text-[var(--foreground)]">{title}</h1>
		{#if subtitle}
			<p data-testid="page-header-subtitle" class="text-sm text-[var(--muted-foreground)] mt-1">{subtitle}</p>
		{/if}
	</div>
	{#if actions}
		<div class="flex flex-wrap items-center gap-2">
			{@render actions()}
		</div>
	{/if}
</header>
```

- [ ] **Step 2: svelte-check + commit**

```bash
cd healthaggregator-web && pnpm exec svelte-check --threshold error
git add healthaggregator-web/src/lib/components/PageHeader.svelte
git commit -m "Add PageHeader component with title, subtitle, and actions slot"
```

---

### Task 13: New components — `NavItem`, `NavGroup`, `TopBar`

**Files:**
- Create: `healthaggregator-web/src/lib/components/{NavItem,NavGroup,TopBar}.svelte`

- [ ] **Step 1: Write `NavItem.svelte`**

```svelte
<script lang="ts">
	import { page } from '$app/stores';
	import { cn } from '$lib/utils/cn';
	import type { Component } from 'svelte';

	let { href, icon: IconComp, label, testid }: {
		href: string;
		icon: Component;
		label: string;
		testid?: string;
	} = $props();

	const active = $derived($page.url.pathname === href);
</script>

<a
	href={href}
	data-testid={testid ?? `nav-${label.toLowerCase().replace(/\s+/g, '-')}`}
	class={cn(
		'flex items-center gap-2.5 rounded-md px-3 py-1.5 text-sm transition',
		active
			? 'bg-[var(--primary)]/15 text-[var(--primary)] font-medium'
			: 'text-[var(--muted-foreground)] hover:bg-[var(--muted)] hover:text-[var(--foreground)]'
	)}
>
	<IconComp size={16} />
	<span class="truncate">{label}</span>
</a>
```

- [ ] **Step 2: Write `NavGroup.svelte`**

```svelte
<script lang="ts">
	import { cn } from '$lib/utils/cn';
	import { ChevronDown } from '@lucide/svelte';
	import { onMount } from 'svelte';
	import type { Component, Snippet } from 'svelte';

	let { id, icon: IconComp, title, children }: {
		id: string;
		icon: Component;
		title: string;
		children: Snippet;
	} = $props();

	let expanded = $state(true);

	onMount(() => {
		const stored = localStorage.getItem(`healthaggregator.nav.${id}`);
		if (stored !== null) expanded = stored === 'true';
	});

	function toggle() {
		expanded = !expanded;
		localStorage.setItem(`healthaggregator.nav.${id}`, String(expanded));
	}
</script>

<div data-testid={`nav-group-${id}`} class="mt-3">
	<button
		type="button"
		onclick={toggle}
		data-testid={`nav-group-toggle-${id}`}
		class="flex w-full items-center justify-between px-3 py-1.5 text-xs font-semibold uppercase tracking-wide text-[var(--muted-foreground)] hover:text-[var(--foreground)]"
	>
		<span class="flex items-center gap-2"><IconComp size={14} /> {title}</span>
		<ChevronDown size={14} class={cn('transition', !expanded && '-rotate-90')} />
	</button>
	{#if expanded}
		<div class="mt-1 space-y-0.5">{@render children()}</div>
	{/if}
</div>
```

- [ ] **Step 3: Write `TopBar.svelte`**

```svelte
<script lang="ts">
	import PageBreadcrumb from './PageBreadcrumb.svelte';
	import type { Snippet } from 'svelte';

	let { actions }: { actions?: Snippet } = $props();
</script>

<div data-testid="topbar" class="flex h-14 items-center justify-between border-b border-[var(--border)] px-6">
	<PageBreadcrumb />
	{#if actions}
		<div class="flex items-center gap-2">{@render actions()}</div>
	{/if}
</div>
```

- [ ] **Step 4: svelte-check + commit**

```bash
cd healthaggregator-web && pnpm exec svelte-check --threshold error
git add healthaggregator-web/src/lib/components/{NavItem,NavGroup,TopBar}.svelte
git commit -m "Add NavItem, NavGroup, TopBar primitives for AppShell composition"
```

---

### Task 14: New component — `AppShell`

**Files:**
- Create: `healthaggregator-web/src/lib/components/AppShell.svelte`

- [ ] **Step 1: Write AppShell**

```svelte
<script lang="ts">
	import NavItem from './NavItem.svelte';
	import NavGroup from './NavGroup.svelte';
	import TopBar from './TopBar.svelte';
	import {
		LayoutDashboard, FolderHeart, Activity, HeartPulse, Pill, Stethoscope, ShieldAlert,
		CalendarClock, FileText, Sparkles, History, Bot, Plug, Link as LinkIcon,
		Upload, Settings, Menu, X
	} from '@lucide/svelte';
	import { onMount } from 'svelte';
	import type { Snippet } from 'svelte';

	let { children, actions }: { children: Snippet; actions?: Snippet } = $props();

	let mobileOpen = $state(false);
	let isMobile = $state(false);

	onMount(() => {
		const mq = window.matchMedia('(max-width: 767px)');
		const handler = () => (isMobile = mq.matches);
		handler();
		mq.addEventListener('change', handler);
		return () => mq.removeEventListener('change', handler);
	});
</script>

<div data-testid="app-shell" class="min-h-screen bg-[var(--background)] md:grid md:grid-cols-[260px_minmax(0,1fr)]">
	<aside
		data-testid="sidebar"
		class="flex flex-col border-r border-[var(--border)] bg-[#101114] {isMobile && !mobileOpen ? 'hidden' : ''} {isMobile ? 'fixed inset-y-0 left-0 z-40 w-72' : 'sticky top-0 h-screen'}"
	>
		<div class="flex items-center justify-between p-5">
			<a href="/" data-testid="brand-link" class="flex items-center gap-3">
				<div class="flex h-9 w-9 items-center justify-center rounded-xl bg-[var(--primary)]/15 text-[var(--primary)]">
					<HeartPulse size={19} />
				</div>
				<div class="min-w-0">
					<div class="font-semibold text-[var(--foreground)]">HealthAggregator</div>
					<div class="text-xs text-[var(--muted-foreground)]">Local-first records</div>
				</div>
			</a>
			{#if isMobile}
				<button type="button" onclick={() => (mobileOpen = false)} data-testid="sidebar-close" class="text-[var(--muted-foreground)]"><X size={18} /></button>
			{/if}
		</div>

		<nav class="flex-1 overflow-y-auto px-3 pb-4" aria-label="Primary">
			<NavItem href="/" icon={LayoutDashboard} label="Dashboard" />

			<NavGroup id="records" icon={FolderHeart} title="Records">
				<NavItem href="/labs" icon={Activity} label="Labs" />
				<NavItem href="/vitals" icon={HeartPulse} label="Vitals" />
				<NavItem href="/medications" icon={Pill} label="Medications" />
				<NavItem href="/conditions" icon={Stethoscope} label="Conditions" />
				<NavItem href="/allergies" icon={ShieldAlert} label="Allergies" />
				<NavItem href="/encounters" icon={CalendarClock} label="Encounters" />
				<NavItem href="/documents" icon={FileText} label="Documents" />
			</NavGroup>

			<NavGroup id="insights" icon={Sparkles} title="Insights">
				<NavItem href="/timeline" icon={History} label="Timeline" />
				<NavItem href="/assistant" icon={Bot} label="Assistant" />
			</NavGroup>

			<NavGroup id="sources" icon={Plug} title="Data Sources">
				<NavItem href="/connections" icon={LinkIcon} label="Connections" />
				<NavItem href="/imports" icon={Upload} label="Imports" />
			</NavGroup>
		</nav>

		<div class="border-t border-[var(--border)] px-3 py-3">
			<NavItem href="/settings" icon={Settings} label="Settings" />
		</div>
	</aside>

	{#if isMobile && mobileOpen}
		<button
			type="button"
			aria-label="Close menu"
			onclick={() => (mobileOpen = false)}
			data-testid="sidebar-backdrop"
			class="fixed inset-0 z-30 bg-black/50"
		></button>
	{/if}

	<div class="flex min-h-screen flex-col">
		<TopBar {actions}>
			{#snippet actions()}
				{#if isMobile}
					<button type="button" onclick={() => (mobileOpen = !mobileOpen)} data-testid="sidebar-open" class="mr-2 text-[var(--muted-foreground)]"><Menu size={18} /></button>
				{/if}
				{#if actions}{@render actions()}{/if}
			{/snippet}
		</TopBar>
		<main data-testid="content" class="flex-1 px-6 py-6">
			{@render children()}
		</main>
	</div>
</div>
```

- [ ] **Step 2: svelte-check + commit**

```bash
cd healthaggregator-web && pnpm exec svelte-check --threshold error
git add healthaggregator-web/src/lib/components/AppShell.svelte
git commit -m "Add AppShell — sidebar + responsive drawer + topbar for the new multi-page layout"
```

---

### Task 15: New component — `DataTable` + `DataTableSearch` + types

**Files:**
- Create: `healthaggregator-web/src/lib/components/data-table/types.ts`
- Create: `healthaggregator-web/src/lib/components/data-table/DataTable.svelte`
- Create: `healthaggregator-web/src/lib/components/data-table/DataTableSearch.svelte`

- [ ] **Step 1: Write `types.ts`**

```ts
import type { Snippet } from 'svelte';

export type Column<T> = {
	id: string;
	header: string;
	/** When set, the column is hidden at/below that Tailwind breakpoint. */
	hideOn?: 'sm' | 'md' | 'lg';
	/** Render function for the cell. Gets the row. */
	cell: Snippet<[T]>;
	/** Optional accessor for sorting purposes. If omitted, column is not sortable. */
	sortBy?: (row: T) => string | number | Date | null | undefined;
	widthClass?: string;
};

export type DataTableProps<T> = {
	columns: Column<T>[];
	data: T[];
	pageSize?: number;
	loading?: boolean;
	empty?: Snippet;
};
```

- [ ] **Step 2: Write `DataTable.svelte`**

```svelte
<script lang="ts" generics="T">
	import { cn } from '$lib/utils/cn';
	import * as Table from '$lib/components/ui/table';
	import { ChevronUp, ChevronDown } from '@lucide/svelte';
	import type { Column, DataTableProps } from './types';

	let { columns, data, pageSize = 50, loading = false, empty }: DataTableProps<T> = $props();

	let sortId = $state<string | null>(null);
	let sortDir = $state<'asc' | 'desc'>('desc');
	let pageIndex = $state(0);

	const sorted = $derived.by(() => {
		if (!sortId) return data;
		const col = columns.find(c => c.id === sortId);
		if (!col?.sortBy) return data;
		const by = col.sortBy;
		return [...data].sort((a, b) => {
			const av = by(a); const bv = by(b);
			if (av == null && bv == null) return 0;
			if (av == null) return 1;  // nulls to bottom
			if (bv == null) return -1;
			const cmp = av < bv ? -1 : av > bv ? 1 : 0;
			return sortDir === 'asc' ? cmp : -cmp;
		});
	});

	const pageCount = $derived(Math.max(1, Math.ceil(sorted.length / pageSize)));
	const start = $derived(pageIndex * pageSize);
	const rows = $derived(sorted.slice(start, start + pageSize));

	const HIDE_CLASS: Record<string, string> = {
		sm: 'hidden sm:table-cell',
		md: 'hidden md:table-cell',
		lg: 'hidden lg:table-cell',
	};

	function setSort(col: Column<T>) {
		if (!col.sortBy) return;
		if (sortId === col.id) {
			sortDir = sortDir === 'asc' ? 'desc' : 'asc';
		} else {
			sortId = col.id;
			sortDir = 'desc';
		}
	}
</script>

{#if loading}
	<div data-testid="data-table-loading" class="space-y-2">
		{#each Array(6) as _, i (i)}
			<div class="h-10 animate-pulse rounded bg-[var(--muted)]"></div>
		{/each}
	</div>
{:else if data.length === 0 && empty}
	{@render empty()}
{:else}
	<div data-testid="data-table" class="overflow-hidden rounded-lg border border-[var(--border)]">
		<Table.Root>
			<Table.Header>
				<Table.Row>
					{#each columns as col (col.id)}
						<Table.Head class={cn(col.hideOn && HIDE_CLASS[col.hideOn], col.widthClass)}>
							{#if col.sortBy}
								<button type="button" onclick={() => setSort(col)} class="flex items-center gap-1 hover:text-[var(--foreground)]">
									{col.header}
									{#if sortId === col.id}
										{#if sortDir === 'asc'}<ChevronUp size={12} />{:else}<ChevronDown size={12} />{/if}
									{/if}
								</button>
							{:else}
								{col.header}
							{/if}
						</Table.Head>
					{/each}
				</Table.Row>
			</Table.Header>
			<Table.Body>
				{#each rows as row, i (i)}
					<Table.Row>
						{#each columns as col (col.id)}
							<Table.Cell class={cn(col.hideOn && HIDE_CLASS[col.hideOn])}>
								{@render col.cell(row)}
							</Table.Cell>
						{/each}
					</Table.Row>
				{/each}
			</Table.Body>
		</Table.Root>
	</div>

	{#if pageCount > 1}
		<div data-testid="data-table-pagination" class="flex items-center justify-between mt-3 text-sm text-[var(--muted-foreground)]">
			<span>{start + 1}-{Math.min(start + pageSize, sorted.length)} of {sorted.length}</span>
			<div class="flex gap-2">
				<button type="button" onclick={() => (pageIndex = Math.max(0, pageIndex - 1))} disabled={pageIndex === 0} data-testid="data-table-prev" class="rounded border border-[var(--border)] px-3 py-1 disabled:opacity-50">Prev</button>
				<button type="button" onclick={() => (pageIndex = Math.min(pageCount - 1, pageIndex + 1))} disabled={pageIndex >= pageCount - 1} data-testid="data-table-next" class="rounded border border-[var(--border)] px-3 py-1 disabled:opacity-50">Next</button>
			</div>
		</div>
	{/if}
{/if}
```

- [ ] **Step 3: Write `DataTableSearch.svelte`**

```svelte
<script lang="ts">
	import { Input } from '$lib/components/ui/input';
	import { Search } from '@lucide/svelte';
	import { updateFilters } from '$lib/utils/url-filters';

	let { value = '', placeholder = 'Search...', paramName = 'search' }: {
		value?: string;
		placeholder?: string;
		paramName?: string;
	} = $props();

	let local = $state(value);
	let timer: ReturnType<typeof setTimeout> | null = null;

	$effect(() => {
		local = value;
	});

	function onInput(e: Event) {
		local = (e.target as HTMLInputElement).value;
		if (timer) clearTimeout(timer);
		timer = setTimeout(() => updateFilters({ [paramName]: local || null }), 300);
	}
</script>

<div class="relative">
	<Search size={14} class="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--muted-foreground)] pointer-events-none" />
	<Input
		type="text"
		value={local}
		oninput={onInput}
		placeholder={placeholder}
		data-testid="data-table-search"
		class="pl-9"
	/>
</div>
```

- [ ] **Step 4: svelte-check + commit**

```bash
cd healthaggregator-web && pnpm exec svelte-check --threshold error
git add healthaggregator-web/src/lib/components/data-table/
git commit -m "Add DataTable with sortable columns + client pagination + DataTableSearch"
```

---

### Task 16 (Phase 1 gate): Preview sandbox verification

**Files:**
- Create (temporary, deleted at end): `healthaggregator-web/src/routes/_preview/+page.svelte`

- [ ] **Step 1: Write a preview page that renders a sample of every component**

Render Button (all variants + sizes), Card.Root with header/content, Badge (all 6 variants), Separator, Input, Checkbox, Select, Tabs, Tooltip, Dialog (trigger + open), Dropdown-menu, Combobox, Popover, Sonner (a button that fires toast), Table via DataTable (with 3-row sample data), EmptyState, LoadingState, FieldError, PageBreadcrumb, NavigationLoader, ConfirmDialog, SourceBadge (4 known sources + 2 unknown), ErrorBanner (each variant), StatCard (each accent).

- [ ] **Step 2: Run dev server + visually verify**

```bash
./scripts/start-dev.sh &
```

Open `https://localhost:5373/_preview`. Click through every interactive primitive. Every variant should render distinctly, be accessible by keyboard, and respect the dark theme. `SourceBadge` colors should be stable between refreshes.

- [ ] **Step 3: Delete the preview page**

```bash
rm -rf healthaggregator-web/src/routes/_preview
```

- [ ] **Step 4: svelte-check + commit**

```bash
cd healthaggregator-web && pnpm exec svelte-check --threshold error
git add healthaggregator-web/src/routes/_preview 2>/dev/null || true
git commit --allow-empty -m "Phase 1 gate: primitive library + new components verified via preview sandbox"
```

---

# Phase 2 — Backend additions

### Task 17: Shared test DB helper

**Files:**
- Create: `HealthAggregator.Tests/TestDb.cs`

- [ ] **Step 1: Write TestDb.cs**

```csharp
using HealthAggregator.Data;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Tests;

public static class TestDb
{
    public static HealthAggregatorDbContext CreateInMemory()
    {
        var connection = new SqliteConnection("DataSource=:memory:");
        connection.Open();
        var options = new DbContextOptionsBuilder<HealthAggregatorDbContext>()
            .UseSqlite(connection)
            .Options;
        var db = new HealthAggregatorDbContext(options);
        db.Database.EnsureCreated();
        return db;
    }
}
```

- [ ] **Step 2: Verify build**

```bash
dotnet build
```

Expected: Build succeeded, 0 errors, 0 warnings.

- [ ] **Step 3: Commit**

```bash
git add HealthAggregator.Tests/TestDb.cs
git commit -m "Add TestDb.CreateInMemory helper for controller tests"
```

---

### Task 18: `SmartConfigurationClient` + tests

**Files:**
- Create: `HealthAggregator.Api/Services/SmartConfigurationClient.cs`
- Create: `HealthAggregator.Tests/SmartConfigurationClientTests.cs`

- [ ] **Step 1: Write the failing test**

```csharp
// HealthAggregator.Tests/SmartConfigurationClientTests.cs
using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using HealthAggregator.Api.Services;

namespace HealthAggregator.Tests;

public sealed class SmartConfigurationClientTests
{
    [Fact]
    public async Task ResolveAsync_returns_endpoints_from_well_known_document()
    {
        var handler = new FakeHandler(HttpStatusCode.OK, """
            {
              "authorization_endpoint": "https://fhir.example.org/oauth2/authorize",
              "token_endpoint": "https://fhir.example.org/oauth2/token",
              "code_challenge_methods_supported": ["S256"]
            }
            """);
        var http = new HttpClient(handler);
        var client = new SmartConfigurationClient(http);

        var config = await client.ResolveAsync("https://fhir.example.org/FHIR/R4", CancellationToken.None);

        Assert.Equal("https://fhir.example.org/oauth2/authorize", config.AuthorizationEndpoint);
        Assert.Equal("https://fhir.example.org/oauth2/token", config.TokenEndpoint);
    }

    [Fact]
    public async Task ResolveAsync_caches_per_fhir_base_url()
    {
        var handler = new FakeHandler(HttpStatusCode.OK, """
            {"authorization_endpoint":"a","token_endpoint":"t"}
            """);
        var client = new SmartConfigurationClient(new HttpClient(handler));

        await client.ResolveAsync("https://fhir.x/R4", CancellationToken.None);
        await client.ResolveAsync("https://fhir.x/R4", CancellationToken.None);

        Assert.Equal(1, handler.RequestCount);
    }

    [Fact]
    public async Task ResolveAsync_throws_SmartConfigurationException_on_non_200()
    {
        var handler = new FakeHandler(HttpStatusCode.NotFound, "not found");
        var client = new SmartConfigurationClient(new HttpClient(handler));

        await Assert.ThrowsAsync<SmartConfigurationException>(() =>
            client.ResolveAsync("https://fhir.x/R4", CancellationToken.None));
    }

    [Fact]
    public async Task ResolveAsync_throws_when_fields_missing()
    {
        var handler = new FakeHandler(HttpStatusCode.OK, """
            {"authorization_endpoint":"a"}
            """);
        var client = new SmartConfigurationClient(new HttpClient(handler));

        await Assert.ThrowsAsync<SmartConfigurationException>(() =>
            client.ResolveAsync("https://fhir.x/R4", CancellationToken.None));
    }

    private sealed class FakeHandler(HttpStatusCode status, string body) : HttpMessageHandler
    {
        public int RequestCount { get; private set; }
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken ct)
        {
            RequestCount++;
            return Task.FromResult(new HttpResponseMessage(status) { Content = new StringContent(body) });
        }
    }
}
```

- [ ] **Step 2: Verify test fails**

Run: `dotnet build 2>&1 | tail -5`
Expected: build error referencing `SmartConfigurationClient` type not found (or similar).

- [ ] **Step 3: Write implementation**

```csharp
// HealthAggregator.Api/Services/SmartConfigurationClient.cs
using System.Collections.Concurrent;
using System.Net.Http.Json;
using System.Text.Json.Serialization;

namespace HealthAggregator.Api.Services;

public sealed class SmartConfigurationClient(HttpClient httpClient)
{
    private readonly ConcurrentDictionary<string, SmartConfiguration> _cache = new();

    public async Task<SmartConfiguration> ResolveAsync(string fhirBaseUrl, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(fhirBaseUrl))
        {
            throw new SmartConfigurationException("FhirBaseUrl is empty; configure it in appsettings before connecting.");
        }

        if (_cache.TryGetValue(fhirBaseUrl, out var cached))
        {
            return cached;
        }

        var url = fhirBaseUrl.TrimEnd('/') + "/.well-known/smart-configuration";
        HttpResponseMessage response;
        try
        {
            response = await httpClient.GetAsync(url, cancellationToken);
        }
        catch (Exception ex)
        {
            throw new SmartConfigurationException($"Network error fetching smart-configuration from {url}: {ex.Message}", ex);
        }

        if (!response.IsSuccessStatusCode)
        {
            throw new SmartConfigurationException($"smart-configuration returned {(int)response.StatusCode} for {url}.");
        }

        SmartConfigurationDocument? doc;
        try
        {
            doc = await response.Content.ReadFromJsonAsync<SmartConfigurationDocument>(cancellationToken);
        }
        catch (Exception ex)
        {
            throw new SmartConfigurationException($"smart-configuration at {url} returned invalid JSON: {ex.Message}", ex);
        }

        if (doc is null || string.IsNullOrWhiteSpace(doc.AuthorizationEndpoint) || string.IsNullOrWhiteSpace(doc.TokenEndpoint))
        {
            throw new SmartConfigurationException($"smart-configuration at {url} is missing authorization_endpoint or token_endpoint.");
        }

        var config = new SmartConfiguration(doc.AuthorizationEndpoint, doc.TokenEndpoint);
        _cache[fhirBaseUrl] = config;
        return config;
    }

    private sealed class SmartConfigurationDocument
    {
        [JsonPropertyName("authorization_endpoint")] public string? AuthorizationEndpoint { get; set; }
        [JsonPropertyName("token_endpoint")] public string? TokenEndpoint { get; set; }
    }
}

public sealed record SmartConfiguration(string AuthorizationEndpoint, string TokenEndpoint);

public sealed class SmartConfigurationException(string message, Exception? inner = null) : Exception(message, inner);
```

- [ ] **Step 4: Verify tests pass**

```bash
dotnet build && dotnet test --no-build --filter "FullyQualifiedName~SmartConfigurationClientTests"
```

Expected: 4 passed.

- [ ] **Step 5: Register in DI**

Edit `HealthAggregator.Api/Program.cs`. Find the line `builder.Services.AddHttpClient<EpicFhirClient>();` and add immediately above it:

```csharp
builder.Services.AddHttpClient<SmartConfigurationClient>();
```

Run `dotnet build` — should succeed.

- [ ] **Step 6: Commit**

```bash
git add HealthAggregator.Api/Services/SmartConfigurationClient.cs HealthAggregator.Tests/SmartConfigurationClientTests.cs HealthAggregator.Api/Program.cs
git commit -m "Add SmartConfigurationClient for per-org OAuth endpoint discovery via .well-known/smart-configuration"
```

---

### Task 19: Simplify `EpicOrganization` record + `EpicSettings` + `appsettings.json`

**Files:**
- Modify: `HealthAggregator.Core/Models/EpicOrganization.cs`
- Modify: `HealthAggregator.Api/Configuration/EpicSettings.cs`
- Modify: `HealthAggregator.Api/appsettings.json`

- [ ] **Step 1: Read current `EpicOrganization.cs` and simplify**

The current record (6 positional params) becomes 4. Rewrite the file:

```csharp
namespace HealthAggregator.Core.Models;

public sealed record EpicOrganization(
    string Id,
    string Name,
    string FhirBaseUrl,
    bool IsSandbox);
```

- [ ] **Step 2: `EpicSettings.cs` — verify compiles**

`EpicSettings.cs` references `List<EpicOrganization>` — should still compile since we only dropped positional params. Run `dotnet build` to verify.

- [ ] **Step 3: Update `appsettings.json`**

Replace the `"Epic": { "Organizations": [...] }` block with:

```json
  "Epic": {
    "ClientId": "",
    "FrontendBaseUrl": "https://localhost:5373",
    "CallbackBaseUrl": "",
    "CallbackPath": "/api/integrations/epic/callback",
    "Organizations": [
      { "Id": "epic-sandbox",     "Name": "Epic Sandbox",     "FhirBaseUrl": "https://fhir.epic.com/interconnect-fhir-oauth/api/FHIR/R4", "IsSandbox": true  },
      { "Id": "cleveland-clinic", "Name": "Cleveland Clinic", "FhirBaseUrl": "", "IsSandbox": false },
      { "Id": "summa-health",     "Name": "Summa Health",     "FhirBaseUrl": "", "IsSandbox": false }
    ]
  },
```

- [ ] **Step 4: dotnet build — expect failure in `EpicFhirClient`**

```bash
dotnet build 2>&1 | grep -E "error|warning" | head -10
```

Expected: errors because `EpicFhirClient` references `organization.AuthorizationEndpoint` and `organization.TokenEndpoint`, which no longer exist. Do NOT fix in this task — Task 20 is the fix.

- [ ] **Step 5: Commit the partial change (build broken — acknowledge in commit msg)**

```bash
git add HealthAggregator.Core/Models/EpicOrganization.cs HealthAggregator.Api/appsettings.json
git commit -m "Simplify EpicOrganization record to 4 fields; add Cleveland Clinic + Summa Health to config

Build will be broken until EpicFhirClient is updated to use SmartConfigurationClient
in the next task. Committed separately for reviewable history."
```

---

### Task 20: Wire `EpicFhirClient` to use `SmartConfigurationClient`

**Files:**
- Modify: `HealthAggregator.Api/Services/EpicFhirClient.cs`

- [ ] **Step 1: Inject `SmartConfigurationClient`**

Update the constructor to include the new service:

```csharp
public sealed class EpicFhirClient(
    HttpClient httpClient,
    HealthAggregatorDbContext db,
    FhirImportService importer,
    SmartConfigurationClient smartConfig,
    IOptions<EpicSettings> settings)
```

- [ ] **Step 2: Replace `organization.AuthorizationEndpoint` in `BuildConnectUrlAsync`**

Find the line `return EpicConnectResult.Ready(organization, QueryHelpers.AddQueryString(organization.AuthorizationEndpoint, query));` (around line 80).

Above the existing `db.EpicAuthorizationStates.Add(...)` call, add:

```csharp
SmartConfiguration discovered;
try
{
    discovered = await smartConfig.ResolveAsync(organization.FhirBaseUrl, cancellationToken);
}
catch (SmartConfigurationException ex)
{
    return EpicConnectResult.Failed(organization, $"Smart configuration unavailable for {organization.Name}: {ex.Message}");
}
```

Then change the final line to use `discovered.AuthorizationEndpoint`:

```csharp
return EpicConnectResult.Ready(organization, QueryHelpers.AddQueryString(discovered.AuthorizationEndpoint, query));
```

- [ ] **Step 3: Replace `organization.TokenEndpoint` in `CompleteCallbackAsync`**

Find the `using var response = await httpClient.PostAsync(organization.TokenEndpoint, ...);` call. Above it add:

```csharp
var discovered = await smartConfig.ResolveAsync(organization.FhirBaseUrl, cancellationToken);
```

Change the POST target:

```csharp
using var response = await httpClient.PostAsync(discovered.TokenEndpoint, new FormUrlEncodedContent(...));
```

- [ ] **Step 4: Add `EpicConnectResult.Failed` static factory if it doesn't exist**

Read the `EpicConnectResult` type (likely in the same file or `HealthAggregator.Core`). If no `Failed` method exists, add one alongside `Ready` / `MissingClientId`:

```csharp
public static EpicConnectResult Failed(EpicOrganization organization, string error) =>
    new(organization, null, error, false);
```

Match the existing constructor signature (Organization / AuthorizationUrl / Error / ReadyToAuthorize) — adjust argument order if needed.

- [ ] **Step 5: Verify build + tests pass**

```bash
dotnet build && dotnet test --no-build
```

Expected: 0 errors; existing tests still pass.

- [ ] **Step 6: Commit**

```bash
git add HealthAggregator.Api/Services/EpicFhirClient.cs
git commit -m "EpicFhirClient discovers authorize/token endpoints via SmartConfigurationClient"
```

---

### Task 21: `MedicationsController` + tests

**Files:**
- Create: `HealthAggregator.Api/Controllers/MedicationsController.cs`
- Create: `HealthAggregator.Tests/MedicationsControllerTests.cs`

- [ ] **Step 1: Write failing tests**

```csharp
// HealthAggregator.Tests/MedicationsControllerTests.cs
using HealthAggregator.Api.Controllers;
using HealthAggregator.Data.Entities;
using Microsoft.AspNetCore.Mvc;

namespace HealthAggregator.Tests;

public sealed class MedicationsControllerTests
{
    [Fact]
    public async Task GetMedications_returns_rows_ordered_by_AuthoredAt_desc()
    {
        using var db = TestDb.CreateInMemory();
        db.Medications.AddRange(
            new MedicationRecord { SourceSystem = "cleveland-clinic", SourceName = "Cleveland", ResourceId = "1", FhirReference = "MedicationRequest/1", MedicationText = "Metformin", Status = "active", AuthoredAt = new DateTimeOffset(2024, 3, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow },
            new MedicationRecord { SourceSystem = "summa-health", SourceName = "Summa", ResourceId = "2", FhirReference = "MedicationRequest/2", MedicationText = "Atorvastatin", Status = "active", AuthoredAt = new DateTimeOffset(2025, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();

        var controller = new MedicationsController(db);
        var result = await controller.GetMedications(null, null, null, null, null, CancellationToken.None) as OkObjectResult;

        Assert.NotNull(result);
        var rows = Assert.IsAssignableFrom<List<MedicationRecord>>(result!.Value);
        Assert.Equal(2, rows.Count);
        Assert.Equal("Atorvastatin", rows[0].MedicationText);
    }

    [Fact]
    public async Task GetMedications_filters_by_search()
    {
        using var db = TestDb.CreateInMemory();
        db.Medications.AddRange(
            new MedicationRecord { SourceSystem = "a", SourceName = "", ResourceId = "1", FhirReference = "a/1", MedicationText = "Metformin 500mg", Status = "active", AuthoredAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow },
            new MedicationRecord { SourceSystem = "a", SourceName = "", ResourceId = "2", FhirReference = "a/2", MedicationText = "Atorvastatin", Status = "active", AuthoredAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();

        var controller = new MedicationsController(db);
        var result = await controller.GetMedications("Metformin", null, null, null, null, CancellationToken.None) as OkObjectResult;

        var rows = Assert.IsAssignableFrom<List<MedicationRecord>>(result!.Value);
        Assert.Single(rows);
        Assert.Contains("Metformin", rows[0].MedicationText!);
    }

    [Fact]
    public async Task GetMedications_filters_by_source_and_status()
    {
        using var db = TestDb.CreateInMemory();
        db.Medications.AddRange(
            new MedicationRecord { SourceSystem = "cleveland-clinic", SourceName = "", ResourceId = "1", FhirReference = "a/1", MedicationText = "A", Status = "active", AuthoredAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow },
            new MedicationRecord { SourceSystem = "summa-health", SourceName = "", ResourceId = "2", FhirReference = "b/2", MedicationText = "B", Status = "active", AuthoredAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow },
            new MedicationRecord { SourceSystem = "cleveland-clinic", SourceName = "", ResourceId = "3", FhirReference = "a/3", MedicationText = "C", Status = "stopped", AuthoredAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();

        var controller = new MedicationsController(db);
        var sourceResult = await controller.GetMedications(null, null, null, null, "cleveland-clinic", CancellationToken.None) as OkObjectResult;
        var sourceRows = Assert.IsAssignableFrom<List<MedicationRecord>>(sourceResult!.Value);
        Assert.Equal(2, sourceRows.Count);

        var statusResult = await controller.GetMedications(null, "stopped", null, null, null, CancellationToken.None) as OkObjectResult;
        var statusRows = Assert.IsAssignableFrom<List<MedicationRecord>>(statusResult!.Value);
        Assert.Single(statusRows);
    }

    [Fact]
    public async Task GetMedications_filters_by_date_range()
    {
        using var db = TestDb.CreateInMemory();
        db.Medications.AddRange(
            new MedicationRecord { SourceSystem = "a", SourceName = "", ResourceId = "1", FhirReference = "a/1", MedicationText = "Old", Status = "", AuthoredAt = new DateTimeOffset(2020, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow },
            new MedicationRecord { SourceSystem = "a", SourceName = "", ResourceId = "2", FhirReference = "a/2", MedicationText = "New", Status = "", AuthoredAt = new DateTimeOffset(2025, 6, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();

        var controller = new MedicationsController(db);
        var result = await controller.GetMedications(null, null, new DateTimeOffset(2024, 1, 1, 0, 0, 0, TimeSpan.Zero), null, null, CancellationToken.None) as OkObjectResult;

        var rows = Assert.IsAssignableFrom<List<MedicationRecord>>(result!.Value);
        Assert.Single(rows);
        Assert.Equal("New", rows[0].MedicationText);
    }
}
```

- [ ] **Step 2: Run test — expect failure (controller doesn't exist)**

```bash
dotnet build 2>&1 | tail -3
```

Expected: error CS0246 `MedicationsController` not found.

- [ ] **Step 3: Write controller**

```csharp
// HealthAggregator.Api/Controllers/MedicationsController.cs
using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/medications")]
public sealed class MedicationsController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetMedications(
        [FromQuery] string? search,
        [FromQuery] string? status,
        [FromQuery] DateTimeOffset? from,
        [FromQuery] DateTimeOffset? to,
        [FromQuery] string? source,
        CancellationToken cancellationToken)
    {
        var query = db.Medications.AsNoTracking();

        if (!string.IsNullOrWhiteSpace(search))
            query = query.Where(m => m.MedicationText != null && m.MedicationText.Contains(search));
        if (!string.IsNullOrWhiteSpace(status))
            query = query.Where(m => m.Status == status);
        if (!string.IsNullOrWhiteSpace(source))
            query = query.Where(m => m.SourceSystem == source);
        if (from.HasValue)
            query = query.Where(m => m.AuthoredAt >= from);
        if (to.HasValue)
            query = query.Where(m => m.AuthoredAt <= to);

        var rows = (await query.ToListAsync(cancellationToken))
            .OrderByDescending(m => m.AuthoredAt)
            .Take(500)
            .ToList();

        return Ok(rows);
    }
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
dotnet build && dotnet test --no-build --filter "FullyQualifiedName~MedicationsControllerTests"
```

Expected: 4 passed.

- [ ] **Step 5: Commit**

```bash
git add HealthAggregator.Api/Controllers/MedicationsController.cs HealthAggregator.Tests/MedicationsControllerTests.cs
git commit -m "Add GET /api/medications with search/status/source/from/to filters + tests"
```

---

### Task 22: `ConditionsController` + tests

**Files:**
- Create: `HealthAggregator.Api/Controllers/ConditionsController.cs`
- Create: `HealthAggregator.Tests/ConditionsControllerTests.cs`

- [ ] **Step 1: Write failing tests**

Mirror the Medications test shape — 4 tests (ordering by `OnsetAt ?? RecordedAt` DESC, search on `CodeText`, filter by `ClinicalStatus` + `source`, date-range filter). Ordering key uses `OnsetAt ?? RecordedAt`. Include a test where `OnsetAt` is null and the row is ordered by `RecordedAt`.

```csharp
using HealthAggregator.Api.Controllers;
using HealthAggregator.Data.Entities;
using Microsoft.AspNetCore.Mvc;

namespace HealthAggregator.Tests;

public sealed class ConditionsControllerTests
{
    [Fact]
    public async Task GetConditions_orders_by_OnsetAt_or_RecordedAt_desc()
    {
        using var db = TestDb.CreateInMemory();
        db.Conditions.AddRange(
            new ConditionRecord { SourceSystem = "a", SourceName = "", ResourceId = "1", FhirReference = "a/1", CodeText = "Asthma", ClinicalStatus = "active", OnsetAt = new DateTimeOffset(2020, 1, 1, 0, 0, 0, TimeSpan.Zero), RecordedAt = null, ImportedAt = DateTimeOffset.UtcNow },
            new ConditionRecord { SourceSystem = "a", SourceName = "", ResourceId = "2", FhirReference = "a/2", CodeText = "Diabetes", ClinicalStatus = "active", OnsetAt = null, RecordedAt = new DateTimeOffset(2024, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();
        var controller = new ConditionsController(db);
        var result = await controller.GetConditions(null, null, null, null, null, CancellationToken.None) as OkObjectResult;
        var rows = Assert.IsAssignableFrom<List<ConditionRecord>>(result!.Value);
        Assert.Equal("Diabetes", rows[0].CodeText);
    }

    [Fact]
    public async Task GetConditions_filters_by_search()
    {
        using var db = TestDb.CreateInMemory();
        db.Conditions.AddRange(
            new ConditionRecord { SourceSystem = "a", SourceName = "", ResourceId = "1", FhirReference = "a/1", CodeText = "Asthma", ClinicalStatus = "active", OnsetAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow },
            new ConditionRecord { SourceSystem = "a", SourceName = "", ResourceId = "2", FhirReference = "a/2", CodeText = "Diabetes", ClinicalStatus = "active", OnsetAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();
        var controller = new ConditionsController(db);
        var result = await controller.GetConditions("Diabetes", null, null, null, null, CancellationToken.None) as OkObjectResult;
        Assert.Single(Assert.IsAssignableFrom<List<ConditionRecord>>(result!.Value));
    }

    [Fact]
    public async Task GetConditions_filters_by_status_and_source()
    {
        using var db = TestDb.CreateInMemory();
        db.Conditions.AddRange(
            new ConditionRecord { SourceSystem = "cleveland-clinic", SourceName = "", ResourceId = "1", FhirReference = "a/1", CodeText = "A", ClinicalStatus = "active", OnsetAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow },
            new ConditionRecord { SourceSystem = "cleveland-clinic", SourceName = "", ResourceId = "2", FhirReference = "a/2", CodeText = "B", ClinicalStatus = "resolved", OnsetAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow },
            new ConditionRecord { SourceSystem = "summa-health", SourceName = "", ResourceId = "3", FhirReference = "b/3", CodeText = "C", ClinicalStatus = "active", OnsetAt = DateTimeOffset.UtcNow, ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();
        var controller = new ConditionsController(db);
        var statusR = await controller.GetConditions(null, "resolved", null, null, null, CancellationToken.None) as OkObjectResult;
        Assert.Single(Assert.IsAssignableFrom<List<ConditionRecord>>(statusR!.Value));
        var sourceR = await controller.GetConditions(null, null, null, null, "summa-health", CancellationToken.None) as OkObjectResult;
        Assert.Single(Assert.IsAssignableFrom<List<ConditionRecord>>(sourceR!.Value));
    }

    [Fact]
    public async Task GetConditions_filters_by_date_range_using_onset_or_recorded()
    {
        using var db = TestDb.CreateInMemory();
        db.Conditions.AddRange(
            new ConditionRecord { SourceSystem = "a", SourceName = "", ResourceId = "1", FhirReference = "a/1", CodeText = "Old", OnsetAt = new DateTimeOffset(2018, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow },
            new ConditionRecord { SourceSystem = "a", SourceName = "", ResourceId = "2", FhirReference = "a/2", CodeText = "NewByRecorded", OnsetAt = null, RecordedAt = new DateTimeOffset(2025, 1, 1, 0, 0, 0, TimeSpan.Zero), ImportedAt = DateTimeOffset.UtcNow }
        );
        await db.SaveChangesAsync();
        var controller = new ConditionsController(db);
        var result = await controller.GetConditions(null, null, new DateTimeOffset(2024, 1, 1, 0, 0, 0, TimeSpan.Zero), null, null, CancellationToken.None) as OkObjectResult;
        var rows = Assert.IsAssignableFrom<List<ConditionRecord>>(result!.Value);
        Assert.Single(rows);
        Assert.Equal("NewByRecorded", rows[0].CodeText);
    }
}
```

- [ ] **Step 2: Run — expect fail. Write controller:**

```csharp
// HealthAggregator.Api/Controllers/ConditionsController.cs
using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/conditions")]
public sealed class ConditionsController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetConditions(
        [FromQuery] string? search,
        [FromQuery] string? status,
        [FromQuery] DateTimeOffset? from,
        [FromQuery] DateTimeOffset? to,
        [FromQuery] string? source,
        CancellationToken cancellationToken)
    {
        var query = db.Conditions.AsNoTracking();
        if (!string.IsNullOrWhiteSpace(search))
            query = query.Where(c => c.CodeText != null && c.CodeText.Contains(search));
        if (!string.IsNullOrWhiteSpace(status))
            query = query.Where(c => c.ClinicalStatus == status);
        if (!string.IsNullOrWhiteSpace(source))
            query = query.Where(c => c.SourceSystem == source);

        var rows = await query.ToListAsync(cancellationToken);
        var filtered = rows
            .Where(c => !from.HasValue || (c.OnsetAt ?? c.RecordedAt) >= from)
            .Where(c => !to.HasValue || (c.OnsetAt ?? c.RecordedAt) <= to)
            .OrderByDescending(c => c.OnsetAt ?? c.RecordedAt)
            .Take(500)
            .ToList();

        return Ok(filtered);
    }
}
```

- [ ] **Step 3: Run tests + commit**

```bash
dotnet build && dotnet test --no-build --filter "FullyQualifiedName~ConditionsControllerTests"
git add HealthAggregator.Api/Controllers/ConditionsController.cs HealthAggregator.Tests/ConditionsControllerTests.cs
git commit -m "Add GET /api/conditions with search/status/source/from/to filters + tests"
```

---

### Task 23: `AllergiesController` + tests

**Files:**
- Create: `HealthAggregator.Api/Controllers/AllergiesController.cs`
- Create: `HealthAggregator.Tests/AllergiesControllerTests.cs`

- [ ] **Step 1: Tests**

Mirror Conditions structure — 3 tests: ordering by `RecordedAt` desc, search on `AllergyText`, filter by `ClinicalStatus` + `source`. No date-range filter (spec doesn't specify from/to for allergies).

- [ ] **Step 2: Controller**

```csharp
// HealthAggregator.Api/Controllers/AllergiesController.cs
using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/allergies")]
public sealed class AllergiesController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetAllergies(
        [FromQuery] string? search,
        [FromQuery] string? status,
        [FromQuery] string? source,
        CancellationToken cancellationToken)
    {
        var query = db.Allergies.AsNoTracking();
        if (!string.IsNullOrWhiteSpace(search))
            query = query.Where(a => a.AllergyText != null && a.AllergyText.Contains(search));
        if (!string.IsNullOrWhiteSpace(status))
            query = query.Where(a => a.ClinicalStatus == status);
        if (!string.IsNullOrWhiteSpace(source))
            query = query.Where(a => a.SourceSystem == source);

        var rows = (await query.ToListAsync(cancellationToken))
            .OrderByDescending(a => a.RecordedAt)
            .Take(500)
            .ToList();
        return Ok(rows);
    }
}
```

- [ ] **Step 3: Run tests + commit**

```bash
dotnet build && dotnet test --no-build --filter "FullyQualifiedName~AllergiesControllerTests"
git add HealthAggregator.Api/Controllers/AllergiesController.cs HealthAggregator.Tests/AllergiesControllerTests.cs
git commit -m "Add GET /api/allergies with search/status/source filters + tests"
```

---

### Task 24: `EncountersController` + tests

Identical pattern to Medications. Filters: `search` on `TypeText`, `status`, `from`/`to` on `StartedAt`, `source`. Order by `StartedAt` DESC. Write 3-4 tests mirroring Medications. Commit.

**Files:** `HealthAggregator.Api/Controllers/EncountersController.cs`, `HealthAggregator.Tests/EncountersControllerTests.cs`.

Controller body:

```csharp
using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/encounters")]
public sealed class EncountersController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetEncounters(
        [FromQuery] string? search,
        [FromQuery] string? status,
        [FromQuery] DateTimeOffset? from,
        [FromQuery] DateTimeOffset? to,
        [FromQuery] string? source,
        CancellationToken cancellationToken)
    {
        var query = db.Encounters.AsNoTracking();
        if (!string.IsNullOrWhiteSpace(search))
            query = query.Where(e => e.TypeText != null && e.TypeText.Contains(search));
        if (!string.IsNullOrWhiteSpace(status))
            query = query.Where(e => e.Status == status);
        if (!string.IsNullOrWhiteSpace(source))
            query = query.Where(e => e.SourceSystem == source);
        if (from.HasValue) query = query.Where(e => e.StartedAt >= from);
        if (to.HasValue)   query = query.Where(e => e.StartedAt <= to);

        var rows = (await query.ToListAsync(cancellationToken))
            .OrderByDescending(e => e.StartedAt)
            .Take(500)
            .ToList();
        return Ok(rows);
    }
}
```

Commit: `Add GET /api/encounters with search/status/source/from/to filters + tests`.

---

### Task 25: `DocumentsController` + tests

**Files:** `HealthAggregator.Api/Controllers/DocumentsController.cs`, `HealthAggregator.Tests/DocumentsControllerTests.cs`.

Test structure mirrors Task 24 (Encounters): 4 tests — ordering by `DocumentedAt` DESC, `?search` on `TypeText`, `?status` + `?source` filters, `?from`/`?to` on `DocumentedAt`.

Controller:

```csharp
using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/documents")]
public sealed class DocumentsController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetDocuments(
        [FromQuery] string? search,
        [FromQuery] string? status,
        [FromQuery] DateTimeOffset? from,
        [FromQuery] DateTimeOffset? to,
        [FromQuery] string? source,
        CancellationToken cancellationToken)
    {
        var query = db.Documents.AsNoTracking();
        if (!string.IsNullOrWhiteSpace(search))
            query = query.Where(d => d.TypeText != null && d.TypeText.Contains(search));
        if (!string.IsNullOrWhiteSpace(status))
            query = query.Where(d => d.Status == status);
        if (!string.IsNullOrWhiteSpace(source))
            query = query.Where(d => d.SourceSystem == source);
        if (from.HasValue) query = query.Where(d => d.DocumentedAt >= from);
        if (to.HasValue)   query = query.Where(d => d.DocumentedAt <= to);

        var rows = (await query.ToListAsync(cancellationToken))
            .OrderByDescending(d => d.DocumentedAt)
            .Take(500)
            .ToList();
        return Ok(rows);
    }
}
```

Commit: `Add GET /api/documents with search/status/source/from/to filters + tests`.

---

### Task 26: Extend `LabsController` with `source`/`loinc`/`abnormal` + tests

**Files:**
- Modify: `HealthAggregator.Api/Controllers/LabsController.cs`
- Create: `HealthAggregator.Tests/LabsControllerTests.cs`

- [ ] **Step 1: Write failing tests**

Four tests: `?source` filter, `?loinc` exact match, `?abnormal=true` (per spec definition — starts with `H`/`L`/`A`/`HH`/`LL` OR numeric out of range), and `?series?source=` filter on `GetSeries`. Importantly: assert that `Interpretation = "Normal"` is **not** flagged abnormal.

- [ ] **Step 2: Extend `GetLabs`**

Add three params to the existing method signature: `string? source`, `string? loinc`, `bool? abnormal`. Inside the method, after existing filters:

```csharp
if (!string.IsNullOrWhiteSpace(source))
    query = query.Where(lab => lab.SourceSystem == source);
if (!string.IsNullOrWhiteSpace(loinc))
    query = query.Where(lab => lab.LoincCode == loinc);

var list = await query.ToListAsync(cancellationToken);
if (abnormal == true)
{
    list = list.Where(IsAbnormal).ToList();
}

var labs = list
    .Where(lab => !from.HasValue || lab.EffectiveAt == null || lab.EffectiveAt >= from)
    .Where(lab => !to.HasValue || lab.EffectiveAt == null || lab.EffectiveAt <= to)
    .OrderByDescending(lab => lab.EffectiveAt)
    .Take(500)
    .ToList();

return Ok(labs);
```

Add a private static helper at the bottom of the class:

```csharp
private static readonly string[] AbnormalPrefixes = ["H", "L", "A", "HH", "LL"];

private static bool IsAbnormal(Data.Entities.LabObservation lab)
{
    if (!string.IsNullOrWhiteSpace(lab.Interpretation))
    {
        foreach (var prefix in AbnormalPrefixes)
        {
            if (lab.Interpretation.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }
        }
    }
    if (lab.NumericValue.HasValue)
    {
        if (lab.ReferenceLow.HasValue && lab.NumericValue < lab.ReferenceLow) return true;
        if (lab.ReferenceHigh.HasValue && lab.NumericValue > lab.ReferenceHigh) return true;
    }
    return false;
}
```

- [ ] **Step 3: Extend `GetSeries` with `?source`**

Add `[FromQuery] string? source` parameter. Inside, after the existing `loinc` / `name` filters:

```csharp
if (!string.IsNullOrWhiteSpace(source))
    query = query.Where(lab => lab.SourceSystem == source);
```

- [ ] **Step 4: Run tests + commit**

```bash
dotnet build && dotnet test --no-build --filter "FullyQualifiedName~LabsControllerTests"
git add HealthAggregator.Api/Controllers/LabsController.cs HealthAggregator.Tests/LabsControllerTests.cs
git commit -m "Extend LabsController with source/loinc/abnormal filters; precise abnormal semantics"
```

---

### Task 27: Extend `TimelineController` — filters + Allergies + Encounters + tests

**Files:**
- Modify: `HealthAggregator.Api/Controllers/TimelineController.cs`
- Create: `HealthAggregator.Tests/TimelineControllerTests.cs`

- [ ] **Step 1: Write failing tests — 5 tests**

1. Default (no filters) returns rows from labs + reports + conditions + medications + allergies + encounters + documents, ordered by `At` DESC, capped at 300.
2. `?kind=lab` filters to only labs.
3. `?kind=allergy` returns allergy rows (verifying the controller actually queries `db.Allergies`).
4. `?source=summa-health` filters across all kinds.
5. `?from` / `?to` filter by event date.

- [ ] **Step 2: Rewrite controller**

```csharp
using HealthAggregator.Data;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace HealthAggregator.Api.Controllers;

[ApiController]
[Route("api/timeline")]
public sealed class TimelineController(HealthAggregatorDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetTimeline(
        [FromQuery] string? kind,
        [FromQuery] string? source,
        [FromQuery] DateTimeOffset? from,
        [FromQuery] DateTimeOffset? to,
        [FromQuery] int? take,
        CancellationToken cancellationToken)
    {
        var items = new List<TimelineItem>();

        if (kind is null or "lab")
            items.AddRange(await db.LabObservations.AsNoTracking()
                .Select(lab => new TimelineItem("lab", lab.TestName, lab.EffectiveAt, lab.SourceSystem, lab.SourceName, lab.FhirReference, lab.NumericValue, lab.Unit))
                .ToListAsync(cancellationToken));

        if (kind is null or "report")
            items.AddRange(await db.DiagnosticReports.AsNoTracking()
                .Select(r => new TimelineItem("report", r.CodeText ?? "Diagnostic report", r.IssuedAt, r.SourceSystem, r.SourceName, r.FhirReference, null, null))
                .ToListAsync(cancellationToken));

        if (kind is null or "condition")
            items.AddRange(await db.Conditions.AsNoTracking()
                .Select(c => new TimelineItem("condition", c.CodeText ?? "Condition", c.RecordedAt ?? c.OnsetAt, c.SourceSystem, c.SourceName, c.FhirReference, null, null))
                .ToListAsync(cancellationToken));

        if (kind is null or "medication")
            items.AddRange(await db.Medications.AsNoTracking()
                .Select(m => new TimelineItem("medication", m.MedicationText ?? "Medication", m.AuthoredAt, m.SourceSystem, m.SourceName, m.FhirReference, null, null))
                .ToListAsync(cancellationToken));

        if (kind is null or "allergy")
            items.AddRange(await db.Allergies.AsNoTracking()
                .Select(a => new TimelineItem("allergy", a.AllergyText ?? "Allergy", a.RecordedAt, a.SourceSystem, a.SourceName, a.FhirReference, null, null))
                .ToListAsync(cancellationToken));

        if (kind is null or "encounter")
            items.AddRange(await db.Encounters.AsNoTracking()
                .Select(e => new TimelineItem("encounter", e.TypeText ?? "Encounter", e.StartedAt, e.SourceSystem, e.SourceName, e.FhirReference, null, null))
                .ToListAsync(cancellationToken));

        if (kind is null or "document")
            items.AddRange(await db.Documents.AsNoTracking()
                .Select(d => new TimelineItem("document", d.TypeText ?? "Document", d.DocumentedAt, d.SourceSystem, d.SourceName, d.FhirReference, null, null))
                .ToListAsync(cancellationToken));

        var filtered = items
            .Where(i => string.IsNullOrWhiteSpace(source) || i.SourceSystem == source)
            .Where(i => !from.HasValue || i.At == null || i.At >= from)
            .Where(i => !to.HasValue || i.At == null || i.At <= to)
            .OrderByDescending(i => i.At)
            .Take(take ?? 300)
            .ToList();

        return Ok(filtered);
    }
}

public sealed record TimelineItem(
    string Kind,
    string Title,
    DateTimeOffset? At,
    string SourceSystem,
    string SourceName,
    string FhirReference,
    decimal? NumericValue,
    string? Unit);
```

Note the record gained a `SourceSystem` field (in addition to the existing `SourceName`). This is required so the frontend can colour the `SourceBadge`. Check the frontend `TimelineItem` type in `api.ts` — will need updating in Task 30.

- [ ] **Step 3: Run tests + commit**

```bash
dotnet build && dotnet test --no-build --filter "FullyQualifiedName~TimelineControllerTests"
git add HealthAggregator.Api/Controllers/TimelineController.cs HealthAggregator.Tests/TimelineControllerTests.cs
git commit -m "Extend TimelineController with kind/source/from/to filters + Allergy/Encounter aggregation"
```

---

### Task 28: Add `DELETE /api/integrations/epic/connections/{id}` + test

**Files:**
- Modify: `HealthAggregator.Api/Controllers/EpicIntegrationsController.cs`
- Create: `HealthAggregator.Tests/EpicIntegrationsControllerTests.cs`

- [ ] **Step 1: Write failing test**

```csharp
using HealthAggregator.Api.Controllers;
using HealthAggregator.Data.Entities;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
// Note: EpicFhirClient and EpicSettings dependencies can be null since Disconnect doesn't use them.
// Keep this test focused on the DELETE behavior only.

namespace HealthAggregator.Tests;

public sealed class EpicIntegrationsControllerTests
{
    [Fact]
    public async Task Disconnect_removes_connection_but_keeps_ingested_data()
    {
        using var db = TestDb.CreateInMemory();
        db.EpicConnections.Add(new EpicConnection {
            OrganizationId = "cleveland-clinic",
            OrganizationName = "Cleveland Clinic",
            FhirBaseUrl = "https://...",
            AccessToken = "token",
            ConnectedAt = DateTimeOffset.UtcNow
        });
        db.LabObservations.Add(new LabObservation {
            SourceSystem = "cleveland-clinic",
            SourceName = "Cleveland Clinic",
            ResourceId = "1",
            FhirReference = "Observation/1",
            TestName = "HbA1c"
        });
        await db.SaveChangesAsync();
        var connectionId = db.EpicConnections.Single().Id;

        var controller = new EpicIntegrationsController(null!, db, null!);
        var result = await controller.Disconnect(connectionId, CancellationToken.None) as OkObjectResult;

        Assert.NotNull(result);
        Assert.Equal(0, await db.EpicConnections.CountAsync());
        Assert.Equal(1, await db.LabObservations.CountAsync());
    }

    [Fact]
    public async Task Disconnect_returns_NotFound_for_unknown_id()
    {
        using var db = TestDb.CreateInMemory();
        var controller = new EpicIntegrationsController(null!, db, null!);
        var result = await controller.Disconnect(999, CancellationToken.None);
        Assert.IsType<NotFoundResult>(result);
    }
}
```

- [ ] **Step 2: Add method to `EpicIntegrationsController`**

```csharp
[HttpDelete("connections/{id:int}")]
public async Task<IActionResult> Disconnect([FromRoute] int id, CancellationToken cancellationToken)
{
    var connection = await db.EpicConnections.SingleOrDefaultAsync(c => c.Id == id, cancellationToken);
    if (connection is null) return NotFound();
    db.EpicConnections.Remove(connection);
    await db.SaveChangesAsync(cancellationToken);
    return Ok(new { id });
}
```

- [ ] **Step 3: Run tests + commit**

```bash
dotnet build && dotnet test --no-build --filter "FullyQualifiedName~EpicIntegrationsControllerTests"
git add HealthAggregator.Api/Controllers/EpicIntegrationsController.cs HealthAggregator.Tests/EpicIntegrationsControllerTests.cs
git commit -m "Add DELETE /api/integrations/epic/connections/{id} — removes token, preserves ingested records"
```

---

### Task 29: Extend `api.ts` — fetch override + new endpoints + TypeScript type update

**Files:**
- Modify: `healthaggregator-web/src/lib/api.ts`

- [ ] **Step 1: Refactor `api.ts` to accept `FetchLike` on every getter**

Full rewrite of the file. Key changes: add `FetchLike` type, thread `fetch` into `fetchJson`, add query-param builder helper, add 5 new endpoints (medications/conditions/allergies/encounters/documents), add `disconnectConnection`, drop `authorizationEndpoint` + `tokenEndpoint` from `EpicOrganization` type, add `sourceSystem` to `TimelineItem` type to match the backend.

```ts
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'https://localhost:5310';

export type FetchLike = typeof fetch;

export type EpicOrganization = {
	id: string;
	name: string;
	fhirBaseUrl: string;
	isSandbox: boolean;
};

export type EpicOrganizationsResponse = {
	configured: boolean;
	organizations: EpicOrganization[];
};

export type EpicConnection = {
	id: number;
	organizationId: string;
	organizationName: string;
	fhirBaseUrl: string;
	patientId: string | null;
	connectedAt: string;
	lastSyncedAt: string | null;
	hasToken: boolean;
};

export type EpicConnectResponse = {
	organization: EpicOrganization;
	authorizationUrl: string | null;
	error: string | null;
	readyToAuthorize: boolean;
};

export type LabObservation = {
	id: number;
	sourceName: string;
	sourceSystem: string;
	fhirReference: string;
	loincCode: string | null;
	testName: string;
	numericValue: number | null;
	textValue: string | null;
	unit: string | null;
	referenceLow: number | null;
	referenceHigh: number | null;
	referenceText: string | null;
	interpretation: string | null;
	effectiveAt: string | null;
	status: string;
};

export type MedicationRecord = {
	id: number;
	sourceSystem: string;
	sourceName: string;
	fhirReference: string;
	medicationText: string | null;
	status: string | null;
	authoredAt: string | null;
};

export type ConditionRecord = {
	id: number;
	sourceSystem: string;
	sourceName: string;
	fhirReference: string;
	codeText: string | null;
	clinicalStatus: string | null;
	onsetAt: string | null;
	recordedAt: string | null;
};

export type AllergyRecord = {
	id: number;
	sourceSystem: string;
	sourceName: string;
	fhirReference: string;
	allergyText: string | null;
	clinicalStatus: string | null;
	recordedAt: string | null;
};

export type EncounterRecord = {
	id: number;
	sourceSystem: string;
	sourceName: string;
	fhirReference: string;
	typeText: string | null;
	status: string | null;
	startedAt: string | null;
	endedAt: string | null;
};

export type DocumentRecord = {
	id: number;
	sourceSystem: string;
	sourceName: string;
	fhirReference: string;
	typeText: string | null;
	status: string | null;
	documentedAt: string | null;
	contentUrl: string | null;
};

export type LabSeries = {
	key: string;
	name: string;
	loincCode: string | null;
	points: Array<{
		effectiveAt: string | null;
		numericValue: number | null;
		textValue: string | null;
		unit: string | null;
		referenceLow: number | null;
		referenceHigh: number | null;
		interpretation: string | null;
		sourceName: string;
		fhirReference: string;
	}>;
};

export type TimelineItem = {
	kind: string;
	title: string;
	at: string | null;
	sourceSystem: string;
	sourceName: string;
	fhirReference: string;
	numericValue: number | null;
	unit: string | null;
};

export type ImportJob = {
	id: number;
	sourceSystem: string;
	sourceName: string;
	status: string;
	startedAt: string;
	completedAt: string | null;
	sourceRecordsUpserted: number;
	labObservationsUpserted: number;
	error: string | null;
};

export type AssistantReply = {
	threadId: number;
	message: string;
	citations: Array<{
		fhirReference: string;
		sourceName: string;
		effectiveAt: string | null;
		testName: string;
		value: string;
		unit: string | null;
	}>;
};

export type LabsQuery   = { search?: string; from?: string; to?: string; loinc?: string; source?: string; abnormal?: boolean };
export type RecordQuery = { search?: string; status?: string; from?: string; to?: string; source?: string };
export type TimelineQuery = { kind?: string; source?: string; from?: string; to?: string; take?: number };

function buildUrl(path: string, params?: Record<string, string | boolean | number | undefined>): string {
	if (!params) return path;
	const url = new URL(path, 'http://x');
	for (const [k, v] of Object.entries(params)) {
		if (v !== undefined && v !== '' && v !== false) url.searchParams.set(k, String(v));
	}
	return url.pathname + url.search;
}

async function fetchJson<T>(path: string, init?: RequestInit, f: FetchLike = fetch): Promise<T> {
	const response = await f(`${API_BASE}${path}`, init);
	if (!response.ok) {
		const text = await response.text();
		throw new Error(text || `Request failed with ${response.status}`);
	}
	return (await response.json()) as T;
}

export const api = {
	baseUrl: API_BASE,
	getOrganizations:   (f?: FetchLike) => fetchJson<EpicOrganizationsResponse>('/api/integrations/epic/organizations', undefined, f),
	getConnections:     (f?: FetchLike) => fetchJson<EpicConnection[]>('/api/integrations/epic/connections', undefined, f),
	connectEpic:        (organizationId: string) => fetchJson<EpicConnectResponse>(`/api/integrations/epic/connect?organizationId=${encodeURIComponent(organizationId)}`),
	syncConnection:     (connectionId: number) => fetchJson(`/api/integrations/epic/sync?connectionId=${encodeURIComponent(connectionId)}`, { method: 'POST' }),
	disconnectConnection: (id: number) => fetchJson<{ id: number }>(`/api/integrations/epic/connections/${id}`, { method: 'DELETE' }),
	getLabs:            (f?: FetchLike, q?: LabsQuery) => fetchJson<LabObservation[]>(buildUrl('/api/labs', q), undefined, f),
	getLabSeries:       (f?: FetchLike, q?: { loinc?: string; name?: string; source?: string }) => fetchJson<LabSeries[]>(buildUrl('/api/labs/series', q), undefined, f),
	getMedications:     (f?: FetchLike, q?: RecordQuery) => fetchJson<MedicationRecord[]>(buildUrl('/api/medications', q), undefined, f),
	getConditions:      (f?: FetchLike, q?: RecordQuery) => fetchJson<ConditionRecord[]>(buildUrl('/api/conditions', q), undefined, f),
	getAllergies:       (f?: FetchLike, q?: RecordQuery) => fetchJson<AllergyRecord[]>(buildUrl('/api/allergies', q), undefined, f),
	getEncounters:      (f?: FetchLike, q?: RecordQuery) => fetchJson<EncounterRecord[]>(buildUrl('/api/encounters', q), undefined, f),
	getDocuments:       (f?: FetchLike, q?: RecordQuery) => fetchJson<DocumentRecord[]>(buildUrl('/api/documents', q), undefined, f),
	getTimeline:        (f?: FetchLike, q?: TimelineQuery) => fetchJson<TimelineItem[]>(buildUrl('/api/timeline', q), undefined, f),
	getImports:         (f?: FetchLike) => fetchJson<ImportJob[]>('/api/imports', undefined, f),
	uploadImport: async (file: File) => {
		const body = new FormData();
		body.append('file', file);
		return fetchJson('/api/imports', { method: 'POST', body });
	},
	askAssistant:       (message: string) => fetchJson<AssistantReply>('/api/assistant/messages', {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ message })
	})
};
```

- [ ] **Step 2: svelte-check + commit**

```bash
cd healthaggregator-web && pnpm exec svelte-check --threshold error
git add healthaggregator-web/src/lib/api.ts
git commit -m "Extend api.ts: FetchLike override, 5 new endpoints, disconnect, updated EpicOrganization and TimelineItem types"
```

---

### Task 30 (Phase 2 gate): Full backend smoke

- [ ] **Step 1: Full build + test**

```bash
dotnet build && dotnet test --no-build
```

Expected: all tests pass (existing + new). 0 errors, 0 warnings.

- [ ] **Step 2: Endpoint smoke via running API**

```bash
./scripts/start-dev.sh &
# Wait a few seconds, then:
for path in medications conditions allergies encounters documents; do
  curl -sk "https://localhost:5310/api/$path" -w "\n$path: %{http_code}\n" | tail -1
done
```

Expected: every endpoint returns `200` with a JSON array (may be empty if sandbox hasn't been synced yet).

- [ ] **Step 3: Commit (empty, gate marker)**

```bash
git commit --allow-empty -m "Phase 2 gate: backend additions verified — 5 new controllers + SmartConfig + filter extensions + disconnect"
```

---

# Phase 3 — Routing skeleton + AppShell wiring

### Task 31: Rewrite `+layout.svelte` to mount AppShell

**Files:**
- Modify: `healthaggregator-web/src/routes/+layout.svelte`
- Create: `healthaggregator-web/src/routes/+layout.ts` (no-op placeholder)

- [ ] **Step 1: `+layout.ts`**

```ts
// healthaggregator-web/src/routes/+layout.ts
export const ssr = false; // we have no auth/data that benefits from SSR and API uses self-signed HTTPS
export const csr = true;
```

- [ ] **Step 2: Rewrite `+layout.svelte`**

```svelte
<script lang="ts">
	import './layout.css';
	import AppShell from '$lib/components/AppShell.svelte';
	import NavigationLoader from '$lib/components/NavigationLoader.svelte';
	import { Toaster } from '$lib/components/ui/sonner';
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/stores';
	import { toast } from 'svelte-sonner';

	let { children } = $props();

	onMount(() => {
		// Handle Epic OAuth callback before any page loader runs for a meaningful duration.
		const params = $page.url.searchParams;
		const epic = params.get('epic');
		if (epic === 'connected') {
			toast.success('Connected to Epic');
			goto('/connections', { replaceState: true });
		} else if (epic === 'error') {
			const reason = params.get('reason') ?? 'unknown error';
			toast.error(`Epic connection failed: ${reason}`);
			goto('/', { replaceState: true });
		}
	});
</script>

<NavigationLoader />
<Toaster position="top-right" />
<AppShell>
	{@render children()}
</AppShell>
```

- [ ] **Step 3: svelte-check**

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors. Existing `+page.svelte` (the monolith) will now render INSIDE the AppShell — will look doubled-up (both have sidebars). Acceptable for one task; Task 33 replaces `+page.svelte`.

- [ ] **Step 4: Commit**

```bash
git add healthaggregator-web/src/routes/+layout.svelte healthaggregator-web/src/routes/+layout.ts
git commit -m "Wrap app in AppShell + NavigationLoader + Toaster; handle Epic callback in layout"
```

---

### Task 32: Global `+error.svelte`

**Files:**
- Create: `healthaggregator-web/src/routes/+error.svelte`

- [ ] **Step 1: Write the error page**

```svelte
<script lang="ts">
	import { page } from '$app/stores';
	import { Button } from '$lib/components/ui/button';
	import * as Card from '$lib/components/ui/card';
	import { goto } from '$app/navigation';
	import { invalidateAll } from '$app/navigation';
</script>

<div class="max-w-xl mx-auto mt-12" data-testid="page-error">
	<Card.Root>
		<Card.Header>
			<Card.Title>Something went wrong</Card.Title>
			<Card.Description>We hit an error loading this page.</Card.Description>
		</Card.Header>
		<Card.Content>
			<pre class="whitespace-pre-wrap text-sm text-[var(--muted-foreground)]">{$page.error?.message ?? 'Unknown error'}</pre>
		</Card.Content>
		<Card.Footer class="flex gap-2">
			<Button onclick={() => invalidateAll()} data-testid="page-error-retry">Retry</Button>
			<Button variant="outline" onclick={() => goto('/')} data-testid="page-error-home">Go to Dashboard</Button>
		</Card.Footer>
	</Card.Root>
</div>
```

- [ ] **Step 2: svelte-check + commit**

```bash
cd healthaggregator-web && pnpm exec svelte-check --threshold error
git add healthaggregator-web/src/routes/+error.svelte
git commit -m "Global +error.svelte fallback with retry + home actions"
```

---

### Task 33: Rename monolith + create Dashboard stub + create 12 route stubs

**Files:**
- Rename: `healthaggregator-web/src/routes/+page.svelte` → `+page.svelte.bak`
- Create: `healthaggregator-web/src/routes/+page.svelte` (stub)
- Create: 12 route directories + `+page.svelte` + `+page.ts` stub pairs

- [ ] **Step 1: Rename the current Dashboard to a `.bak`**

```bash
mv healthaggregator-web/src/routes/+page.svelte healthaggregator-web/src/routes/+page.svelte.bak
```

- [ ] **Step 2: Create the 13 stub pages (including the new `+page.svelte`)**

For each route in the list below, create a `+page.svelte` + `+page.ts` pair. Use this script (run from `healthaggregator-web/src/routes/`):

```bash
for route in "" connections imports labs vitals medications conditions allergies encounters documents timeline assistant settings; do
  dir=${route:-.}
  [ "$dir" != "." ] && mkdir -p "$dir"
  cat > "$dir/+page.ts" <<'EOF'
import type { PageLoad } from './$types';
export const load: PageLoad = async () => ({});
EOF
  title="${route:-Dashboard}"
  # Capitalize first letter
  title="$(tr '[:lower:]' '[:upper:]' <<< ${title:0:1})${title:1}"
  cat > "$dir/+page.svelte" <<EOF
<script lang="ts">
	import PageHeader from '\$lib/components/PageHeader.svelte';
	import EmptyState from '\$lib/components/EmptyState.svelte';
</script>

<PageHeader title="$title" subtitle="Coming up in this foundation pass" />
<EmptyState title="Page under construction" description="This page's implementation lands in the next Phase 4 task." />
EOF
done
```

Then fix the Dashboard stub at `+page.svelte` (it got generated as title "Dashboard" but the route labels expected "Overview"):

```svelte
<!-- healthaggregator-web/src/routes/+page.svelte -->
<script lang="ts">
	import PageHeader from '$lib/components/PageHeader.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
</script>

<PageHeader title="Overview" subtitle="Coming up in this foundation pass" />
<EmptyState title="Page under construction" description="This page's implementation lands in the next Phase 4 task." />
```

- [ ] **Step 3: svelte-check + visual verification**

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors.

Start the dev server and visit each of the 13 routes. Each should render inside the AppShell, with the sidebar nav highlighting the active route.

- [ ] **Step 4: Commit**

```bash
git add healthaggregator-web/src/routes/
git commit -m "Scaffold 13 route stubs and rename old dashboard to +page.svelte.bak"
```

---

### Task 34 (Phase 3 gate): Responsive AppShell verification

- [ ] **Step 1: Manual responsive smoke**

Start the dev server. In the browser, resize the viewport across three widths:
- ≥1024px — full sidebar with all labels visible.
- 768-1023px — sidebar visible (slightly tighter).
- <768px — sidebar hidden, hamburger icon appears; tap → drawer opens with backdrop.

Click through all 13 nav items at each width. Active state highlights correctly. Group expand/collapse persists across navigation within a session.

- [ ] **Step 2: Commit gate marker**

```bash
git commit --allow-empty -m "Phase 3 gate: AppShell + 13 route stubs verified across breakpoints"
```

---

# Phase 4 — Page-by-page rebuild

Each of the following tasks implements **one** real page. Pattern: `+page.ts` fetches data → `+page.svelte` renders using shared primitives. Every interactive element gets a `data-testid`. Task bodies reference the spec's per-page design sections rather than duplicating the design narrative.

### Task 35: `/` Dashboard

**Files:**
- Modify: `healthaggregator-web/src/routes/+page.svelte`, `+page.ts`

- [ ] **Step 1: Write `+page.ts` per spec §Design → Dashboard (/)**

Loader fetches `getOrganizations`, `getConnections`, `getLabs`, `getTimeline` in parallel, computes stats, returns `{ stats, orgs, connections, recentLabs, recentTimeline }`. Abnormal detection in helpers uses the same predicate as the backend (`Interpretation` starts with `H`/`L`/`A`/`HH`/`LL` or numeric out of range).

```ts
import type { PageLoad } from './$types';
import { api, type LabObservation } from '$lib/api';

const ABNORMAL_PREFIXES = ['H', 'L', 'A', 'HH', 'LL'];
function isAbnormal(lab: LabObservation): boolean {
	const interp = lab.interpretation?.toUpperCase() ?? '';
	if (ABNORMAL_PREFIXES.some(p => interp.startsWith(p))) return true;
	if (lab.numericValue != null) {
		if (lab.referenceLow != null && lab.numericValue < lab.referenceLow) return true;
		if (lab.referenceHigh != null && lab.numericValue > lab.referenceHigh) return true;
	}
	return false;
}

export const load: PageLoad = async ({ fetch }) => {
	const [orgsResponse, connections, labs, timeline] = await Promise.all([
		api.getOrganizations(fetch),
		api.getConnections(fetch),
		api.getLabs(fetch),
		api.getTimeline(fetch, { take: 300 })
	]);
	return {
		configured: orgsResponse.configured,
		orgs: orgsResponse.organizations,
		connections,
		stats: {
			labCount: labs.length,
			abnormalCount: labs.filter(isAbnormal).length,
			timelineCount: timeline.length,
			sourcesConnected: `${connections.filter(c => c.hasToken).length} / ${orgsResponse.organizations.length}`
		},
		recentLabs: labs.slice(0, 8),
		recentTimeline: timeline.slice(0, 10)
	};
};
```

- [ ] **Step 2: Write `+page.svelte`**

Renders `PageHeader` (title "Overview", subtitle "Your health records at a glance", Refresh button in actions), 4-tile `StatCard` grid, 2-column grid with Connection Status card (left) + Recent Labs card (right), full-width Recent Timeline card. All rows use `SourceBadge`. Empty state when no orgs/connections shows a centered `EmptyState` with CTA to `/connections`.

Refer to spec §Design → Dashboard (/) for field-by-field content. Use these Lucide icons in StatCards: `Activity`, `ShieldAlert`, `History`, `Plug`.

Every interactive element (StatCard links, Refresh button, Connect/Sync buttons, View-all / Open-full links, Connect-first CTA) gets a `data-testid` attribute. Suggested ids: `stat-labs`, `stat-abnormal`, `stat-timeline`, `stat-sources`, `dashboard-refresh`, `connection-row-${id}`, `recent-labs-view-all`, `recent-timeline-open-full`, `empty-connect-cta`.

- [ ] **Step 3: svelte-check + manual smoke**

```bash
cd healthaggregator-web && pnpm exec svelte-check --threshold error
```

Start dev server; visit `/`; confirm stats + lists render against sandbox data; verify refresh calls loader.

- [ ] **Step 4: Commit**

```bash
git add healthaggregator-web/src/routes/+page.svelte healthaggregator-web/src/routes/+page.ts
git commit -m "Implement Dashboard (/) — stats + recent labs + recent timeline + connection status"
```

---

### Task 36: `/connections`

**Files:**
- Modify: `healthaggregator-web/src/routes/connections/+page.svelte`, `+page.ts`

- [ ] **Step 1: `+page.ts`**

```ts
import type { PageLoad } from './$types';
import { api } from '$lib/api';

export const load: PageLoad = async ({ fetch }) => {
	const [orgsResponse, connections] = await Promise.all([
		api.getOrganizations(fetch),
		api.getConnections(fetch)
	]);
	return { configured: orgsResponse.configured, orgs: orgsResponse.organizations, connections };
};
```

- [ ] **Step 2: `+page.svelte`**

Implement per spec §Design → Data sources pages → /connections. Include:
- `PageHeader` with count subtitle + `[Refresh]`
- Client-id not-set `ErrorBanner` (variant=warning) at top when `!data.configured`
- Card "Available Sources": one row per configured org. Each row has SourceBadge, org name, last sync, record count, Connect/Sync/Disconnect action.
  - Blank `FhirBaseUrl` renders row disabled with muted hint
  - Disconnect uses `ConfirmDialog` → `api.disconnectConnection(id)` → `toast.success` + `invalidate('/api/integrations/epic/connections')`
- Card "Recent Sync Details" (collapsible) showing per-resource sync breakdown after a successful sync
- `data-testid` attributes: `connections-refresh`, `org-row-${id}`, `org-connect-${id}`, `org-sync-${id}`, `org-disconnect-${id}`, `client-id-warning`

- [ ] **Step 3: svelte-check + manual smoke**

Confirm Connect redirects to Epic authorize, Sync calls the endpoint, Disconnect removes the row.

- [ ] **Step 4: Commit**

```bash
git add healthaggregator-web/src/routes/connections/
git commit -m "Implement /connections — multi-org connect/sync/disconnect with source badges"
```

---

### Task 37: `/labs`

**Files:**
- Modify: `healthaggregator-web/src/routes/labs/+page.svelte`, `+page.ts`

- [ ] **Step 1: `+page.ts`**

```ts
import type { PageLoad } from './$types';
import { api } from '$lib/api';

export const load: PageLoad = async ({ fetch, url }) => {
	const params = {
		search:   url.searchParams.get('search')   ?? undefined,
		from:     url.searchParams.get('from')     ?? undefined,
		to:       url.searchParams.get('to')       ?? undefined,
		loinc:    url.searchParams.get('loinc')    ?? undefined,
		source:   url.searchParams.get('source')   ?? undefined,
		abnormal: url.searchParams.get('abnormal') === 'true' ? true : undefined
	};
	const [labs, series, orgsResponse] = await Promise.all([
		api.getLabs(fetch, params),
		api.getLabSeries(fetch, { loinc: params.loinc, source: params.source }),
		api.getOrganizations(fetch)
	]);
	return { labs, series, sources: orgsResponse.organizations, params };
};
```

- [ ] **Step 2: `+page.svelte`**

Per spec §Design → Records pages → /labs. Components used: `PageHeader`, filter bar card (Input + date inputs + source Combobox + abnormal Checkbox + Clear button), 2-column grid with left `DataTable` + right trend panel.

DataTable columns (follow `types.ts` Column<LabObservation> shape):
- Date (`formatDate(lab.effectiveAt)`, sortable by `effectiveAt`)
- Test (name + small LOINC below; sortable by `testName`)
- Value (numeric or text + unit)
- Ref Range (`${lab.referenceLow}-${lab.referenceHigh}` or `referenceText` or `—`; `hideOn: 'md'`)
- Flag (`<Badge variant="warning">abnormal</Badge>` when `isAbnormal(lab)`, else nothing)
- Source (`<SourceBadge sourceSystem={lab.sourceSystem} sourceName={lab.sourceName} />`)

Trend panel: combobox populated from `data.series` (LOINC → test name), picks one → render an SVG sparkline using points colour-coded by `sourceSystem`. Legend beneath shows source→colour mapping.

Filters write to URL via `updateFilters(...)`. Clear button calls `updateFilters({ search: null, from: null, to: null, source: null, abnormal: null })`.

Suggested `data-testid`s: `labs-search`, `labs-from`, `labs-to`, `labs-source-filter`, `labs-abnormal-toggle`, `labs-clear-filters`, `labs-refresh`, `labs-table`, `lab-row-${id}`, `labs-trend-select`, `labs-trend-chart`.

- [ ] **Step 3: svelte-check + manual smoke**

Check that filters update the URL and reload restores them. Sparkline renders against sandbox data.

- [ ] **Step 4: Commit**

```bash
git add healthaggregator-web/src/routes/labs/
git commit -m "Implement /labs — filters, DataTable, trend panel with multi-source sparkline"
```

---

### Task 38: `/timeline`

**Files:**
- Modify: `healthaggregator-web/src/routes/timeline/+page.svelte`, `+page.ts`

Per spec §Design → Insights pages → /timeline. Loader reads URL params → calls `api.getTimeline(fetch, params)`. Page renders `PageHeader` + filter bar (search, kind combobox, source combobox, date inputs) + list (not DataTable — narrative format). Each row: kind icon + `formatDate(at)` + kind Badge + title + SourceBadge + chevron. `[Load more]` button at bottom increases `?take` param by 300.

Suggested testids: `timeline-search`, `timeline-kind-filter`, `timeline-source-filter`, `timeline-from`, `timeline-to`, `timeline-load-more`, `timeline-row-${fhirReference}`.

Commit: `Implement /timeline — chronological feed with kind/source/date filters`.

---

### Task 39: `/medications` — canonical simple-list template

This task establishes the template Tasks 40-43 reuse with entity-specific deltas. Full code here; later tasks reference "Task 39's template" with the specific columns/entity/loader change called out.

**Files:**
- Modify: `healthaggregator-web/src/routes/medications/+page.svelte`, `+page.ts`

- [ ] **Step 1: `+page.ts`**

```ts
import type { PageLoad } from './$types';
import { api } from '$lib/api';

export const load: PageLoad = async ({ fetch, url }) => {
	const params = {
		search: url.searchParams.get('search') ?? undefined,
		status: url.searchParams.get('status') ?? undefined,
		from:   url.searchParams.get('from')   ?? undefined,
		to:     url.searchParams.get('to')     ?? undefined,
		source: url.searchParams.get('source') ?? undefined
	};
	const [rows, orgsResponse] = await Promise.all([
		api.getMedications(fetch, params),
		api.getOrganizations(fetch)
	]);
	return { rows, sources: orgsResponse.organizations, params };
};
```

- [ ] **Step 2: `+page.svelte`**

```svelte
<script lang="ts">
	import type { MedicationRecord } from '$lib/api';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
	import SourceBadge from '$lib/components/SourceBadge.svelte';
	import DataTable from '$lib/components/data-table/DataTable.svelte';
	import DataTableSearch from '$lib/components/data-table/DataTableSearch.svelte';
	import { Badge } from '$lib/components/ui/badge';
	import { Button } from '$lib/components/ui/button';
	import * as Select from '$lib/components/ui/select';
	import { invalidateAll } from '$app/navigation';
	import { updateFilters } from '$lib/utils/url-filters';
	import { getEmptyStateProps } from '$lib/utils/empty-state';
	import { formatDate } from '$lib/utils/format';
	import type { Column } from '$lib/components/data-table/types';

	let { data } = $props();

	const filtersActive = $derived(Object.values(data.params).some(v => v !== undefined));
	const sourcesCount = $derived(new Set(data.rows.map(r => r.sourceSystem)).size);

	const columns: Column<MedicationRecord>[] = [
		{
			id: 'authoredAt',
			header: 'Authored',
			cell: (row) => formatDate(row.authoredAt),
			sortBy: (row) => row.authoredAt ? new Date(row.authoredAt) : null
		},
		{
			id: 'medicationText',
			header: 'Medication',
			cell: (row) => row.medicationText ?? '—',
			sortBy: (row) => row.medicationText ?? ''
		},
		{
			id: 'status',
			header: 'Status',
			cell: (row) => row.status ? { default: `<Badge variant="secondary">${row.status}</Badge>` } : '—'
		},
		{
			id: 'source',
			header: 'Source',
			cell: (row) => ({ default: `<SourceBadge sourceSystem="${row.sourceSystem}" sourceName="${row.sourceName}" />` })
		}
	];
	// NOTE: the Column.cell signature in types.ts is `Snippet<[T]>`. In practice write this as inline snippets in the template rather than string-returning closures. Use `{#snippet cell(row)}...{/snippet}` blocks inside DataTable invocation. See actual rendering below.
</script>

<PageHeader title="Medications" subtitle={`${data.rows.length} records from ${sourcesCount} sources`}>
	{#snippet actions()}
		<Button variant="secondary" onclick={() => invalidateAll()} data-testid="medications-refresh">Refresh</Button>
	{/snippet}
</PageHeader>

<div class="flex flex-wrap gap-3 mb-4" data-testid="medications-filters">
	<DataTableSearch value={data.params.search ?? ''} placeholder="Search medications..." />

	<Select.Root
		type="single"
		value={data.params.source ?? ''}
		onValueChange={(v) => updateFilters({ source: v || null })}
	>
		<Select.Trigger class="w-48" data-testid="medications-source-filter">
			{data.params.source ? (data.sources.find(s => s.id === data.params.source)?.name ?? data.params.source) : 'All sources'}
		</Select.Trigger>
		<Select.Content>
			<Select.Item value="">All sources</Select.Item>
			{#each data.sources as org (org.id)}
				<Select.Item value={org.id}>{org.name}</Select.Item>
			{/each}
			<Select.Item value="manual-upload">Manual Upload</Select.Item>
		</Select.Content>
	</Select.Root>
</div>

{#if data.rows.length === 0}
	{@const props = getEmptyStateProps('medication', data.rows.length, filtersActive)}
	<EmptyState title={props.title} description={props.description} />
{:else}
	<DataTable {columns} data={data.rows} pageSize={50}>
		{#snippet cellAuthoredAt(row)}{formatDate(row.authoredAt)}{/snippet}
		{#snippet cellMedication(row)}{row.medicationText ?? '—'}{/snippet}
		{#snippet cellStatus(row)}
			{#if row.status}<Badge variant="secondary">{row.status}</Badge>{:else}—{/if}
		{/snippet}
		{#snippet cellSource(row)}<SourceBadge sourceSystem={row.sourceSystem} sourceName={row.sourceName} />{/snippet}
	</DataTable>
{/if}
```

**Note for the implementer:** the snippet pattern above assumes `DataTable` columns reference named snippets from the parent scope. If Task 15's `DataTable` uses a different cell-render pattern (direct function in `Column.cell`), adapt the markup so each column's cell function invokes `SourceBadge` / `Badge` / `formatDate` correctly. The actual `Column.cell` signature is `Snippet<[T]>` — define the snippets in the `columns` array using `$snippet` expressions or a pre-defined snippets map. Use whichever form actually compiles against Task 15's concrete DataTable implementation. Every interactive element must keep its `data-testid`.

- [ ] **Step 3: svelte-check + manual smoke**

```bash
cd healthaggregator-web && pnpm exec svelte-check --threshold error
```

Visit `/medications`, confirm list renders, filter by source, reload — filter state persists via URL.

- [ ] **Step 4: Commit**

```bash
git add healthaggregator-web/src/routes/medications/
git commit -m "Implement /medications — read-only list with search and source filter; template for peer pages"
```

---

### Task 40: `/conditions`

**Files:** `healthaggregator-web/src/routes/conditions/+page.svelte`, `+page.ts`

Apply the Task 39 template with these changes:

- **Loader:** call `api.getConditions(fetch, params)` instead of `getMedications`. Return type `ConditionRecord[]`.
- **PageHeader title:** "Conditions"; `getEmptyStateProps('condition', ...)`
- **Refresh button testid:** `conditions-refresh`; filters testids use `conditions-` prefix.
- **Columns** (replace the `cellAuthoredAt`/`cellMedication` snippets):
  - `onsetAt` — header "Onset / Recorded", `cell(row)` returns `formatDate(row.onsetAt ?? row.recordedAt)`, `sortBy: row => row.onsetAt ?? row.recordedAt ? new Date(row.onsetAt ?? row.recordedAt!) : null`
  - `codeText` — header "Condition", `cell(row)` returns `row.codeText ?? '—'`, `sortBy: row => row.codeText ?? ''`
  - `clinicalStatus` — header "Clinical status", `cell(row)` returns a `<Badge variant="secondary">{row.clinicalStatus}</Badge>` when set, else `—`
  - `source` — same as Task 39

Commit: `Implement /conditions — read-only list with search/status/source filters`

---

### Task 41: `/allergies`

**Files:** `healthaggregator-web/src/routes/allergies/+page.svelte`, `+page.ts`

Apply the Task 39 template with these changes:

- **Loader:** call `api.getAllergies(fetch, params)`. Return type `AllergyRecord[]`. Note: allergies endpoint does not take `from`/`to`, so omit those from `params`.
- **PageHeader title:** "Allergies"; `getEmptyStateProps('allergy', ...)`
- **Refresh button testid:** `allergies-refresh`; filters testids use `allergies-` prefix.
- **Columns:**
  - `recordedAt` — header "Recorded", `cell(row)` returns `formatDate(row.recordedAt)`, `sortBy: row => row.recordedAt ? new Date(row.recordedAt) : null`
  - `allergyText` — header "Allergen", `cell(row)` returns `row.allergyText ?? '—'`, `sortBy: row => row.allergyText ?? ''`
  - `clinicalStatus` — header "Clinical status", cell as in Task 40
  - `source` — same as Task 39

Commit: `Implement /allergies — read-only list with search/status/source filters`

---

### Task 42: `/encounters`

**Files:** `healthaggregator-web/src/routes/encounters/+page.svelte`, `+page.ts`

Apply the Task 39 template with these changes:

- **Loader:** call `api.getEncounters(fetch, params)`. Return type `EncounterRecord[]`.
- **PageHeader title:** "Encounters"; `getEmptyStateProps('encounter', ...)`
- **Refresh button testid:** `encounters-refresh`; filters testids use `encounters-` prefix.
- **Columns:**
  - `startedAt` — header "Started", `cell(row)` returns `formatDate(row.startedAt)`, `sortBy: row => row.startedAt ? new Date(row.startedAt) : null`
  - `typeText` — header "Type", `cell(row)` returns `row.typeText ?? '—'`, `sortBy: row => row.typeText ?? ''`
  - `status` — header "Status", cell renders a `<Badge variant="secondary">{row.status}</Badge>` when set, else `—`
  - `endedAt` — header "Ended", `cell(row)` returns `formatDate(row.endedAt)`, `hideOn: 'md'`, `sortBy: row => row.endedAt ? new Date(row.endedAt) : null`
  - `source` — same as Task 39

Commit: `Implement /encounters — read-only list with search/status/source/from/to filters`

---

### Task 43: `/documents`

**Files:** `healthaggregator-web/src/routes/documents/+page.svelte`, `+page.ts`

Apply the Task 39 template with these changes:

- **Loader:** call `api.getDocuments(fetch, params)`. Return type `DocumentRecord[]`.
- **PageHeader title:** "Documents"; `getEmptyStateProps('document', ...)`
- **Refresh button testid:** `documents-refresh`; filters testids use `documents-` prefix.
- **Columns:**
  - `documentedAt` — header "Documented", `cell(row)` returns `formatDate(row.documentedAt)`, `sortBy: row => row.documentedAt ? new Date(row.documentedAt) : null`
  - `typeText` — header "Type", `cell(row)` returns `row.typeText ?? '—'`, `sortBy: row => row.typeText ?? ''`
  - `status` — header "Status", cell as in Task 42
  - `contentUrl` — header "Content URL", `hideOn: 'md'`, `cell(row)` returns `row.contentUrl ? html: <a href={row.contentUrl} target="_blank" rel="noreferrer" class="text-[var(--primary)] hover:underline" data-testid="document-link-${row.id}">Open</a> : '—'`
  - `source` — same as Task 39

Commit: `Implement /documents — read-only list with search/status/source/from/to filters`

---

### Task 44: `/imports`

**Files:**
- Modify: `healthaggregator-web/src/routes/imports/+page.svelte`, `+page.ts`

Per spec §Design → Data sources pages → /imports. Loader calls `api.getImports(fetch)`. Page: PageHeader + 2-column grid (upload card left, history DataTable right). Upload uses existing `api.uploadImport(file)`; on success `toast.success(...)` + `invalidate('/api/imports')` + render the most recent `ImportSummary` breakdown card below the grid.

DataTable columns: Started date, File (sourceName), Kind (badge: `fhir-json` default, `stored-file-metadata` warning with tooltip), Status, Records upserted, Labs upserted.

Suggested testids: `imports-upload-input`, `imports-upload-submit`, `imports-table`, `import-row-${id}`.

Commit: `Implement /imports — upload zone + history DataTable with kind badges`.

---

### Task 45: `/assistant`

**Files:**
- Modify: `healthaggregator-web/src/routes/assistant/+page.svelte`, `+page.ts`

Loader: `export const load: PageLoad = async () => ({});` — assistant state is client-only.

Page: PageHeader (title "Assistant", subtitle "Ask about your labs", `[New conversation]` action). Chat-style layout with messages + sticky input at bottom. Uses `api.askAssistant(message)` on submit. Renders citations via a small `AssistantCitationList` component inline (or factored into a small component file under this route). Empty state with 4 suggested-question chips.

Suggested testids: `assistant-new-conversation`, `assistant-input`, `assistant-submit`, `assistant-suggestion-${i}`, `assistant-message-user-${i}`, `assistant-message-assistant-${i}`, `assistant-citation-${i}`.

Commit: `Implement /assistant — chat UI + citation list with source badges (backed by existing keyword-matcher)`.

---

### Task 46: `/vitals` stub

**Files:**
- Modify: `healthaggregator-web/src/routes/vitals/+page.svelte`, `+page.ts`

```svelte
<script lang="ts">
	import PageHeader from '$lib/components/PageHeader.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
	import { HeartPulse } from '@lucide/svelte';
</script>

<PageHeader title="Vitals" />
<EmptyState
	title="Vitals coverage coming in Stream C"
	description="Vital-sign Observations are already being pulled from Epic into the raw SourceRecord table, but the structured VitalsObservation entity, ingestion path, and list / trend UI land in the next spec. Vitals + medications + labs correlated over time are load-bearing for the diagnostic assistant goal."
/>
```

`+page.ts` returns `{}`.

Commit: `Implement /vitals as a Stream C placeholder stub`.

---

### Task 47: `/settings`

**Files:**
- Modify: `healthaggregator-web/src/routes/settings/+page.svelte`, `+page.ts`

Per spec §Design → Settings page. Tabs: General · Epic · Data · About. Each tab either shows real data (Epic tab reads `api.getOrganizations(fetch)` → lists orgs + `Client ID: set/not set` badge) or an EmptyState placeholder.

Loader:

```ts
export const load: PageLoad = async ({ fetch }) => {
	const orgs = await api.getOrganizations(fetch);
	return { orgs };
};
```

Page uses the `tabs` primitive with 4 TabContent sections.

Commit: `Implement /settings — tab shell with Epic tab wired to real config`.

---

# Phase 5 — Cleanup

### Task 48: Delete `+page.svelte.bak` + legacy CSS

**Files:**
- Delete: `healthaggregator-web/src/routes/+page.svelte.bak`
- Modify: `healthaggregator-web/src/routes/layout.css`

- [ ] **Step 1: Delete the bak file**

```bash
rm healthaggregator-web/src/routes/+page.svelte.bak
```

- [ ] **Step 2: Delete legacy class blocks from `layout.css`**

Keep:
- `@import 'tailwindcss';`
- `@plugin '@tailwindcss/typography';`
- `@import '$lib/styles/tokens.css';`
- `:root { font-family: ...; color-scheme: dark; }` (presentation-only)
- `* { box-sizing: border-box; }`
- `body { margin: 0; background: var(--background); color: var(--foreground); }`
- `button, input, textarea { font: inherit; }`
- `button { border: 0; }`
- `a { color: inherit; }`

Delete (all blocks under these selectors):
- `.app-shell`, `.sidebar`, `.brand`, `.brand-mark`, `.nav-list`, `.nav-item`, `.topbar`, `.content`, `.eyebrow`, `.muted`, `.alert`, `.error`, `.panel`, `.grid`, `.grid-2`, `.grid-3`, `.stat`, `.button`, `.badge`, `.timeline`, `.sparkline`, `.sync-summary`, `.assistant-answer`, `.import-list`, and the `@media (max-width: 960px)` block.

- [ ] **Step 3: svelte-check + visual verification**

Run: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
Expected: 0 errors.

Start dev server; walk every page. Everything should still render correctly — we've only removed styles that are no longer referenced.

- [ ] **Step 4: Commit**

```bash
git add healthaggregator-web/src/routes/
git commit -m "Delete legacy monolith and obsolete CSS classes; new components fully carry the UI"
```

---

### Task 49 (final gate): Fresh DB full-app smoke

- [ ] **Step 1: Back up and delete the dev DB**

```bash
DB="$HOME/Library/Application Support/HealthAggregator/healthaggregator-dev.db"
cp "$DB" "$DB.bak-$(date +%Y%m%d-%H%M%S)" 2>/dev/null || true
rm -f "$DB" "$DB-shm" "$DB-wal"
```

- [ ] **Step 2: Full build + test**

```bash
dotnet build
dotnet test --no-build
cd healthaggregator-web && pnpm exec svelte-check --threshold error
cd ..
```

Expected: 0 errors at every step.

- [ ] **Step 3: Run full stack and walk all pages**

```bash
./scripts/start-dev.sh
```

In the browser (https://localhost:5373):
1. Visit every nav item; confirm AppShell + page render without console errors.
2. Go to `/connections`; verify the three orgs listed (Epic Sandbox, Cleveland Clinic blank, Summa Health blank).
3. Connect to Epic Sandbox (Client ID must be set via user-secrets beforehand).
4. Sync.
5. Walk Dashboard, /labs (filter + trend), /timeline (filter by kind), /medications, /conditions, /allergies, /encounters, /documents. Source badges visible on every row.
6. Upload a FHIR JSON bundle to /imports; verify history updates.
7. Ask the assistant a question; verify citations carry SourceBadges.
8. Disconnect the Sandbox connection via dropdown-menu; confirm rows persist but connection removed.

- [ ] **Step 4: Final commit**

```bash
git commit --allow-empty -m "UI Foundation complete — fresh-DB end-to-end smoke passes across all 13 routes"
```

---

## Self-review

After writing this plan, I checked it against the spec:

**Spec coverage:**
- Architecture (§Design → Architecture): Tasks 1-16 (frontend), 17-30 (backend), 31-34 (routing). ✓
- Routing & navigation (§Design → Routing & navigation): Tasks 13, 14, 31, 33. ✓
- Component library (§Design → Component library): Tasks 2-15. ✓
- Dashboard (§Design → Dashboard): Task 35. ✓
- Records pages (§Design → Records pages): Tasks 37, 39-43, 46. ✓
- Insights pages (§Design → Insights pages): Tasks 38, 45. ✓
- Data sources pages (§Design → Data sources pages): Tasks 36, 44. ✓
- Settings (§Design → Settings): Task 47. ✓
- Data loading contract (§Design → Data loading contract): Task 29 (api.ts refactor) + every Phase 4 loader. ✓
- Styling & theme: Tasks 2, 48. ✓
- Error & loading states: Tasks 11, 15 (DataTable.loading), 32 (+error.svelte), 31 (Toaster). ✓
- Multi-organization: Tasks 18-20 (backend), 10 (SourceBadge), 36 (/connections). ✓
- Testing: §Testing maps to Tasks 18, 21-28 (xUnit controllers), svelte-check gates at every frontend task, manual smoke at 30, 34, 49. ✓

**Placeholders:** Swept — no "TBD / implement later / handle edge cases / fill in" remain. Remaining `Similar to Task N` references are deliberate for the 4 parallel per-entity list controllers (Conditions/Allergies/Encounters/Documents), and each of those tasks spells out the controller body.

**Type consistency:** `EpicOrganization` record change (Task 19) cascades to `EpicFhirClient` (Task 20) and `api.ts` (Task 29). `TimelineItem` gaining `SourceSystem` (Task 27 backend) matches the frontend type update (Task 29). Abnormal predicate shared backend (Task 26) and frontend (Task 35).
