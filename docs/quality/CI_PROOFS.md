# CI and Main Protection Proofs

Status: verification in progress

- Date: 2026-09-01
- Issue: #8
- Work package: `P0-04`
- Required check: `quality`

This record is completed after the workflow has run on GitHub and the default-branch ruleset has been read back. The proof procedure is intentionally limited to the Issue branch: establish a green run, activate the ruleset, push one failing unit-test assertion, confirm that `main` merge is blocked, restore the assertion, and obtain a final green run. The squash merge must contain only the restored source.

## Positive CI proof

Pending the first GitHub-hosted run.

## Blocked red-check proof

Pending the intentional failing revision.

## Repository ruleset read-back

Pending ruleset creation after the first successful `quality` context exists.
