package io.github.hideyukimori.nenepixel.measurement

import android.os.Debug

internal fun sortedArtRuntimeStats(): List<Pair<String, String>> {
    val runtimeStats: Map<*, *> = Debug.getRuntimeStats()
    return runtimeStats.entries
        .mapNotNull { entry ->
            val name = entry.key as? String ?: return@mapNotNull null
            val value = entry.value as? String ?: return@mapNotNull null
            name to value
        }.sortedBy { (name, _) -> name }
}

internal data class ArtRuntimeDelta(
    val allocation: Allocation,
    val gc: Gc,
    val blockingGc: BlockingGc,
) {
    data class Allocation(
        val allocatedBytesBefore: Long,
        val allocatedBytesAfter: Long,
        val allocatedBytesDelta: Long,
    )

    data class Gc(
        val gcCountDelta: Long,
        val gcTimeMillisDelta: Long,
    )

    data class BlockingGc(
        val blockingGcCountDelta: Long,
        val blockingGcTimeMillisDelta: Long,
    )

    val allocatedBytesBefore: Long
        get() = allocation.allocatedBytesBefore

    val allocatedBytesAfter: Long
        get() = allocation.allocatedBytesAfter

    val allocatedBytesDelta: Long
        get() = allocation.allocatedBytesDelta

    val gcCountDelta: Long
        get() = gc.gcCountDelta

    val gcTimeMillisDelta: Long
        get() = gc.gcTimeMillisDelta

    val blockingGcCountDelta: Long
        get() = blockingGc.blockingGcCountDelta

    val blockingGcTimeMillisDelta: Long
        get() = blockingGc.blockingGcTimeMillisDelta
}

internal class ArtRuntimeSnapshot private constructor(
    private val values: Map<String, Long>,
) {
    fun deltaFrom(before: ArtRuntimeSnapshot): ArtRuntimeDelta =
        ArtRuntimeDelta(
            allocation =
                ArtRuntimeDelta.Allocation(
                    allocatedBytesBefore = before.value(ALLOCATED_BYTES),
                    allocatedBytesAfter = value(ALLOCATED_BYTES),
                    allocatedBytesDelta = delta(before, ALLOCATED_BYTES),
                ),
            gc =
                ArtRuntimeDelta.Gc(
                    gcCountDelta = delta(before, GC_COUNT),
                    gcTimeMillisDelta = delta(before, GC_TIME),
                ),
            blockingGc =
                ArtRuntimeDelta.BlockingGc(
                    blockingGcCountDelta = delta(before, BLOCKING_GC_COUNT),
                    blockingGcTimeMillisDelta = delta(before, BLOCKING_GC_TIME),
                ),
        )

    private fun delta(
        before: ArtRuntimeSnapshot,
        key: String,
    ): Long {
        val initial = before.value(key)
        val final = value(key)
        return if (initial == UNAVAILABLE || final == UNAVAILABLE) UNAVAILABLE else final - initial
    }

    private fun value(key: String): Long = values[key] ?: UNAVAILABLE

    companion object {
        fun capture(): ArtRuntimeSnapshot {
            val runtimeStats = Debug.getRuntimeStats()
            return ArtRuntimeSnapshot(
                TRACKED_RUNTIME_STATS.associateWith { key ->
                    runtimeStats[key]?.toLongOrNull() ?: UNAVAILABLE
                },
            )
        }

        private const val ALLOCATED_BYTES: String = "art.gc.bytes-allocated"
        private const val GC_COUNT: String = "art.gc.gc-count"
        private const val GC_TIME: String = "art.gc.gc-time"
        private const val BLOCKING_GC_COUNT: String = "art.gc.blocking-gc-count"
        private const val BLOCKING_GC_TIME: String = "art.gc.blocking-gc-time"
        private const val UNAVAILABLE: Long = -1L
        private val TRACKED_RUNTIME_STATS: List<String> =
            listOf(
                ALLOCATED_BYTES,
                GC_COUNT,
                GC_TIME,
                BLOCKING_GC_COUNT,
                BLOCKING_GC_TIME,
            )
    }
}

internal data class PostGcMemorySnapshot(
    val heap: Heap,
    val pss: Pss,
    val dirty: Dirty,
) {
    data class Heap(
        val javaHeapUsedBytes: Long,
        val javaHeapCommittedBytes: Long,
    )

    data class Pss(
        val totalPssKilobytes: Int,
        val dalvikPssKilobytes: Int,
        val nativePssKilobytes: Int,
        val otherPssKilobytes: Int,
    )

    data class Dirty(
        val totalPrivateDirtyKilobytes: Int,
        val totalSharedDirtyKilobytes: Int,
    )

    val javaHeapUsedBytes: Long
        get() = heap.javaHeapUsedBytes

    val javaHeapCommittedBytes: Long
        get() = heap.javaHeapCommittedBytes

    val totalPssKilobytes: Int
        get() = pss.totalPssKilobytes

    val dalvikPssKilobytes: Int
        get() = pss.dalvikPssKilobytes

    val nativePssKilobytes: Int
        get() = pss.nativePssKilobytes

    val otherPssKilobytes: Int
        get() = pss.otherPssKilobytes

    val totalPrivateDirtyKilobytes: Int
        get() = dirty.totalPrivateDirtyKilobytes

    val totalSharedDirtyKilobytes: Int
        get() = dirty.totalSharedDirtyKilobytes

    companion object {
        fun captureBaseline(retained: Any): PostGcMemorySnapshot = capture(retained)

        fun captureRetainedMemory(retained: Any): PostGcMemorySnapshot = capture(retained)

        private fun capture(retained: Any): PostGcMemorySnapshot {
            forceJavaGc()
            val runtime = Runtime.getRuntime()
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            val javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory()
            val javaHeapCommittedBytes = runtime.totalMemory()
            val snapshot =
                PostGcMemorySnapshot(
                    heap =
                        PostGcMemorySnapshot.Heap(
                            javaHeapUsedBytes = javaHeapUsedBytes,
                            javaHeapCommittedBytes = javaHeapCommittedBytes,
                        ),
                    pss =
                        PostGcMemorySnapshot.Pss(
                            totalPssKilobytes = memoryInfo.totalPss,
                            dalvikPssKilobytes = memoryInfo.dalvikPss,
                            nativePssKilobytes = memoryInfo.nativePss,
                            otherPssKilobytes = memoryInfo.otherPss,
                        ),
                    dirty =
                        PostGcMemorySnapshot.Dirty(
                            totalPrivateDirtyKilobytes = memoryInfo.totalPrivateDirty,
                            totalSharedDirtyKilobytes = memoryInfo.totalSharedDirty,
                        ),
                )
            RetainedReferenceSink.consume(retained)
            return snapshot
        }

        private fun forceJavaGc() {
            repeat(GC_PASSES) {
                Runtime.getRuntime().gc()
                System.runFinalization()
            }
        }

        private const val GC_PASSES: Int = 2
    }
}

private object RetainedReferenceSink {
    @Volatile
    private var identityHash: Int = 0

    fun consume(retained: Any) {
        identityHash = System.identityHashCode(retained)
    }
}
