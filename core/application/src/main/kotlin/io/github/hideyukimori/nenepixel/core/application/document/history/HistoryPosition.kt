package io.github.hideyukimori.nenepixel.core.application.document.history

@JvmInline
internal value class HistoryPosition private constructor(
    val value: Long,
) {
    fun next(): HistoryPositionResult =
        if (value == Long.MAX_VALUE) {
            HistoryPositionResult.Exhausted
        } else {
            HistoryPositionResult.Created(HistoryPosition(value + 1L))
        }

    companion object {
        val initial: HistoryPosition = HistoryPosition(0L)

        fun create(value: Long): HistoryPosition {
            require(value >= 0L) { "HistoryPosition must not be negative." }
            return HistoryPosition(value)
        }
    }
}

internal sealed interface HistoryPositionResult {
    data class Created(
        val position: HistoryPosition,
    ) : HistoryPositionResult

    data object Exhausted : HistoryPositionResult
}
