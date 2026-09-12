package io.github.hideyukimori.nenepixel.core.application.workspace

/** Session-only display choices owned by WorkspaceState (ADR 0020). */
public data class EditorAppearance(
    public val theme: EditorTheme,
    public val layout: EditorLayout,
    public val controlEdge: EditorControlEdge,
) {
    public companion object {
        public val initial: EditorAppearance =
            EditorAppearance(EditorTheme.Dark, EditorLayout.Tabletop, EditorControlEdge.Right)
    }
}
