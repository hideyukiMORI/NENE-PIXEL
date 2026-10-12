# Canonical Glossary

Status: normative

One concept has one canonical name. New synonyms in code are prohibited. Add or change a term through the same PR that introduces the concept; use an ADR when the term changes architecture or serialized contracts.

| Term | Canonical meaning | Do not use for this meaning |
| --- | --- | --- |
| `Document` | The complete user-created pixel project as a product concept | project data, canvas file, workspace |
| `DocumentId` | Validated 32-character lowercase hexadecimal identity of one Document | document key, UUID string |
| `DocumentState` | Immutable saved and undoable DocumentId, Revision, shared PaletteDefinition and ordered Frames under ADR 0041 | editor state, model data |
| `WorkspaceState` | Immutable ephemeral editor/session state not saved in the Document | temporary document, UI model |
| `EditorAppearance` | Workspace-owned session theme, tablet layout and physical control edge changed through SetAppearance | Compose theme flag, project preference |
| `EditorTheme` | Closed Dark or Light appearance selection | system theme Boolean |
| `EditorLayout` | Closed Tabletop bottom controls or Handheld side controls selection | device orientation, tablet Boolean |
| `EditorControlEdge` | Physical Left or Right control and drawer edge independent of text direction | RTL alignment, dominant-hand Boolean |
| actual-size window | Floating window over the canvas showing the committed document at an exact device-pixel multiple, owned by WorkspaceState and changed through SetActualSizeWindow; its UI label is Preview (a separate concept from the ToolGesture preview, and the code type names are unchanged, ADR 0026 amendment #126) | preview gesture, thumbnail, minimap, zoom window |
| `ActualSizeScale` | Closed X1/X2/X4/X8/X16/X32 device-pixels-per-cell multiple of the actual-size window | ViewportZoom, scale factor Double |
| `ActualSizeWindow` | Workspace-owned visibility, ActualSizeScale and WindowAnchor of the one actual-size window | Compose visibility flag, saved preference |
| reference underlay | One image chosen from the device that is drawn beneath every layer for tracing, owned by WorkspaceState, changed through SetReferenceUnderlay and ClearReferenceUnderlay, and never part of the document, its export or its history, though the device remembers it per work under ADR 0034; its UI label is Underlay (ADR 0032) | reference layer, background layer, tracing layer, imported image |
| `ReferenceImage` | Immutable straight RGBA8888 raster of at most 1024 x 1024 pixels decoded once from the chosen file | Bitmap, content URI, document layer |
| `UnderlayOpacity` | Integer alpha from 26 to 255 with which the reference underlay is drawn; hiding is the visibility, not a zero opacity | layer opacity, Float alpha |
| `UnderlayPlacement` | Top-left corner and scale of the reference underlay in document-pixel coordinates, clamped against the image and canvas sizes | surface offset, viewport, matrix |
| `ReferenceUnderlay` | Workspace-owned ReferenceImage, UnderlayPlacement, UnderlayOpacity, visibility and resting/adjusting interaction of the one reference underlay | saved preference, layer |
| `WorkMemory` | The state category of ARC-004 for what this device remembers about a work between sessions, outside the document; ADR 0034 introduces only the reference underlay in it | AppPreferences, project file, recovery record |
| underlay memory | The bounded app-private record of the reference underlay of each work, keyed by DocumentId, restored once after an installation and written only from WorkspaceState (ADR 0034) | saved preference, document layer, autosave |
| `RememberedUnderlay` | Validated value of the image, placement, opacity and visibility of one remembered reference underlay, exchanged through UnderlayMemoryPort | ReferenceUnderlay, DTO, pending UI state |
| `UnderlayMemoryPort` | Application-owned recall, remember and forget of one work's underlay memory, outside the persistence operation lease | ReferenceImagePort, RecoveryRecordPort, ProjectStoragePort |
| quick select | Floating work-area control whose press-drag-release or tap-mode menu chooses one `QuickSelectItem` through `QuickSelectAction`; menu and highlight are workspace state (ADR 0029) | long-press canvas menu, palette panel, radial tool menu |
| `QuickSelection` | Workspace-owned recent painted slots (at most 8, most recent first), eyedropper state and open `QuickSelectMenu` | saved preference, colour history by RGBA, Compose menu state |
| `QuickSelectItem` | Closed menu item vocabulary: `PaletteSlot(index)` or `Eyedropper`; later tool or zoom items add a case with their consuming feature | menu string id, palette-specific pointer phase |
| eyedropper | One-shot armed workspace state whose next canvas pointer down reads the exact palette slot at that pixel through `PickPaletteEntryAt` | DrawingTool, RGBA colour sampling, composited or reference-image sampling |
| `CanvasPointerIntent` | Derived closed drawing, exact-slot picking, underlay adjustment or rectangular selection meaning of the next canvas pointer down, read by presentation to translate input | DrawingTool, pointer phase |
| `WindowAnchor` | Normalized 0..1 top-left placement of a floating window inside the free space of the editor work area, on physical left/right and top/bottom axes | pixel offset, drag state, ViewportCenter |
| `EditorRuntime` | Application owner of the current CommandGateway, WorkspaceState, clean checkpoint, persistence coordination, and derived projections | view model, controller, session |
| `NewDocumentRequest` | Validated canvas request created once from raw width and height text before allocation | width/height integers, form state |
| `DocumentIdSource` | Core-owned port that supplies a validated identity without core random or process reads | UUID call in core, ID string |
| `DocumentDirtyState` | Derived closed state comparing the current runtime-local history position with DocumentCleanCheckpoint | revision comparison, latched changed flag, UI Boolean |
| `DocumentCleanCheckpoint` | Application-owned exact document/history position defining the current verified clean boundary within one runtime generation; recovered unsaved state has no user-file checkpoint | saved Revision, snapshot copy, UI dirty flag |
| `RuntimeGeneration` | Private application identity for one atomically installed set of editor owners, used with operation and history identity to reject stale persistence completion | Revision, process ID, serialized lineage |
| `PersistenceOperation` | One runtime-owned capture/completion or load/install coordination sequence with an opaque identity, one physical-operation lease, and typed busy/stale/cancelling outcomes | adapter job, UI Boolean, DocumentCommand |
| `PersistenceOperationProjection` | Read-only immutable phase, last outcome, and recovery status derived from the sole EditorRuntime-owned persistence state | mutable UI operation flag, adapter state |
| `Project Format v1` | The exact bounded `.nenepixel` byte contract in `PROJECT_FORMAT_V1.md` for DocumentId, Revision, CanvasSize, and straight-sRGB RGBA8 pixels | domain model serialization, recovery envelope, external API |
| `ProjectFormatBytes` | Defensively owned bounded project bytes or the current maximum-plus-one validation probe; older decoders retain their own version limits | ByteArray API, stream, mutable buffer, validated document |
| `Project Format v2` | Exact indexed document byte contract in PROJECT_FORMAT_V2.md, including ordered palette/default and U8 raster | RGBA v1 writer, JSON palette file |
| `ProjectFormatCodec` | One no-I/O read-v1/v2/v3/v4, write-v4 boundary; exact legacy-source encoding is restricted to original copying | public per-version document writer, import planner |
| `ProjectFormatVersion` | Meaning-bearing unsigned 16-bit project-format wire version used in typed diagnostics | schema Int, app version |
| `ProjectFormatResult` | Closed accepted/rejected result shared by bounded-byte construction and explicit version decoding | nullable result, exception, Boolean success |
| `ProjectFormatRejection` | Closed reachable reason that project-format bytes or version decoding are rejected | I/O failure, generic parse error, error string |
| `RecoveryRecord` | One app-private AtomicFile envelope with version-matched Candidate project payload or Retired marker and conditional generation identity; read 1/2/3/4, write 4 | project file, autosave journal, timestamp winner |
| `RecoveryGeneration` | Validated positive identity stored in a RecoveryRecord and advanced by each verified Candidate or Retired publication | Revision, runtime generation, timestamp |
| `ExpectedRecoveryLineage` | Closed Missing or Present(RecoveryGeneration) precondition for one conditional retirement; Missing compares as virtual zero without creating RecoveryGeneration(0) | nullable generation, unchecked Long |
| `AutosaveCapture` | Application-owned immutable latest committed DocumentState and its exact runtime-generation/history-position identity waiting for one Candidate publication | autosave queue, revision-only identity, DocumentCleanCheckpoint |
| `AutosaveStateToken` | Opaque immutable equality token for one runtime generation and internal HistoryPosition, created only by application ownership | Revision, numeric public lineage, persisted identity |
| `AutosaveProjection` | Read-only pending/published/publishing state tokens and last autosave outcome derived from the sole EditorRuntime-owned autosave state | mutable UI autosave flag, adapter state |
| `AutosavePolicy` | The one app-module value holding the ADR 0018 quiet window and latency cap read by the autosave scheduler | debounce constant in core, adapter timeout, periodic timer |
| `ViewportZoom` | Validated finite fit-relative viewport factor in the closed range 1.0 through 64.0 | raw scale Double, saved zoom |
| `ViewportCenter` | Validated finite preferred center in continuous document-edge coordinates | screen pan, surface offset |
| `ViewportState` | Workspace-owned fit-relative zoom and document-coordinate center | camera state, transform matrix |
| `ViewportSurface` | Validated physical-pixel extent and pixels-per-dp used as a viewport transform input | canvas size, Compose Size |
| `ViewportSurfacePoint` | Validated finite point in physical-pixel surface coordinates | Compose Offset, document point |
| `ViewportGesture` | Previous and current pair of validated surface points for one atomic two-pointer transform | raw pointer event, tool gesture |
| `ViewportTransform` | Immutable canonical forward/inverse mapping derived from CanvasSize, ViewportSurface, and ViewportState | UI matrix, touch translator |
| `ViewportSurfaceBounds` | Four finite physical-pixel edges of one projected document pixel | PixelRegion, Compose Rect |
| `ViewportGridVisibility` | Derived visible/hidden grid result from cell scale and density | grid preference, workspace flag |
| `ViewportMappingResult` | Closed mapped/outside-surface/outside-canvas result of viewport conversion | nullable mapping, Boolean inside |
| `ViewportValueResult` | Closed created/rejected result for viewport value or derived-transform validation | DomainValueResult, nullable viewport |
| `ViewportValueRejection` | Closed expected reason a viewport value or derived calculation is invalid | exception, error string |
| `RenderCache` | Disposable derived data used only to accelerate rendering | state, document cache |
| `CanvasSize` | Validated pixel width and height whose total area is at most 65,536 pixels | image size when referring to document coordinates |
| `CanvasWidth` / `CanvasHeight` | Positive typed axis dimension in the supported range 1 through 256 | width/height Int at a core boundary |
| `PixelPosition` | Validated integer position in document pixel coordinates | point, coord, offset |
| `DocumentPoint` | Signed integer document-pixel sample that may lie outside the canvas; ToolGesture emits only its canvas-contained interpolated cells (ADR 0007, #163) | PixelPosition, surface point, clipped pointer |
| `PixelX` / `PixelY` | Non-negative typed document pixel axes used to construct PixelPosition | x/y Int at a core boundary |
| `PixelRegion` | Canvas-contained rectangular region with half-open right/bottom bounds | rect when domain meaning is intended |
| `ColorChannel` | One unsigned 8-bit semantic RGBA channel | color byte, channel Int |
| `PixelColor` | Straight sRGB red, green, blue, and alpha U8 values; hidden RGB at alpha zero is significant | premultiplied color, packed color, Android Color |
| `PixelBlank` | Exact transparent-black PixelColor `(0,0,0,0)`; indexed fill/erase uses the document defaultIndex, which may name another color | implicit erase target, alpha-only clear |
| `PixelLimits` | One conservative MVP policy: axis 256, area 65,536, raw stroke 262,144, patch 65,536, history 64, retained changes 524,288 and logical payload 8 MiB | device memory check, adapter-local maximum |
| `Palette` | Immutable ordered value containing 1 through 256 exact PixelColor entries; sole color-list owner referenced by PaletteDefinition | duplicate color storage, mutable color list |
| `PaletteLimits` | One deterministic palette policy with maximum 256 entries and definition minimum 2; a lower-level one-entry Palette is never a document definition | screen-size-dependent palette cap, PixelLimits |
| `PaletteIndex` | Non-negative typed position used for closed lookup and active Palette selection | palette Int, color index |
| `PaletteEntry` | Derived immutable Palette slot with its typed index and exact PixelColor | color id, mutable swatch data |
| `Revision` | Non-negative version of the exact committed DocumentState; canonical undo restores the recorded prior revision | global event sequence, timestamp |
| `Layer` | A frame-owned ordered element with LayerId, name, visibility and one PixelSnapshot; 1 through 16 per frame, bottom first, with at most 16 total document records | plane, sheet |
| `LayerId` | Positive document-unique layer identity independent of order and name; new = current maximum + 1 | layer index, layer position |
| `DuplicateLayerCommand` | Source-admitted command inserting a new layer directly above a named source, preserving its name, visibility and exact immutable pixels as one ordinary Added transition (ADR 0030 / #208) | UI copy loop, imported layer, merged layer |
| `PixelTransfer` | Validated canvas, source PixelRegion, destination PixelPlacement and Move/Copy mode for exact same-layer rectangular replacement, including Empty (ADR 0039/0040) | clipboard pixels, raw offset, blended image |
| `PixelTransferMode` | Closed Move or Copy meaning of a rectangular transfer | Boolean copy flag, drawing tool |
| `PixelOrientation` | One of eight exact orthogonal orientations relative to the original source; then composes a later operation on the displayed rectangle (ADR 0040) | arbitrary angle, cumulative resampled pixels, presentation matrix |
| `PixelPlacement` | Typed destination position and PixelOrientation grouped for a rectangular transfer; canvas fit is admitted by PixelTransfer (ADR 0040) | copied pixels, independently editable rectangle |
| `TransferPixelsCommand` | Source-admitted command applying one PixelTransfer through an ordinary pixel patch and layer change | sequence of paint commands, direct UI pixel copy |
| `SelectionSession` | Workspace-owned captured admission, target layer, rectangle/destination geometry and orientation, mode and pointer phase before Apply; owns no pixel payload | saved selection, clipboard document, Compose-local draft |
| `EditTarget` | Workspace-owned atomic active FrameId, LayerId and palette slot; never saved | current layer state, brush target |
| `Empty` | A pixel cell with no palette index; always fully transparent and never remapped | transparent index, erase color |
| `Composite` | The derived visible RGBA image of visible layers under ADR 0030's one integer source-over rule; never document truth | flattened layer, merged image |
| `Frame` | One ordered frame with stable identity, saved duration and ordered layer state; [frame vocabulary](#frames-adr-0041) | page, image |
| `PixelSurface` | Pixel-engine private mutable flat packed work surface for a bounded raster | Android Bitmap, domain snapshot |
| `PixelSnapshot` | Domain-owned immutable canvas size and packed row-major U8 indices plus a one-bit coverage mask for `Empty` cells, with no second palette owner | exposed buffer, RGBA raster, Android Bitmap |
| `PixelPatch` | Pixel-engine-owned row-major positions and before/after `PixelCell` changes (`Empty` or a covered index) for one layer, with one shared directional inverse payload | materialized inverse, diff, delta |
| `Stroke` | One committed drawing gesture with a defined tool/path/style | line when it includes the complete drawing operation |
| `DrawingTool` | Closed workspace-selectable Pencil or Eraser identity | brush string, UI-local selected tool |
| `StrokeEffect` | Closed Paint or Erase intent carrying the gesture-captured target PaletteIndex | handler choice, current UI color |
| `ToolGesture` | Application-owned bounded sampled gesture retaining its captured effect and gateway admission through canonical interpolation and commit | command, raw surface path, Compose-owned stroke |
| `DocumentCommand` | Typed request to perform one atomic saved/undoable operation | event, action, intent |
| `WorkspaceAction` | Typed request to change ephemeral WorkspaceState, including active tool and PaletteIndex | command, UI event |
| `CommandHandler` | The single application component implementing one command | manager, processor |
| `CommandGateway` | The only entry point for executing DocumentCommand values | bus when no asynchronous/distributed bus exists |
| `CommandResult` | Closed applied/rejected/failed outcome returned by command execution | nullable result, Boolean success |
| `DocumentTransition` | Pure description of the next DocumentState and its ChangeSet | mutation callback, update result |
| `ChangeSet` | Complete committed palette/default and optional indexed transition, exact revisions and shared inverse used by state/history/invalidation | result data, diff |
| `RejectionReason` | Expected typed reason a validly formed command cannot apply | error string, exception |
| `CommandFailure` | Typed external/runtime failure while executing a command | rejection, false |
| `DomainValueResult` | Closed created/rejected result returned by invariant-bearing domain factories | nullable value, thrown validation error |
| `HistoryEntry` | Undo/redo record derived from one committed ChangeSet | callback, snapshot stack item |
| `BoundedLinearHistory` | Gateway-owned ordered HistoryEntry list with one between-entry cursor and oldest-first entry/change/logical-byte eviction | undo stack plus redo stack, snapshot history |
| `HistoryPosition` | Internal runtime-local identity restored with a history cursor and used by clean, persistence and prepared-command preconditions | Revision, audit sequence, persisted lineage |
| `CommandSourceAdmission` | Non-constructible gateway-issued owner/document/exact-position token with immutable planning base | snapshot reference identity, revision-only command input |
| `HistoryAvailability` | Closed none, undo-only, redo-only, or undo-and-redo projection derived from the history cursor | mutually exclusive stack flag, two UI-owned Booleans |
| `Port` | Core-owned interface for a required outside capability | service interface, gateway when it is not command execution |
| `Adapter` | Boundary implementation or translator connected to a Port | manager, integration helper |
| `Codec` | Deterministic encoder/decoder for a versioned format | serializer helper, converter |
| `Mapper` | Pure conversion between two explicitly named representations | util, transformer |
| `Renderer` | Component that derives visible output from state without changing document truth | view manager |
| `PngExportPort` | Application-owned export of one immutable document-and-frame capture without changing editor truth or recovery | save checkpoint, renderer capture |
| `PngBytes` | Adapter-internal immutable bounded PNG encoding with defensive bulk copies | mutable pixel surface, project file |
| `DocumentCreationRequest` | Typed fresh-destination request selecting PROJECT or PNG and a suggested filename | existing URI, raw MIME selection |

## Frames (ADR 0041)

Issue #216 implements these definitions and the v4/envelope 4 path under
[ADR 0041](adr/0041-bounded-frames-and-project-v4.md). There is no document-level active-frame
layer alias or unused playback API.

| Term | Canonical meaning | Not an alias for |
| --- | --- | --- |
| `FrameId` | Positive document-unique Int independent of chronological position; new ID follows the current document maximum and Undo/Redo restores recorded IDs | ordinal, filename, layer ID |
| `FrameDuration` | Validated saved integer milliseconds from 10 through 60,000, default 100; command-editable timing data | elapsed clock sample, playback counter |
| frame capacity | 1–16 chronological frames, each with 1–16 bottom-first layers, at most 16 total logical layer records including shared snapshots | independent 16-by-16 allowance, runtime free memory |
| frame focus | The workspace EditTarget's atomic active FrameId, LayerId and palette slot; SelectFrame chooses a different frame's top layer, same-frame choice retains its layer | saved selection, per-frame remembered-layer map |
| frame structure transition | One ChangeSet structural branch targeting an existing frame's layer structure or adding/deleting/moving/changing duration of a frame | frame-local history, per-layer command sequence |
| PNG export capture | Application-owned immutable committed document plus active FrameId captured together under the runtime lock; the captured frame alone supplies visibility and pixels | current UI frame at write time, animation export |
| PNG import target | FrameId and insertion-anchor LayerId captured with source admission when picking begins; focus changes do not retarget the layer form | palette-only raster plan, current selection at confirmation |
| `Project Format v4` | Exact bounded frame hierarchy byte contract in PROJECT_FORMAT_V4.md, used by all current document writes | saved opacity, a v3 writer accepting frames |
| playback session | ADR0042 workspace-owned Loop/Once preview using saved duration/order and explicit monotonic samples, a fresh opaque token and bounded immutable timing metadata | document command per tick, Compose-owned frame counter |
| `PlaybackView` | Visible mode and optional paired session token/displayed FrameId; excludes sampled time and fractional progress so same-frame pulses retain equality | editing focus, independent timeline |
| displayed frame | PlaybackView's frame while playing, otherwise EditTarget's frame; shared by canvas and actual-size | a second editable active frame, PNG target captured from the screen |

Frame LayerIds remain globally unique across the document. The shared PaletteDefinition applies
to all frames, including non-active/hidden layers during remapping. Render/cache identity includes
the displayed frame; preparations retain at most the active frame. Save/autosave/recovery capture
the whole committed hierarchy, and focus changes create no history or dirty/autosave effect.
Playback follows ADR0042; saved opacity and animated export remain separate consuming decisions.

## Indexed palette and preserved import (ADR 0022/0024)

The #106 cutover adopts the indexed document and compatibility vocabulary atomically. Palette draft
editing remains #107; target-only session types are introduced with their consuming implementation.

| Term | Canonical meaning | Not an alias for |
| --- | --- | --- |
| `PaletteDefinition` | Immutable Palette plus validated defaultIndex, 2–256 actual entries; document-owned definition and interchange value | active swatch, generic JSON DTO |
| `PaletteRemap` | Complete immutable old-slot→new-slot mapping bound to exact source/target definitions, created through one domain factory | partial draft, pixel patch, inverse history, runtime token |
| `PaletteRemapPlanner` | Pixel-engine algorithm producing the same validated remap for number/nearest/explicit replacement, reorder or deletion | UI mapping logic, command handler, generic converter |
| `PaletteRemapResult` | Closed planned/rejected outcome of the bounded palette planner | command result, document commit |
| `PaletteRemapRejection` | Domain mapping rejection or invalid reorder/deletion input | file error, stale runtime token |
| `defaultIndex` | Slot used to fill a new indexed canvas and by Eraser; may be nontransparent | active Pencil selection, transparency sentinel |
| `PaletteEditSession` | Workspace-owned bounded draft timeline tied to exact source generation/history position, with at most one continuous slot-colour edit whose intermediate values are preview only | document history, Compose-owned document copy |
| `ReplacePaletteCommand` | Atomic source-admitted command committing definition and complete pixel mapping | immediate import side effect |
| `PaletteJsonBytes` | Defensive bounded palette-file bytes, maximum-plus-one probe | ProjectFormatBytes, unbounded input string |
| `PaletteJsonCodec` | The no-I/O palette JSON v1 mapping to/from PaletteDefinition | project codec, runtime mutation path |
| `PaletteJsonResult` | Closed accepted/rejected result for palette carrier construction and definition decoding | nullable parse result, Boolean success |
| `PaletteJsonRejection` | Closed format, syntax, encoding, resource or domain-definition failure from the palette codec | localized message, I/O failure |
| `ConversionRequired` | Uninstalled exact source needs explicit lossy conversion to fit indexed limits | successful lossless migration, editable RGBA compatibility mode |
| `LegacyRgbaSource` | Bounded immutable uninstalled exact v1 identity/revision/size/RGBA raster, never an editable runtime | DocumentState, mutable import buffer, format DTO |
| `DocumentImportSource` | Closed Current(DocumentState) or Legacy(LegacyRgbaSource) value returned by validated load/recovery ports | serialized DTO, adapter-owned conversion policy |
| `LegacySourceCopyOutcome` | Closed Copied/Cancelled/Failed result for exact original-project copy; Copied requires complete close/read-back | save checkpoint, picker success, reduced export |
| PNG import | Reading one picked PNG exactly and adding it as a new work or as a new layer under ADR 0033; no palette is generated and nothing is dithered | reference underlay, legacy v1 conversion, PNG export |
| `ImportRaster` | Bounded immutable uninstalled straight RGBA8888 raster of at most 1024 x 1024 pixels read exactly from a picked PNG | ReferenceImage, LegacyRgbaSource, DocumentState, Bitmap |
| `RasterImportPlanner` | Pixel-engine algorithm mapping an ImportRaster to a new-work plan or a layer plan by exact match, append and the nearest metric, with Empty cells for transparent pixels | quantizer, LegacyImportPlanner, adapter conversion |
| `LayerImportPlan` | Validated domain value binding one planned layer snapshot and its counts to the source and resulting palette definitions | PaletteRemap, pixel patch, pending UI state |
| `NewWorkImportPlan` | Validated domain value holding the canvas size, palette definition and one layer snapshot of a work to be opened from a PNG | DocumentState, DocumentImportSource, LegacyReductionPreview |
| `ImportLayerCommand` | Atomic source-admitted command adding one layer that already holds planned pixels, with the plan's palette transition, as one history entry | AddLayerCommand, ReplacePaletteCommand, immediate import side effect |
| `PendingRasterImport` | Workspace-owned facts and plans of one picked PNG awaiting the choice of a form | persistence operation, document state, retained raster |
| `PngImportPort` | Application-owned pick of one PNG returning an ImportRaster or a typed cancellation, rejection or failure | ReferenceImagePort, ProjectStoragePort.load |
| `PersistencePorts` | Named composition input grouping existing project/recovery/PNG ports for one workflow | service locator, second persistence owner |

Target command/session names are introduced only in their consuming implementation Issue.

## Reserved state verbs

- `create`: construct a new validated value/entity through its canonical factory
- `apply`: execute a patch or transition against a defined input
- `execute`: run a `DocumentCommand` through the command boundary
- `reduce`: apply a `WorkspaceAction` to `WorkspaceState`
- `map`: pure representation conversion
- `encode` / `decode`: convert a versioned external representation
- `render`: derive visual output without document mutation
- `persist` / `load`: adapter-level durable storage operations

Avoid vague verbs such as `process`, `handle`, `update`, `do`, and `manage` unless the canonical contract gives them a narrower explicit meaning. Command handler methods use the one method name chosen by the initial application API ADR.

## App language (ADR 0021)

`AppPreferences` are app/platform settings independent of the open document. `AppLanguage` is the
closed System/English/Japanese/SimplifiedChinese selection. `AppLanguageSettings` is its read-only
UI projection with operation status; platform storage is the authority. A localized resource context
is disposable display configuration derived from the selected language and host configuration.
