# 2026-09-13 番号画素への移行 日報

担当: NENE-PIXELサナ。Issue #106 / P4-02、M4。
引き継ぎ: [2026-09-13-indexed-cutover-handoff.md](2026-09-13-indexed-cutover-handoff.md)。
GitHub IssuesがTODOの正本。この日報は本日の観測記録。

## 本日の区切り

hideの指定により、実装・実機機能確認・既存端末データの復元までで停止する。
日報と引き継ぎ書の作成後は作業を継続しない。hideの裁定待ちではない。
#106はOPEN、未push・未PR・未merge。mainは2dd4e01のまま。
性能測定、最終required CI、#107/#108は未実施。性能PASSやM4完了を主張しない。

## 今日の成果

| 内容 | 状態 |
| --- | --- |
| #101の契約、#105の2〜256色定義・JSON基盤、#111の共通パレット変換計画 | 今日の早い時間帯にmain反映済み。前回日報を参照 |
| DocumentStateにパレット定義とU8画素番号を所有させる一括移行 | 本体commit f5b41a8、ホスト検証済み |
| 描画・消去・Undo/Redo・パレット置換・表示・PNG・保存・復旧の同時切り替え | 実装済み。番号の異なる同色も区別 |
| project v2 / recovery envelope v2、旧v1との型付き互換処理 | ADR0024・PROJECT_FORMAT_V2を先に確定し、golden/境界/失敗系を検証 |
| 旧作品256色以下の無損失移行、256色超の明示変換と原本保全 | 実装済み。復旧原本は検証済みコピー前に失わない |
| 変換確認画面 | 原本/変換後比較、保存状態、同意、キャンセル待機、日英簡体中を実装 |
| Android実機の重点機能テスト | 18件成功 |
| 実際のファイル選択画面を通す耐久性の通し確認 | 保存・読み込み/PNG/自動保存・中断・復旧・3言語案内の5段階成功 |
| 既存端末データ | 元20ファイルを復元照合、起動後も元の復旧ファイルが同一。テスト成果も退避保管 |
| 比較用APK・測定準備 | baseline/candidate各4APK作成、実ソース番号を内包。測定開始は明示的に禁止したまま |

M0〜M3はGitHubでCLOSED、M4はOPEN。作業量の概算は今回#106約8割、Androidベータ(M5)まで約5〜6割、M6込み約4〜5割。
Issue数の比率や正式な受入完了率ではない。パレット編集UI、スポイト/長押し、レイヤー/フレーム/選択・変形、ベータ品質が残る。

## 検証

影響範囲と狭い検証コマンドは実行前に `C:/n106-indexed/build/reports/issue-106/work-plan.md` へ記録。
JBR21、通常のGradle daemon/cache。clean、強制再実行、全canonicalローカル検査、profile再生成は行っていない。

- domain 41、pixel-engine 51、project-format 43、applicationの既存一式と追加2件、persistence 56、app/presentation 56のホスト検証が成功。
  applicationは最後の一式(170件と既存opt-in skipの記録)と、後続の変更なし本体に対する追加2件を区別する。
- 関連ktlint/detekt、AndroidTest compile、Android lint、`validateDocumentation validateArchitecture` が成功。
- 重点実機: `AndroidPersistenceFunctionalTest` 6件、`CanvasBitmapProjectionTest` 4件、
  `LegacyConversionDialogTest` 3件、`EditorRuntimeLifecycleTest` 3件、`EditorRecoveryOfferTest` 2件。
- 通し実機: `run_m3_acceptance.py` の既存経路。各段階は単一class/methodを明示。
  `durable-journey-1/PASS.txt` に成功、保存原本bytes不変を記録。
- 測定コレクターの検査はsynthetic/no-deviceのみ。実測の代用にはしない。

rawは上記 `build/reports/issue-106/` に保管。失敗ログを消していない。
最初の実機補助スクリプトはhash関数のimport漏れで結果一覧出力に失敗した。
18件のJUnit生ログは成功しており、後から端末内4APKを読み出して使用APKとのbytes一致を確認し、
`functional-device-1/verified-results.json` を作成した。テストはやり直していない。
その他、英語lintの2件、baseline overlayのnullable文字列化、APKのVCS埋め込み準備時の失敗も修正・保存した。

## 端末の保全・最終状態

iPlay80miniPro / API36、serial `T830128GB26321131293`。
昨日の16×16・赤い1画素の未保存作業が前面にあったため、画面/事前tarを記録し、HOMEによる通常停止でflush後に保全。
元復旧ファイルは1089 bytes、SHA256 `2c1c0efcadfd81ea70b6e2086e38bdd632d4e7479234ceebbd98d49bd0d6bc37`。

PC原本tarと新しい端末guardを照合。復元初回はAPK installで更新された24-byte `files/profileInstalled` を検出してmutation前に停止。
差分がこの管理ファイルだけと確認後、元のPC tarから復元し、テスト生成ファイルを別archiveへ移動、sync、完全inventoryと言語設定を検証した。
起動後も復旧ファイルが一致し、画面は「前回の未保存の作業があります」の復元案内。復元/破棄のボタンは押していない。
app test APKはdisabled。`pm clear` / uninstall / 既存作品削除なし。

## 文書・ルール・残り

ADR0024、PROJECT_FORMAT_V2、憲法/command/layout/glossary、ADR0005/0007/0008/0009の限定改訂とP4 protocol v3を更新。
新dependency/module/plugin/transport APIなし。
Rules: ARC-001/004/005/007–012、CMD-001/002/005–010/012、KOT-001–008/013/016–020、QLT-006–009/011–016。
Waivers: none。

残る中心課題は測定用起動・解析・preflightの接続と独立レビュー指摘6点。詳細は引き継ぎ書へ。
現時点では33 slotを予約・実行できないよう `Assert-P4CollectionImplementationReady` が必ず拒否する。
次回、全条件が一致してから必要な性能/メモリ証拠、最終CI、PR/main反映を進める。

日時: 2026-09-13 18:21:45 JST
