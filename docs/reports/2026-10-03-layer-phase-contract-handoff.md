# NENE-PIXEL 引き継ぎ書 — 2026-10-03 計測契約の固定

作成者: NENE-PIXELサナ。まず [今回の日報](2026-10-03-layer-phase-contract.md)、
[Issue #145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145)、
[PR #187](https://github.com/hideyukiMORI/NENE-PIXEL/pull/187) を読む。TODOの正本はIssue。
保全・端末の詳細は [前段の引き継ぎ](2026-10-03-layer-gate-session-handoff.md) を併読する。

## 再開位置

- 開発lab `D:/NENE-PIXEL/worktrees/issue-145`、branch `perf/145-layer-gate`。今回の変更前HEADは
  `3f9d80bc92c8e15c60aaf5ade04f08b09db4f8de`。今回の変更と本書は同じcommitに含め、最新headはPRで確認する。
- #145はOPEN、#187はDraft。#172はPR #185でmerge済み、hideの目視4件も完了済み。再確認を要求しない。
- Cドライブの初期checkoutは別のdirty履歴。編集・reset・commitしない。labでも過去の未追跡reportと
  既存session日報2件のdirty差分を残しているため、一括stage・削除しない。
- ADR 0018/0030/0031/0035、QLT-011〜QLT-019。Waiver none。#186の既存typed detekt 40件は別件。

## 今回完成した境界

正本は `docs/quality/P4_LAYER_PHASE_PROTOCOL.md`。完全なphase admissionはまだ拒否する。

| 対象 | 固定内容 |
| --- | --- |
| Identity | phase `nene-pixel-p4-layer-phase-verification-v1`、frame v9、experiment v6、verdict `layer-phase-2026-10-03-relative-m5` |
| Frame | single / layers16 / underlayの順。各群baseline decision → candidate decision → baseline diagnostic → candidate diagnostic |
| Population | 12 slots / attempt 1、620 measured + 110 warmup = 730操作。collector bound合計14,550秒 |
| Comparison | 3 baseline artifactsと共通candidate。comparison roleとartifact roleを混同しない。対応群のbaselineだけを参照 |
| Numeric gate | baseline admission 33.33 ms、candidate M5 16.67 ms、all-frame +1/+2 ms。FAIL時の診断・停止はprotocol通り |
| Memory | 実editorのC0空 / C1最大load / C2長いpreview保持 / C3commitとautosave / C4 Undo/Redo 10組後。各role 5 fresh runs |
| Storage | candidate AtomicFile最大・最小各5+20、その後実SAF最大5+20。公開save adapter・実grant・固定区間 |

`Get-P4FrameGroupCatalog`と明示protocolの`Get-P4FrameSlotCatalog`は実装済み。historical v7既定値は不変。
`Get-P4FirstPreviewAssociation`はcomplete DOWN-only rowsから最初の行自身のtimestampsだけを返す。
双方は未接続のhost準備部品で、manifestやsample admissionを開かない。
Catalog456 PASS、association281 PASS。各source hash/logは日報から専用reportを参照する。
Scalar/container修正前結果とcatalog初回FAILも残す。成功済み対象の再実行はsource依存が変わる場合だけ。
文書・担当・commit・handoffだけを理由に実機、profile、full buildを実行しない。

## 次の作業順序

1. 新frame identityを既存preflight/slot/collector/analyzerに接続する設計をIssueへ記録する。
   `.roles`の旧baseline/candidateと4 artifact roles、同じgroupのbaseline参照を全境界で追う。
   既存単一経路を拡張し、別collectorを作らない。raw/sampleの完全性と初回preview fieldsを相互照合する。
2. 下敷きは同じ正確な画像bytes/hashとplacementをbaseline/candidateへ渡す。baselineはremembered state以前なので、
   設定ファイルをoracleにしない。1024画像→256 canvasの既定placementとalpha128はsource根拠があるがasset未固定。
3. 実MainActivityのmodelを使うmemory instrumentation、最大fixtureのpublication、実target appへgrantしたSAF保存を
   既存runnerと`Invoke-P4InstrumentationLane`へ統合する。別runtime・fake provider・文字列URIだけの証明を使わない。
4. 完全manifestと全phase予算・停止規則を確定し、preservation/restoration-v2へ最後のinstall hashを結ぶ。
   各実装scopeと対象checkを実行前にIssueへ記録する。対象外の成功済み検証は再利用する。
5. production treeを変えないmeasurement overlay、APK、fixture entry、実SAF load、profileを固定する。
   必要なBaseline Profileはphase前に1回。すべてのadmission成立後にcurrent snapshot・隔離・収集・復元へ進む。
6. retained結果で判定し、原本bytes/mtime/APKと計測成果物の保全を証明する。最終non-draft候補のrequired CI後にmerge。

frame/memory/saveの根拠sourceと接続点は、今回の2 integration probeに整理した。
実装前に読んで最新sourceへ照合する。契約レビューは通過したが、実機成立を証明したものではない。
ADR 0018の250 msはautosave定数判断だけ。+512 KiBは論理量の見込みでPSSの許容幅ではない。

## 端末・証跡

対象serial `T830128GB26321131293`、profile `NENE-P2-ALLDOCUBE-IPL80MP-A16-API36`。
原本APK SHA-256 `44e1d211b3692ce15edae6003d371be38d41f15e3108b6e7647bfde130a88c94`。
過去snapshot `evidence/145-native-snapshot-device/20261003T160746439/original-snapshot.json` はhistorical identityを保持する。
今回も実機move/install/app launch/force-stop/profile/performanceは未実施。
hideの「保存して終了した」は取得済みで返答待ちではないが、実行時の停止状態・current inventory確認は省かない。
`New-P4PrivateSnapshot` → `Invoke-P4PrivateIsolation` → `Invoke-P4PrivateRestoration` が唯一の経路。
元APK先行復元、計測データ保管後の原本復元、各moveの前後照合、失敗時の自動再開禁止を守る。

今回のhide指定では設計判断を現在のNENE-PIXELサナ、適切な調査・限定実装・検証をSOL等へ分担する。
この指定を恒久的な積極分担ルールに変更しない。全ユーザー向けメッセージ末尾に送信時点のJST日時を付ける。
