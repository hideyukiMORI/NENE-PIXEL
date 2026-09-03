package io.github.hideyukimori.nenepixel.core.application.document.history

public enum class HistoryAvailability(
    public val canUndo: Boolean,
    public val canRedo: Boolean,
) {
    None(canUndo = false, canRedo = false),
    UndoAvailable(canUndo = true, canRedo = false),
    RedoAvailable(canUndo = false, canRedo = true),
    UndoAndRedoAvailable(canUndo = true, canRedo = true),
}
