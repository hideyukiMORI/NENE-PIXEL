package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor

internal class P2CandidateRetainedHistoryMeasurementFixture private constructor(
    private val descriptor: P2CandidateRetainedHistoryMeasurementDescriptor,
    private val currentSnapshot: P2CandidateSnapshot,
    private val preparedEntries: List<P2CandidateRetainedEntryReference>,
    private val expectedOutcome: P2CandidateRetainedHistoryMeasurementOutcome,
) {
    fun execute(): P2CandidateRetainedHistory = P2CandidateRetainedHistory.retain(currentSnapshot, preparedEntries)

    fun verify(retained: P2CandidateRetainedHistory): P2CandidateRetainedHistoryMeasurementOutcome {
        check(
            retained.currentSnapshot === currentSnapshot,
        ) { "Retained history changed the current snapshot reference." }
        check(retained.entryCount == preparedEntries.size) { "Retained history entry count changed." }
        preparedEntries.indices.forEach { index ->
            val expected = preparedEntries[index]
            val actual = retained.entryAt(index)
            check(actual.forward === expected.forward) { "Retained history changed forward patch identity." }
            check(actual.inverse === expected.inverse) { "Retained history changed inverse patch identity." }
            check(actual.forward.changeCount == descriptor.workload.changeCountPerEntry) {
                "Retained history changed per-entry count."
            }
            check(actual.forward.revisions == P2CandidateRevisionTransition(index.toLong(), index + 1L)) {
                "Retained history changed the revision chain."
            }
            actual.forward.pairStorage(actual.inverse)
        }
        return expectedOutcome
    }

    fun executeAndVerify(): P2CandidateRetainedHistoryMeasurementOutcome = verify(execute())

    companion object {
        fun create(
            descriptor: P2CandidateRetainedHistoryMeasurementDescriptor,
        ): P2CandidateRetainedHistoryMeasurementFixture {
            validateDescriptor(descriptor)
            val prepared = prepareChain(descriptor)
            verifyPreparedChain(descriptor, prepared)
            val outcome = outcome(prepared)
            return P2CandidateRetainedHistoryMeasurementFixture(
                descriptor,
                prepared.current,
                prepared.entries,
                outcome,
            )
        }

        private fun validateDescriptor(descriptor: P2CandidateRetainedHistoryMeasurementDescriptor) {
            val workload = descriptor.workload
            check(
                P2CandidateRetainedHistoryMatrix.isValidPair(
                    workload.historyEntries,
                    workload.totalRetainedChanges,
                    descriptor.canvas.pixelCount,
                ),
            ) {
                "Retained-history descriptor has an invalid entry/change pair."
            }
            check(workload.changeCountPerEntry <= descriptor.canvas.pixelCount) {
                "Retained-history entry exceeds the canvas."
            }
        }

        private fun prepareChain(
            descriptor: P2CandidateRetainedHistoryMeasurementDescriptor,
        ): P2CandidatePreparedRetainedHistory {
            val initialPixels = IntArray(descriptor.canvas.pixelCount.toInt()) { OPAQUE_BLACK }
            val initial = descriptor.configuration.createSnapshot(descriptor.canvas, 0L, initialPixels)
            var current = initial
            val entries =
                List(descriptor.workload.historyEntries) { index ->
                    val target = if (index % 2 == 0) RED else GREEN
                    val positions = IntArray(descriptor.workload.changeCountPerEntry) { position -> position }
                    val after = List(positions.size) { target }
                    val forward =
                        P2CandidatePatchFactory
                            .create(descriptor.configuration, current, positions, after)
                            .requiredPatch()
                    val inverse = forward.inverse()
                    current = current.apply(forward).requiredApplication().snapshot
                    P2CandidateRetainedEntryReference(forward, inverse)
                }
            return P2CandidatePreparedRetainedHistory(initial, current, entries)
        }

        private fun verifyPreparedChain(
            descriptor: P2CandidateRetainedHistoryMeasurementDescriptor,
            prepared: P2CandidatePreparedRetainedHistory,
        ) {
            verifyEntryContracts(descriptor, prepared.entries)
            val replayed = replayForward(prepared.initial, prepared.entries)
            check(replayed == prepared.current) { "Retained-history forward replay did not reach current state." }
            val restored = replayInverse(replayed, prepared.entries)
            check(restored == prepared.initial) { "Retained-history inverse replay did not restore the source." }
            verifySnapshotPixels(prepared.initial, OPAQUE_BLACK, descriptor.workload.changeCountPerEntry)
            verifySnapshotPixels(
                prepared.current,
                finalPackedColor(descriptor.workload.historyEntries),
                descriptor.workload.changeCountPerEntry,
            )
        }

        private fun verifyEntryContracts(
            descriptor: P2CandidateRetainedHistoryMeasurementDescriptor,
            entries: List<P2CandidateRetainedEntryReference>,
        ) {
            val expectedRegion = affectedRegion(descriptor.canvas, descriptor.workload.changeCountPerEntry)
            entries.forEachIndexed { entryIndex, entry ->
                val expectedBefore = packedColorBefore(entryIndex)
                val expectedAfter = packedColorAfter(entryIndex)
                check(entry.forward.configuration == descriptor.configuration)
                check(entry.forward.direction == P2CandidatePatchDirection.Forward)
                check(entry.forward.revisions == P2CandidateRevisionTransition(entryIndex.toLong(), entryIndex + 1L))
                check(entry.forward.changeCount == descriptor.workload.changeCountPerEntry)
                check(entry.forward.affectedRegion == expectedRegion)
                check(entry.inverse.direction == P2CandidatePatchDirection.Inverse)
                check(entry.inverse.revisions == entry.forward.revisions.inverse())
                check(entry.inverse.affectedRegion == expectedRegion)
                repeat(entry.forward.changeCount) { changeIndex ->
                    check(entry.forward.positionAt(changeIndex) == changeIndex)
                    check(entry.forward.beforeAt(changeIndex) == expectedBefore)
                    check(entry.forward.afterAt(changeIndex) == expectedAfter)
                    check(entry.inverse.positionAt(changeIndex) == changeIndex)
                    check(entry.inverse.beforeAt(changeIndex) == expectedAfter)
                    check(entry.inverse.afterAt(changeIndex) == expectedBefore)
                }
                entry.forward.pairStorage(entry.inverse)
            }
        }

        private fun replayForward(
            initial: P2CandidateSnapshot,
            entries: List<P2CandidateRetainedEntryReference>,
        ): P2CandidateSnapshot {
            var current = initial
            entries.forEach { entry ->
                val next = current.apply(entry.forward).requiredApplication().snapshot
                assertCandidateUnaffectedPixels(current, next, entry.forward)
                current = next
            }
            return current
        }

        private fun replayInverse(
            current: P2CandidateSnapshot,
            entries: List<P2CandidateRetainedEntryReference>,
        ): P2CandidateSnapshot {
            var restored = current
            entries.asReversed().forEach { entry ->
                val next = restored.apply(entry.inverse).requiredApplication().snapshot
                assertCandidateUnaffectedPixels(restored, next, entry.inverse)
                restored = next
            }
            return restored
        }

        private fun verifySnapshotPixels(
            snapshot: P2CandidateSnapshot,
            changedColor: Int,
            changeCount: Int,
        ) {
            repeat(snapshot.shape.pixelCount.toInt()) { index ->
                val expected = if (index < changeCount) changedColor else OPAQUE_BLACK
                check(snapshot.packedAt(index) == expected) { "Retained-history pixel changed at $index." }
            }
        }

        private fun outcome(
            prepared: P2CandidatePreparedRetainedHistory,
        ): P2CandidateRetainedHistoryMeasurementOutcome {
            val storage = aggregateStorage(prepared)
            return P2CandidateRetainedHistoryMeasurementOutcome(
                storage,
                P2CandidateRetainedHistoryCorrectness(
                    entryChangeCountsDigest = P2CandidateDigest.retainedEntryChangeCounts(prepared.entries),
                    semanticDigest =
                        P2CandidateDigest.retainedHistory(
                            prepared.current.shape,
                            prepared.entries,
                            prepared.current,
                        ),
                    status = "pass",
                ),
            )
        }

        private fun aggregateStorage(
            prepared: P2CandidatePreparedRetainedHistory,
        ): P2CandidateRetainedHistoryStorageEvidence {
            val patchStorage =
                prepared.entries.fold(emptyPatchPairStorage()) { total, entry ->
                    total + entry.forward.pairStorage(entry.inverse)
                }
            return P2CandidateRetainedHistoryStorageEvidence(
                prepared.current.storage,
                patchStorage.forward,
                patchStorage.inverseAdditional,
                patchStorage.shared,
                patchStorage.retainedUnion,
            )
        }

        private fun affectedRegion(
            canvas: P2CanvasShape,
            changeCount: Int,
        ): P2CandidateAffectedRegion? =
            when {
                changeCount == 0 -> null
                changeCount <= canvas.width -> P2CandidateAffectedRegion(0, 0, changeCount, 1)
                else -> P2CandidateAffectedRegion(0, 0, canvas.width, (changeCount + canvas.width - 1) / canvas.width)
            }

        private fun packedColorBefore(entryIndex: Int): Int =
            if (entryIndex == 0) OPAQUE_BLACK else packedColorAfter(entryIndex - 1)

        private fun packedColorAfter(entryIndex: Int): Int = if (entryIndex % 2 == 0) OPAQUE_RED else OPAQUE_GREEN

        private fun finalPackedColor(historyEntries: Int): Int =
            if (historyEntries == 0) OPAQUE_BLACK else packedColorAfter(historyEntries - 1)

        private val RED: PixelColor = P2PackedRgba8888.unpack(OPAQUE_RED)
        private val GREEN: PixelColor = P2PackedRgba8888.unpack(OPAQUE_GREEN)
        private const val OPAQUE_BLACK: Int = 0x000000ff
        private const val OPAQUE_RED: Int = -0x00ffff01
        private const val OPAQUE_GREEN: Int = 0x00ff00ff
    }
}

private data class P2CandidatePreparedRetainedHistory(
    val initial: P2CandidateSnapshot,
    val current: P2CandidateSnapshot,
    val entries: List<P2CandidateRetainedEntryReference>,
)

private fun emptyPatchPairStorage(): P2CandidatePatchPairStorage =
    P2CandidatePatchPairStorage(
        P2CandidatePatchStorageCounts.Empty,
        P2CandidatePatchStorageCounts.Empty,
        P2CandidatePatchStorageCounts.Empty,
        P2CandidatePatchStorageCounts.Empty,
    )

private operator fun P2CandidatePatchPairStorage.plus(other: P2CandidatePatchPairStorage): P2CandidatePatchPairStorage =
    P2CandidatePatchPairStorage(
        forward + other.forward,
        inverseAdditional + other.inverseAdditional,
        shared + other.shared,
        retainedUnion + other.retainedUnion,
    )
