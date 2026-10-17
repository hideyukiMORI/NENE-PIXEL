# Privacy and data handling / プライバシーとデータの取扱い / 隐私与数据处理

Scope: current Android editor; updated 2026-10-17.

## English

### Offline editing and communication

NENE-PIXEL edits artwork on the device. It has no account, advertising, analytics, developer-operated
upload service or automatic crash-report submission. The application has no internet,
camera, microphone, location or broad storage permission. It does not send artwork, reference
images or editing history to the developer. Android and other applications have their own data
handling: a selected cloud file provider can communicate over the network, and opening a web link
uses a browser that may connect to the internet.

### What the device keeps

- **Projects and exports:** Save As creates a new `.nenepixel` file at the location chosen in
  Android's file picker. Current project format v4 contains document identity/revision, canvas,
  palette, frames, durations and layers/pixels. PNG contains the chosen frame's visible composite;
  palette JSON contains the palette. These outputs do not contain the reference underlay, undo/redo
  history, tools, zoom, appearance or app language. Opening/importing reads the selected file;
  the app does not retain a persistent file-access grant or scan other files.
- **Recovery:** one app-private, no-backup record holds the last safely published document or a
  retired marker. It is separate from files chosen in the picker. Recovery asks before replacing
  the editor, starts with empty history and needs Save As. New/load/discard and a matching verified
  save retire recovery through the existing checked workflow. Retirement/storage failure is
  reported; it must not be assumed that data was removed. Pending edits may be lost if publication
  has not completed. Recovery is not a backup of every operation.
- **Reference underlays:** choosing an image copies its decoded pixels into the editor and may
  remember those pixels, placement, opacity and visibility in app-private no-backup storage.
  The copy is separate from the source image, project, recovery and PNG export. Hiding keeps it;
  Remove requests deletion for that work. Save As copies with the same document identity share
  the remembered underlay. Deleting an external project or source image does not delete that copy.
  The store is bounded to 16 works and about 32 MiB of image-record bytes, with older records
  evicted when needed; unreadable/orphan records are cleaned up. Memory is best effort: interrupted
  writes or failed removal can leave an older copy, and there is no in-app “forget all” control.
- **Session and language:** undo/redo, previews, selection, viewport and appearance are session
  state, not project/recovery content. Language is remembered separately: Android manages it on
  Android 13+, and a private preference stores it on Android 8–12. An OS upgrade does not promise
  migration of the older preference. Select System in language settings to remove the app override.

### Removal, backup and external storage

Android's Clear storage/app-data action and a normal uninstall remove the app's private recovery,
underlay records and legacy language preference. Clear cache is different and does not remove
those records. The app does not promise secure erasure or recovery of deleted data. Android-managed
language settings follow the operating system's rules.

The current manifest disables application backup and the packaged extraction rules exclude the
private root from cloud backup and device transfer. Recovery and underlay records additionally use
Android's no-backup directory. Do not rely on Android backup or device migration to preserve them;
save important projects yourself. Android and device manufacturers manage their own transfer tools
and system settings.

Files saved to a selected provider remain there after app-data clearing or uninstall. Delete them
using that provider. Its accounts, sync, backups, trash, retention and network use are outside
NENE-PIXEL's control. A failed/cancelled fresh output is deleted best effort; an incomplete file
may remain if cleanup fails. Reported write/read-back success verifies bytes at that time and
does not guarantee later cloud or hardware durability. Providers can differ in their capabilities;
check that your chosen files open and save successfully.

### Errors and diagnostics

Expected file/recovery failures are shown as typed editor status, not automatically uploaded.
The application has no artwork/file-address logging or custom persistent diagnostic log.
Android, its libraries and selected providers may produce system diagnostics. Share reports
deliberately and remove private artwork, file paths, account details and identifiers first.

## 日本語

### オフライン編集と通信

