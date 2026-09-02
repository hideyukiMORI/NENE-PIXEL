package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

internal data class P2CandidateRetainedHistoryWorkload(
    val historyEntries: Int,
    val changeCountPerEntry: Int,
) {
    val totalRetainedChanges: Long
        get() = historyEntries.toLong() * changeCountPerEntry
}

internal object P2CandidateRetainedHistoryMatrix {
    private const val PIXEL_COUNT: Int = 65_536
    private val ENTRY_COUNTS: List<Int> = listOf(1, 8, 16, 32, 64)
    private val RETAINED_CHANGE_MULTIPLIERS: List<Int> = listOf(1, 2, 4, 8)

    val workloads: List<P2CandidateRetainedHistoryWorkload> =
        buildList {
            add(P2CandidateRetainedHistoryWorkload(0, 0))
            add(P2CandidateRetainedHistoryWorkload(1, PIXEL_COUNT))
            ENTRY_COUNTS.drop(1).forEach { historyEntries ->
                RETAINED_CHANGE_MULTIPLIERS.forEach { multiplier ->
                    val totalRetainedChanges = PIXEL_COUNT * multiplier
                    add(
                        P2CandidateRetainedHistoryWorkload(
                            historyEntries,
                            totalRetainedChanges / historyEntries,
                        ),
                    )
                }
            }
        }

    fun isValidPair(
        historyEntries: Int,
        totalRetainedChanges: Long,
        pixelCount: Long = PIXEL_COUNT.toLong(),
    ): Boolean =
        when {
            historyEntries == 0 -> totalRetainedChanges == 0L
            historyEntries < 0 || totalRetainedChanges <= 0L -> false
            totalRetainedChanges > historyEntries * pixelCount -> false
            totalRetainedChanges % historyEntries != 0L -> false
            else -> true
        }
}

internal data class P2CandidateRetainedEntryReference(
    val forward: P2CandidatePatch,
    val inverse: P2CandidatePatch,
)

internal class P2CandidateRetainedEntry private constructor(
    val forward: P2CandidatePatch,
    val inverse: P2CandidatePatch,
) {
    companion object {
        fun retain(reference: P2CandidateRetainedEntryReference): P2CandidateRetainedEntry =
            P2CandidateRetainedEntry(reference.forward, reference.inverse)
    }
}

internal class P2CandidateRetainedHistory private constructor(
    val currentSnapshot: P2CandidateSnapshot,
    private val retainedEntries: List<P2CandidateRetainedEntry>,
) {
    val entryCount: Int
        get() = retainedEntries.size

    fun entryAt(index: Int): P2CandidateRetainedEntry = retainedEntries[index]

    companion object {
        fun retain(
            currentSnapshot: P2CandidateSnapshot,
            preparedEntries: List<P2CandidateRetainedEntryReference>,
        ): P2CandidateRetainedHistory =
            P2CandidateRetainedHistory(
                currentSnapshot,
                List(preparedEntries.size) { index -> P2CandidateRetainedEntry.retain(preparedEntries[index]) },
            )
    }
}
