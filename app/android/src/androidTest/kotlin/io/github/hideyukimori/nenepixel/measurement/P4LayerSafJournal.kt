package io.github.hideyukimori.nenepixel.measurement

/** Bounded primitive evidence only. No document, resolver, output stream or report I/O is retained. */
internal class P4LayerSafJournal {
    private val samples = ArrayList<P4LayerSafSample>(25)
    private var summaries: Pair<P4LayerSafSample, P4LayerSafSample>? = null
    private var closed = false

    @Synchronized
    fun isOpen(): Boolean = !closed && summaries == null && !Thread.currentThread().isInterrupted

    @Synchronized
    fun append(sample: P4LayerSafSample): Boolean {
        if (!isOpen() || samples.size == 25 || sample.ordinal != samples.size) {
            return false
        }
        return if (samples.none { it.destination.uri == sample.destination.uri }) {
            samples.add(sample)
            true
        } else {
            false
        }
    }

    @Synchronized
    fun summarize() {
        check(isOpen() && samples.size == 25 && samples.all { it.accepted() })
        val measured = samples.drop(5)
        summaries = measured.minBy { it.elapsedNanos } to measured.maxBy { it.elapsedNanos }
    }

    @Synchronized
    fun isComplete(): Boolean = samples.size == 25 && summaries != null

    @Synchronized
    fun freeze(): List<String> {
        closed = true
        val rows = samples.map { it.row() }
        val summary = summaries ?: return rows
        return rows + listOf(summary.first.row("summary_min"), summary.second.row("summary_max"))
    }
}
