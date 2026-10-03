# 2026-10-03 — layer underlay fixture handoff

Issue [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145) remains the task-state authority;
Draft PR [#187](https://github.com/hideyukiMORI/NENE-PIXEL/pull/187) remains preparation.
Read the [session report](2026-10-03-layer-underlay-fixture.md) and the preceding
[frame connection handoff](2026-10-03-layer-frame-connection-handoff.md).
Implementation base: `d997627e5e4eb6eb070f8dfa556468d24bd83090`; use the PR head for current commit.
Work remains in development-lab `worktrees/issue-145`, branch `perf/145-layer-gate`.

## Current boundary

The underlay PNG and its encoded/RGBA hashes are pinned in the prospective protocol. The checked
asset, JDK-only host generator and exact placement checks pass; both merged AndroidTest assets
match. The production decoder test compiles and is opt-in with `p4LayerFixtureCheck=true`.
It has not run on Android. The source SHA/blob ledger and retained logs are in the report.
The seven baseline/candidate pick/placement/decoder sources are identical.

Both roles must use fresh empty single-layer 256-by-256 works, this exact PNG and black palette
index 0. A successful canonical pick produces `(0,0,0.25)`, alpha 128, Shown and Resting without
an opacity slider, fit or adjust gesture. The host factory proof does not substitute for actual
provider bytes, framework grant, app picker/load, visible pattern or completion of pending recall.

The existing test DocumentsProvider requires framework permission and allows fresh `i89-...`
names. Do not assume the app instrumentation's file directory is the provider UID's storage,
transfer a URI grant between the app and persistence test APKs, or bypass the actual picker.
Provider staging/fixture-load automation and complete phase admission are still unimplemented.

## Next implementation

Continue directly as one agent under hide's current scope; use read-only independent review only
when needed. Reuse unchanged successful checks under QLT-011–019, including at commit/push/handoff.

Implement the already-specified live-editor memory checkpoints and actual autosave/SAF storage
boundaries through the existing owners. Resolve canonical provider staging and actual grants for
both the maximum document and underlay. Keep full-pixel/hash/setup checks outside timed intervals.
Then complete the four-artifact manifest and existing outer adapters/frame-slot-v2, artifact/profile
identities, whole-phase budgets/stop rules and native preservation/restoration integration.

Do not enable phase live collection from host-only success. The manifest still admits historical
v7 only; collector phase live/geometry refusal remains. Keep one immutable phase/manifest identity
and the canonical separate wrapper/raw roots. Do not introduce a second collection route.

No device state changed in this slice. Historical original snapshot/APK evidence remains historical.
A current original snapshot and complete preservation/last-install/restore proof are required before
future isolation. Never delete or overwrite lab evidence, including the retained formatting failure.
Do not reset the separate historical checkout or include the two pre-existing dirty session reports
and older untracked notes in focused commits. Index only named changed files.

No new app cost, dependency, production API, project schema or threshold change. Remaining risks
are incomplete device integration and unmeasured phase performance. Active waivers: none.
