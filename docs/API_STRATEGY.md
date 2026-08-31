# API and Contract Strategy

Status: planning constraint

## Position

NENE-PIXEL has an application API before it has a network API. The canonical behavior contract is the Kotlin command/query boundary defined in `COMMAND_MODEL.md`.

OpenAPI is appropriate only for an HTTP transport. Creating OpenAPI first would force URLs, status codes, request envelopes, and transport concerns onto an editor that initially runs in one Android process.

## Contract hierarchy

### 1. Domain contract

Strong Kotlin values and sealed state/result types define valid editor meaning. This is authoritative for invariants but is not serialized directly.

### 2. Application contract

`DocumentCommand`, `WorkspaceAction`, queries, `CommandResult`, and `ChangeSet` define behavior. Every UI and future automation mutation converges on `CommandGateway`.

Internal Kotlin class names and package names are not compatibility promises to external clients.

### 3. Project-file contract

Versioned DTOs, codec rules, migration rules, limits, and golden fixtures define durable documents. Domain objects do not carry serialization annotations.

### 4. External automation contract

If M6 begins, an ADR chooses the schema technology and compatibility policy for external command/query DTOs. JSON Schema is a candidate, not an adopted dependency in advance.

The schema must model semantic operations such as an atomic stroke/patch. It must not expose mutable buffers or create per-pixel remote mutation as an alternate editor path.

### 5. HTTP/OpenAPI contract

OpenAPI 3.1 becomes authoritative only if an ADR adopts an HTTP adapter. It then describes:

- versioned external DTOs, not internal Kotlin classes
- typed query and command operations
- authentication/authorization and approval boundaries
- stable success, rejection, and failure envelopes
- idempotency/cancellation behavior where relevant
- resource and document-size limits

The HTTP adapter translates to the same `CommandGateway`; it never owns business rules.

### 6. MCP catalog

An MCP catalog, if adopted, is derived from or checked against the same external DTO vocabulary. MCP tools do not directly access the database, filesystem implementation, pixel buffer, or domain mutation methods.

## OpenAPI decision checklist

OpenAPI work MUST NOT start until all answers are accepted by ADR:

- What real client requires HTTP rather than in-process or local protocol access?
- Is the server embedded, local companion, or remote?
- How is document ownership and authorization established?
- Which commands are externally allowed?
- Which mutations require preview and explicit human approval?
- How are cancellation, retries, idempotency, and concurrent edits handled?
- What versioning and compatibility guarantee is offered?
- How are payload, rate, and resource limits enforced once at the boundary?
- How are external operations audited and undone?

If these questions have no concrete product answer, OpenAPI remains out of scope.

## Planning consequence

M0 through M5 plan internal and durable contracts without HTTP. M6 starts with external use cases and threat boundaries, then chooses transport. This keeps OpenAPI a precise description of a justified interface rather than a speculative second architecture.
