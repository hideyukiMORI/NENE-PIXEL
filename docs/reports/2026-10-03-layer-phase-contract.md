# NENE-PIXEL 日報 — 2026-10-03 計測契約の固定

作成者: NENE-PIXELサナ。対象は [Issue #145](https://github.com/hideyukiMORI/NENE-PIXEL/issues/145) / P4-05d。
TODOの正本はIssue。本書は今回の変更と検証の記録であり、段階性能の受け入れ結果ではない。
前段の保全・共有fixtureの記録は [前回日報](2026-10-03-layer-gate-session.md)、再開手順は
[今回の引き継ぎ書](2026-10-03-layer-phase-contract-handoff.md) を参照する。

## 結果と変更範囲

単層・16層・下敷きの比較、DOWN-only描き始めの関連付け、実editorのメモリと保存時間の
計測契約を固定した。既存preflightにphase専用のcatalog、既存analyzerに純粋な関連付け関数を追加した。
collectorへの接続と完全なphase admissionは未完了。Issue #145とPR #187はOPEN / Draftを維持する。

作業checkoutは開発labの `worktrees/issue-145`、branchは `perf/145-layer-gate`。
変更前HEADは `3f9d80bc92c8e15c60aaf5ade04f08b09db4f8de`。
今回の実装と本書は同じfocused changeに含める。現在のheadはPRで確認する。
開始時のdirtyなCドライブcheckout、既存日報2件の未commit差分、多数の過去reportは変更・一括stageしない。

Rules: ADR 0018/0030/0031/0035、ARC-001/004/005/007/009/011、CMD-001/002/008、QLT-011〜QLT-019。
Active waivers: none。製品Kotlin、依存、module、公開API、project-file schema、閾値の変更はない。

## 設計とファイル

| ファイル | 変更した意味 |
| --- | --- |
| `docs/quality/P4_LAYER_PHASE_PROTOCOL.md` | 新phase identity、3群12slot、baseline参照、固定母集団・予算・停止、初回preview、実editor memory、autosave/SAF境界 |
| `docs/adr/0018-bounded-autosave-debounce-contract.md` | 250 msの既存定数判断を実際の最大16層v3 Candidateに適用するprospective note |
| `docs/quality/measurements/p4-indexed-preflight.ps1` | 明示protocol指定のgroup/slot catalog。省略時のhistorical v7とphase拒否は維持 |
| `docs/quality/measurements/p4-indexed-frame-analysis.ps1` | `Get-P4FirstPreviewAssociation`追加。既存解析関数・定数は内容同一 |
| `docs/quality/validate-p4-layer-frame-catalog.ps1` | catalogの件数・順序・role/commit・参照・予算とhistorical既定値を検証 |
| `docs/quality/validate-p4-first-preview-association.ps1` | own-row timestamps、完全性・順序・型・精度を検証 |

- Frameは3種類のbaselineと1つのcandidate artifactを使う。12 slots、620 measured + 110 warmup = 730操作。
  collector上限の合計14,550秒はハング防止の上限であり、全phaseの所要時間ではない。
  baseline admissionは従来の33.33 ms、candidateはM5 16.67 msと対応baseline比+1/+2 ms。
- 初回previewはlayered tapの最初の行自身のtimestampsを返す。異なる行の最小値を組み合わせない。
  PowerShellの整数丸めと配列の単一値化を拒否し、nanosecondの差をdecimalで計算する。
  関数はraw rowsの1走査とframe-ID setを使うhost処理。製品のフレーム処理に負荷は追加しない。
- Memoryは実MainActivity/ViewModel/Composeの5 checkpoints、baseline/candidate各5 fresh processes。
  previewの15 non-target surfaces 1,105,920 bytesとpalette 1,024 bytesを別途把握した。
  +512 KiBの論理見込みをPSS許容幅にしない。
- Storageは最大Candidate 1,182,885 bytesのAtomicFile出版と、最大project 1,182,862 bytesの実SAF保存。
  実target appのgrantとpublic save adapterを使う。人によるdestination選択待ちは計測区間外。
  ADR 0018の250 msをSAFの合格閾値に流用しない。

調査の根拠は [frame integration probe](2026-10-03-145-frame-integration-probe.md) と
[memory/save integration probe](2026-10-03-145-memory-save-integration-probe.md)。
hideの今回の分担指定に従い、NENE-PIXELサナが設計・統合判断、SOL担当が限定実装・調査を行った。
[独立契約レビュー](2026-10-03-145-phase-contract-review.md) はblocker / should / nitすべてnone。
実装レビューは親がsource・validator・証跡identityを確認した。レビューのPASSを実機成立とは扱わない。

## 検証計画と結果

実行前にIssueへcatalog、関連付け関数、memory/storage文書のscopeと対象checkを記録した。
各コマンド・所要時間・SHA-256・logは下記の専用reportに保持する。

| コマンド | 結果と範囲 |
| --- | --- |
| `pwsh -NoProfile -File docs/quality/validate-p4-layer-frame-catalog.ps1 -OutputDirectory <fresh evidence>/catalog` | 456 assertions PASS、0.8470322 s。ASTを含む。詳細はcatalog report |
| `pwsh -NoProfile -File docs/quality/validate-p4-first-preview-association.ps1` | 281 assertions PASS、2.4569314 s。対象関数・validatorのASTを含む |
| `gradlew.bat validateDocumentation --offline` | 契約・ADRはPASS、15.5908364 s。新report追加後のlinksもPASS、13.5415821 s |
| `git diff --check` の今回の対象path | PASS。既存の無関係なdirty差分は含めない |

[catalog report](2026-10-03-145-frame-catalog.md)、
[association report](2026-10-03-145-first-preview-association.md) を参照する。
Catalogはempty protocolのbinding修正前FAILを保存し、修正後に対象validatorだけを再実行した。
Associationはscalar/container境界の具体的な懸念に伴うsource修正後のみ再実行し、先行PASSも保持した。

契約文書checkの証跡は `evidence/145-phase-contract-docs/20261003T173304571/`。
新reportを含む文書checkの証跡は `evidence/145-phase-final-docs/20261003T174028701/`。
後者の実行理由は新reportのlinks追加。結果の数値記録だけでは再実行しない。
検証済みprotocol SHA-256は `66ecc12f9164cad81217b8ede6305aa2870196fd27c019fabb4fd4a0998ffe17`、
ADR 0018は `e17e5c590d102f62354a9981be6e58c28d1d821e91168241751ce1d498e7d93d`。
Catalog実行後のprotocol追記はmemory/storage境界であり、catalog実装・frame契約の検証は無効化しない。

前回の保全・fixture・履歴契約の成功結果は入力が不変のため再利用した。
新規の無関係な失敗はない。既存typed detekt 40件はIssue #186に分離したままで再実行しない。
full local check/build、端末操作、profile生成、性能収集は今回実施していない。
Draft CIの拒否はfull suiteのFAIL/PASSを意味しない。最終non-draft候補でrequired qualityを満たす。

## 残るリスクと次の境界

Frame collector/analyzer/raw/sampleの接続、完全manifest、preservation-v2/restoration-v2と最後のinstall記録、
下敷きasset/placementの固定、実editor memory/storage instrumentation、packaged assetと実grant/load、
artifact/profileの固定、全phaseの有限予算・停止を完成する。そこで初めてcurrent snapshotと実機収集へ進む。
過去のreadonly snapshotをcurrent isolation証拠へ読み替えない。
Issue #145の性能判定は未完了。歴史的FAIL/invalidと原本・計測成果物を保持する。Waivers: none。
