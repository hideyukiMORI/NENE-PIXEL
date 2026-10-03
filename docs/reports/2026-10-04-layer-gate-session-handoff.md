# NENE-PIXEL 引き継ぎ書 — 2026-10-04

作成者: NENE-PIXELサナ。まず [日報](2026-10-04-layer-gate-session.md)、
[Issue #145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145)、
[phase protocol](../quality/P4_LAYER_PHASE_PROTOCOL.md) を読む。
TODOの正本はIssue。本書は再開時の説明であり、Markdownのチェックリストを進捗DBにしない。

## 作業場所と現在地

| 項目 | 値 |
| --- | --- |
| 作業checkout | `D:/NENE-PIXEL/worktrees/issue-145` |
| branch | `perf/145-layer-gate` |
| 最新の実装commit | `e8962b61d3a5b99e0d59c5e95ee9c56e4b6ab53f` |
| PR | [#187](https://github.com/hideyukiMORI/NENE-PIXEL/pull/187)、OPEN / Draft |
| Issue | #145 / P4-05d、OPEN。段階計測の準備中 |
| 実機収集 | 未実施。complete manifestとframe live admissionは閉鎖中 |
| waiver | none |

本書は上の実装commit後の文書変更。最終文書commitはbranch logで確認する。
`C:/Users/info/WORKS/NENE-PIXEL` は別のdirtyな履歴checkoutであり、編集・reset・commitしない。
作業checkoutにも前から変更中の `2026-10-03-layer-gate-session.md` と同`-handoff.md`、多数の未追跡reportがある。
それらは今回のstage対象から外した。削除、reset、一括stageをしない。

現在の既定はNENE-PIXELサナの単体実行。古い引き継ぎにある積極的分担やSol/Luna固定委任を継承しない。
ユーザーはhideと呼び、ユーザー向けメッセージの最終行に送信時点のJST日時を記す。

## 完了した接続

主なファイルは `docs/quality/measurements/` にある。

| ファイル | 現在の責務 |
| --- | --- |
| `p4-indexed-preflight.ps1` | 4 artifact roles・完全source/APK/fixture・24-slot catalog。完全phase admissionはまだ有効化しない |
| `p4-indexed-device-lanes.ps1` | 実artifact role、11/12-field context、install intent/readback、停止後report、collector budget |
| `p4-layer-slot-routing.ps1` | frame準備/収集、sealed raw結合、先行memory/同群baseline chain、共有staging証拠reader |
| `collect-p4-indexed-slot.ps1` / `analyze-p4-indexed-slot.ps1` | 既存入口のphase dispatch。full manifest拒否は維持 |
| `p4-layer-frame-preparation.ps1` | 通常New/Load/SAF picker、固定fixture照合、機能確認のPNG/XMLとwarmup後の作品置換 |
| `p4-operation-budget.ps1` | 明示された共通単調時計、15秒終了処理reserve、残時間内のnative呼出し、4096-entry cap |
| `p4-device-private-preservation.ps1` / `p4-device-private-slot-reset.ps1` | 同じ方針とnative move境界による6対象のslot間整理。原本guardはphase最後まで保持 |
| `p4-layer-slot-cleanup.ps1` | 全writer停止→preservation確認→正しいdebug APK→report/全private/APK archive→slot reset |

cleanupは共通3000秒で全componentを動かす。appは毎slot、test/providerはtest_debugを使うslot、
publication packageはpublication slotで保管する。保管用に不要な補助APKをinstallしない。
archive上限はprivate 256 MiB、APK 128 MiB、inventory 4096 entries。
captureに欠落・失敗があればresetしない。部分出力・失敗record・install intentをすべて保持する。
成功frameはcaptured staging証拠を共有readerで検証する。強制終了でframe-slot-v2が未作成なら、
canonicalな部分frame directoryからrecordを作れる。既存recordは上書きしない。

## 次の着手点

1. Issueへ次の変更範囲と対象検証を登録し、protocolを先に整える。
2. `invoke-p4-indexed-slot.ps1` の既存outer slot/chainへphaseを結合する。
   `Invoke-P4IndexedSlot`、`Get-P4SlotDeviceContext`、`Get-P4SlotCollectorBudget`、`Read-P4FreshAnalysis` は
   legacy role/catalog/protocol前提が残る。canonical slot、実artifact role、実manifest SHAを渡し、
   reservation/先行slot/seal/analysisの同一性を同じ経路で検証する。
3. collectorの成功・失敗両方で、今回のcleanupをseal前に呼ぶ。旧private quarantineをphaseへ使わない。
   collector最大3540秒、cleanup 3000秒だけでは全体上限にならない。
   元の端末設定取得・復元、seal/analysis、phase最後の原本復元まで有限の時間割を明示する。
   既存cleanupの90秒を無条件に流用しない。設定読取は最大3×30秒、復元は最大6×30秒の経路がある。
   admissionの保存済みdevice dumpsを再利用する場合も、rotation mode等の必要項目が実際に揃うことを確認する。
4. 同時に、下敷きの非同期処理完了を計測前に証明できる方法を決める。下記の未解決点を維持する。
5. 全helperとadmissionの整合後に、4 roleのimmutable overlay/APK、packaged fixture/provider、profileを固定する。
   phaseが必要とするBaseline Profileは1回。profile更新はproduction inputなので、候補pinを事前に改訂し、
   test-only overlayと偽って扱わない。profile取得自体の原本保全・復元の順序も先に決める。
6. 条件が揃ってから新しいsnapshot・隔離・1回の登録済み段階収集・原本復元・判定へ進む。
   新しいsnapshotや実機隔離を先行しない。成功/FAIL/invalidを保持し、無計画な再収集をしない。

上記は将来のTODOを追加実装済みと扱わないための再開説明。新しい第二のcollector、保存方針、数値parserを作らない。

## 非同期の下敷き処理で未解決のこと

`app/android/src/main/kotlin/.../UnderlayMemoryScheduler.kt` はMainのscopeで独立してrecall/publishする。
`core/application/src/main/kotlin/.../persistence/UnderlayMemoryWorkflow.kt` とruntimeのSettledへの遷移は、
通常のUI成功statusへ投影されていない。onStopのflush要求も完了待機ではない。
正常New/Loadはautosave publication待機とautosave tracking初期化をsourceで確認済みだが、
下敷きの別経路のPending/Publishingまで消えたことの証明にはならない。

固定sleep、stable PNG、作品読込成功、CPU/IOが静かという観測だけでSettledを主張しない。
下敷きなしの見た目はrecall前後で同じなので、画像だけの証明は単層/16層にも通用しない。
production observer追加、release instrumentation、反射などは検討案にとどまり、採用・実装していない。
production source identityや計測条件を変える解決策には、先に明示した決定と新しいartifact bindingが必要。

## 原本と比較対象

端末profile: `NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`、serial: `T830128GB26321131293`。
hideは以前「保存して終了した」と連絡済み。その返答待ちには戻さない。
実行時のPID不在・current inventoryの確認は別途必要。

historical snapshot:
`D:/NENE-PIXEL/evidence/145-native-snapshot-device/20261003T160746439/original-snapshot.json`。
149 entries / 90 files / 59 directories。元APK SHA-256:
`44e1d211b3692ce15edae6003d371be38d41f15e3108b6e7647bfde130a88c94`。
現行snapshot source inventoryはbudget helperを含む8 sourceなので、古い7-source recordを新しい保全証明に流用しない。

canonical APIは `New-P4PrivateSnapshot` → `Invoke-P4PrivateIsolation` → phase slots →
`Invoke-P4PrivateRestoration`。slot resetは原本復元ではない。元APKと原本の復元はphase全体のfinallyで行う。
最後のinstall intent/resultと実観測を突き合わせて期待APKを決め、見えたhashをそのまま期待値にしない。
原本guard、元ファイルのbytes/mtime、全計測データを保持する。途中状態の自動再開・clear・削除・上書きは禁止。

| role | production commit |
| --- | --- |
| baseline_single | `8120c06fae1a372b23d2a7af4f50aa2b9cdfeff9` |
| baseline_layers16 | `169b59287ca60e77e07ac91690450dd1a44b9ba4` |
| baseline_underlay | `f92b1006be5f7145a32258446474f8640b14b60b` |
| candidate | `1f9bb1637058d3fa4a98122f4942406211bd1c69` |

24 slotsはframe 12、memory 10、publication/SAF各1。frameは620 measured +110 warmups。
12回のuntimed機能確認gestureはその母集団外。UP-to-committed p95 ≤16.67 ms、
all-frame overrun p95 ≤対応baseline +1.0 ms / p99 ≤+2.0 msを維持する。
既存のbaseline admission 33.33 msを候補の16.67 ms gateと混同しない。歴史的#142 FAILも残す。

## 検証の引き継ぎ

[日報の表](2026-10-04-layer-gate-session.md) と各専用reportを使う。
直近はrouting 126、budget 94、reset 108、cleanup 143の対象項目が成功済み。
source/dependency identityとlogを照合し、担当交代・commit・報告だけでは再実行しない。
cleanup証跡rootは日報記載、`source-reconciliation.json` が143項目のsource一致を記録する。
端末境界はmockであり、Android実行・性能PASS・実機復元を証明しない。

関連しない既存typed detekt 40件は#186へ分離済み。最終non-draft候補のrequired `quality`がfull gate。
引き継ぎのための全件テスト、cold build、profile再生成は不要。
GitHub本文は `gh ... --json body | ConvertFrom-Json` の `.body` をbody-fileへ保存し、更新後に完全一致をreadbackする。
multi-lineのnative出力を不用意に配列連結しない。

適用規則: ADR0018/0030/0031/0032/0034/0035、ARC-007、QLT-011〜QLT-019。active waiver: none。
