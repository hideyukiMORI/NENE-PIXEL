# 2026-09-13 番号画素への移行 引き継ぎ書

担当: NENE-PIXELサナ。日報: [2026-09-13-indexed-cutover.md](2026-09-13-indexed-cutover.md)。

## 停止指示と再開の境界

hideの最新指示: 「区切りのいいところまで対応を進めたら日報と引き継ぎ書を書いて」「引き継ぎ書を書き終わったら手を止めてね」。
実機機能確認と端末データ復元までで区切り、文書完成後は停止。自動継続、測定、#107/#108着手はしない。
裁定待ちはない。再開指示が来たらこの引き継ぎとGitHub Issue #106を照合する。
TODO正本はGitHub。M0〜M3 CLOSED、M4 OPEN、#106/#107/#108 OPEN。main反映済みは#105/#111まで。

## 作業場所と不変の識別

| 用途 | 場所 / commit |
| --- | --- |
| 現在の作業 | `C:/n106-indexed`、`feat/106-indexed-document` |
| 本体・契約の固定 | `f5b41a8e974752fba2adb75c76d8daced52b67b0` |
| fresh guardを使うacceptance fixture/host修正 | `3642437c5f9817f70452a2a4d016db8d1962d184` |
| 測定準備のローカルcheckpoint | `26a977e36f501cd3ce45b78f79fa025930120163`、後続は本日報/引き継ぎの文書commit |
| candidate APK用clean standalone clone | `C:/n106-candidate-build`、HEAD `3642437c5f9817f70452a2a4d016db8d1962d184` |
| baseline APK用clean standalone clone | `C:/n106-baseline-build`、HEAD `0b605481ad97ee3726864e556e6519f3a862271f` |
| baseline production | `2dd4e01e3bbe88967237cde4e28412d2962fd590`。overlayは測定test7ファイルだけ |
| 途中のworktree | `C:/n106-candidate` (f5b41a8)、`C:/n106-baseline` (0b60548)。APKの最終source用途にはstandalone cloneを使う |
| clean main | `C:/Users/info/.codex/tmp/nene-pixel-sol-20260906/worktrees/issue-58-main`、2dd4e01 |
| 元workspace | `C:/Users/info/WORKS/NENE-PIXEL`、既存dirty `perf/54-compose-frame`。reset/checkout/clean/production編集禁止。本日の追加は報告2ファイルのみ |

未push・未PR・未merge。#106を閉じていない。ローカルcheckpointはmerge候補の受入完了を意味しない。
Git HEADとdirtyを実物で確認する。新しいdocs commitへ古いAPKの埋め込みrevisionを付け替えて説明しない。

Production-tree aggregate:
- candidate f5b41a8とbuild3642437: `34f84bdd69a4ccd2968ce787158c4b8568520f6afb5fdfee6a9b6b8060e09440`
- baseline2dd4e01とbuild0b60548: `c5145871ff9d82bcaae1ef7f5d4664b28aa6decb46677a0f3a0311afdcdf062e`

JDK `C:/Users/info/.jdks/jbr-21.0.11`、SDK `C:/Users/info/AppData/Local/Android/Sdk`。
Gradleは同時に1 invocation。通常daemon/cache、formatterの完了後にchecks。全canonicalは最終required CIで満たし、重複ローカル実行しない。
AGPのVCS抽出は通常worktreeの`.git`ファイルに対応せず`.git/HEAD`が必要。APKは短いstandalone cloneで作る。

## 読む正本と実装済みの意味

AGENTS.mdの必読14文書、Issue106、ADR0022/0023/0024、PROJECT_FORMAT_V2、P4_INDEXED_CUTOVER_PROTOCOLを読む。
詳細は `docs/reports/2026-09-13-indexed-compatibility.md` とignored `build/reports/issue-106/` の契約/実装報告。

