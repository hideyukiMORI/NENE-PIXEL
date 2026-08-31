# Rule Waivers

A waiver is a temporary, narrow exception to one named mechanical rule. It is not an alternative architecture and does not change project policy.

## Principles

- A waiver is the last resort after the canonical implementation has been attempted.
- It applies to one rule, one reason, and the smallest possible file/declaration scope.
- It has an Issue, owner, creation date, expiry date or objective removal condition.
- It adds tests or containment appropriate to the risk.
- It is referenced next to the code suppression and in the PR.
- Expired waivers fail CI.
- A waiver that becomes permanent must be removed by fixing the code or replaced by an ADR that changes the rule.

Blanket lint/detekt baselines, directory-wide exclusions, `@Suppress("ALL")`, and undocumented generated-code exclusions are prohibited and cannot be authorized by a waiver.

## Naming

```text
WVR-NNNN-short-kebab-title.md
```

Copy `0000-template.md` and add the active waiver to the index.

## Active waiver index

| Waiver | Rule | Scope | Removal condition | Expiry |
| --- | --- | --- | --- | --- |
| — | — | — | No active waivers | — |
