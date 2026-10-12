package io.github.hideyukimori.nenepixel.core.application.workspace.selection

/** The pointer phase of the ephemeral selection session (ADR 0039). */
public enum class SelectionInteraction {
    Idle,
    Selecting,
    Translating,
}
