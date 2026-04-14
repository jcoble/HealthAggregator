---
name: auto-resolve
description: Autonomously resolves a bug, feature, or fix through the COMPLETE superpowers pipeline — git worktree isolation, deep research, brainstorming, spec writing, implementation planning, subagent-driven development, verification, code review, and branch finishing — without stopping for user input. Use when the user says "auto resolve", "resolve autonomously", "just do it", or wants hands-off end-to-end resolution of a task.
---

# Autonomous Resolve

Run the COMPLETE superpowers pipeline autonomously. Every phase below is MANDATORY — do not skip, abbreviate, or combine phases regardless of task size. Make your own decisions. Only stop for the user if genuinely blocked.

**Task:** $ARGUMENTS

---

## Phase 1: Research & Understand

1. **Read context** — if the task references a tracked TODO/issue, read it. Check git history, related specs in `Docs/specs/`, and existing tests in `HealthAggregator.Tests/`.
2. **Deep-dive the codebase** — dispatch an Explore agent to understand:
   - How the existing feature/system works (controllers in `HealthAggregator.Api/Controllers`, services in `HealthAggregator.Api/Services` and `HealthAggregator.Data/Services`, entities in `HealthAggregator.Data/Entities`)
   - What files are involved and what patterns are established
   - What tests exist for related functionality
   - What UI components are involved (note their existing `data-testid` attributes if any)
3. **Form your approach.** You are the architect — decide the best path forward.

---

## Phase 2: Git Worktree

**MANDATORY.** All work happens in an isolated git worktree.

Invoke `superpowers:using-git-worktrees` to create an isolated worktree for this task. All subsequent phases operate inside the worktree. Do NOT work on the main branch or the user's current branch.

---

## Phase 3: Brainstorming

**MANDATORY.** Even for "simple" fixes.

Invoke `superpowers:brainstorming` to:
- Explore project context and understand the problem deeply
- Consider 2-3 approaches with trade-offs
- Select the best approach with clear reasoning
- Since this is autonomous, make the decisions yourself — do not ask the user clarifying questions. Use your research from Phase 1 to answer them.

Write and commit the spec to `Docs/specs/YYYY-MM-DD-<topic>-design.md`.

---

## Phase 4: Design (Spec)

1. Write a focused design spec proportional to the task (small fix = short spec, new feature = detailed spec).
   - Cover: what changes, why, architecture, data flow, error handling, testing
   - Save to `Docs/specs/YYYY-MM-DD-<topic>-design.md` (update the file from Phase 3 if brainstorming already created it)
2. **DO NOT self-review the spec.** Dispatch a **separate spec reviewer sub-agent** (Opus) to check:
   - Does it make sense given the codebase?
   - Are there gaps, contradictions, or missed edge cases?
   - Is the scope right?
   - The reviewer MUST read the actual source files referenced in the spec to verify claims.
3. Fix any issues the reviewer raises. Commit the spec.

**No self-review.** The spec author cannot review their own spec — a fresh sub-agent with no prior context must do the review.

For detailed spec writing guidelines, see [spec-guidelines.md](spec-guidelines.md).

---

## Phase 5: Implementation Plan

**MANDATORY.** Invoke `superpowers:writing-plans` to create the plan.

1. Write a detailed implementation plan — exact file paths, complete code, TDD.
   - Save to `Docs/plans/YYYY-MM-DD-<topic>.md`
   - **The plan MUST include a final verification task** (see below)
2. **DO NOT self-review the plan.** Dispatch a **separate plan reviewer sub-agent** (Opus) to verify:
   - Plan matches spec, steps are complete (no placeholders), types are consistent
   - Method signatures, property names, and EF entity types match the actual codebase (reviewer must read source files to verify)
   - Any new EF entity changes include a `dotnet ef migrations add <Name>` step
   - TDD order is correct (tests before implementation)
3. Fix any issues the reviewer raises. Commit the plan.

**No self-review.** The plan author cannot review their own plan — a fresh sub-agent with no prior context must do the review. Skip the self-review step in the `writing-plans` skill; the sub-agent review replaces it.

### Required Final Task in Every Plan

The implementation plan MUST end with this task (adjust for the actual files touched):

```markdown
### Task N (Final): Verification

- [ ] Step 1: `dotnet build` (must succeed)
- [ ] Step 2: `dotnet test --no-build` (all tests pass)
- [ ] Step 3: If frontend changed, `cd healthaggregator-web && pnpm exec svelte-check --threshold error` (0 errors)
- [ ] Step 4: If Epic OAuth, FHIR ingestion, or DB schema changed, run `./scripts/start-dev.sh` and smoke-test the affected flow in a browser
- [ ] Step 5: Confirm no unrelated diffs via `git diff main..HEAD --stat`
```

If the task has NO frontend changes, mark the svelte-check step as "Skip — no frontend changes" so there is an explicit, auditable decision.

For detailed plan writing guidelines, see [plan-guidelines.md](plan-guidelines.md).

---

