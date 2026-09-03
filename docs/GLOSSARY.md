# Canonical Glossary

Status: normative

One concept has one canonical name. New synonyms in code are prohibited. Add or change a term through the same PR that introduces the concept; use an ADR when the term changes architecture or serialized contracts.

| Term | Canonical meaning | Do not use for this meaning |
| --- | --- | --- |
| `Document` | The complete user-created pixel project as a product concept | project data, canvas file, workspace |
| `DocumentId` | Validated 32-character lowercase hexadecimal identity of one Document | document key, UUID string |
| `DocumentState` | Immutable saved and undoable truth of a Document | editor state, model data |
| `WorkspaceState` | Immutable ephemeral editor/session state not saved in the Document | temporary document, UI model |
| `EditorRuntime` | Application owner of the current CommandGateway, WorkspaceState, clean checkpoint, and derived dirty-state projection | view model, controller, session |
| `NewDocumentRequest` | Validated canvas request created once from raw width and height text before allocation | width/height integers, form state |
| `DocumentIdSource` | Core-owned port that supplies a validated identity without core random or process reads | UUID call in core, ID string |
| `DocumentDirtyState` | Derived closed state comparing the current runtime-local history position with DocumentCleanCheckpoint | revision comparison, latched changed flag, UI Boolean |
| `DocumentCleanCheckpoint` | Application-owned document identity and runtime-local HistoryPosition defining the current clean boundary | saved Revision, snapshot copy, UI dirty flag |
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
| `PixelX` / `PixelY` | Non-negative typed document pixel axes used to construct PixelPosition | x/y Int at a core boundary |
| `PixelRegion` | Canvas-contained rectangular region with half-open right/bottom bounds | rect when domain meaning is intended |
| `ColorChannel` | One unsigned 8-bit semantic RGBA channel | color byte, channel Int |
| `PixelColor` | Straight sRGB red, green, blue, and alpha U8 values; hidden RGB at alpha zero is significant | premultiplied color, packed color, Android Color |
| `PixelBlank` | Canonical transparent-black `PixelColor` `(0,0,0,0)` written by erasing and used for a blank new canvas | white background, alpha-only clear |
| `PixelLimits` | One conservative MVP policy: axis 256, area 65,536, raw stroke 262,144, patch 65,536, history 64, retained changes 524,288 | device memory check, adapter-local maximum |
| `Palette` | Immutable ordered tool configuration containing 1 through 32 exact PixelColor entries | DocumentState palette, mutable color list |
| `PaletteLimits` | One deterministic tool-palette policy whose maximum entry count is 32 | screen-size-dependent palette cap, PixelLimits |
| `PaletteIndex` | Non-negative typed position used for closed lookup and active Palette selection | palette Int, color index |
| `PaletteEntry` | Derived immutable Palette slot with its typed index and exact PixelColor | color id, mutable swatch data |
| `Revision` | Non-negative version of the exact committed DocumentState; canonical undo restores the recorded prior revision | global event sequence, timestamp |
| `Layer` | An ordered document element contributing pixels/visibility | plane, sheet |
| `Frame` | One animation frame containing an ordered layer state | page, image |
| `PixelSurface` | Pixel-engine private mutable flat packed work surface for a bounded raster | Android Bitmap, domain snapshot |
| `PixelSnapshot` | Domain-owned immutable row-major semantic pixels at one revision, privately stored as packed `RRGGBBAA` | exposed buffer, Android Bitmap |
| `PixelPatch` | Pixel-engine-owned row-major packed before/after changes with one shared directional inverse payload | materialized inverse, diff, delta |
| `Stroke` | One committed drawing gesture with a defined tool/path/style | line when it includes the complete drawing operation |
| `DrawingTool` | Closed workspace-selectable Pencil or Eraser identity | brush string, UI-local selected tool |
| `StrokeEffect` | Gesture-captured document replacement effect: Paint with exact PixelColor or Erase to PixelBlank | handler choice, UI color |
| `ToolGesture` | Application-owned bounded sampled gesture with canonical document-pixel interpolation before commit | command, raw surface path, Compose-owned stroke |
| `DocumentCommand` | Typed request to perform one atomic saved/undoable operation | event, action, intent |
| `WorkspaceAction` | Typed request to change ephemeral WorkspaceState, including active tool and PaletteIndex | command, UI event |
| `CommandHandler` | The single application component implementing one command | manager, processor |
| `CommandGateway` | The only entry point for executing DocumentCommand values | bus when no asynchronous/distributed bus exists |
| `CommandResult` | Closed applied/rejected/failed outcome returned by command execution | nullable result, Boolean success |
| `DocumentTransition` | Pure description of the next DocumentState and its ChangeSet | mutation callback, update result |
| `ChangeSet` | Complete committed transition record used by state, history, and invalidation | result data, diff |
| `RejectionReason` | Expected typed reason a validly formed command cannot apply | error string, exception |
| `CommandFailure` | Typed external/runtime failure while executing a command | rejection, false |
| `DomainValueResult` | Closed created/rejected result returned by invariant-bearing domain factories | nullable value, thrown validation error |
| `HistoryEntry` | Undo/redo record derived from one committed ChangeSet | callback, snapshot stack item |
| `BoundedLinearHistory` | Gateway-owned ordered HistoryEntry list with one between-entry cursor and oldest-first dual-budget eviction | undo stack plus redo stack, snapshot history |
| `HistoryPosition` | Internal runtime-local identity restored with a history cursor and used by the clean checkpoint | Revision, audit sequence, persisted lineage |
| `HistoryAvailability` | Closed none, undo-only, redo-only, or undo-and-redo projection derived from the history cursor | mutually exclusive stack flag, two UI-owned Booleans |
| `Port` | Core-owned interface for a required outside capability | service interface, gateway when it is not command execution |
| `Adapter` | Boundary implementation or translator connected to a Port | manager, integration helper |
| `Codec` | Deterministic encoder/decoder for a versioned format | serializer helper, converter |
| `Mapper` | Pure conversion between two explicitly named representations | util, transformer |
| `Renderer` | Component that derives visible output from state without changing document truth | view manager |

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
