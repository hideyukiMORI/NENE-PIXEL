package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor

internal class P2CandidatePatchMeasurementFixture private constructor(
    private val descriptor: P2CandidatePatchMeasurementDescriptor,
    private val semantic: P2CandidatePatchSemanticFixture,
    private val lifecycle: P2CandidatePatchLifecycleFixture,
    private val conflicted: P2CandidateSnapshot,
) {
    fun execute(): P2CandidatePatchExecution =
        when (descriptor.operation) {
            P2CandidatePatchOperationKind.CreateShuffled -> {
                createShuffled()
            }

            P2CandidatePatchOperationKind.CreateInverse -> {
                P2CandidatePatchExecution.Inverted(lifecycle.forward.inverse())
            }

            P2CandidatePatchOperationKind.ApplyForward -> {
                P2CandidatePatchExecution.Applied(lifecycle.initial.apply(lifecycle.forward))
            }

            P2CandidatePatchOperationKind.ApplyInverse -> {
                P2CandidatePatchExecution.Applied(lifecycle.applied.apply(lifecycle.inverse))
            }

            P2CandidatePatchOperationKind.RoundTrip -> {
                executeRoundTrip()
            }

            P2CandidatePatchOperationKind.ApplyLateConflict -> {
                P2CandidatePatchExecution.Rejected(conflicted.apply(lifecycle.forward))
            }
        }

    fun verify(execution: P2CandidatePatchExecution): P2CandidatePatchMeasurementOutcome =
        when (descriptor.operation) {
            P2CandidatePatchOperationKind.CreateShuffled -> verifyCreate(execution)
            P2CandidatePatchOperationKind.CreateInverse -> verifyInverse(execution)
            P2CandidatePatchOperationKind.ApplyForward -> verifyForwardApply(execution)
            P2CandidatePatchOperationKind.ApplyInverse -> verifyInverseApply(execution)
            P2CandidatePatchOperationKind.RoundTrip -> verifyRoundTrip(execution)
            P2CandidatePatchOperationKind.ApplyLateConflict -> verifyLateConflict(execution)
        }

    fun executeAndVerify(): P2CandidatePatchMeasurementOutcome = verify(execute())

    private fun createShuffled(): P2CandidatePatchExecution.Created =
        P2CandidatePatchExecution.Created(
            P2CandidatePatchFactory.create(
                descriptor.configuration,
                lifecycle.initial,
                semantic.shuffledPositions,
                semantic.shuffledAfter,
            ),
        )

    private fun executeRoundTrip(): P2CandidatePatchExecution.RoundTripped {
        val forwardResult = lifecycle.initial.apply(lifecycle.forward)
        val inverse = lifecycle.forward.inverse()
        val inverseResult =
            when (forwardResult) {
                is P2CandidatePatchApplicationResult.Applied -> forwardResult.application.snapshot.apply(inverse)
                is P2CandidatePatchApplicationResult.Rejected -> forwardResult
            }
        return P2CandidatePatchExecution.RoundTripped(forwardResult, inverse, inverseResult)
    }

    private fun verifyCreate(execution: P2CandidatePatchExecution): P2CandidatePatchMeasurementOutcome {
        val created = requireExecution<P2CandidatePatchExecution.Created>(execution)
        val patch = created.result.requiredPatch()
        assertPatchContract(patch)
        val applied =
            lifecycle.initial
                .apply(patch)
                .requiredApplication()
                .snapshot
        val restored = applied.apply(patch.inverse()).requiredApplication().snapshot
        assertCandidatePixels(applied, semantic.appliedPixels)
        assertCandidatePixels(restored, semantic.initialPixels)
        return outcome(
            patch,
            patch.inverse(),
            P2CandidatePatchResultEvidence("Created", "", null),
            P2CandidatePatchOperationSnapshots(lifecycle.initial, lifecycle.initial),
        )
    }

    private fun verifyInverse(execution: P2CandidatePatchExecution): P2CandidatePatchMeasurementOutcome {
        val inverse = requireExecution<P2CandidatePatchExecution.Inverted>(execution).patch
        assertInverseContract(lifecycle.forward, inverse)
        return outcome(
            lifecycle.forward,
            inverse,
            P2CandidatePatchResultEvidence("Inverted", "", null),
            P2CandidatePatchOperationSnapshots(lifecycle.initial, lifecycle.initial),
        )
    }

    private fun verifyForwardApply(execution: P2CandidatePatchExecution): P2CandidatePatchMeasurementOutcome {
        val result = requireExecution<P2CandidatePatchExecution.Applied>(execution).result
        val applied = result.requiredApplication().snapshot
        assertCandidatePixels(applied, semantic.appliedPixels)
        assertCandidateUnaffectedPixels(lifecycle.initial, applied, lifecycle.forward)
        return outcome(
            lifecycle.forward,
            lifecycle.inverse,
            P2CandidatePatchResultEvidence("Applied", "", null),
            P2CandidatePatchOperationSnapshots(lifecycle.initial, applied),
        )
    }

    private fun verifyInverseApply(execution: P2CandidatePatchExecution): P2CandidatePatchMeasurementOutcome {
        val result = requireExecution<P2CandidatePatchExecution.Applied>(execution).result
        val restored = result.requiredApplication().snapshot
        assertCandidatePixels(restored, semantic.initialPixels)
        check(restored.revision == lifecycle.initial.revision) { "Candidate inverse revision was not restored." }
        return outcome(
            lifecycle.forward,
            lifecycle.inverse,
            P2CandidatePatchResultEvidence("Applied", "", null),
            P2CandidatePatchOperationSnapshots(lifecycle.applied, restored),
        )
    }

    private fun verifyRoundTrip(execution: P2CandidatePatchExecution): P2CandidatePatchMeasurementOutcome {
        val roundTrip = requireExecution<P2CandidatePatchExecution.RoundTripped>(execution)
        val applied = roundTrip.forwardResult.requiredApplication().snapshot
        val restored = roundTrip.inverseResult.requiredApplication().snapshot
        assertInverseContract(lifecycle.forward, roundTrip.inverse)
        assertCandidatePixels(applied, semantic.appliedPixels)
        assertCandidatePixels(restored, semantic.initialPixels)
        check(restored == lifecycle.initial) { "Candidate patch round trip did not restore the source snapshot." }
        return outcome(
            lifecycle.forward,
            roundTrip.inverse,
            P2CandidatePatchResultEvidence("RoundTripped", "", null),
            P2CandidatePatchOperationSnapshots(lifecycle.initial, restored),
        )
    }

    private fun verifyLateConflict(execution: P2CandidatePatchExecution): P2CandidatePatchMeasurementOutcome {
        val result = requireExecution<P2CandidatePatchExecution.Rejected>(execution).result
        val rejected = requireRejected(result)
        val mismatch =
            rejected.rejection as? P2CandidatePatchApplicationRejection.BeforeValueMismatch
                ?: error("Candidate late conflict returned ${rejected.rejection}.")
        check(mismatch.position == semantic.conflictPosition) { "Candidate conflict position changed." }
        check(mismatch.expected == lifecycle.forward.beforeAt(lifecycle.forward.changeCount - 1)) {
            "Candidate conflict expected value changed."
        }
        check(mismatch.actual == conflicted.packedAt(semantic.conflictPosition)) {
            "Candidate conflict actual value changed."
        }
        assertCandidatePixels(conflicted, semantic.conflictedPixels)
        check(conflicted.revision == lifecycle.initial.revision) { "Candidate conflict changed source revision." }
        return outcome(
            lifecycle.forward,
            lifecycle.inverse,
            P2CandidatePatchResultEvidence("Rejected", "BeforeValueMismatch", mismatch.position),
            P2CandidatePatchOperationSnapshots(conflicted, conflicted),
        )
    }

    private fun outcome(
        forward: P2CandidatePatch,
        inverse: P2CandidatePatch,
        result: P2CandidatePatchResultEvidence,
        operation: P2CandidatePatchOperationSnapshots,
    ): P2CandidatePatchMeasurementOutcome {
        assertPatchContract(forward)
        assertInverseContract(forward, inverse)
        return P2CandidatePatchMeasurementOutcome(
            storage = P2CandidatePatchStorageEvidence(lifecycle.initial.storage, forward.pairStorage(inverse)),
            state = stateEvidence(forward, operation),
            correctness = correctnessEvidence(forward, inverse),
            result = result,
        )
    }

    private fun stateEvidence(
        patch: P2CandidatePatch,
        operation: P2CandidatePatchOperationSnapshots,
    ): P2CandidatePatchStateEvidence {
        val inputUnaffected = P2CandidateDigest.unaffectedPixels(operation.input, patch)
        val outputUnaffected = P2CandidateDigest.unaffectedPixels(operation.output, patch)
        check(inputUnaffected == outputUnaffected) { "Candidate operation changed an unaffected pixel." }
        return P2CandidatePatchStateEvidence(
            lifecycle = lifecycleEvidence(),
            operation =
                P2CandidatePatchOperationEvidence(
                    operation.input.revision,
                    operation.output.revision,
                    P2CandidateDigest.pixels(operation.input),
                    P2CandidateDigest.pixels(operation.output),
                ),
            unaffected = P2CandidatePatchUnaffectedEvidence(inputUnaffected, outputUnaffected),
            affectedRegion = patch.affectedRegion,
        )
    }

    private fun lifecycleEvidence(): P2CandidatePatchLifecycleEvidence =
        P2CandidatePatchLifecycleEvidence(
            revisions =
                P2CandidatePatchRevisionEvidence(
                    lifecycle.initial.revision,
                    lifecycle.applied.revision,
                    lifecycle.initial.revision,
                ),
            digests =
                P2CandidatePatchLifecycleDigests(
                    P2CandidateDigest.pixels(lifecycle.initial),
                    P2CandidateDigest.pixels(lifecycle.applied),
                    P2CandidateDigest.pixels(lifecycle.initial),
                ),
        )

    private fun correctnessEvidence(
        forward: P2CandidatePatch,
        inverse: P2CandidatePatch,
    ): P2CandidatePatchCorrectness =
        P2CandidatePatchCorrectness(
            canonicalOrderDigest = P2CandidateDigest.canonicalOrder(forward),
            forwardPatchDigest = P2CandidateDigest.patch(forward),
            inversePatchDigest = P2CandidateDigest.patch(inverse),
            status = "pass",
        )

    private fun assertPatchContract(patch: P2CandidatePatch) {
        check(patch.configuration == descriptor.configuration) { "Candidate patch configuration changed." }
        check(patch.shape == descriptor.canvas) { "Candidate patch shape changed." }
        check(patch.direction == P2CandidatePatchDirection.Forward) { "Candidate patch direction changed." }
        check(patch.revisions == P2CandidateRevisionTransition(0L, 1L)) { "Candidate patch revisions changed." }
        check(patch.changeCount == descriptor.canvas.pixelCount.toInt()) { "Candidate patch count changed." }
        repeat(patch.changeCount) { index ->
            check(patch.positionAt(index) == index) { "Candidate patch ordering changed at $index." }
            check(patch.beforeAt(index) == semantic.initialPixels[index]) { "Candidate patch before value changed." }
            check(patch.afterAt(index) == semantic.appliedPixels[index]) { "Candidate patch after value changed." }
        }
        check(
            patch.affectedRegion == P2CandidateAffectedRegion(0, 0, descriptor.canvas.width, descriptor.canvas.height),
        ) {
            "Candidate patch affected region changed."
        }
    }

    private fun assertInverseContract(
        forward: P2CandidatePatch,
        inverse: P2CandidatePatch,
    ) {
        check(inverse.direction == P2CandidatePatchDirection.Inverse) { "Candidate inverse direction changed." }
        check(inverse.revisions == forward.revisions.inverse()) { "Candidate inverse revisions changed." }
        check(inverse.affectedRegion == forward.affectedRegion) { "Candidate inverse region changed." }
        repeat(forward.changeCount) { index ->
            check(inverse.positionAt(index) == forward.positionAt(index)) { "Candidate inverse ordering changed." }
            check(inverse.beforeAt(index) == forward.afterAt(index)) { "Candidate inverse before value changed." }
            check(inverse.afterAt(index) == forward.beforeAt(index)) { "Candidate inverse after value changed." }
        }
    }

    companion object {
        fun create(descriptor: P2CandidatePatchMeasurementDescriptor): P2CandidatePatchMeasurementFixture {
            val semantic = semanticFixture(descriptor.canvas)
            val initial = descriptor.configuration.createSnapshot(descriptor.canvas, 0L, semantic.initialPixels)
            val forward =
                P2CandidatePatchFactory
                    .create(
                        descriptor.configuration,
                        initial,
                        semantic.shuffledPositions,
                        semantic.shuffledAfter,
                    ).requiredPatch()
            val inverse = forward.inverse()
            val applied = initial.apply(forward).requiredApplication().snapshot
            val lifecycle = P2CandidatePatchLifecycleFixture(initial, forward, inverse, applied)
            val conflicted = descriptor.configuration.createSnapshot(descriptor.canvas, 0L, semantic.conflictedPixels)
            return P2CandidatePatchMeasurementFixture(descriptor, semantic, lifecycle, conflicted)
        }

        private fun semanticFixture(canvas: P2CanvasShape): P2CandidatePatchSemanticFixture {
            val initial = IntArray(canvas.pixelCount.toInt(), ::highEntropyPacked)
            val positions = IntArray(initial.size) { index -> initial.lastIndex - index }
            val after =
                positions.map { position ->
                    P2PackedRgba8888.unpack(initial[position] xor ALPHA_XOR_MASK)
                }
            val applied = initial.copyOf()
            positions.indices.forEach { index -> applied[positions[index]] = P2PackedRgba8888.pack(after[index]) }
            val conflictPosition = initial.lastIndex
            val conflicted = initial.copyOf().also { pixels -> pixels[conflictPosition] = applied[conflictPosition] }
            return P2CandidatePatchSemanticFixture(
                initial,
                applied,
                conflicted,
                P2CandidatePatchShuffledInput(positions, after, conflictPosition),
            )
        }

        private fun highEntropyPacked(index: Int): Int =
            ((index and CHANNEL_MASK) shl RED_SHIFT) or
                ((index ushr BYTE_BITS and CHANNEL_MASK) shl GREEN_SHIFT) or
                ((index * BLUE_MULTIPLIER and CHANNEL_MASK) shl BLUE_SHIFT) or
                (index * ALPHA_MULTIPLIER and CHANNEL_MASK)

        private const val RED_SHIFT: Int = 24
        private const val GREEN_SHIFT: Int = 16
        private const val BLUE_SHIFT: Int = 8
        private const val BYTE_BITS: Int = 8
        private const val CHANNEL_MASK: Int = 0xff
        private const val BLUE_MULTIPLIER: Int = 29
        private const val ALPHA_MULTIPLIER: Int = 43
        private const val ALPHA_XOR_MASK: Int = 0x000000ff
    }
}

