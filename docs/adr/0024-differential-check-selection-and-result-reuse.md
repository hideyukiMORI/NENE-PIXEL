# ADR 0024: Differential check selection, cross-stage result reuse, and scope discipline

- Status: accepted
- Date: 2026-09-22
- Issue: #113
- Affected rules: `QLT-005`, `QLT-011`, `QLT-012`, `QLT-013`, `QLT-017`, `QLT-018`

## Context

hide reported on 2026-09-22 that unrelated tests and repeated verification are slowing development,
and asked that check selection, result reuse, and automation follow the change instead of habit.
[ADR 0011](0011-change-scoped-verification.md) already makes verification change-scoped, reserves
the full canonical suite for the final merge candidate, and requires identity-proven artifact reuse.
The recorded work since then shows the remaining gaps are behavioral, not tooling gaps:

- QLT-011 names changed paths and applicable checks but does not require that every executed check
  be traceable to a regression the current diff could cause, so "nearby" suites still run.
- QLT-012 ties reuse to unchanged inputs but does not say that a change of assignee, work stage,
  appended documentation, or commit identity is not an input change, so handoffs and independent
  reviews rerun checks that already passed on the same tree.
- No rule tells an agent what to do with a failure the diff cannot have caused; the observed
  response is a full rerun or an unrelated fix inside the current Issue.
- The retry-until-green prohibition in QLT-013 covers performance samples only; a functional test
  that passes on its second attempt is still treated as passing.
- Nothing states that hooks, CI, and agent automation are bound by the same policy. The repository
  currently has no local Git hooks and no agent hooks that run Gradle; the required CI `quality` job
  on a non-draft pull request is the only automatic full run, and it fails closed before toolchain
  setup while the pull request is draft.

## Decision

Extend the verification execution policy in [Quality Gates](../QUALITY_GATES.md) as follows and keep
it the single normative authority.

1. Checks are selected from the diff. Before a check runs, the agent names the changed behavior and
   the regression the check detects in the changed code or in a direct dependent or caller whose
   contract the change affects. A check that cannot be explained this way does not run. Documentation,
   comment, and rule changes need no app behavior tests; a hook or developer-tool change needs only that
   tool's short functional check.
2. A passing result is reused across a change of assignee, work stage, appended documentation, and
   commit identity when the verified paths and their relevant dependencies are unchanged. Reruns need a
   relevant change, a failure, or a concrete unverified concern. Reviewers read the recorded result and
   its tree identity instead of re-executing it.
3. The full canonical suite is never a default. It runs locally only when narrow checks cannot show
   the impact, such as a shared build-logic, toolchain, or cross-module contract change, and the target
   and reason are stated in one line beforehand. "To be safe", habit, and "the hook runs it anyway" are
   not reasons, and no new approval ceremony is added.
4. New rule `QLT-017`: failures caused by the diff are fixed; failures the diff cannot have caused are
   recorded with evidence in a separate Issue, and the current work is neither stopped for a full rerun
   nor widened to fix them. A test that passes only on retry is failing for every check, not only for
   performance samples.
5. New rule `QLT-018`: hooks, CI, and agent automation follow the same policy. No automation runs an
   unscoped full suite or forces a rerun of verified work at push, review, or merge. The repository
   keeps no local Gradle-running hooks; adding one requires an accepted decision. Existing target selection and
   recorded result reuse are preferred over any automatic test-selection infrastructure, and
   verification is never disabled wholesale.

The CI workflow, `check` contents, tests, and the required status-check context are unchanged.

## Rejected alternatives

### Skip the `quality` job on non-base `edited` events

Rejected: a job skipped through an `if:` condition still creates a check run with conclusion
`skipped`, which GitHub treats as satisfying a required check and which becomes the latest run for
that head commit. A title edit could therefore overwrite a failing result and unblock merge. The
full run on every non-draft event remains the single pre-merge gate, and iteration stays in draft
where the job fails closed within seconds.

### Build an automatic change-to-test selection tool

Rejected: the repository's Gradle module tasks and `--tests` filters already provide target
selection, and the observed cost comes from unexplained selection and repetition, not from the
absence of a tool. A selection engine would add a second authority over what "affected" means.

### Add a pre-push or agent hook that runs the affected checks

Rejected: an automatic run cannot know that the same tree already passed, so it would reintroduce
the forced reruns this decision removes. The agent records the result and its tree identity instead.

### Keep the retry allowance for functional tests

Rejected: a test that needs a second attempt hides a product or harness defect; labeling it green
removes the evidence the owning Issue needs.

## Consequences

### Benefits

- Each executed check has a stated purpose; unrelated suites stop running during iteration.
- Handoffs between design, implementation, and review reuse recorded results instead of repeating them.
- Unrelated failures become tracked Issues with evidence instead of scope creep or full reruns.
- Flaky behavior is recorded rather than laundered by retries.

### Costs and risks

- Selection depends on the agent's dependency reasoning; a missed direct dependent is caught by the
  required CI full run at the merge boundary, not earlier.
- Recording reuse identity adds a line to reports and PR descriptions.
- These are agent and reviewer obligations; Gradle does not detect a violation of them.

## Enforcement impact

Update `AGENTS.md`, Quality Gates (QLT-011, QLT-012, new QLT-017 and QLT-018), Development Workflow,
Development Setup, and the ADR index. Existing documentation validation checks links, rule IDs, and
ADR shape. No compiler, module, static-analysis, test, or CI change is required; the CI trigger set,
the fail-closed draft guard, and the canonical command are unchanged.

## Migration and rollback

Apply prospectively to every Issue, including in-flight measurement work under #106; historical
results, verdicts, and protocol identities are untouched. No production, build, or schema migration
occurs. Rollback requires another accepted policy decision restoring one normative execution policy;
never maintain two active policies.

## Related

- Issue: [#113](https://github.com/hideyukiMORI/NENE-PIXEL/issues/113)
- PR:
- [ADR 0011](0011-change-scoped-verification.md)
- [Quality Gates](../QUALITY_GATES.md)
- Supersedes: none; amends the execution policy accepted in ADR 0011
- Superseded by: none