- DocumentStateがPaletteDefinitionとprivate ByteArrayのU8 snapshotを所有。Runtime.paletteやRGBA編集モデルは除去。
- Newは現在定義/defaultを継承、選択0。初期appだけ8 opaque + transparent black default8を組み立てる。
- ソースadmissionはgateway private owner + doc + exact history position。gesture開始時にadmissionとPencil/default番号を捕捉。
- ReplacePaletteCommand、palette-only revision/dirty/autosave/full invalidation、描画と共通Undo/Redo。
  多対一の逆操作は元番号を正確に保存。inverseがpatch storageを共有し、retention bytesを二重計上しない。
  上限64件/524288 changes/8MiB logical。到達可能なpalette最大は3,279,360 bytes。
- レンダー/PNGは現在パレットを解決、bitmap cacheはsnapshot+definitionで識別。
- v2はheader0..37、P U16@38/defaultU8@40、RGBA@41、indices@41+4P、CRC末尾、total45+4P+N。
  v2 min54/max66605、共通carrier max262186。header/長さ/CRC/所属をsnapshot allocation前に検査。
- 旧v1はexact immutable LegacyRgbaSource。<=256 distinct fullRGBAはrow-majorで無損失移行。
  1色はduplicate、空きがあればtransparent black。256色で透明blackなしはdefault0を明示。
  >256は現作品へ設置しない候補。唯一のnearest尺度でpreviewし、明示acceptで新ID/rev0/dirty/空history。
- コピーはfresh SAF先へexact v1を書き、close/readback一致でのみproof。SaveAsはproofにならない。
  proofはsource/op/recovery lineageへ結合。宛先変更/追加copy失敗で既存proofを失わない。
  recovery-only原本はproof前にNew/Load/decline/retire等で失わない。cancelはphysical drainまでmodalを保持。
- preview epochでA/B/Aの古い完了を拒否。重いplanningはdispatcher上、physical leaseは実処理完了まで維持。
- recoveryファイル名は `nene-pixel-recovery-v1` のまま。envelope1/project1と2/project2をread、writer2。
- native conversion UIはja/en/zh、原本/変換後、コピー状態、同意、48dp、横2列/縦scroll。
  app側presetはDusk16/Gray32/Swatches256。#107の編集UIや#108のスポイトではない。

## 確認済み検証と実機成果

raw rootは `C:/n106-indexed/build/reports/issue-106/`。既存FAILも残す。

- `lane45-harness-check-2.log`: format/application/persistenceの対象host/static/AndroidTest compile。
- `lane12-final-gates-8.log`: command/memory test artifact compile/staticと追加の2core contract。
- `presentation-app-unit-tests-3.log`: app/presentation 56件、`integration-ui-lint-2.log`: UI lint成功。
- `production-freeze-narrow-1.log`: docs/architecture/presentation detekt/adapterAndroidTest compile+static成功。
- `acceptance-guard-narrow-1.log`: `:app:android:compileDebugAndroidTestKotlin :app:android:ktlintCheck :app:android:detekt` 成功。
- `functional-device-1/verified-results.json`: 18件のJUnit生ログと、端末から読み出した4APK bytesを照合。
  最初のwrapperはGet-FileSha256 import漏れで一覧出力のみ失敗。ログ冒頭/末尾のエラーを消さない。
  事後のinstalled-byte照合でartifactを補い、テストは再実行していない。
- `durable-journey-1/`: 保存/読込・PNG・autosave/未完了書込/再起動復旧/3言語案内の5stage成功。
  `PASS.txt`、artifacts.json、各stdout/stderr、saved/export/recovered bytes、6枚の案内画面画像を保管。
  APKはcandidate build3642437。app SHA `793afc9a1454034c6e09a00bfc21d6603ddf0515f95c8b501166376f9afe22d1`、
  test SHA `0ed286f9767e1760e10d817fe40fcdfefdb8afc2212cad2aed5633004e34a769`。
  `run_m3_acceptance.py` はfresh `--preservation-manifest`、fixtureは`m3PreservationGuard`必須になった。
  過去のissue89 guardを今回の保全と呼ばない。