## Phase 6: Implement (Subagent-Driven Development)

**MANDATORY.** Invoke `superpowers:subagent-driven-development` to execute the plan.

This is the ONLY allowed execution mode for auto-resolve. Do NOT execute inline.

1. Create tasks for each implementation step from the plan.
2. **Fresh implementer sub-agent per task** — each agent gets a clean context with:
   - The spec
   - The plan
   - The specific task to implement
   - Instructions to follow TDD (test first, verify red, implement, verify green)
   - **MANDATORY data-testid rule:** Every Svelte component or HTML element you create or modify MUST have a `data-testid` attribute. This includes buttons, inputs, links, cards, rows, dialogs, modals, dropdowns, tabs, badges, status indicators, and any key display element. Use descriptive kebab-case names. Omitting `data-testid` is a review failure.
   - **Build discipline:** Run `dotnet build` once at the start of the task, then every subsequent `dotnet test` invocation uses `--no-build`.
3. **After each task completes, dispatch TWO reviewer sub-agents** (both Opus):
   - **Spec reviewer:** Does the implementation match the spec?
   - **Code quality reviewer:** Code quality, patterns, test coverage, no regressions, **and verify every new/modified UI element has a `data-testid` attribute** — flag any missing ones as a blocking issue.
4. Fix any issues raised before moving to the next task.

### TDD Discipline (Non-Negotiable)

Every implementation task MUST follow TDD:
- Write the failing test FIRST
- Verify it fails for the RIGHT reason
- Write the minimum implementation to pass
- Verify it passes
- Refactor if needed

---

## Phase 7: Verification

**MANDATORY.** Invoke `superpowers:verification-before-completion`.

1. `dotnet build && dotnet test --no-build` — ALL tests must pass.
2. If any frontend file changed: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`.
3. If Epic OAuth, FHIR ingestion, or DB schema changed, run `./scripts/start-dev.sh` and smoke-test the affected flow in a browser. Confirm rows land in SQLite as expected.
4. Fix any failures. Do NOT proceed until green.

---

## Phase 8: Code Review

**MANDATORY.** Invoke `superpowers:requesting-code-review`.

**DO NOT self-review.** Dispatch a **separate code reviewer sub-agent** (Opus, use `superpowers:code-reviewer` agent type) to review ALL changes against the spec and plan. You cannot review your own code.

1. The reviewer MUST:
   - Run `git diff main..HEAD` to see actual changes (not trust any summary)
   - Read every changed file
   - Verify all spec requirements are implemented
   - Verify all plan tasks are completed
   - Check code quality, test coverage, no regressions
   - Check for leftover TODOs, placeholders, or debug code
   - Verify no unrelated changes leaked onto the branch
   - Verify any EF entity change has a corresponding migration in `HealthAggregator.Data/Migrations/`
2. Fix any issues the reviewer raises.

---

## Phase 9: Finish Branch

**MANDATORY.** Invoke `superpowers:finishing-a-development-branch`.

1. **Update any tracked TODO/issue** if the task came from there.
2. Commit all changes with a comprehensive message.
3. Present the branch for merge/PR — since this is autonomous, default to creating a PR unless the user specified otherwise.
4. Summarize what was done to the user.

---

## Rules

- **EVERY PHASE IS MANDATORY.** Do not skip phases. Do not combine phases. Do not abbreviate phases.
- **NO SELF-REVIEW.** You cannot review your own work. Every review (spec, plan, code) MUST be done by a separate sub-agent dispatched specifically for that review. Self-review steps in upstream skills (brainstorming, writing-plans) are replaced by sub-agent review in auto-resolve.
- **Git worktree always.** Never work on the user's current branch.
- **Subagent-driven development always.** Never execute plan tasks inline.
- **Autonomous decisions.** Don't ask the user unless genuinely blocked.
- **Proportional effort.** Scale the DEPTH of each phase to the task size, but still DO every phase. A one-line fix still gets a (brief) brainstorm, a (brief) spec, a (brief) plan, and implementation — just shorter ones.
- **Follow project conventions.** TDD, comprehensive commit messages, no Co-Authored-By, no mention of Claude/Anthropic in PR descriptions.
- **YAGNI.** Don't over-engineer. Minimum complexity for the current task.
- **All sub-agents MUST be Opus.** Never use Sonnet or Haiku for sub-agents.
- **EF migrations are the schema source of truth.** Any entity change requires a new migration via `dotnet ef migrations add <Name> --project HealthAggregator.Data --startup-project HealthAggregator.Api`. Never hand-edit applied migrations.
- **data-testid on all UI elements.** Any UI changes MUST include `data-testid` attributes on interactive elements (buttons, inputs, dialogs, links, cards, rows, etc.) and key display elements. Use descriptive kebab-case names (e.g., `data-testid="epic-connect-btn"`, `data-testid="lab-trend-card"`). Even though E2E coverage isn't wired up yet, adopting the convention now keeps it cheap to add later. Include the testid in implementation plan steps so future test steps can reference them directly.
