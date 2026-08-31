# Canonical Glossary

Status: normative

One concept has one canonical name. New synonyms in code are prohibited. Add or change a term through the same PR that introduces the concept; use an ADR when the term changes architecture or serialized contracts.

| Term | Canonical meaning | Do not use for this meaning |
| --- | --- | --- |
| `Document` | The complete user-created pixel project as a product concept | project data, canvas file, workspace |
| `DocumentState` | Immutable saved and undoable truth of a Document | editor state, model data |
| `WorkspaceState` | Immutable ephemeral editor/session state not saved in the Document | temporary document, UI model |
| `RenderCache` | Disposable derived data used only to accelerate rendering | state, document cache |
| `CanvasSize` | Validated pixel width and height of the drawable coordinate space | image size when referring to document coordinates |
| `PixelPosition` | Validated integer position in document pixel coordinates | point, coord, offset |
| `PixelRegion` | Bounded rectangular region in document pixel coordinates | rect when domain meaning is intended |
| `PaletteEntry` | A palette slot with its typed color value/metadata | color id, swatch data |
| `Layer` | An ordered document element contributing pixels/visibility | plane, sheet |
| `Frame` | One animation frame containing an ordered layer state | page, image |
| `PixelSurface` | Pixel-engine abstraction for a bounded raster surface | bitmap when referring to the core abstraction |
| `PixelSnapshot` | Immutable, owned view of complete pixel content at a defined revision | exposed buffer, bitmap data |
| `PixelPatch` | Immutable set of bounded before/after pixel changes | diff, delta, update data |
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
