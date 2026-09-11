package io.github.hideyukimori.nenepixel.adapters.persistence

/** Bounded, test-owned row storage, independent from report I/O. */
internal class AutosavePublicationEvidenceJournal(
    private val maxRows: Int = MAX_ROW_COUNT,
) {
    private val rows: MutableList<String> = ArrayList(maxRows)
    private var closed: Boolean = false

    @Synchronized
    fun append(row: String): Boolean {
        if (closed || Thread.currentThread().isInterrupted || rows.size == maxRows) return false
        rows += row
        return true
    }

    @Synchronized
    fun freeze(): List<String> {
        closed = true
        return rows.toList()
    }

    @Synchronized
    fun snapshot(): List<String> = rows.toList()

    @Synchronized
    fun isOpenAndNotInterrupted(): Boolean = !closed && !Thread.currentThread().isInterrupted

    @Synchronized
    fun isFull(): Boolean = rows.size == maxRows
}