- `handoff-artifact-inventory.json`: baseline/candidate各4APK、bytes/SHA/実embedded revision/packaged prof/profm。
  これはartifact一覧であり、収集preflightではない。

APK生成コマンドは各standalone cloneでJDK/SDKを設定し、
`./gradlew.bat -I C:/n106-indexed/build/reports/issue-106/p4-apk-source-identity.init.gradle :app:android:assembleDebug :app:android:assembleDebugAndroidTest :adapters:persistence:assembleDebugAndroidTest :app:android:assembleBenchmarkRelease --console=plain`。
baselineはhost用`:core:project-format:compileTestKotlin :adapters:persistence:compileDebugUnitTestKotlin`も追加。
最終ログはcandidate-clone-artifacts-3.log / baseline-clone-artifacts-1.log。
同じinitを `docs/quality/measurements/p4-apk-source-identity.init.gradle` に保管した。
AGP VcsInfoをapp debugにも有効にし、そのAGP生成textprotoをVariant Sources API経由でAndroidTest resourcesへコピーする。
新しいmetadata producerやproductionコードは作らず、全8APKの実revisionを確認した。

## 端末を次回まで守るための情報

serial `T830128GB26321131293` / iPlay80miniPro / API36。
保全root `C:/n106-indexed/build/reports/issue-106/issue-106-user-recovery-20260913-1757/`。
`preservation.json` SHA `4e4b03fd8ae94b58289061260f312e834e5638da44b1bac606762fc5defa4b18`。
元20ファイルは `stopped-original.tar`、guard移動後は `isolated-original.tar`。
元recovery1089B SHA `2c1c0efcadfd81ea70b6e2086e38bdd632d4e7479234ceebbd98d49bd0d6bc37`。

復元初回は`files/profileInstalled`の24-byte管理marker差分を検出してadmissionで停止、まだ何も移動していなかった。
差分1ファイルだけと確認し、原本tarから復元する限定continuationを実施。
`restoration-2/restored.json` が完全inventory/sync/locale成功の証拠。
端末guard原本とPC原本は残す。test生成物は同guard名の `-test-final` ディレクトリへ退避。
`restoration-launch-check/verified.json` と `restored-offer.png` は再起動後のrecovery一致と画面確認。
画面は「前回の未保存の作業があります」。復元/破棄は押していない。app.testはdisabled、言語は元のsystem追従[]。

重要: preservation.jsonの保存時stateはpreservedのまま不変で、後続restored.jsonが今回の終了証拠。
このguardを次回のfresh保全として再利用しない。必ずその時点の作品/端末状態を新しい場所に保全する。
`preserve_device.py` は今回専用。復元は完了済みで、blind再実行しない。
`pm clear`、uninstall、既存作品/古いguard/FAILログの削除は禁止。

## P4測定は未実施・起動禁止のまま

accepted `nene-pixel-p4-indexed-cutover-verification-v3`。v1/v2はuncollected archive。全33slotは未予約/未消費。
ホスト5、command2、memory20、publication2、frame4。baselineを過去計測から代用しない。
`Assert-P4CollectionImplementationReady` が意図して必ずthrowする。以下を解決しレビュー完了するまで外さない。

1. 中央collectorはhostだけ。device command/memory/publication/frameを実際のentrypointへ接続する。
   中央analyzerはhost/publication/command/memoryまででframeが未接続。全laneの実装/contract matrixをpreflightで必須化。
2. measurement/compiled inventory、実JavaExec classpath、canonical tool paths、role cloneのGit blobへの結合が不足。
   path escape/reparse/重複を拒否し、期待ファイル集合を固定。baseline ancestor、前後hashも必要。
3. APK内revisionは実物で揃ったが、preflightのvariant/package/test target/dexoptは非空検査だけ。
   aapt実manifestとのmapping、profile.sourceとaccepted canonical bytesの結合を完成させる。
4. device/保全/四つのcanvas boundsのpreflightはraw内容を充分に検証していない。
   毎slot直前のlive identity/thermal/power/display/rotation/locale/USB、no-sample geometryをparseして比較。
