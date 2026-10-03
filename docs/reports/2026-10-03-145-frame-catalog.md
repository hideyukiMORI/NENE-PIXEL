# Issue #145 — device-free layer phase frame catalog

Rules: ARC-001/007/009; ADR 0031/0035; QLT-011/012/015/016/017/019. Waivers: none.

## Scope and verification plan recorded before execution

The registered Issue scope is the frame preparation implementation slice in
[P4 Layer Phase Protocol](../quality/P4_LAYER_PHASE_PROTOCOL.md#frame-preparation-implementation-slice).
Only the existing preflight frame catalog and a new host validator change. No production code,
dependency, module, project schema, collector integration, external update or device operation.

Command: `pwsh -NoProfile -File docs/quality/validate-p4-layer-frame-catalog.ps1 -OutputDirectory <fresh lab evidence directory>`.
The check parses the changed scripts and compares literal expected group/slot records, exact keys,
role/artifact/commit/family bindings, sample/warmup arithmetic and bounds, predecessor/comparator
isolation, unknown protocol rejection, unchanged historical four-slot default, and continued phase
manifest rejection at the existing identity gate. These regressions are all in the changed catalog
or its direct legacy manifest consumer. The test writes a fresh validation record and its caller
retains stdout/stderr even on failure. No existing full validator, Gradle, app test or device check.
Documentation validation of the parent-owned governing prose remains with the design seat.

## Implementation

- `docs/quality/measurements/p4-indexed-preflight.ps1`: adds phase-only `Get-P4FrameGroupCatalog`
  and explicit `ProtocolId` selection for `Get-P4FrameSlotCatalog`; omitted selection remains v7.
- The sole phase group definition supplies ordered families, baseline commits, artifact roles and
  protocol/frame/experiment/verdict identities. The phase schedule is generated only in preflight.
- Twelve slots carry attempt 1, role separate from artifact role, sequential neighbors and a separate
  same-group decision baseline reference. Group neighbors and comparator bindings remain distinct.
- `docs/quality/validate-p4-layer-frame-catalog.ps1`: host contract with fresh immutable lab evidence.
- `Assert-P4ManifestContract`, collectors, analyzers, historical populations and gates are unchanged.

## Result

First execution failed at the empty group-protocol refusal boundary: PowerShell mandatory string
binding refused the empty value before the catalog's own unknown-protocol error. The failed result
and log remain under `evidence/145-frame-catalog/20261003T172728003-09f57ec9942f4202b4b69a89997a84e2/`.
The group parameter now explicitly passes empty/null strings to its closed protocol gate. Rerun of
the same narrow validator is required because this relevant input changed; this is not an unchanged retry.

Corrected execution PASS: 456 assertions, 0.8470322 seconds validator wall time (1.897 s caller
command wall time). Three groups, 12 attempt-1 slots, 620 measured + 110 warmup = 730 operations;
collector bounds sum to 14,550 seconds. This is a host contract result, not operation latency.

Executed command:
`pwsh -NoProfile -File docs/quality/validate-p4-layer-frame-catalog.ps1 -OutputDirectory <lab>/evidence/145-frame-catalog/20261003T172759001-495a77c4f1a5410c8ca480ea71cd83f8/catalog`.
Evidence: `evidence/145-frame-catalog/20261003T172759001-495a77c4f1a5410c8ca480ea71cd83f8/validator.log`
and `catalog/validation.json`. The JSON pins the tested scripts, protocol and direct imported inputs.
Both changed PowerShell files passed AST parsing in that same execution.
`git diff --check -- docs/quality/measurements/p4-indexed-preflight.ps1` also passed.

Source base: `3f9d80bc92c8e15c60aaf5ade04f08b09db4f8de` plus the focused uncommitted change.
Verified input SHA-256:

| Input | SHA-256 |
| --- | --- |
| `p4-indexed-preflight.ps1` | `c9f952429211e3146708fca64dc5f9fa1eed3904bcc164bb62c9bf0074baf7b1` |
| `validate-p4-layer-frame-catalog.ps1` | `3b4a7dc6ff678fdf3a82767be18901f7ace48d165c0930cf759502383cb5f5f0` |
| `P4_LAYER_PHASE_PROTOCOL.md` | `dabdfa9cecfc96d148a6e805f8140902b15aff24b38e1257813ee021e81f6fdd` |

Documentation/schema changes: this report and a device-free catalog validator evidence schema;
the accepted parent-owned prospective frame-v9/experiment-v6/verdict identities are exposed as
catalog data. No manifest admission or project-file format changed.
Reused results: none for this new contract; parent retains prior unaffected preservation/fixture results.
Unrelated failures: none observed. Remaining risk: this preparatory catalog does not validate or admit
a complete phase manifest, device/display binding, collector/analyzer integration or performance result.
No performance claim. No commit or push by the implementation seat.
