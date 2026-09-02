package io.github.hideyukimori.nenepixel.measurement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
internal class P2AndroidFinalCommandContractValidationTest {
    @Test
    fun preservesExact256SquarePlan() {
        val plan = resolve(CANDIDATE_256, runIndex = 1)

        assertEquals("current-canonical-command-256-square", plan.candidateId)
        assertEquals(1, plan.runIndex)
        assertEquals(256, plan.canvasEdge)
        assertEquals("device-core", plan.outputIdentity)
        assertEquals("p2-measurements/p2-android-final-command-measurement.csv", plan.outputRelativePath)
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.OverwriteExisting, plan.publicationPolicy)
        assertEquals("nene-pixel-p2-android-final-command-measurement-v1", plan.schema)
        assertCommonCountsAndOrder(plan, densePositionCount = 65_536)
    }

    @Test
    fun resolvesExact64SquarePlanWithDistinctIdentityAndPath() {
        val legacyPlan = resolve(CANDIDATE_256, runIndex = 1)
        val plan = resolve(CANDIDATE_64, runIndex = 1)

        assertEquals("current-canonical-command-64-square", plan.candidateId)
        assertEquals(1, plan.runIndex)
        assertEquals(64, plan.canvasEdge)
        assertEquals("device-core-current-64-square", plan.outputIdentity)
        assertEquals("p2-measurements/p2-android-final-command-64-square.csv", plan.outputRelativePath)
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists, plan.publicationPolicy)
        assertEquals("nene-pixel-p2-android-final-command-measurement-v1", plan.schema)
        assertNotEquals(legacyPlan.outputIdentity, plan.outputIdentity)
        assertNotEquals(legacyPlan.outputRelativePath, plan.outputRelativePath)
        assertCommonCountsAndOrder(plan, densePositionCount = 4_096)
    }

    @Test
    fun resolvesExact128SquarePlanWithDistinctIdentityAndPath() {
        val legacyPlan = resolve(CANDIDATE_256, runIndex = 1)
        val smallerPlan = resolve(CANDIDATE_64, runIndex = 1)
        val plan = resolve(CANDIDATE_128, runIndex = 1)

        assertEquals("current-canonical-command-128-square", plan.candidateId)
        assertEquals(1, plan.runIndex)
        assertEquals(128, plan.canvasEdge)
        assertEquals("device-core-current-128-square", plan.outputIdentity)
        assertEquals("p2-measurements/p2-android-final-command-128-square.csv", plan.outputRelativePath)
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists, plan.publicationPolicy)
        assertEquals("nene-pixel-p2-android-final-command-measurement-v1", plan.schema)
        assertNotEquals(legacyPlan.outputIdentity, plan.outputIdentity)
        assertNotEquals(legacyPlan.outputRelativePath, plan.outputRelativePath)
        assertNotEquals(smallerPlan.outputIdentity, plan.outputIdentity)
        assertNotEquals(smallerPlan.outputRelativePath, plan.outputRelativePath)
        assertCommonCountsAndOrder(plan, densePositionCount = 16_384)
    }

    @Test
    fun rejectsWrongRunAndUnknownCandidate() {
        assertThrows(IllegalStateException::class.java) {
            resolve(CANDIDATE_128, runIndex = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolve("unknown-final-command-candidate", runIndex = 1)
        }
    }

    @Test
    fun squareWorkloadsPreservePositionCountsAndFullRegionSemantics() {
        listOf(64, 128, 256).forEach { edge ->
            P2CommandWorkloadCatalog.squareSpecs(edge).forEach { spec ->
                val prepared = PreparedCommandWorkload.create(spec)
                val outcome = prepared.verify(prepared.execute())
                val expectedPositionCount =
                    if (spec.kind == P2CommandWorkloadKind.SparseApply) edge else edge * edge

                assertEquals(expectedPositionCount, spec.positionCount)
                if (spec.kind == P2CommandWorkloadKind.DenseNoOp) {
                    assertNull(outcome.renderInvalidation)
                } else {
                    assertEquals(P2CommandRegionDescriptor(0, 0, edge, edge), outcome.renderInvalidation)
                }
            }
        }
    }

    @Test
    fun fixedPhysicalProfileMetadataIsExact() {
        assertEquals(
            "ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94110:user/release-keys",
            P2AndroidFinalCommandProfile.BUILD_FINGERPRINT,
        )
        assertEquals("2026-06-05", P2AndroidFinalCommandProfile.SECURITY_PATCH)
        assertEquals(268_435_456L, P2AndroidFinalCommandProfile.RUNTIME_MAX_MEMORY_BYTES)
        assertEquals(256, P2AndroidFinalCommandProfile.MEMORY_CLASS_MIB)
        assertEquals(1, P2AndroidFinalCommandProfile.DISPLAY_MODE_ID)
        assertEquals(1_200, P2AndroidFinalCommandProfile.DISPLAY_WIDTH_PIXELS)
        assertEquals(1_920, P2AndroidFinalCommandProfile.DISPLAY_HEIGHT_PIXELS)
        assertEquals(90.0f, P2AndroidFinalCommandProfile.REFRESH_RATE_HERTZ)
    }

    @Test
    fun connectedDeviceMatchesExactFinalCommandProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        P2AndroidFinalCommandProfile.validateRuntime(context)
        val display = P2AndroidPhysicalCheckpointCapture.defaultDisplay(context)
        val checkpoint = P2AndroidPhysicalCheckpointCapture.capture(context, display, "before_samples", 0)

        P2AndroidFinalCommandProfile.validateBaselineCheckpoint(checkpoint)
    }

    @Test
    fun rejectsAuxiliaryEmulatorArgumentForFinalPhysicalRun() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val environment =
            P2AndroidMeasurementEnvironment(
                targetContext = context,
                profileId = P2AndroidFinalCommandProtocol.PHYSICAL_PROFILE_ID,
                evidenceClass = "physical_device",
                emulatorDetection = EmulatorDetection(false, emptyList(), "", ""),
                auxiliaryEmulatorArgumentPresent = true,
                warmupIterations = P2AndroidFinalCommandProtocol.WARMUP_ITERATIONS,
                sampleCount = P2AndroidFinalCommandProtocol.SAMPLES_PER_WORKLOAD,
                frameWarmupIterations = 5,
                frameSampleCount = 200,
            )
        val identity = identity(CANDIDATE_128)
        val plan = resolve(CANDIDATE_128, runIndex = 1)

        assertThrows(IllegalStateException::class.java) {
            P2AndroidFinalCommandProtocol.validate(environment, identity, plan)
        }
    }

    @Test
    fun checkpointValidatorRejectsWrongOrder() {
        val plan = resolve(CANDIDATE_128, runIndex = 1)
        val checkpoints = validCheckpoints(plan).toMutableList()
        P2AndroidFinalCommandContractValidator.validateCheckpoints(plan, checkpoints)
        val second = checkpoints[1]
        checkpoints[1] = checkpoints[2]
        checkpoints[2] = second

        assertThrows(IllegalStateException::class.java) {
            P2AndroidFinalCommandContractValidator.validateCheckpoints(plan, checkpoints)
        }
    }

    @Test
    fun sampleValidatorRejectsWrongFullRegion() {
        val plan = resolve(CANDIDATE_128, runIndex = 1)
        val sample = validSample(plan, invalidation = P2CommandRegionDescriptor(0, 0, 127, 128))

        assertThrows(IllegalStateException::class.java) {
            P2AndroidFinalCommandContractValidator.validateSample(plan, zeroBasedIndex = 0, sample)
        }
    }

    @Test
    fun sampleValidatorRejectsWrongTotalAndIndices() {
        val plan = resolve(CANDIDATE_128, runIndex = 1)
        assertThrows(IllegalStateException::class.java) {
            P2AndroidFinalCommandContractValidator.validateSamples(plan, emptyList())
        }
        assertThrows(IllegalStateException::class.java) {
            P2AndroidFinalCommandContractValidator.validateSample(
                plan,
                zeroBasedIndex = 0,
                validSample(plan, localIndex = 2),
            )
        }
        assertThrows(IllegalStateException::class.java) {
            P2AndroidFinalCommandContractValidator.validateSample(
                plan,
                zeroBasedIndex = 0,
                validSample(plan, globalIndex = 2),
            )
        }
    }

    @Test
    fun checkpointValidatorRejectsWrongExactBaselineModeAndDimensions() {
        val plan = resolve(CANDIDATE_64, runIndex = 1)
        val validBaseline = validCheckpoint("before_samples", sampleIndex = 0)
        val invalidBaselines =
            listOf(
                validBaseline.copy(displayModeId = 2),
                validBaseline.copy(physicalWidthPixels = 1_199),
                validBaseline.copy(physicalHeightPixels = 1_919),
            )

        invalidBaselines.forEach { invalidBaseline ->
            val checkpoints = validCheckpoints(plan).toMutableList()
            checkpoints[0] = invalidBaseline
            assertThrows(IllegalStateException::class.java) {
                P2AndroidFinalCommandContractValidator.validateCheckpoints(plan, checkpoints)
            }
        }
    }

    @Test
    fun publicationPoliciesOverwriteLegacyAndProtectImmutableContent() {
        val legacyOutput = uniqueCacheOutput("legacy-overwrite")
        val immutableOutput = uniqueCacheOutput("immutable-existing")
        try {
            legacyOutput.writeText("old-legacy")
            P2AndroidFinalCommandOutputPublication.publish(
                legacyOutput,
                resolve(CANDIDATE_256, runIndex = 1).publicationPolicy,
                writeRows = { output -> output.writeText("new-legacy") },
            )
            assertEquals("new-legacy", legacyOutput.readText())

            immutableOutput.writeText("keep-immutable")
            assertThrows(IllegalStateException::class.java) {
                P2AndroidFinalCommandOutputPublication.publish(
                    immutableOutput,
                    resolve(CANDIDATE_128, runIndex = 1).publicationPolicy,
                    writeRows = { output -> output.writeText("must-not-write") },
                )
            }
            assertEquals("keep-immutable", immutableOutput.readText())
        } finally {
            deleteCacheOutput(legacyOutput)
            deleteCacheOutput(immutableOutput)
        }
    }

    @Test
    fun immutablePublicationDeletesPartialOutputAfterWriteFailure() {
        val output = uniqueCacheOutput("partial-cleanup")
        val originalFailure = IllegalArgumentException("write failed")
        try {
            val thrown =
                assertThrows(IllegalArgumentException::class.java) {
                    P2AndroidFinalCommandOutputPublication.publish(
                        output,
                        resolve(CANDIDATE_128, runIndex = 1).publicationPolicy,
                        writeRows = { target ->
                            target.writeText("partial")
                            throw originalFailure
                        },
                    )
                }
            assertSame(originalFailure, thrown)
            assertFalse(output.exists())
        } finally {
            deleteCacheOutput(output)
        }
    }

    @Test
    fun immutablePublicationRetainsWriteAndCleanupFailures() {
        val output = uniqueCacheOutput("cleanup-failure")
        val originalFailure = IllegalArgumentException("write failed")
        val cleanupFailure = IllegalStateException("cleanup failed")
        try {
            val thrown =
                assertThrows(IllegalStateException::class.java) {
                    P2AndroidFinalCommandOutputPublication.publish(
                        output,
                        resolve(CANDIDATE_128, runIndex = 1).publicationPolicy,
                        writeRows = { target ->
                            target.writeText("partial")
                            throw originalFailure
                        },
                        cleanup = { throw cleanupFailure },
                    )
                }
            assertSame(originalFailure, thrown.cause)
            assertEquals(1, thrown.suppressed.size)
            assertSame(cleanupFailure, thrown.suppressed.single())
        } finally {
            deleteCacheOutput(output)
        }
    }

    @Test
    fun serializesFixedContractMetadataRowsForAllPlans() {
        assertContractMetadataRows(
            plan = resolve(CANDIDATE_256, runIndex = 1),
            outputIdentity = "device-core",
            canvas = "256x256",
        )
        assertContractMetadataRows(
            plan = resolve(CANDIDATE_64, runIndex = 1),
            outputIdentity = "device-core-current-64-square",
            canvas = "64x64",
        )
        assertContractMetadataRows(
            plan = resolve(CANDIDATE_128, runIndex = 1),
            outputIdentity = "device-core-current-128-square",
            canvas = "128x128",
        )
    }

    private fun assertCommonCountsAndOrder(
        plan: P2AndroidFinalCommandPlan,
        densePositionCount: Int,
    ) {
        assertEquals("${plan.canvasEdge}x${plan.canvasEdge}", plan.canvasMetadata)
        assertEquals(P2CommandWorkloadKind.entries, plan.specs.map(P2CommandWorkloadSpec::kind))
        assertEquals(
            listOf(plan.canvasEdge, densePositionCount, densePositionCount, densePositionCount, densePositionCount),
            plan.specs.map(P2CommandWorkloadSpec::positionCount),
        )
        assertEquals(
            listOf(
                "sparse_apply_stroke",
                "dense_apply_stroke",
                "dense_same_color_no_op",
                "dense_undo",
                "dense_redo",
            ),
            plan.workloadNames,
        )
        assertEquals(5, plan.warmupIterations)
        assertEquals(200, plan.samplesPerWorkload)
        assertEquals(5, plan.workloadCount)
        assertEquals(1_000, plan.totalSampleCount)
        assertEquals(42, plan.checkpointCount)
        assertEquals(P2CommandRegionDescriptor(0, 0, plan.canvasEdge, plan.canvasEdge), plan.fullCanvasRegion)
    }

    private fun resolve(
        candidateId: String,
        runIndex: Int,
    ): P2AndroidFinalCommandPlan = P2AndroidFinalCommandProtocol.resolve(identity(candidateId, runIndex))

    private fun identity(
        candidateId: String,
        runIndex: Int = 1,
    ): P2AndroidRunIdentity =
        P2AndroidRunIdentity(
            candidateId = candidateId,
            runIndex = runIndex,
            sourceCommit = "0".repeat(40),
        )

    private fun validSample(
        plan: P2AndroidFinalCommandPlan,
        localIndex: Int = 1,
        globalIndex: Int = 1,
        invalidation: P2CommandRegionDescriptor = plan.fullCanvasRegion,
    ): P2AndroidFinalCommandSample =
        P2AndroidFinalCommandSample(
            spec = plan.specs.first(),
            indices = P2AndroidFinalCommandSample.Indices(localIndex, globalIndex),
            observation =
                P2AndroidFinalCommandSample.Observation(
                    latencyNanos = 0L,
                    runtimeDelta = zeroRuntimeDelta(),
                    memory = zeroMemorySnapshot(),
                ),
            outcome = validAppliedOutcome(invalidation),
        )

    private fun validCheckpoints(plan: P2AndroidFinalCommandPlan): List<P2AndroidPhysicalCheckpoint> =
        P2AndroidFinalCommandContractValidator
            .checkpointIdentities(plan)
            .map { (name, sampleIndex) -> validCheckpoint(name, sampleIndex) }

    private fun validCheckpoint(
        name: String,
        sampleIndex: Int,
    ): P2AndroidPhysicalCheckpoint =
        P2AndroidPhysicalCheckpoint(
            name = name,
            sampleIndex = sampleIndex,
            displayModeId = 1,
            physicalWidthPixels = 1_200,
            physicalHeightPixels = 1_920,
            refreshRateHertz = 90.0f,
            thermalStatus = 1,
            powerSaveMode = false,
            interactive = true,
            usbPowered = true,
            batteryLevelPercent = 50,
        )

    private fun validAppliedOutcome(invalidation: P2CommandRegionDescriptor): CommandOutcomeDescriptor =
        CommandOutcomeDescriptor(
            resultKind = "applied",
            documentHash = 1,
            snapshotHash = 2,
            revision = 1L,
            history = "undo_available",
            changeSetBeforeRevision = 0L,
            changeSetAfterRevision = 1L,
            renderInvalidation = invalidation,
            unchangedStateIdentity = false,
        )

    private fun zeroRuntimeDelta(): ArtRuntimeDelta =
        ArtRuntimeDelta(
            allocatedBytesBefore = 0L,
            allocatedBytesAfter = 0L,
            allocatedBytesDelta = 0L,
            gcCountDelta = 0L,
            gcTimeMillisDelta = 0L,
            blockingGcCountDelta = 0L,
            blockingGcTimeMillisDelta = 0L,
        )

    private fun zeroMemorySnapshot(): PostGcMemorySnapshot =
        PostGcMemorySnapshot(
            javaHeapUsedBytes = 0L,
            javaHeapCommittedBytes = 0L,
            totalPssKilobytes = 0,
            dalvikPssKilobytes = 0,
            nativePssKilobytes = 0,
            otherPssKilobytes = 0,
            totalPrivateDirtyKilobytes = 0,
            totalSharedDirtyKilobytes = 0,
        )

    private fun uniqueCacheOutput(label: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(
            context.cacheDir,
            "p2-final-command-contract-$label-${System.nanoTime()}.csv",
        ).also { output -> check(!output.exists()) }
    }

    private fun deleteCacheOutput(output: File) {
        if (output.exists()) check(output.delete()) { "Failed to clean up contract-test cache output." }
    }

    private fun assertContractMetadataRows(
        plan: P2AndroidFinalCommandPlan,
        outputIdentity: String,
        canvas: String,
    ) {
        val rows =
            P2AndroidFinalCommandMeasurementReport.contractMetadataRows(
                plan,
                identity(plan.candidateId),
            )
        assertMetadataRow(rows, "schema", "nene-pixel-p2-android-final-command-measurement-v1")
        assertMetadataRow(rows, "output_identity", outputIdentity)
        assertMetadataRow(rows, "candidate_id", plan.candidateId)
        assertMetadataRow(rows, "run_index", "1")
        assertMetadataRow(rows, "canvas", canvas)
        assertMetadataRow(rows, "warmup_iterations_per_workload", "5")
        assertMetadataRow(rows, "sample_count_per_workload", "200")
        assertMetadataRow(rows, "sample_count_total", "1000")
        assertMetadataRow(rows, "sample_indices", "local=1..200;global=1..1000")
        assertMetadataRow(rows, "checkpoint_interval_global_samples", "25")
        assertMetadataRow(rows, "checkpoint_row_count", "42")
    }

    private fun assertMetadataRow(
        rows: Map<String, String>,
        name: String,
        value: String,
    ) {
        val values = listOf("metadata", name, value) + List(FINAL_COMMAND_COLUMN_COUNT - 3) { "" }
        val expected = values.joinToString(",") { field -> "\"${field.replace("\"", "\"\"")}\"" }
        assertEquals(expected, rows.getValue(name))
    }

    private companion object {
        const val CANDIDATE_256: String = "current-canonical-command-256-square"
        const val CANDIDATE_64: String = "current-canonical-command-64-square"
        const val CANDIDATE_128: String = "current-canonical-command-128-square"
        const val FINAL_COMMAND_COLUMN_COUNT: Int = 53
    }
}
