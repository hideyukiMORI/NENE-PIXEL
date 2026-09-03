# Canonical Glossary

Status: normative

One concept has one canonical name. New synonyms in code are prohibited. Add or change a term through the same PR that introduces the concept; use an ADR when the term changes architecture or serialized contracts.

| Term | Canonical meaning | Do not use for this meaning |
| --- | --- | --- |
| `Document` | The complete user-created pixel project as a product concept | project data, canvas file, workspace |
| `DocumentId` | Validated 32-character lowercase hexadecimal identity of one Document | document key, UUID string |
| `DocumentState` | Immutable saved and undoable truth of a Document | editor state, model data |
| `WorkspaceState` | Immutable ephemeral editor/session state not saved in the Document | temporary document, UI model |
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
| `PaletteIndex` | Non-negative typed position of a PaletteEntry | palette Int, color index |
| `PaletteEntry` | A palette slot with its typed color value/metadata | color id, swatch data |
| `Revision` | Non-negative version of the exact committed DocumentState; canonical undo restores the recorded prior revision | global event sequence, timestamp |
| `Layer` | An ordered document element contributing pixels/visibility | plane, sheet |
| `Frame` | One animation frame containing an ordered layer state | page, image |
| `PixelSurface` | Pixel-engine private mutable flat packed work surface for a bounded raster | Android Bitmap, domain snapshot |
| `PixelSnapshot` | Domain-owned immutable row-major semantic pixels at one revision, privately stored as packed `RRGGBBAA` | exposed buffer, Android Bitmap |
| `PixelPatch` | Pixel-engine-owned row-major packed before/after changes with one shared directional inverse payload | materialized inverse, diff, delta |
| `Stroke` | One committed drawing gesture with a defined tool/path/style | line when it includes the complete drawing operation |
| `ToolGesture` | Presentation-level pointer/stylus sequence before commit | command, stroke before it is committed |
| `DocumentCommand` | Typed request to perform one atomic saved/undoable operation | event, action, intent |
| `WorkspaceAction` | Typed request to change ephemeral WorkspaceState | command, UI event |
| `CommandHandler` | The single application component implementing one command | manager, processor |
| `CommandGateway` | The only entry point for executing DocumentCommand values | bus when no asynchronous/distributed bus exists |
| `CommandResult` | Closed applied/rejected/failed outcome returned by command execution | nullable result, Boolean success |
| `DocumentTransition` | Pure description of the next DocumentState and its ChangeSet | mutation callback, update result |
| `ChangeSet` | Complete committed transition record used by state, history, and invalidation | result data, diff |
| `RejectionReason` | Expected typed reason a validly formed command cannot apply | error string, exception |
| `CommandFailure` | Typed external/runtime failure while executing a command | rejection, false |
| `DomainValueResult` | Closed created/rejected result returned by invariant-bearing domain factories | nullable value, thrown validation error |
| `HistoryEntry` | Undo/redo record derived from one committed ChangeSet | callback, snapshot stack item |
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
