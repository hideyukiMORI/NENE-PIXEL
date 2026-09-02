package io.github.hideyukimori.nenepixel.measurement

internal data class P2AndroidMemoryCheckpointRowIdentity(
    val name: String,
    val index: Int,
)

internal object P2AndroidMemoryContractValidation {
    fun validateMemoryRowSequence(rows: List<P2AndroidMemoryCheckpointRowIdentity>) {
        check(
            rows ==
                listOf(
                    P2AndroidMemoryCheckpointRowIdentity("baseline_post_gc", 0),
                    P2AndroidMemoryCheckpointRowIdentity("retained_post_gc", 1),
                ),
        ) {
            "Retained-memory checkpoint name, index, or ordering changed."
        }
    }

    fun validateFixedMetadata(metadata: Map<String, String>) {
        val expected =
            mapOf(
                "entry_sequence" to P2AndroidMemoryProtocol.ENTRY_SEQUENCE_DESCRIPTOR,
                "gc_protocol" to P2AndroidMemoryProtocol.GC_PROTOCOL_DESCRIPTOR,
                "capture_sequence" to P2AndroidMemoryProtocol.CAPTURE_SEQUENCE_DESCRIPTOR,
                "retained_boundary" to P2AndroidMemoryProtocol.RETAINED_OWNER_DESCRIPTOR,
                "projection_boundary" to P2AndroidMemoryProtocol.PROJECTION_BOUNDARY_DESCRIPTOR,
                "private_patch_boundary" to P2AndroidMemoryProtocol.PRIVATE_PATCH_BOUNDARY_DESCRIPTOR,
            )
        expected.forEach { (name, value) ->
            check(metadata[name] == value) { "Retained-memory metadata '$name' changed." }
        }
    }

    fun validateMemory(
        memory: PostGcMemorySnapshot,
        runtimeMaxMemoryBytes: Long,
    ) {
        check(runtimeMaxMemoryBytes > 0L) { "Runtime.maxMemory must be positive." }
        check(memory.javaHeapUsedBytes > 0L) { "Post-GC Java heap used bytes must be positive." }
        check(memory.javaHeapCommittedBytes > 0L) { "Post-GC Java heap committed bytes must be positive." }
        check(memory.javaHeapUsedBytes <= runtimeMaxMemoryBytes) {
            "Post-GC Java heap used bytes must not exceed Runtime.maxMemory."
        }
        check(memory.javaHeapCommittedBytes <= runtimeMaxMemoryBytes) {
            "Post-GC Java heap committed bytes must not exceed Runtime.maxMemory."
        }
        check(memory.javaHeapCommittedBytes >= memory.javaHeapUsedBytes) {
            "Post-GC Java heap committed bytes must cover used bytes."
        }
        check(memory.totalPssKilobytes > 0) { "Post-GC total PSS must be positive." }
        check(memory.dalvikPssKilobytes >= 0 && memory.nativePssKilobytes >= 0 && memory.otherPssKilobytes >= 0)
        check(memory.totalPrivateDirtyKilobytes >= 0 && memory.totalSharedDirtyKilobytes >= 0)
    }
}
