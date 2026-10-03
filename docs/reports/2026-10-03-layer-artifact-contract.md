# 2026-10-03 — four-role artifact/source contract

Issue [#145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145), Draft PR
[#187](https://github.com/hideyukiMORI/NENE-PIXEL/pull/187). Rules ADR 0031/0035, ARC-007,
QLT-011–019. Waivers: none. Base: `8e11f931c8f16c15cdaa94fab520319ca3bfb8a8`.
Acceptance remains OPEN; no device operation or performance sample ran.

## Scope and behavior

The existing `p4-indexed-preflight.ps1` resolves four immutable production roles and combines its
unchanged frame, memory and storage subcatalogs into the ordered 24-slot phase. The shared candidate
production is accepted #172, `1f9bb1637058d3fa4a98122f4942406211bd1c69`. Baseline roles have debug
app/test and release-like APKs; only candidate has a persistence publication APK. Production-tree
equality and descendant test-overlay identity remain required. The phase does not require unused
baseline publication builds or historical host timing classpaths.

Explicit phase selection includes every tracked app/persistence AndroidTest file, measurement
tooling and checked phase asset in the source inventory. Required provider Java/helper, manifest,
SAF helper/admission and fixture paths cannot be omitted. Per-file Git blobs and hashes retain the
existing path/link and closed-set checks. Packaged test fixtures require unique exact entries with
pinned sizes/hashes; production APKs must contain neither fixture. The packaged provider must bind
the actual authority/class, exported/grant flags, permission and DocumentsProvider action.

The actual SDK aapt2 prints full Android namespace names and textual booleans. Only phase packaging
normalizes the known namespace prefix; provider validation supports those exact booleans and the
older typed representation. It checks attribute ownership and action ancestry, rather than finding
those strings anywhere in the manifest. Historical packaging defaults remain unchanged.

The [protocol](../quality/P4_LAYER_PHASE_PROTOCOL.md#four-immutable-artifact-roles-and-complete-source-inventory)
records these prospective decisions. `validate-p4-layer-artifacts.ps1` owns the narrow host checks.
No production code, dependency, format, public API, autosave constant or threshold changes. No
per-operation scan, allocation/copy, pixel arithmetic or preview work is added to the application.

## Selected checks and retained results

Scope was recorded in Issue #145 before execution. Lab root:
`evidence/145-layer-artifact-contract/20261003T215813844-825f320c542b419b95ffd429d3580d4a/`.

- `pwsh -NoProfile -File docs/quality/validate-p4-layer-artifacts.ps1 -OutputDirectory <fresh>`
  — PASS, 54 cases, 17.4800457 s inside validator; `contract-git-name-correction.log` and its result
  directory. Actual temporary Git files test closed inventories, omissions, duplicates, added
  entries, wrong roles and build-blob drift. Actual ZIPs test exact/missing/duplicate/aliased/wrong
  size/same-size changed fixtures and production leakage. Pure checks cover exact role/schedule
  projection, historical defaults, unknown protocols and provider manifest ownership/facts.
- The initial validator function named `Git` shadowed the external command and recursed. Its two
  own validator/wrapper processes were stopped; initial outputs and `contract-initial-stopped.json`
  remain. The helper was renamed and calls `git.exe` explicitly. No result from that invocation is
  accepted, and no device process or source checkout was affected.
- `pwsh -NoProfile -File docs/quality/validate-p4-layer-artifacts.ps1 -OutputDirectory <fresh> -CaseGroup RoleBoundaries`
  — PASS, 26 additional cases, 2.2484466 s; `role-boundaries-initial.log` and results. This runs only
  the new group. It checks actual Git ancestry, all four production/overlay/kind-set refusals,
  historical compiled-inventory requirements and unchanged production/profile/APK identity helpers.
  Full manifest admission still refuses phase collection. The earlier 54 cases are reused.
- SDK `aapt2 link --manifest <fixture> -I <android.jar> -o <manifest-only.ap_>`, then `aapt2 dump xmltree`
  and `Assert-P4LayerProviderXmlTree` — PASS. The actual repository test manifest receives only a
  package attribute in a fresh host fixture. It contains no dex/resources/app build. Source, aapt2,
  framework jar and analyzer hashes plus elapsed time are in `provider-actual-run.json`; linked
  bytes, source XML, logs and actual tree remain. An earlier read of an old APK had no provider and
  is retained as `historical-provider.xmltree.txt`, not used as phase packaging proof.

The unchanged source/fixture/preservation/storage/frame numerical results remain reusable. This
scope does not run a full suite, device APK build/install, profile generation or physical collection.
`gradlew.bat validateDocumentation --offline` — PASS, exit 0, 14.2933659 s; `documentation.log`
and run JSON. `git diff --check` passes. `source-reuse.json` proves preflight bytes unchanged since
the 54-case pass; the separate 26-case group did not rerun that population. This numerical report
addition reuses documentation verification and introduces no link or contract change.

## Remaining work

These are preflight helpers, not a completed admission branch. The outer wrapper/collector must
consume explicit artifact roles, connect real fixture setup and all storage records, bind current
native preservation and last-install restoration, and derive the full finite execution budget.
APK fixtures/provider checks still must run against every actual immutable measurement artifact.
Profile and real load/render/grant/save evidence remain pending. Continue with that integration;
retain all previous FAIL/invalid results and unrelated dirty reports/checkouts.
