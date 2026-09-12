# M4 Palette Remap Evidence

Issue #111 / P4-02a. Governing contracts: ADR 0022 and ADR 0023.

## Scope and checks recorded before execution

Changed paths are core/domain palette/validation and core/pixel-engine palette planning plus their
contracts and documentation. This adds bounded immutable mapping and algorithms; the existing live
RGBA document, drawing/history/storage/UI remain unchanged. Applicable checks are domain remap and
engine palette contracts, owning ktlint/detekt, affected application/project-format compilation and
documentation/architecture validation. The final non-draft PR requires canonical quality CI, with
no duplicate local full run. No device, profile regeneration or cold build is triggered.

## Prospective protocol: palette-remap-host-v1

Hypothesis: maximum supported remapping finishes without runaway traversal/allocation. Record
descriptive host-JVM latency of one complete plan from immutable existing inputs. There is no old
implementation, baseline comparison, speedup claim, Android latency or product-performance PASS.
ARC-005 and QLT-011–016 require bounded workspace evidence for this new pixel algorithm.

Use one internal main in the existing engine test artifact, never a routine test or permanent
Gradle task. An ignored external init script runs that main via JavaExec test runtime classpath.
No dependency/plugin/framework is added. Capture source/configuration and compiled-class manifests,
Git base, runner/init/wrapper hashes, JDK/JVM/OS and exact invocation before collection; verify
identities afterward. Retain raw stdout/stderr and separate Gradle wall time from operation latency.

One invocation runs these groups in order using a prebuilt 256-entry source with default 127:

1. byNumber to another 256-entry palette with default 255.
2. nearest against that target, all 256 source colors lacking exact matches (256×256 distances).
3. reorder by the complete reversed old-index list; resulting default is 128.
4. remove old default 127 with explicit old survivor 255; target size 255 and default 254.

Source slots are opaque grayscale `(i,i,i,255)`. Target slots are `(i,i,i,254)`, so every RGBA differs.
Full reference-map, identity, appearance and default checks run before and after all groups. Each
group has five unreported warmups and 20 samples. System.nanoTime surrounds exactly one planner
call; immediately print/flush each raw row, then check Planned plus source/target counts and target
default only. No scan, equality, hashing, GC or memory probe occurs between timed samples. Report
min/max only; no percentile or diagnostic-to-acceptance promotion. No reset is needed for pure plans.

Budget is one invocation, 80 measured rows, JavaExec timeout 60 seconds, wrapper timeout 180 seconds,
no retries or sample replacement. Each completed sample must be at most 1 second as a gross-anomaly
guard (not a product threshold or preemptive operation timeout). Failed checks, exception, timeout,
wrong/missing/out-of-order rows or changed artifact invalidate the observation; preserve all output.
Further collection needs an explicit revised protocol and review, never rerun until green.

## Bounded ownership audit

Source/target definitions admit 2..256 entries. PaletteRemap checks exact source count before copying
the destination list and checks each target bound. It owns at most 256 indices and references the
immutable definitions; it neither copies pixels nor retains caller collections. Bulk reads copy.
Nearest derives two bounded entry lists and one destination list. Exact scan and distance scan each
visit at most 256 targets per source; scalar Long arithmetic needs no color-pair matrix. Reorder
copies only after exact length admission, uses one BooleanArray and at most 256-entry mapping/color
lists. Removal filters at most 256 entries and constructs a 255-entry palette. Their new palettes
defensively own the colors; scratch never escapes. All paths return the same validated remap value.
This is a traversal/ownership bound, not a measurement of total retained heap.

## Results

The single invocation on 2026-09-13 JST completed with all 80 rows in order, correct summaries,
empty stderr and no one-second guard or timeout violation. No retry, replacement or protocol
revision occurred. Gradle invocation wall time was 12,015 ms, separate from planner latency.

| Group | Minimum ns | Maximum ns |
| --- | ---: | ---: |
| byNumber, 256 | 78,500 | 961,300 |
| nearest, 256×256 without exact RGBA matches | 352,100 | 1,313,800 |
| reverse reorder, 256 | 130,400 | 203,500 |
| remove default, 256→255 | 108,800 | 196,200 |