private data class P2CandidatePatchSemanticFixture(
    val initialPixels: IntArray,
    val appliedPixels: IntArray,
    val conflictedPixels: IntArray,
    val shuffled: P2CandidatePatchShuffledInput,
) {
    val shuffledPositions: IntArray
        get() = shuffled.positions

    val shuffledAfter: List<PixelColor>
        get() = shuffled.after

    val conflictPosition: Int
        get() = shuffled.conflictPosition
}

private data class P2CandidatePatchShuffledInput(
    val positions: IntArray,
    val after: List<PixelColor>,
    val conflictPosition: Int,
)

private data class P2CandidatePatchLifecycleFixture(
    val initial: P2CandidateSnapshot,
    val forward: P2CandidatePatch,
    val inverse: P2CandidatePatch,
    val applied: P2CandidateSnapshot,
)

private data class P2CandidatePatchOperationSnapshots(
    val input: P2CandidateSnapshot,
    val output: P2CandidateSnapshot,
)

internal sealed interface P2CandidatePatchExecution {
    data class Created(
        val result: P2CandidatePatchCreationResult,
    ) : P2CandidatePatchExecution

    data class Inverted(
        val patch: P2CandidatePatch,
    ) : P2CandidatePatchExecution

    data class Applied(
        val result: P2CandidatePatchApplicationResult,
    ) : P2CandidatePatchExecution

    data class RoundTripped(
        val forwardResult: P2CandidatePatchApplicationResult,
        val inverse: P2CandidatePatch,
        val inverseResult: P2CandidatePatchApplicationResult,
    ) : P2CandidatePatchExecution

    data class Rejected(
        val result: P2CandidatePatchApplicationResult,
    ) : P2CandidatePatchExecution
}

private inline fun <reified T : P2CandidatePatchExecution> requireExecution(execution: P2CandidatePatchExecution): T =
    execution as? T
        ?: error("Candidate patch execution was ${execution::class.simpleName}, expected ${T::class.simpleName}.")

private fun requireRejected(result: P2CandidatePatchApplicationResult): P2CandidatePatchApplicationResult.Rejected =
    result as? P2CandidatePatchApplicationResult.Rejected ?: error("Candidate patch conflict unexpectedly applied.")
