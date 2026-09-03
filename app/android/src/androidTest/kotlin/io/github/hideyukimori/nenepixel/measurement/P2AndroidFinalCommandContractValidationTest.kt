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
    fun resolvesSingleCopyFlatPackedProductionPlanWithThirdNoOverwritePath() {
        val plan = resolve(CANDIDATE_FLAT_PACKED_256_CLEAN_V3, runIndex = 1)

        assertEquals(CANDIDATE_FLAT_PACKED_256_CLEAN_V3, plan.candidateId)
        assertEquals("nene-pixel-p2-android-clean-command-latency-v1", plan.schema)
        assertEquals("device-clean-flat-packed-command-256-run-03", plan.outputIdentity)
        assertEquals(
            "p2-measurements/p2-android-clean-flat-packed-command-256-run-03.csv",
            plan.outputRelativePath,
        )
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists, plan.publicationPolicy)
        assertCommonCountsAndOrder(plan, sparsePositionCount = 256, densePositionCount = 65_536)
    }

    @Test
    fun resolvesOptimizedFlatPackedProductionPlanWithSecondNoOverwritePath() {
        val plan = resolve(CANDIDATE_FLAT_PACKED_256_CLEAN_V2, runIndex = 1)

        assertEquals(CANDIDATE_FLAT_PACKED_256_CLEAN_V2, plan.candidateId)
        assertEquals("nene-pixel-p2-android-clean-command-latency-v1", plan.schema)
        assertEquals("device-clean-flat-packed-command-256-run-02", plan.outputIdentity)
        assertEquals(
            "p2-measurements/p2-android-clean-flat-packed-command-256-run-02.csv",
            plan.outputRelativePath,
        )
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists, plan.publicationPolicy)
        assertCommonCountsAndOrder(plan, sparsePositionCount = 256, densePositionCount = 65_536)
    }

    @Test
    fun resolvesCleanFlatPackedProductionPlanWithNoOverwritePath() {
        val plan = resolve(CANDIDATE_FLAT_PACKED_256_CLEAN, runIndex = 1)

        assertEquals(CANDIDATE_FLAT_PACKED_256_CLEAN, plan.candidateId)
        assertEquals("nene-pixel-p2-android-clean-command-latency-v1", plan.schema)
        assertEquals("device-clean-flat-packed-command-256-run-01", plan.outputIdentity)
        assertEquals(
            "p2-measurements/p2-android-clean-flat-packed-command-256-run-01.csv",
            plan.outputRelativePath,
        )
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists, plan.publicationPolicy)
        assertCommonCountsAndOrder(plan, sparsePositionCount = 256, densePositionCount = 65_536)
    }

    @Test
    fun resolvesCleanCurrentLatencyPlanWithDistinctSchemaAndNoOverwritePath() {
        val plan = resolve(CANDIDATE_256_CLEAN, runIndex = 1)

        assertEquals(CANDIDATE_256_CLEAN, plan.candidateId)
        assertEquals("nene-pixel-p2-android-clean-command-latency-v1", plan.schema)
        assertEquals("device-clean-current-command-256-run-01", plan.outputIdentity)
        assertEquals(
            "p2-measurements/p2-android-clean-current-command-256-run-01.csv",
            plan.outputRelativePath,
        )
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists, plan.publicationPolicy)
        assertCommonCountsAndOrder(plan, sparsePositionCount = 256, densePositionCount = 65_536)
    }

    @Test
    fun preservesExact256SquarePlan() {
        val plan = resolve(CANDIDATE_256, runIndex = 1)

        assertEquals("current-canonical-command-256-square", plan.candidateId)
        assertEquals(1, plan.runIndex)
        assertEquals(256, plan.canvasWidth)
        assertEquals(256, plan.canvasHeight)
        assertEquals("device-core", plan.outputIdentity)
        assertEquals("p2-measurements/p2-android-final-command-measurement.csv", plan.outputRelativePath)
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.OverwriteExisting, plan.publicationPolicy)
        assertEquals("nene-pixel-p2-android-final-command-measurement-v1", plan.schema)
        assertCommonCountsAndOrder(plan, sparsePositionCount = 256, densePositionCount = 65_536)
    }

    @Test
    fun resolvesExact64SquarePlanWithDistinctIdentityAndPath() {
        val legacyPlan = resolve(CANDIDATE_256, runIndex = 1)
        val plan = resolve(CANDIDATE_64, runIndex = 1)

        assertEquals("current-canonical-command-64-square", plan.candidateId)
        assertEquals(1, plan.runIndex)
        assertEquals(64, plan.canvasWidth)
        assertEquals(64, plan.canvasHeight)
        assertEquals("device-core-current-64-square", plan.outputIdentity)
        assertEquals("p2-measurements/p2-android-final-command-64-square.csv", plan.outputRelativePath)
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists, plan.publicationPolicy)
        assertEquals("nene-pixel-p2-android-final-command-measurement-v1", plan.schema)
        assertNotEquals(legacyPlan.outputIdentity, plan.outputIdentity)
        assertNotEquals(legacyPlan.outputRelativePath, plan.outputRelativePath)
        assertCommonCountsAndOrder(plan, sparsePositionCount = 64, densePositionCount = 4_096)
    }

    @Test
    fun resolvesExact128SquarePlanWithDistinctIdentityAndPath() {
        val legacyPlan = resolve(CANDIDATE_256, runIndex = 1)
        val smallerPlan = resolve(CANDIDATE_64, runIndex = 1)
        val plan = resolve(CANDIDATE_128, runIndex = 1)

        assertEquals("current-canonical-command-128-square", plan.candidateId)
        assertEquals(1, plan.runIndex)
        assertEquals(128, plan.canvasWidth)
        assertEquals(128, plan.canvasHeight)
        assertEquals("device-core-current-128-square", plan.outputIdentity)
        assertEquals("p2-measurements/p2-android-final-command-128-square.csv", plan.outputRelativePath)
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists, plan.publicationPolicy)
        assertEquals("nene-pixel-p2-android-final-command-measurement-v1", plan.schema)
        assertNotEquals(legacyPlan.outputIdentity, plan.outputIdentity)
        assertNotEquals(legacyPlan.outputRelativePath, plan.outputRelativePath)
        assertNotEquals(smallerPlan.outputIdentity, plan.outputIdentity)
        assertNotEquals(smallerPlan.outputRelativePath, plan.outputRelativePath)
        assertCommonCountsAndOrder(plan, sparsePositionCount = 128, densePositionCount = 16_384)
    }

    @Test
    fun resolvesExactFourKilopixelRectanglePlansWithOrderedGeometryAndDistinctOutputs() {
        val squarePlan = resolve(CANDIDATE_64, runIndex = 1)
        val tallPlan = resolve(CANDIDATE_16_X_256, runIndex = 1)
        val widePlan = resolve(CANDIDATE_256_X_16, runIndex = 1)

        assertRectanglePlan(
            plan = tallPlan,
            candidateId = CANDIDATE_16_X_256,
            width = 16,
            height = 256,
            outputIdentity = "device-core-current-16x256-rectangle",
            outputPath = "p2-measurements/p2-android-final-command-16x256-rectangle.csv",
        )
        assertRectanglePlan(
            plan = widePlan,
            candidateId = CANDIDATE_256_X_16,
            width = 256,
            height = 16,
            outputIdentity = "device-core-current-256x16-rectangle",
            outputPath = "p2-measurements/p2-android-final-command-256x16-rectangle.csv",
        )
        assertPairwiseDistinctOutputs(listOf(squarePlan, tallPlan, widePlan))
    }

    @Test
    fun resolvesExactSixteenKilopixelRectanglePlansWithOrderedGeometryAndDistinctOutputs() {
        val tallPlan = resolve(CANDIDATE_64_X_256, runIndex = 1)
        val widePlan = resolve(CANDIDATE_256_X_64, runIndex = 1)

        assertRectanglePlan(
            plan = tallPlan,
            candidateId = CANDIDATE_64_X_256,
            width = 64,
            height = 256,
            outputIdentity = "device-core-current-64x256-rectangle",
            outputPath = "p2-measurements/p2-android-final-command-64x256-rectangle.csv",
        )
        assertRectanglePlan(
            plan = widePlan,
            candidateId = CANDIDATE_256_X_64,
            width = 256,
            height = 64,
            outputIdentity = "device-core-current-256x64-rectangle",
            outputPath = "p2-measurements/p2-android-final-command-256x64-rectangle.csv",
        )
        assertPairwiseDistinctOutputs(
            listOf(
                resolve(CANDIDATE_256, runIndex = 1),
                resolve(CANDIDATE_64, runIndex = 1),
                resolve(CANDIDATE_128, runIndex = 1),
                resolve(CANDIDATE_16_X_256, runIndex = 1),
                resolve(CANDIDATE_256_X_16, runIndex = 1),
                tallPlan,
                widePlan,
            ),
        )
    }

    @Test
    fun rejectsWrongRunAndUnknownCandidate() {
        assertThrows(IllegalStateException::class.java) {
            resolve(CANDIDATE_128, runIndex = 2)
        }
        assertThrows(IllegalStateException::class.java) {
            resolve(CANDIDATE_16_X_256, runIndex = 2)
        }
        assertThrows(IllegalStateException::class.java) {
            resolve(CANDIDATE_64_X_256, runIndex = 2)
        }
        assertThrows(IllegalStateException::class.java) {
            resolve(CANDIDATE_256_X_64, runIndex = 2)
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
    fun legacyMeasurementCatalogRetainsOnlyTheOriginalSquareMatrix() {
        val shapes =
            P2CommandWorkloadCatalog.specs
                .map { spec -> spec.canvasWidth to spec.canvasHeight }
                .distinct()

        assertEquals(listOf(16 to 16, 64 to 64, 256 to 256), shapes)
        assertEquals(15, P2CommandWorkloadCatalog.specs.size)
    }

    @Test
    fun rectangleWorkloadsUseMinAxisDiagonalAndShapeSpecificInvalidations() {
        listOf(16 to 256, 256 to 16, 64 to 256, 256 to 64).forEach { (width, height) ->
            P2CommandWorkloadCatalog.shapeSpecs(width, height).forEach { spec ->
                val prepared = PreparedCommandWorkload.create(spec)
                val outcome = prepared.verify(prepared.execute())
                val sparsePositionCount = minOf(width, height)
                val expectedPositionCount =
                    if (spec.kind == P2CommandWorkloadKind.SparseApply) {
                        sparsePositionCount
                    } else {
                        width * height
                    }
                val expectedInvalidation =
                    when (spec.kind) {
                        P2CommandWorkloadKind.SparseApply -> {
                            P2CommandRegionDescriptor(0, 0, sparsePositionCount, sparsePositionCount)
                        }

                        P2CommandWorkloadKind.DenseNoOp -> {
                            null
                        }

                        else -> {
                            P2CommandRegionDescriptor(0, 0, width, height)
                        }
                    }

                assertEquals(width, spec.canvasWidth)
                assertEquals(height, spec.canvasHeight)
                assertEquals(expectedPositionCount, spec.positionCount)
                assertEquals(expectedInvalidation, outcome.renderInvalidation)
            }
        }
    }

    @Test
    fun fixedPhysicalProfileMetadataIsExact() {
        assertEquals(
            "ALLDOCUBE/iPlay80miniPro/T830:16/BP2A.250605.031.A3/94111:user/release-keys",
            P2AndroidFinalCommandProfile.BUILD_FINGERPRINT,
        )
        assertEquals("2026-08-05", P2AndroidFinalCommandProfile.SECURITY_PATCH)
        assertEquals(268_435_456L, P2AndroidFinalCommandProfile.RUNTIME_MAX_MEMORY_BYTES)
        assertEquals(256, P2AndroidFinalCommandProfile.MEMORY_CLASS_MIB)
        assertEquals(1, P2AndroidFinalCommandProfile.DISPLAY_MODE_ID)
        assertEquals(1_200, P2AndroidFinalCommandProfile.DISPLAY_WIDTH_PIXELS)
        assertEquals(1_920, P2AndroidFinalCommandProfile.DISPLAY_HEIGHT_PIXELS)
        assertEquals(90.0f, P2AndroidFinalCommandProfile.REFRESH_RATE_HERTZ)
    }

    @Test
    fun fixedArtRuntimeStatMetadataNamesAreExact() {
        assertEquals(
            listOf(
                "art.gc.blocking-gc-count",
                "art.gc.blocking-gc-count-rate-histogram",
                "art.gc.blocking-gc-time",
                "art.gc.bytes-allocated",
                "art.gc.bytes-freed",
                "art.gc.gc-count",
                "art.gc.gc-count-rate-histogram",
                "art.gc.gc-time",
            ),
            P2AndroidFinalCommandProfile.ART_RUNTIME_STAT_NAMES,
        )
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
    fun rectangleSampleValidatorAcceptsExactRegionsAndRejectsEdgeSwapOrWrongInvalidation() {
        RECTANGLE_CANDIDATES.forEach { candidateId ->
            val plan = resolve(candidateId, runIndex = 1)
            plan.specs.forEachIndexed { workloadIndex, spec ->
                val zeroBasedIndex = workloadIndex * plan.samplesPerWorkload
                P2AndroidFinalCommandContractValidator.validateSample(
                    plan,
                    zeroBasedIndex,
                    validSample(
                        plan = plan,
                        spec = spec,
                        localIndex = 1,
                        globalIndex = zeroBasedIndex + 1,
                        outcome = validOutcome(plan, spec),
                    ),
                )
            }

            val sparse = plan.specs.first()
            val edgeSwapped = sparse.copy(canvasWidth = plan.canvasHeight, canvasHeight = plan.canvasWidth)
            assertThrows(IllegalStateException::class.java) {
                P2AndroidFinalCommandContractValidator.validateSample(
                    plan,
                    zeroBasedIndex = 0,
                    validSample(plan, spec = edgeSwapped, outcome = validOutcome(plan, sparse)),
                )
            }
            assertThrows(IllegalStateException::class.java) {
                P2AndroidFinalCommandContractValidator.validateSample(
                    plan,
                    zeroBasedIndex = 0,
                    validSample(
                        plan,
                        spec = sparse,
                        outcome = validAppliedOutcome(plan.fullCanvasRegion),
                    ),
                )
            }
            val dense = plan.specs[1]
            assertThrows(IllegalStateException::class.java) {
                P2AndroidFinalCommandContractValidator.validateSample(
                    plan,
                    zeroBasedIndex = plan.samplesPerWorkload,
                    validSample(
                        plan,
                        spec = dense,
                        localIndex = 1,
                        globalIndex = plan.samplesPerWorkload + 1,
                        outcome = validAppliedOutcome(plan.sparseRegion),
                    ),
                )
            }
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
        assertContractMetadataRows(
            plan = resolve(CANDIDATE_16_X_256, runIndex = 1),
            outputIdentity = "device-core-current-16x256-rectangle",
            canvas = "16x256",
        )
        assertContractMetadataRows(
            plan = resolve(CANDIDATE_256_X_16, runIndex = 1),
            outputIdentity = "device-core-current-256x16-rectangle",
            canvas = "256x16",
        )
        assertContractMetadataRows(
            plan = resolve(CANDIDATE_64_X_256, runIndex = 1),
            outputIdentity = "device-core-current-64x256-rectangle",
            canvas = "64x256",
        )
        assertContractMetadataRows(
            plan = resolve(CANDIDATE_256_X_64, runIndex = 1),
            outputIdentity = "device-core-current-256x64-rectangle",
            canvas = "256x64",
        )
    }

    @Test
    fun serializesRectangleSampleWidthAndHeightInExistingFiftyThreeColumnSchema() {
        RECTANGLE_CANDIDATES.map { candidateId -> resolve(candidateId, runIndex = 1) }.forEach { plan ->
            val sample = validSample(plan)
            val fields =
                P2AndroidFinalCommandMeasurementReport
                    .contractSampleRow(reportInput(plan, sample, sourceCommit = SOURCE_COMMIT), sample)
                    .removePrefix("\"")
                    .removeSuffix("\"")
                    .split("\",\"")

            assertEquals(FINAL_COMMAND_COLUMN_COUNT, fields.size)
            assertEquals(plan.candidateId, fields[5])
            assertEquals("1", fields[6])
            assertEquals(SOURCE_COMMIT, fields[7])
            assertEquals(plan.canvasWidth.toString(), fields[8])
            assertEquals(plan.canvasHeight.toString(), fields[9])
            assertEquals(minOf(plan.canvasWidth, plan.canvasHeight).toString(), fields[10])
            assertEquals(List(SAMPLE_MEMORY_COLUMN_COUNT) { "" }, fields.slice(SAMPLE_MEMORY_COLUMN_RANGE))
        }
    }

    private fun assertCommonCountsAndOrder(
        plan: P2AndroidFinalCommandPlan,
        sparsePositionCount: Int,
        densePositionCount: Int,
    ) {
        assertEquals("${plan.canvasWidth}x${plan.canvasHeight}", plan.canvasMetadata)
        assertEquals(P2CommandWorkloadKind.entries, plan.specs.map(P2CommandWorkloadSpec::kind))
        assertEquals(
            List(plan.workloadCount) { plan.canvasWidth },
            plan.specs.map(P2CommandWorkloadSpec::canvasWidth),
        )
        assertEquals(
            List(plan.workloadCount) { plan.canvasHeight },
            plan.specs.map(P2CommandWorkloadSpec::canvasHeight),
        )
        assertEquals(
            listOf(sparsePositionCount, densePositionCount, densePositionCount, densePositionCount, densePositionCount),
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
        assertEquals(P2CommandRegionDescriptor(0, 0, plan.canvasWidth, plan.canvasHeight), plan.fullCanvasRegion)
        assertEquals(
            P2CommandRegionDescriptor(0, 0, sparsePositionCount, sparsePositionCount),
            plan.sparseRegion,
        )
    }

    private fun assertRectanglePlan(
        plan: P2AndroidFinalCommandPlan,
        candidateId: String,
        width: Int,
        height: Int,
        outputIdentity: String,
        outputPath: String,
    ) {
        assertEquals(candidateId, plan.candidateId)
        assertEquals(1, plan.runIndex)
        assertEquals(width, plan.canvasWidth)
        assertEquals(height, plan.canvasHeight)
        assertEquals(outputIdentity, plan.outputIdentity)
        assertEquals(outputPath, plan.outputRelativePath)
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.FailIfExists, plan.publicationPolicy)
        assertEquals("nene-pixel-p2-android-final-command-measurement-v1", plan.schema)
        assertCommonCountsAndOrder(
            plan,
            sparsePositionCount = minOf(width, height),
            densePositionCount = width * height,
        )
    }

    private fun assertPairwiseDistinctOutputs(plans: List<P2AndroidFinalCommandPlan>) {
        plans.forEachIndexed { index, plan ->
            plans.drop(index + 1).forEach { other ->
                assertNotEquals(other.outputIdentity, plan.outputIdentity)
                assertNotEquals(other.outputRelativePath, plan.outputRelativePath)
            }
        }
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
        spec: P2CommandWorkloadSpec = plan.specs.first(),
        localIndex: Int = 1,
        globalIndex: Int = 1,
        invalidation: P2CommandRegionDescriptor = plan.sparseRegion,
        outcome: CommandOutcomeDescriptor = validAppliedOutcome(invalidation),
    ): P2AndroidFinalCommandSample =
        P2AndroidFinalCommandSample(
            spec = spec,
            indices = P2AndroidFinalCommandSample.Indices(localIndex, globalIndex),
            observation =
                P2AndroidFinalCommandSample.Observation(
                    latencyNanos = 0L,
                    runtimeDelta = zeroRuntimeDelta(),
                ),
            outcome = outcome,
        )

    private fun validOutcome(
        plan: P2AndroidFinalCommandPlan,
        spec: P2CommandWorkloadSpec,
    ): CommandOutcomeDescriptor =
        when (spec.kind) {
            P2CommandWorkloadKind.DenseNoOp -> {
                CommandOutcomeDescriptor(
                    resultKind = "rejected_no_effective_change",
                    documentHash = 1,
                    snapshotHash = 2,
                    revision = 0L,
                    history = "none",
                    changeSetBeforeRevision = null,
                    changeSetAfterRevision = null,
                    renderInvalidation = null,
                    unchangedStateIdentity = true,
                )
            }

            P2CommandWorkloadKind.DenseUndo -> {
                validAppliedOutcome(plan.fullCanvasRegion).copy(
                    revision = 0L,
                    history = "redo_available",
                    changeSetBeforeRevision = 1L,
                    changeSetAfterRevision = 0L,
                )
            }

            P2CommandWorkloadKind.SparseApply -> {
                validAppliedOutcome(plan.sparseRegion)
            }

            P2CommandWorkloadKind.DenseApply,
            P2CommandWorkloadKind.DenseRedo,
            -> {
                validAppliedOutcome(plan.fullCanvasRegion)
            }
        }

    private fun reportInput(
        plan: P2AndroidFinalCommandPlan,
        sample: P2AndroidFinalCommandSample,
        sourceCommit: String = "0".repeat(40),
    ): P2AndroidFinalCommandReportInput {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val environment =
            P2AndroidMeasurementEnvironment(
                targetContext = context,
                profileId = P2AndroidFinalCommandProtocol.PHYSICAL_PROFILE_ID,
                evidenceClass = "physical_device",
                emulatorDetection = EmulatorDetection(false, emptyList(), "", ""),
                auxiliaryEmulatorArgumentPresent = false,
                warmupIterations = P2AndroidFinalCommandProtocol.WARMUP_ITERATIONS,
                sampleCount = P2AndroidFinalCommandProtocol.SAMPLES_PER_WORKLOAD,
                frameWarmupIterations = 5,
                frameSampleCount = 200,
            )
        return P2AndroidFinalCommandReportInput(
            plan = plan,
            run =
                P2AndroidFinalCommandReportInput.Run(
                    environment,
                    identity(plan.candidateId).copy(sourceCommit = sourceCommit),
                ),
            observations =
                P2AndroidFinalCommandReportInput.Observations(
                    baseline = zeroMemorySnapshot(),
                    checkpoints = emptyList(),
                    samples = listOf(sample),
                ),
        )
    }

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
        const val CANDIDATE_FLAT_PACKED_256_CLEAN_V3: String = "flat-packed-command-256-clean-latency-v3"
        const val CANDIDATE_FLAT_PACKED_256_CLEAN_V2: String = "flat-packed-command-256-clean-latency-v2"
        const val CANDIDATE_FLAT_PACKED_256_CLEAN: String = "flat-packed-command-256-clean-latency-v1"
        const val CANDIDATE_256_CLEAN: String = "current-canonical-command-256-clean-latency-v1"
        const val CANDIDATE_256: String = "current-canonical-command-256-square"
        const val CANDIDATE_64: String = "current-canonical-command-64-square"
        const val CANDIDATE_128: String = "current-canonical-command-128-square"
        const val CANDIDATE_16_X_256: String = "current-canonical-command-16x256-rectangle"
        const val CANDIDATE_256_X_16: String = "current-canonical-command-256x16-rectangle"
        const val CANDIDATE_64_X_256: String = "current-canonical-command-64x256-rectangle"
        const val CANDIDATE_256_X_64: String = "current-canonical-command-256x64-rectangle"
        const val SOURCE_COMMIT: String = "0123456789abcdef0123456789abcdef01234567"
        const val FINAL_COMMAND_COLUMN_COUNT: Int = 53
        const val SAMPLE_MEMORY_COLUMN_COUNT: Int = 8
        val SAMPLE_MEMORY_COLUMN_RANGE: IntRange = 35..42
        val RECTANGLE_CANDIDATES: List<String> =
            listOf(
                CANDIDATE_16_X_256,
                CANDIDATE_256_X_16,
                CANDIDATE_64_X_256,
                CANDIDATE_256_X_64,
            )
    }
}
