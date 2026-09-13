package io.github.hideyukimori.nenepixel.core.application.document.command

internal data class HistoryRetentionSummary(
    val entryCount: Int,
    val changeCount: Int,
    val byteCount: Long,
)
