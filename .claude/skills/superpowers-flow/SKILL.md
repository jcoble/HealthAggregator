---
name: superpowers-flow
description: Collaborative superpowers flow with user involvement — walks through brainstorming, spec design, implementation planning, and execution with the user making key decisions at each stage. Use when the user says "superpowers flow", "let's design this", "let's plan this", or wants to collaborate through the full development lifecycle.
disable-model-invocation: true
---

# Superpowers Flow (Collaborative)

Walk through the full superpowers pipeline WITH the user — they make key decisions, review designs, and approve before each phase.

**Task:** $ARGUMENTS

## Process

This skill orchestrates the existing superpowers skills in sequence. Invoke each one at the appropriate stage:

### 1. Brainstorming
Invoke `superpowers:brainstorming` to:
- Explore project context
- Ask clarifying questions (one at a time)
- Propose 2-3 approaches with trade-offs
- Present design section by section, get user approval
- Write and commit the spec

**Spec review:** DO NOT self-review the spec. Dispatch a **separate sub-agent** (Opus) to review the spec against the actual codebase. The reviewer must read source files to verify claims, check for gaps, contradictions, and missed edge cases. Fix any issues the reviewer raises before proceeding.

### 2. Implementation Plan
Invoke `superpowers:writing-plans` to:
- Create a detailed, step-by-step implementation plan from the approved spec
- Include exact file paths, complete code, TDD steps
- **Every task that creates or modifies UI must list the `data-testid` attributes** that will be added — include them in the plan so future test steps can reference them directly

**Plan review:** DO NOT self-review the plan. Dispatch a **separate sub-agent** (Opus) to review the plan against the spec. The reviewer must verify method signatures, property names, and types against actual source files (HealthAggregator.Api / .Core / .Data, healthaggregator-web/src). Fix any issues the reviewer raises.

- Get user approval on the plan

### 3. Execute
Ask the user which execution mode they prefer:

**Option A: Subagent-Driven (recommended)**
- Invoke `superpowers:subagent-driven-development`
- Fresh sub-agent per task, two-stage review after each
- Each sub-agent MUST add `data-testid` attributes to every new/modified UI element
- Code quality reviewer MUST flag missing `data-testid` as a blocking issue

**Option B: Inline Execution**
- Invoke `superpowers:executing-plans`
- Batch execution with review checkpoints
- Same `data-testid` requirement applies — every new/modified UI element needs one

### 4. Code Review
Before final verification, dispatch a **separate code reviewer sub-agent** (Opus, use `superpowers:code-reviewer` agent type) to review ALL changes against the spec and plan. You cannot review your own code. The reviewer must run `git diff` to see actual changes, read every changed file, and verify no unrelated changes leaked onto the branch.

### 5. Verification
- Backend (`.cs` changed): `dotnet build && dotnet test --no-build` — must show 0 errors and 0 failures.
- Frontend (`.svelte` / `.ts` / `.js` in `healthaggregator-web/` changed): `cd healthaggregator-web && pnpm exec svelte-check --threshold error` — must show 0 errors.
- If the changes touch the Epic SMART OAuth flow, the FHIR ingestion path, or DB schema, run a manual smoke pass: start `./scripts/start-dev.sh`, walk the affected feature in the browser, confirm rows land in SQLite as expected.

### 6. Complete TODO
If the task came from a tracked TODO (e.g. `Docs/TODO.md` if/when adopted, or a GitHub issue):
- Mark the original item complete or remove it
- Reference the commit/PR that completed it

### 7. Finish
Invoke `superpowers:finishing-a-development-branch` to:
- Present merge/PR/cleanup options
- Complete the work based on user's choice

## Key Difference from /auto-resolve

This flow **keeps the user involved** at every decision point. Use this when:
- The task is ambiguous and needs discussion
- The user wants to understand and guide the approach
- Multiple valid approaches exist and the user should choose
- The changes are high-risk and need human review at each stage

Use `/auto-resolve` instead when the task is well-defined and the user wants hands-off execution.

## UI Rules (Forward-Looking Convention)

Automated UI testing isn't wired up in this repo yet, but **every new or modified UI element MUST carry a `data-testid` attribute** so that when E2E coverage is added later it has reliable selectors. This includes:

- Interactive elements: buttons, inputs, links, dialogs, modals, dropdowns, tabs, toggles, selects
- Display elements: cards, rows, badges, status indicators, labels, headings, alerts, toasts
- Container elements: panels, sections, lists, tables, forms

Use descriptive kebab-case names (e.g., `data-testid="epic-connect-btn"`, `data-testid="lab-trend-card"`, `data-testid="assistant-citation-row"`).

**Missing `data-testid` is a blocking review issue.** Adopt the discipline now so retrofitting tests later doesn't require touching every component.
