package io.github.hideyukimori.nenepixel.core.application.document.history

internal sealed interface OneLevelHistoryState {
    val availability: HistoryAvailability

    data object Empty : OneLevelHistoryState {
        override val availability: HistoryAvailability = HistoryAvailability.None
    }

    data class UndoAvailable(
        val entry: HistoryEntry,
    ) : OneLevelHistoryState {
        override val availability: HistoryAvailability = HistoryAvailability.UndoAvailable
    }

    data class RedoAvailable(
        val entry: HistoryEntry,
    ) : OneLevelHistoryState {
        override val availability: HistoryAvailability = HistoryAvailability.RedoAvailable
    }
}
