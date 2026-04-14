# Spec Writing Guidelines

## Structure

```markdown
# [Feature/Fix Name]

## Problem
What's broken or missing. Include root cause if it's a bug.

## Fix / Design
What changes and why. Include code snippets showing before/after for bug fixes.
For features: architecture, components, data flow.

## Testing
Numbered list of unit/integration/E2E tests to write.

## Files Changed
- `path/to/file.cs` — what changes
- `path/to/test.cs` — new tests
- `HealthAggregator.Data/Migrations/<timestamp>_<Name>.cs` — new migration if entities changed
```

## Principles

- **Proportional:** One-line bug fix = half-page spec. New feature = full spec with architecture.
- **Concrete:** Show actual code, actual types, actual file paths. No hand-waving.
- **Testable:** Every requirement maps to at least one test assertion.
- **Scoped:** One spec = one logical change. If it has two independent parts, make two specs.
- **No placeholders:** "TBD", "TODO", "implement later" are spec failures.

## Reviewer Checklist

When dispatching the spec reviewer sub-agent, ask them to check:
1. Does the spec accurately describe the current codebase state?
2. Are there gaps — requirements with no implementation path?
3. Are there contradictions between sections?
4. Is the scope right — not too broad, not too narrow?
5. Could any requirement be interpreted two ways?
6. If the spec touches EF entities, does it call out a new migration?
