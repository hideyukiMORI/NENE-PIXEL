package io.github.hideyukimori.nenepixel.core.application.document.history

public sealed interface HistoryAvailability {
    public data object None : HistoryAvailability

    public data object UndoAvailable : HistoryAvailability

    public data object RedoAvailable : HistoryAvailability
}
