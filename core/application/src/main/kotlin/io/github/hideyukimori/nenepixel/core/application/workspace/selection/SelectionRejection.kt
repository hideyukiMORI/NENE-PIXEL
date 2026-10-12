package io.github.hideyukimori.nenepixel.core.application.workspace.selection

/** Expected refusal from the ephemeral selection boundary; no document transition occurs. */
public enum class SelectionRejection {
    NoSession,
    PointerAlreadyActive,
    NoPointer,
    PendingImport,
    NoRectangle,
    PointerActive,
    SessionActive,
}
