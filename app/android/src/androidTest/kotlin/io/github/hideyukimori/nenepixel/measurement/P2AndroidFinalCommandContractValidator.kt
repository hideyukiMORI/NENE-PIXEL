package io.github.hideyukimori.nenepixel.measurement

internal object P2AndroidFinalCommandContractValidator {
    fun validateSamples(
        plan: P2AndroidFinalCommandPlan,
        samples: List<P2AndroidFinalCommandSample>,
    ) {
        check(samples.size == plan.totalSampleCount) {
            "Final command report requires exactly ${plan.totalSampleCount} samples."
        }
        samples.forEachIndexed { zeroBasedIndex, sample -> validateSample(plan, zeroBasedIndex, sample) }
    }

    fun validateSample(
        plan: P2AndroidFinalCommandPlan,
        zeroBasedIndex: Int,
        sample: P2AndroidFinalCommandSample,
    ) {
        val expectedWorkloadIndex = zeroBasedIndex / plan.samplesPerWorkload
        val expectedLocalIndex = zeroBasedIndex % plan.samplesPerWorkload + 1
        val expectedSpec = plan.specs[expectedWorkloadIndex]
        check(sample.spec == expectedSpec)
        check(sample.localSampleIndex == expectedLocalIndex)
        check(sample.globalSampleIndex == zeroBasedIndex + 1)
        check(sample.latencyNanos >= 0L)
        validateOutcome(plan, sample)
        validateDiagnostics(sample)
    }

    fun validateCheckpoints(
        plan: P2AndroidFinalCommandPlan,
        checkpoints: List<P2AndroidPhysicalCheckpoint>,
    ) {
        check(checkpoints.size == plan.checkpointCount) {
            "Final command report requires exactly ${plan.checkpointCount} physical checkpoints."
        }
        val actualIdentities =
            checkpoints.map { checkpoint -> checkpoint.name to checkpoint.sampleIndex }
        check(actualIdentities == checkpointIdentities(plan)) {
            "Final command physical checkpoint order or identity changed."
        }
        val baseline = checkpoints.first()
        P2AndroidFinalCommandProfile.validateBaselineCheckpoint(baseline)
        checkpoints.drop(1).forEach { checkpoint -> checkpoint.assertCompatibleWith(baseline) }
    }

    fun checkpointIdentities(plan: P2AndroidFinalCommandPlan): List<Pair<String, Int>> =
        listOf("before_samples" to 0) +
            (
                P2AndroidPhysicalCheckpointPolicy.CHECKPOINT_INTERVAL..plan.totalSampleCount step
                    P2AndroidPhysicalCheckpointPolicy.CHECKPOINT_INTERVAL
            ).map { index -> "after_$index" to index } +
            listOf("after_samples" to plan.totalSampleCount)

    fun validateMemory(memory: PostGcMemorySnapshot) {
        check(memory.javaHeapUsedBytes >= 0L && memory.javaHeapCommittedBytes >= memory.javaHeapUsedBytes)
        check(memory.totalPssKilobytes >= 0)
        check(memory.dalvikPssKilobytes >= 0 && memory.nativePssKilobytes >= 0 && memory.otherPssKilobytes >= 0)
        check(memory.totalPrivateDirtyKilobytes >= 0 && memory.totalSharedDirtyKilobytes >= 0)
    }

    private fun validateOutcome(
        plan: P2AndroidFinalCommandPlan,
        sample: P2AndroidFinalCommandSample,
    ) {
        val outcome = sample.outcome
        val noOp = sample.spec.kind == P2CommandWorkloadKind.DenseNoOp
        val undo = sample.spec.kind == P2CommandWorkloadKind.DenseUndo
        check(outcome.resultKind == if (noOp) "rejected_no_effective_change" else "applied")
        check(outcome.revision == if (undo || noOp) 0L else 1L)
        check(
            outcome.history ==
                when {
                    undo -> "redo_available"
                    noOp -> "none"
                    else -> "undo_available"
                },
        )
        check(outcome.unchangedStateIdentity == noOp)
        if (noOp) {
            check(outcome.changeSetBeforeRevision == null)
            check(outcome.changeSetAfterRevision == null)
            check(outcome.renderInvalidation == null)
        } else {
            check(outcome.changeSetBeforeRevision == if (undo) 1L else 0L)
            check(outcome.changeSetAfterRevision == if (undo) 0L else 1L)
            val expectedRegion =
                if (sample.spec.kind == P2CommandWorkloadKind.SparseApply) {
                    plan.sparseRegion
                } else {
                    plan.fullCanvasRegion
                }
            check(outcome.renderInvalidation == expectedRegion)
        }
    }

    private fun validateDiagnostics(sample: P2AndroidFinalCommandSample) {
        val runtime = sample.runtimeDelta
        check(runtime.allocatedBytesBefore >= 0L && runtime.allocatedBytesAfter >= 0L)
        check(runtime.allocatedBytesDelta >= 0L)
        check(runtime.gcCountDelta >= 0L && runtime.gcTimeMillisDelta >= 0L)
        check(runtime.blockingGcCountDelta >= 0L && runtime.blockingGcTimeMillisDelta >= 0L)
        validateMemory(sample.memory)
    }
}
