package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition

public class AutosaveStateToken internal constructor(
    private val runtimeGeneration: Long,
    private val historyPosition: HistoryPosition,
) {
    internal fun belongsTo(runtimeGeneration: Long): Boolean = this.runtimeGeneration == runtimeGeneration

    public override fun equals(other: Any?): Boolean =
        other is AutosaveStateToken &&
            runtimeGeneration == other.runtimeGeneration &&
            historyPosition == other.historyPosition

    public override fun hashCode(): Int = HASH_MULTIPLIER * runtimeGeneration.hashCode() + historyPosition.hashCode()

    public override fun toString(): String = "AutosaveStateToken"

    private companion object {
        private const val HASH_MULTIPLIER: Int = 31
    }
}
