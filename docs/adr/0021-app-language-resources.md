# ADR 0021: App language settings and localized resources

- Status: accepted
- Date: 2026-09-12
- Issue: #102
- Affected rules: ARC-001/003/004/006/007/012, CMD-001/002/009/012,
  KOT-001/002/008/014/017–019, QLT-006 and QLT-011–016

## Context

hide requested English, Japanese and Chinese UI with retained language selection, then authorized
implementation. The initial Chinese scope assumes Simplified Chinese (`zh-Hans`), not a
claim of Traditional Chinese translation. Current `MainActivity` is a `ComponentActivity`, minSdk
is 26, and editor text and test locators contain English literals. Language is an app preference,
not project truth or a document-specific workspace selection. Existing OS fonts support these scripts.

## Decision

`AppPreferences` is an explicit platform-setting category, separate from editor state. This Issue
introduces only app language. On API 33+ Android `LocaleManager.applicationLocales` is authoritative;
on API 26–32 one private SharedPreferences tag is authoritative. One injected platform adapter
selects the appropriate backing for that Android version. There are no concurrent competing stores
for the current setting and no locale in the project schema, document commands or workspace.
The retained app language controller publishes immutable language/status projections. Storage and
startup inspection use its lifecycle scope and injected dispatcher; no main-thread file operation,
global `Locale.setDefault`, unmanaged coroutine or Compose service lookup is introduced.

Choices are System, English (`en`), Japanese (`ja`) and SimplifiedChinese (`zh-Hans`). The default is
System. Write success precedes publication; a failed read/write produces a typed, retryable status.
Legacy commit failure restores the previous preference in memory and attempts a durable rollback,
then reports Failed regardless of rollback outcome; Failed never claims durable success. Regional
OS tags are normalized by language/script. Unsupported explicit overrides report read failure,
not System, so selecting System can always clear them.
One operation runs at a time. Equal selection in a ready state is a no-op. The OS setting is reread
on activity start so external changes and configuration recreation cannot leave a stale UI setting.
The Android 13+ OS value takes precedence over any unused older-OS preference; OS-upgrade transfer
of a previous legacy selection is outside this first release and is disclosed as a remaining risk.

The app composition root applies one localized resource context/Configuration to Compose. The
context is derived from the current Android configuration and selected language, preserves all
non-language configuration (font scale, density, orientation), and is invalidated on either change.
System mode uses the host configuration. The app-owned Compose boundary explicitly provides `LocalResources` as well as Context and
Configuration, so a Dialog-owned Compose root cannot fall back to the host language on legacy OS.
It also derives `LocalLayoutDirection` from that configuration.
Both API ranges render through the same `stringResource`
and resource formatter path. This bounded context adaptation provides API26–32 support without
AppCompat Activity/dependency changes. API33+ uses AGP-generated `LocaleConfig` from the three shipped language resources and an English
default; resource configurations restrict transitive library translations to this supported set.
The native settings UI and picker share this declaration. No automatic/manual dual declaration.
App Bundle language splitting is disabled so all three translations remain available offline.

The editor is shown after asynchronous startup language inspection settles. The platform emits
immutable `AppLanguageSettings` and typed selection/retry callbacks to presentation; these are the
new public presentation API, not Android types in core. A language change cancels active preview
through the existing editor cancellation callback, changes no document/history/checkpoint/capture,
and retains the runtime and session appearance. Existing lifecycle autosave flush still applies.
Panel identity, new-document raw text and submission-attempt state are saveable presentation
mechanics. Validation is re-derived through `NewDocumentRequest` after restore, never copied into a
second validation path. Typed confirmation and picker ownership stay with the existing workflow.

## Consequences

### Resource contract

Presentation owns complete default English `values/strings.xml`, Japanese `values-ja` and Chinese
`values-b+zh+Hans` resources. The app owns only host configuration/resources. UI labels, state/error
messages and accessibility descriptions use resource IDs; enum names and concatenated English are
not a display API. Typed application outcomes are mapped in presentation, with positional format
arguments and plurals as appropriate. No translated String is retained in the controller/runtime.
Same meaning has one key; changing locale cannot change validation, filenames, schema keys or pixels.
User input, technical identifiers and format tokens are not translated. System fonts remain in use.

English resources are complete fallback. All shipped translated keys and format arguments match the default set. Plurals contain only
locale-relevant categories (English one/other; Japanese and Chinese other), with compatible arguments. Android locale matching selects regional/script resources; Simplified
Chinese is labeled explicitly. No guarantee of untranslated Traditional Chinese UI is implied.

Human-readable semantics are localized. Stable presentation test tags identify actions, canvas,
fields, panels and palette entries independently of locale; tags are also exported as resource IDs
for the out-of-process UiAutomator consumers. ADR 0020's stable semantics mean stable operation
identity and meaning, not frozen English words. Existing drawing/retirement/picker assertions remain.

## Rejected alternatives

- Kotlin string constants or a parallel translation dictionary: neither provides the one Android
  resource fallback/formatting path.
- Locale in `WorkspaceState` or project files: creates a duplicate owner of a platform preference.
- AppCompat auto-store: adds an otherwise unused Activity stack and synchronous legacy storage;
  the selected resource-context boundary is sufficient for this Compose-only application.
- Downloaded/bundled fonts: installed Android CJK fallback already serves the requested UI scripts.

## Enforcement impact

Host contracts cover typed failure/retry/no-op/ordering and resource/format completeness. Device
functional tests cover all three languages, runtime/history/input retention, OS setting sync,
restart persistence and the API26 compatibility path, including localized glyph/layout review.
Related test and profile-harness consumers migrate in this Issue; their compilation is required,
not profile regeneration. No pixel/storage representation change, performance claim or measurement
is included. Existing quality gates and full final PR CI are unchanged. Waivers: none.

## Migration and rollback

Replace literal UI and human-language locators together. Project and recovery formats stay v1.
Rollback restores the previous shell text; any stored language setting contains no artwork.
The app declares its existing Compose UI artifact directly for the host resource/Configuration
boundary (same pinned version, already in its runtime graph); no new runtime artifact, module, font
binary, HTTP/MCP API or document serialization is introduced.

## Related

- [Android app languages](https://developer.android.com/guide/topics/resources/app-languages)
- [Compose resources](https://developer.android.com/develop/ui/compose/resources)
- [Context.createConfigurationContext](https://developer.android.com/reference/android/content/Context#createConfigurationContext(android.content.res.Configuration))
- Refines: ADR 0020. Supersedes: none. Superseded by: none.
