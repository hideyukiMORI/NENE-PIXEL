package io.github.hideyukimori.nenepixel.measurement

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class P2AndroidMemoryContractValidationTest {
    @Test
    fun acceptsTheFixedMemoryContract() {
        P2AndroidMemoryRawAudit.validateMemoryRowSequence(validMemoryRows())
        P2AndroidMemoryRawAudit.validateFixedMetadata(validFixedMetadata())
        P2AndroidMemoryInvocationReport.validateCheckpointMemory(VALID_MEMORY, RUNTIME_MAX_MEMORY_BYTES)
        P2AndroidMemoryRawAudit.validateCheckpointMemory(VALID_MEMORY, RUNTIME_MAX_MEMORY_BYTES)
        P2AndroidMemoryAggregateReport.validateMemoryInputs(listOf(validRuntime()))
    }

    @Test
    fun rejectsSwappedBaselineAndRetainedRows() {
        assertThrows(IllegalStateException::class.java) {
            P2AndroidMemoryRawAudit.validateMemoryRowSequence(validMemoryRows().reversed())
        }
    }

    @Test
    fun rejectsChangedFixedMetadataDescriptors() {
        val metadata = validFixedMetadata()
        metadata.keys.forEach { name ->
            assertThrows(IllegalStateException::class.java) {
                P2AndroidMemoryRawAudit.validateFixedMetadata(metadata + (name to "changed"))
            }
        }
    }

    @Test
    fun rejectsZeroMemoryFieldsAtEveryValidationLayer() {
        val invalidMemories =
            listOf(
                VALID_MEMORY.copy(javaHeapUsedBytes = 0L),
                VALID_MEMORY.copy(javaHeapCommittedBytes = 0L),
                VALID_MEMORY.copy(totalPssKilobytes = 0),
            )
        invalidMemories.forEach(::assertEveryLayerRejects)
    }

    @Test
    fun rejectsNonPositiveRuntimeMaximumAtEveryValidationLayer() {
        listOf(0L, -1L).forEach { runtimeMaxMemoryBytes ->
            assertEveryLayerRejects(VALID_MEMORY, runtimeMaxMemoryBytes)
        }
    }

    @Test
    fun rejectsHeapValuesAboveRuntimeMaximumAtEveryValidationLayer() {
        val runtimeMaxMemoryBytes = 2_048L
        listOf(
            VALID_MEMORY.copy(javaHeapUsedBytes = 2_049L, javaHeapCommittedBytes = 2_049L),
            VALID_MEMORY.copy(javaHeapCommittedBytes = 2_049L),
        ).forEach { invalid ->
            assertEveryLayerRejects(invalid, runtimeMaxMemoryBytes)
        }
    }

    @Test
    fun rejectsCommittedHeapBelowUsedHeapAtEveryValidationLayer() {
        assertEveryLayerRejects(
            VALID_MEMORY.copy(javaHeapUsedBytes = 2_048L, javaHeapCommittedBytes = 1_024L),
        )
    }

    @Test
    fun rejectsNegativePssAndDirtyFieldsAtEveryValidationLayer() {
        listOf(
            VALID_MEMORY.copy(totalPssKilobytes = -1),
            VALID_MEMORY.copy(dalvikPssKilobytes = -1),
            VALID_MEMORY.copy(nativePssKilobytes = -1),
            VALID_MEMORY.copy(otherPssKilobytes = -1),
            VALID_MEMORY.copy(totalPrivateDirtyKilobytes = -1),
            VALID_MEMORY.copy(totalSharedDirtyKilobytes = -1),
        ).forEach(::assertEveryLayerRejects)
    }

    @Test
    fun steadyHeapConditionDoesNotOverflowAtLongMaximum() {
        assertFalse(
            P2AndroidMemoryAggregateReport.steadyArtLiveHeapPass(
                usedBytes = Long.MAX_VALUE,
                runtimeMaxMemoryBytes = Long.MAX_VALUE,
            ),
        )
    }

    private fun validMemoryRows(): List<P2AndroidMemoryCheckpointRowIdentity> =
        listOf(
            P2AndroidMemoryCheckpointRowIdentity("baseline_post_gc", 0),
            P2AndroidMemoryCheckpointRowIdentity("retained_post_gc", 1),
        )

    private fun validFixedMetadata(): Map<String, String> =
        mapOf(
            "entry_sequence" to P2AndroidMemoryProtocol.ENTRY_SEQUENCE_DESCRIPTOR,
            "gc_protocol" to P2AndroidMemoryProtocol.GC_PROTOCOL_DESCRIPTOR,
            "capture_sequence" to P2AndroidMemoryProtocol.CAPTURE_SEQUENCE_DESCRIPTOR,
            "retained_boundary" to P2AndroidMemoryProtocol.RETAINED_OWNER_DESCRIPTOR,
            "projection_boundary" to P2AndroidMemoryProtocol.PROJECTION_BOUNDARY_DESCRIPTOR,
            "private_patch_boundary" to P2AndroidMemoryProtocol.PRIVATE_PATCH_BOUNDARY_DESCRIPTOR,
        )

    private fun validRuntime(): P2AndroidMemoryRawRuntime =
        P2AndroidMemoryRawRuntime(
            runtimeMaxMemoryBytes = RUNTIME_MAX_MEMORY_BYTES,
            memoryClassMib = 256,
            baseline = VALID_MEMORY,
            retained = VALID_MEMORY,
        )

    private fun assertEveryLayerRejects(
        invalid: PostGcMemorySnapshot,
        runtimeMaxMemoryBytes: Long = RUNTIME_MAX_MEMORY_BYTES,
    ) {
        assertThrows(IllegalStateException::class.java) {
            P2AndroidMemoryInvocationReport.validateCheckpointMemory(invalid, runtimeMaxMemoryBytes)
        }
        assertThrows(IllegalStateException::class.java) {
            P2AndroidMemoryRawAudit.validateCheckpointMemory(invalid, runtimeMaxMemoryBytes)
        }
        assertThrows(IllegalStateException::class.java) {
            P2AndroidMemoryAggregateReport.validateMemoryInputs(
                listOf(
                    validRuntime().copy(
                        runtimeMaxMemoryBytes = runtimeMaxMemoryBytes,
                        baseline = invalid,
                    ),
                ),
            )
        }
        assertThrows(IllegalStateException::class.java) {
            P2AndroidMemoryAggregateReport.validateMemoryInputs(
                listOf(
                    validRuntime().copy(
                        runtimeMaxMemoryBytes = runtimeMaxMemoryBytes,
                        retained = invalid,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val RUNTIME_MAX_MEMORY_BYTES: Long = 268_435_456L
        val VALID_MEMORY: PostGcMemorySnapshot =
            PostGcMemorySnapshot(
                javaHeapUsedBytes = 1_024L,
                javaHeapCommittedBytes = 2_048L,
                totalPssKilobytes = 128,
                dalvikPssKilobytes = 64,
                nativePssKilobytes = 32,
                otherPssKilobytes = 32,
                totalPrivateDirtyKilobytes = 16,
                totalSharedDirtyKilobytes = 8,
            )
    }
}