These are valid descriptive host observations only. Read [all raw rows](measurements/m4-palette-remap-host-v1.csv).
Historical #105 observations are not used as #111 performance results. #106 remains responsible
for live indexed editing and its storage/device evidence.

### Exact identity

Collection used uncommitted #111 source based on main `d345f3ef23297b3402b3644aa21bb804de1f7619`.
The 116-file source/configuration manifest covers domain/engine sources and fixtures, build logic,
toolchain/configuration, owning build/lock files and temporary runner scripts. The class manifest
covers domain main and engine main/test classes. Both manifests were identical after collection;
neither aggregate is mislabeled as a Git revision. Later prose and commit metadata do not require
recollection if these inputs remain identical.

| Artifact | SHA-256 |
| --- | --- |
| Source/configuration aggregate | `7e21b4b3f39dc75c2f2b5ad2a981098fa5eeee43cc4062df4450f78dfd763e08` |
| Compiled-class aggregate | `124443675f63d59843d9f5fda2979481506a83cfdc37cf4036697cd95b529cd3` |
| PaletteRemapHostEvidence.kt | `fadeec2c0ccc86b820dd291ef9d2ac1f963cfa06365bb82456673daf6ba65cc7` |
| Temporary init script | `b41b6b6ea3c87b949738a767bef3e691723e8c435bafadee8870bb080ce38c4f` |
| Temporary collection wrapper | `e31bd7f727c8aff087d333eebaddade6c3a45574338ea4f8a3ee3a5f4415fea1` |
| Raw CRLF stdout | `dde8473ae103e28006d3263dacc260060a6d3ad7e2c46de083f7c4506f2c09a7` |
| Committed LF CSV, identical rows | `cdc144eb9d4adb40e66a9d33ae35e5597e05c76f9ecd2ad031e8b58c860dcb95` |

Environment: Windows 11 10.0 amd64, OpenJDK 64-Bit Server VM 21.0.11; repository toolchain and locks
unchanged. Exact invocation: `gradlew.bat -q -I build/reports/issue-111/host.init.gradle.kts
:core:pixel-engine:issue111HostEvidence`. Local raw manifests, process records and Issue adoption
read-back remain under `build/reports/issue-111/` in the owning worktree. No APK or device changed.

### Correctness and integration

The 21 narrow tests passed without failures/errors/skips: domain PaletteRemapTest (5), engine
PaletteReplacementTest (4), PaletteReorderDeleteTest (8) and PaletteNearestTest (4). The nearest
tests include golden alpha/tie/overflow expectations and a fixed-seed arbitrary-precision reference
at 2/17/256 entries. Relevant static checks, affected consumers and documentation/architecture passed.

```text
./gradlew :core:domain:test --tests '*PaletteRemap*' :core:pixel-engine:test --tests '*Palette*'
./gradlew :core:domain:ktlintCheck :core:pixel-engine:ktlintCheck :core:domain:detekt :core:pixel-engine:detekt
./gradlew :core:application:compileKotlin :core:project-format:compileKotlin validateDocumentation validateArchitecture
```

Normal cache/daemon defaults were used. The successful combined narrow invocation took 11 seconds.
Earlier iterations found missing ADR Issue/Related formatting and test-fixture JUnit/ColorChannel
type errors; these were corrected before the successful run or any performance collection. Failed
compile/check logs remain intact and are not performance observations. No gate was weakened.

A read-only independent review found no implementation blocker. Its request to record the exact
prospective protocol in Issue #111 was resolved and read back before collection. The final non-draft
PR still requires canonical quality CI; no duplicate local full suite or Android claim follows.

Rules: ARC-001/004/005/007/008/010/012; KOT-001–008/012/013; QLT-006/008/011–016. ADR 0023 and the
glossary/layout/development-plan updates record the public mapping operation. No schema, dependency,
plugin, module or product limit changed. Many-to-one Undo still needs exact before indices in #106;
this mapping cannot be inverted to restore them.

Waivers: none