NENE-PIXEL は端末内で作品を編集します。アカウント、広告、アクセス解析、開発者が運営する
アップロードサービス、クラッシュ報告の自動送信はありません。アプリにインターネット、
カメラ、マイク、位置情報、ストレージ全体へのアクセス権限はありません。作品、下絵、編集履歴を
開発者へ送信しません。Android や他のアプリにはそれぞれの取扱いがあります。選択したクラウドの
ファイル提供元は通信する場合があり、Web リンクを開くブラウザもインターネットに接続する場合があります。

### 端末に残るデータ

- **作品と書き出し:** 「名前を付けて保存」は Android のファイル選択画面で選んだ場所に新しい
  `.nenepixel` ファイルを作ります。現在の形式 v4 は作品の識別子・リビジョン、キャンバス、パレット、
  フレーム、表示時間、レイヤー・画素を含みます。PNG は選んだフレームの表示合成、パレット JSON は
  パレットを含みます。下絵、元に戻す・やり直す履歴、ツール、倍率、外観、アプリ言語は含みません。
  読み込みは選んだファイルを読み、永続的なファイルアクセス権を保持したり他のファイルを走査したりしません。
- **復旧:** バックアップ対象外のアプリ専用領域に、最後に安全な書き込みが完了した作品または破棄済みの
  記録を一つ保持します。選んだ保存先とは別です。編集内容を置き換える前に確認し、復旧後の履歴は空で、
  名前を付けて保存が必要です。新規・読み込み・破棄と、対応する検証済み保存は既存の確認付き処理で
  復旧記録を退役させます。退役・保存の失敗は表示されるため、削除済みと決めつけないでください。
  書き込みが終わる前の変更は失われる場合があり、全操作のバックアップではありません。
- **下絵:** 選んだ画像の復号済み画素を編集画面へコピーし、画素・位置・不透明度・表示状態を
  バックアップ対象外のアプリ専用領域に記憶する場合があります。元画像、作品ファイル、復旧、PNG 出力とは
  別です。非表示にしても残り、「削除」でその作品の記録の削除を要求します。同じ作品識別子を持つ別名保存は
  下絵の記憶を共有します。外部の作品・元画像を削除しても、このコピーは消えません。最大16作品・画像記録
  約32 MiBで、必要に応じ古いものを取り除き、不正・不完全な記録も整理します。保存はベストエフォートで、
  中断や削除失敗で古いコピーが残る場合があります。アプリ内の一括削除はありません。
- **セッションと言語:** 履歴、プレビュー、選択、表示位置・倍率、外観はセッション状態で、作品・復旧に
  含みません。言語は別に保持し、Android 13以降は OS、Android 8～12 はアプリ専用設定が管理します。
  OS 更新時の古い設定の移行は保証しません。言語設定の「システムに従う」でアプリの指定を解除できます。

### 削除・バックアップ・外部ファイル

Android のストレージ／アプリデータ消去と通常のアンインストールで、アプリ専用の復旧・下絵・旧 OS 用
言語設定は消えます。キャッシュ消去ではこれらは消えません。安全な完全消去や削除データの復元は保証しません。
OS 管理の言語設定は OS の規則に従います。

現在の manifest はアプリのバックアップを無効にし、組み込まれた抽出ルールは専用領域のルートを
クラウドバックアップと端末間転送から除外します。復旧・下絵はさらに Android のバックアップ対象外領域を
使います。バックアップや端末移行で残ることを頼りにせず、大切な作品は自分で保存してください。
Android やメーカーの移行ツール、システム設定にはそれぞれの取扱いがあります。

選んだ提供元に保存したファイルは、アプリデータ消去・アンインストール後も残ります。提供元で削除して
ください。そのアカウント、同期、バックアップ、ごみ箱、保持期間、通信は NENE-PIXEL の管理外です。
失敗・キャンセルした新規出力の削除は可能な範囲で行い、削除失敗では不完全なファイルが残る場合があります。
書き込み後の読み戻し成功はその時点のバイトを確認したもので、その後のクラウド・機器の耐久性を保証しません。
提供元によって使える機能や動作は異なります。選んだファイルを開けることと、保存の完了を確認してください。