5. outer wrapperはanalyzerをremote停止/absence/drain/設定復元より先に呼ぶ。
   停止・drain・復元・capture seal後に解析し、結果へrestoration proofを結合する。packageは検証済APKから導出。
6. timeoutはcollectorだけ、slot全体deadline/cleanup reserveではない。active Gradle/instrumentation等の共有quiescence、
   completed chainのschema/protocol/capture/analysis/restoration hash再検証も不足。

現在のno-device PASS:
- command: lane12-command-analyzer-validator-14.log
- memory: lane12-memory-analyzer-validator-15.log
- publication: validate-p4-publication-evidence.ps1のsynthetic検査
- frame: lane3-no-device-validator-20260913-174253.log
- shared Windows Job boundary: bounded-native-contract-3.log
- outer synthetic: p4-slot-boundary-2.log
- host/preflight: p4-handoff-preflight-contract-1.log

frame単一路はmeasure-m2-frame.ps1 v8/experiment v4。16/256とも同じNew UI、pm clear除去、locale非依存ID、late frame/fatal/early-stopを実装。
commandは6/11workloads、5warm+200。memory4families各5freshprocess、十個のold projection weak refsとowning coreの構造テストを区別。
publicationはcodec encode→AtomicFile publish/write/sync/finish/readbackの既存metric。public adapter全体のtimerではない。
hostはJavaExec opt-inの5warm+20、descriptiveのみ。ignored `issue106-lane4-host-evidence.init.gradle.kts` はtyped TaskActionでCREATE_NEW streamsをclose。
synthetic failure/collision/60s timeoutのproof `init-stream-contract-proof.json` を残す。実測は一度もしていない。

`p4-preflight-template.json` はUNSETの草案。android_sdk等の最新必須項目/slot catalogもまだ埋めていない。
profile再生成はしていない。両roleのcommitted canonical profileはP62由来sha3be9f24...。
過去accepted generation鎖を必要時にread-only確認し、現在APKのpackaged bytesへ正確に結合する。過去性能値は流用不可。
QLT-011〜016に従い、文書/commit/handoffだけを理由にcold build/profile/device測定を始めない。

## Claude / Chromeの既知事実

9/12のログではWindows ClaudeCode `--model opus --chrome` とhideのChromeを使用。
`/design`はconsent/revoke、`/design-canvas`は不存在。frontend-designは参照しただけでskill実行とは呼ばない。
この既知事項を再発見してhideに確認待ちを作らない。普通のCLIデザイン+Chrome確認の実績で今回のmockを作った。
mock: `build/reports/issue-106/design/legacy-conversion.html`。
SHA `213C9382C200F8FE628AA699185B90BA8A917626A372E3E9A71D4EFB0C45A857`。
日英簡体中/明暗/小横画面/48dp/コピー・同意・キャンセルを確認済み。mockはAndroid受入証拠ではない。
本日用127.0.0.1:8770のserver(PID3912)は停止。hideのChromeタブは閉じていない。

## 次回の順序

hideの再開指示後、最初にGit/Issue/端末実物と上記証拠を照合する。実装を最初からやり直さない。
未解決の測定統合6点を修正し、独立read-only reviewと対応する狭いsynthetic検証を通す。
新たな端末保全とno-sample geometry、完全なimmutable preflight後にだけfixed33slotsを一度ずつ収集。
INVALID/PERFORMANCE_FAILは残し、後続を止める。初回失敗を消した再測定は不可。
全受入が満たされた最終PR候補でrequired quality CI `check :app:android:assembleDebug` を通し、PR/merge/main同期。
その後#107のdraft palette editor/JSON UI、#108のexact eyedropper/long-pressへ進む。M4をpaletteだけで閉じない。

Rules: ARC-001/004/005/007–012、CMD-001/002/005–010/012、KOT-001–008/013/016–020、QLT-006–009/011–016。Waivers: none。

日時: 2026-09-13 18:21:45 JST
