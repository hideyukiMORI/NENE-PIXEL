package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

internal class P2CandidatePatchMeasurementFixture private constructor(
    private val descriptor: P2CandidatePatchMeasurementDescriptor,
    private val semantic: P2CandidateWorkloadFixture,
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
                semantic.reverseCanonicalPositions,
                semantic.reverseCanonicalAfter,
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
        P2CandidatePatchVerification.verifyForwardPatch(descriptor.configuration, patch, semantic)
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
        P2CandidatePatchVerification.verifyInverse(lifecycle.forward, inverse)
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
        P2CandidatePatchVerification.verifyInverse(lifecycle.forward, roundTrip.inverse)
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
        val operationLifecycle =
            P2CandidatePatchLifecycleFixture(
                lifecycle.initial,
                forward,
                inverse,
                lifecycle.applied,
            )
        val audit =
            P2CandidatePatchVerification.audit(
                descriptor.configuration,
                operationLifecycle,
                semantic,
                operation,
            )
        return P2CandidatePatchMeasurementOutcome(
            storage = P2CandidatePatchStorageEvidence(lifecycle.initial.storage, forward.pairStorage(inverse)),
            state = audit.state,
            correctness = audit.correctness,
            result = result,
        )
    }

    companion object {
        fun create(descriptor: P2CandidatePatchMeasurementDescriptor): P2CandidatePatchMeasurementFixture {
            val semantic = P2CandidateWorkloadFixture.create(descriptor.canvas, descriptor.workload.pathKind)
            val initial = descriptor.configuration.createSnapshot(descriptor.canvas, 0L, semantic.initialPixels)
            val forward =
                P2CandidatePatchFactory
                    .create(
                        descriptor.configuration,
                        initial,
                        semantic.reverseCanonicalPositions,
                        semantic.reverseCanonicalAfter,
                    ).requiredPatch()
            val inverse = forward.inverse()
            val applied = initial.apply(forward).requiredApplication().snapshot
            val lifecycle = P2CandidatePatchLifecycleFixture(initial, forward, inverse, applied)
            val conflicted =
                descriptor.configuration.createSnapshot(
                    descriptor.canvas,
                    0L,
                    semantic.conflictedPixels,
                )
            return P2CandidatePatchMeasurementFixture(descriptor, semantic, lifecycle, conflicted)
        }
    }
}

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
    result as? P2CandidatePatchApplicationResult.Rejected
        ?: error("Candidate patch conflict unexpectedly applied.")
