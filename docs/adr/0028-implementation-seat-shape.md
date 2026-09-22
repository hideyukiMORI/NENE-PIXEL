# ADR 0028: Implementation seat shape: one seat per stage, small tool output, small tasks

- Status: accepted
- Date: 2026-09-23
- Issue: #130
- Affected rules: `QLT-005`, `QLT-011`, `QLT-017`, `QLT-018`

## Context

[ADR 0027](0027-agent-seat-model-tiers.md) assigned every background seat a model by the kind of
work. On 2026-09-22 hide measured the Claude Code transcripts (`assistant` usage records under
`~/.claude/projects/<repo>/**/*.jsonl`) and found that the budget being consumed is the Opus 5
quota, which is separate from the design seat's own quota, and that all of that consumption is the
implementation seats created by the design seat with the Agent tool:

- one seat runs 150 to 400 turns;
- its context grows to 350K to 800K tokens and is re-read on every turn;
- cache reads per seat reach 40 to 170 million tokens.

Cost is the sum over turns of the context length at that turn, so a seat that has grown fat and
keeps running costs quadratically. Model tiers alone (ADR 0027) do not reduce this: the tier table
changes who does the work, not the shape of the seat that does it. Of the 108 Opus seats started on
2026-09-22, about half did review, persona, inventory, screenshot capture, or checkout preparation,
which ADR 0027 already assigns to lower tiers.

Context grows because tool output stays in the transcript: full test output, build logs, and whole
files read once remain in every later turn. A seat that is reused for the next stage of the same
task, or for a returned review, carries all of it forward.

## Decision

### One seat per stage

A task is not run in one seat. The probe of the actual code, the implementation, and each rework
after a returned review are each a new agent. State passes between stages through the report file
and the mailbox, never through a seat's context. An Opus seat is not reused for the next piece of
work with `SendMessage`; when its stage is done, it ends. `fork` inherits the parent's whole
context and is never used for a background seat.

### Small tool output

The instruction to an implementation seat contains these lines, in the same form every time:

- run only the tests for the changed target, and tail only the failing lines;
- write build and test logs to a file and read them with `grep`;
- read only the needed range of a file, never a whole file to look for something;
- the final report is at most 30 lines: the detail goes to the report file, and the parent receives
  the path and the numbers.

### Small tasks

Three S-size tasks beat one M-size task. A returned review is one changed proposition, and one
changed proposition is a new seat (this narrows the "one proposition and one rerun set" bound of
ADR 0027 to one seat as well).

### Tiers stay in force

Review, persona opinions, inventories, screenshot capture, and checkout preparation go to `sonnet`
or `haiku` as ADR 0027 assigns them; Opus is for implementation and rework only.

### Opus 5.5 is a trial

Opus 5.5 has a lower API price than Opus 5 and the same tokenizer, but its weight in the
subscription's weekly quota is unconfirmed. It runs as the implementation seat for one day, and the
decision follows from the observed quota consumption, not from the price list.

### Measure before choosing a remedy

When quota consumption is suspected, the transcript usage is aggregated per seat first: cache read,
output, maximum context, and turn count. No further remedy is added until it can be stated in
numbers.

## Rejected alternatives

### Keep one long-lived seat per task and rely on context compaction

Rejected: compaction is not under the design seat's control, happens late, and the measured seats
had already re-read 350K to 800K tokens per turn for most of their life. The cost is paid before
compaction helps.

### Move implementation to a lower tier

Rejected: the tier test of ADR 0027 is who fixes the error, and implementation errors are fixed by
the implementation seat; a lower tier would move the cost to more returned reviews, which the
measurement shows are the expensive part.

### Limit turns per seat with a hard cap

Rejected: a cap cuts a seat mid-stage and loses the work; the seat-per-stage rule ends seats at
a boundary where the report file already holds the state.

## Consequences

### Benefits

- Each seat starts with a small context and ends before it grows; the quadratic term is bounded by
  the stage, not the task.
- Report files and the mailbox become the only handoff between stages, which the design seat can
  read directly.
- Small tool output keeps the transcript readable for review of the seat itself.

### Costs and risks

- Each new seat re-reads the Issue and the report file; this is a fixed cost per stage that is
  small against the measured per-turn re-read.
- A probe report that omits something forces the implementation seat to probe again; the report
  form (path and numbers, detail in the file) is the safeguard.
- The Opus 5.5 trial may show no quota advantage; the decision is then recorded as a one-line
  amendment here, not a second policy.

## Enforcement impact

- `AGENTS.md`: the "Agent seats and model tiers" section gains the seat lifetime rule and the
  implementation seat instruction form.
- The current handoff gains a policy section and a first line under next actions.
- No compiler, test, CI, or dependency change. Documentation validation covers links and ADR shape.

## Migration and rollback

Apply from the next Opus implementation seat onward (Issue #128 is the first). Existing seats are
not restarted. Rollback is another accepted policy decision; never two active seat-shape rules.

## Related

- Issue: [#130](https://github.com/hideyukiMORI/NENE-PIXEL/issues/130)
- PR:
- [ADR 0027](0027-agent-seat-model-tiers.md)
- Supersedes: none
- Superseded by: none
