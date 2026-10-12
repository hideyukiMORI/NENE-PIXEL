# ADR 0038: Current agent roles, scoped guidance, and memory authority

- Status: accepted
- Date: 2026-10-12
- Issue: #204
- Affected rules: `ARC-001`, `ARC-012`, `QLT-005`, `QLT-011`, `QLT-012`, `QLT-016`, `QLT-017`, `QLT-018`, `QLT-019`

## Context

On 2026-10-12 hide assigned Astra the design seat and authorized suitable SOL/LUNA background
work for implementation, investigation, and verification. hide then requested an audit and cleanup
of instructions and memories that no longer match the model or project. This replaces the Claude
quota-specific operating prescriptions of ADR 0027 and ADR 0028, not the product's quality gates.

The audit found a fixed Opus/Sonnet/Haiku table, a new agent for every probe/implementation/rework,
and a requirement to read sixteen documents before any production change. Local memories repeat
old C-drive locations, a module-wide device-test recipe, and the superseded Profile pair condition.
Later amendments often sit below those imperatives, so reading the first paragraph can select the
wrong instruction. The original checkout is on an older dirty branch; updating `main` alone does
not update its automatically read entry file.

## Decision

### Roles follow the owner's current assignment

For NENE-PIXEL's owner-directed Astra workflow, the design seat keeps responsibility for
requirements, architecture, ambiguous decisions, acceptance, integration, and communication. It
delegates bounded execution to an available SOL or LUNA model when that model can meet the task's
precision and the delegation is useful. Choose from the runtime's actual capabilities; do not
translate old Claude aliases into assumed equivalents or claim unmeasured cost advantages.

Routine tightly coupled work may stay in the current seat. A background agent may complete a
coherent task including its related investigation, implementation, and correction; there is no
per-file/per-stage quota, forced restart, or blanket ban on continuation. Independent review is
read-only and is used where the change's risk warrants another view, not for every small edit.
The parent owns scope, model choice, and acceptance of the actual diff and evidence. Helpers do
not recursively delegate without the parent's explicit instruction. Runtime limits and higher
priority instructions still apply; if delegation is unavailable, do the work directly.

A delegation states the outcome, owned paths, relevant rules/decisions, D-drive lab location,
affected checks, and completion/cleanup conditions. Parallel workers do not edit the same files
or run Gradle tasks sharing a module's mutable outputs concurrently. Preserve logs outside the
working copy; return the result, changed paths, evidence identity, and unresolved findings without
copying whole logs into the conversation. Useful precision replaces fixed report-line limits.

For visual design, use Claude Code when requested and available, or the requested equivalent
design capability. An unavailable skill, quota limit, or provider failure is reported honestly;
it does not justify repeated attempts or block independent work. Model/provider changes in other
projects are outside this decision.

### Read by affected contract

`AGENTS.md` is a concise entry and document router. At task entry read the focused live Issue and
the applicable workflow/verification sections. Before production work, establish the affected
architecture/state/command boundaries and coding rules; read the documents, ADRs, waivers, and
consumer contracts that govern those boundaries. Use the charter and layout when project purpose
or ownership is not already established. Storage work reads the current format plus affected
migration formats; historical v1/v2 are not the default format for all work.

Previously read, unchanged guidance stays usable in the same task. A task switch or changed rule
requires the relevant new content, not an automatic reread of the entire document stack. Missing
understanding expands the reading scope. This changes discovery, not compliance with any rule.
The task report records affected rules and verification rationale under QLT-011.

### One authority for each kind of information

- Canonical repository documents and accepted ADRs own durable project rules. A newer explicit
  owner instruction governs its stated scope and is reconciled into those documents when durable.
- Live GitHub Issues/PRs own task and acceptance state. Handoffs include an observation time, exact
  source/artifact identity, and links; they are restart aids, not a second task board.
- Local memory is a short index of useful facts and non-obvious lessons. It points to the rule
  source, states its scope/date, and is corrected in place when superseded. It cannot authorize a
  new experiment, destructive action, or global policy from an old session's event.
- Historical decisions, FAIL/invalid results, and raw artifacts remain intact. Old active memory
  text is backed up with hashes outside the active memory index, then replaced by current guidance
  or a clearly historical pointer. Do not append a new imperative below an incompatible old one.

No new state database, automatic dispatcher, memory synchronization service, or rule-selection
framework is introduced. A repeated manual procedure becomes a script only when its concrete
benefit warrants a focused change; repetition count alone is not a trigger. Existing `tools/`
scripts remain available with their documented contracts and narrow self-tests.

### Completion and verification

Continue authorized work through applicable verification, review, PR, required CI, integration,
and cleanup; do not stop merely because an implementation seat or stage ended. Keep work draft
when required acceptance is missing. A real dependency on hide, such as device unlock or an
explicitly required eye check, is stated specifically while independent work continues.

QLT-011 through QLT-019 remain the verification authority. Reuse equivalent successful evidence;
do not rerun a module's full device suite because a memory says to do so. Feature performance
workloads go to the phase gate, not a feature-level collection. Profile adoption follows current
ADR 0010; an old pair recipe is historical, and a future collection must reconcile its Issue,
accepted protocol, and executable harness before execution. No threshold, gate, or data protection
is weakened here. Completed task copies follow the owner's D-drive cleanup instruction after
checking integration, unique/ignored artifacts, active references, and links; uncertain or active
copies remain with their exact path and reason recorded.

## Rejected alternatives

### Replace Claude model names in the old table

That would preserve the costly stage splitting and historical quota assumptions. Assign by task
and verified capability instead.

### Delete all memories or add a new policy engine

Deletion loses useful evidence; a policy engine creates another authority and maintenance task.
Keep concise current pointers and a recoverable historical record.

## Consequences

### Benefits

- Entry guidance describes current work and directs attention to relevant contracts.
- Delegation can finish coherent work without repeated handoffs and duplicate verification.
- Historical recipes cannot silently become new execution instructions.

### Costs and risks

- Scoped reading depends on identifying affected consumers; explicit rule/verification reporting
  and risk-based review remain necessary.
- Local instructions can be loaded once per session. File updates affect future reads/sessions;
  do not claim that rewriting a file removes already loaded higher-priority instructions.
- An old dirty checkout needs a backed-up, reviewed entry-file update without resetting its code.

## Enforcement impact

Update `AGENTS.md`, ADR statuses/index, and setup/layout references. Reconcile local memory,
handoff, and lab guidance without committing private paths or machine settings. Documentation
validation checks links/ADR shape; a read-only review checks semantic consistency. No production,
compiler, test selection code, CI job, dependency, or measurement harness changes.

## Migration and rollback

ADR 0027 and ADR 0028 become superseded historical records with links here. Keep their original
decision bodies and all measured evidence. Back up local files with before/after identities and
record which active entry files were synchronized. Other project memories and managed plugin
bundles are outside scope. Rollback is an explicit owner decision restoring one active policy,
not simultaneous conflicting tables; local originals remain recoverable from the task backup.

## Related

- Issue: [#204](https://github.com/hideyukiMORI/NENE-PIXEL/issues/204)
- [Development Workflow](../DEVELOPMENT_WORKFLOW.md)
- [Verification policy](../QUALITY_GATES.md#verification-execution-policy)
- Supersedes: [ADR 0027](0027-agent-seat-model-tiers.md), [ADR 0028](0028-implementation-seat-shape.md)
- Superseded by: none
