# Plan Writing Guidelines

## Structure

Every plan starts with:

```markdown
# [Feature Name] Implementation Plan

**Goal:** One sentence
**Architecture:** 2-3 sentences
**Tech Stack:** Key technologies
```

Then tasks:

```markdown
### Task N: [Name]

**Files:**
- Create: `exact/path/to/file.cs`
- Modify: `exact/path/to/existing.cs:123-145`
- Test: `HealthAggregator.Tests/exact/path/to/test.cs`
- Migration: `HealthAggregator.Data/Migrations/<timestamp>_<Name>.cs` (if entities changed)

- [ ] Step 1: Write failing test (include actual test code)
- [ ] Step 2: Run test, verify it fails (include command + expected output)
- [ ] Step 3: Write implementation (include actual code)
- [ ] Step 4: Run test, verify it passes (include command)
- [ ] Step 5: Commit (include git command with message)
```

## Principles

- **TDD always:** Test before implementation in every task.
- **Complete code:** Every step that changes code shows the actual code. No "implement similar to Task 3".
- **Exact paths:** Full file paths, line numbers where relevant.
- **Exact commands:** `dotnet test --filter "FullyQualifiedName~ClassName" --no-build`, not "run the tests".
- **Bite-sized steps:** Each step is 2-5 minutes of work.
- **No placeholders:** "Add appropriate error handling" is a plan failure.

## Project Conventions

- Backend tests: xUnit in `HealthAggregator.Tests/`
- Build once: `dotnet build`, then all subsequent test runs use `--no-build`
- Frontend checks: `cd healthaggregator-web && pnpm exec svelte-check --threshold error`
- EF schema changes: `dotnet ef migrations add <Name> --project HealthAggregator.Data --startup-project HealthAggregator.Api`
- Commit messages: comprehensive, no Co-Authored-By, no mention of Claude/Anthropic
- DB safety: never delete the local SQLite dev DB without explicit user confirmation; always back up first

## Reviewer Checklist

When dispatching the plan reviewer, ask them to check:
1. Does every spec requirement have a corresponding task?
2. Are all code blocks complete (no "..." or "similar to above")?
3. Do types/signatures stay consistent across tasks (matching `HealthAggregator.Data.Entities` and `HealthAggregator.Core.Models`)?
4. Are test assertions actually testing the right thing?
5. Is the task ordering correct (dependencies respected, tests before implementation)?
6. If any EF entity changes, is there an explicit migration step?