### エラーと診断

想定されるファイル・復旧の失敗は編集画面の状態として表示し、自動送信しません。アプリに
作品・ファイルのアドレスのログ出力や独自の永続診断ログはありません。Android、ライブラリ、提供元がシステム診断を
残す場合はあります。報告を共有するときは、作品、パス、アカウント情報、識別子などの私的情報を先に
取り除いてください。

## 简体中文

### 离线编辑与通信

NENE-PIXEL 在设备上编辑作品，没有账号、广告、使用分析、开发者运营的上传服务或自动崩溃报告。
应用没有互联网、相机、麦克风、位置或整个存储空间的访问权限，不会将作品、参考底图或编辑历史
发送给开发者。Android 和其他应用各有自己的数据处理方式：所选云端文件提供方可能联网，打开网页链接的
浏览器也可能连接互联网。

### 设备保留什么

- **项目与导出:** 另存为会在 Android 文件选择器中所选的位置新建 `.nenepixel` 文件。当前 v4 格式包含
  文档标识与修订、画布、调色板、帧、时长和图层／像素。PNG 包含所选帧的可见合成图，调色板 JSON 包含
  调色板。这些输出不包含参考底图、撤销／重做历史、工具、缩放、外观或应用语言。打开／导入只读取所选文件，
  不保留永久文件访问授权，也不扫描其他文件。
- **恢复:** 应用私有且不参与备份的一个记录保存最后安全写入的作品或已退役标记，与文件选择器中的保存位置
  分开。恢复前会询问，恢复后历史为空，需要另存为。新建、打开、放弃以及匹配的已验证保存，通过既有检查流程
  退役恢复记录。退役或存储失败会显示，不能假定数据已删除。写入完成前的最新修改可能丢失；它不是每次操作的备份。
- **参考底图:** 选择图片会将解码后的像素复制到编辑器，并可能在应用私有、不参与备份的存储中记住像素、
  位置、不透明度和可见状态。此副本独立于原图、项目、恢复和 PNG 导出。隐藏仍会保留；移除会请求删除该作品的
  记录。具有相同文档标识的另存副本共享底图记忆；删除外部项目或原图不会删除此副本。最多保留16个作品、
  约32 MiB图像记录，需要时淘汰较旧记录并清理无效／不完整记录。记忆是尽力保存，中断或移除失败可能留下
  旧副本，应用内没有全部清除功能。
- **会话与语言:** 撤销／重做、预览、选区、视图和外观是会话状态，不在项目或恢复中。语言单独保留：
  Android 13及以上由系统管理，Android 8～12保存在私有偏好设置中。系统升级不保证迁移旧设置。
  在语言设置中选择“系统”可清除应用的指定语言。

### 删除、备份与外部存储

Android 的清除存储／应用数据和正常卸载会删除应用私有的恢复、底图记录及旧系统语言偏好。
清除缓存不会删除这些记录。不保证安全擦除或找回已删除的数据。系统管理的语言设置遵循系统规则。

当前 manifest 禁用应用备份，打包的提取规则从云备份与设备间传输中排除私有根目录。恢复和底图还存于
Android 的不备份目录。请自行保存重要项目，不要依靠备份或换机保留这些数据。Android 和设备厂商的迁移工具、
系统设置各有自己的处理方式。

所选提供方中的文件在清除应用数据或卸载后仍会保留，请通过该提供方删除。其账号、同步、备份、回收站、
保留期限和联网不由 NENE-PIXEL 控制。失败或取消的新输出会尽力删除，清理失败时可能留下不完整文件。
写入后的读回验证只确认当时的字节，不保证日后云端或硬件的持久性。不同提供方的能力和行为可能不同，
请确认所选文件能够打开并且保存完成。

### 错误与诊断

预期的文件／恢复失败以编辑器状态显示，不会自动上传。应用不记录作品或文件地址，也没有自建的
持久诊断日志。Android、库和提供方可能产生系统诊断。主动分享报告前，请去除私人作品、路径、账号信息和
标识符。
