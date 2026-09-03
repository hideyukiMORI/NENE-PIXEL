package io.github.hideyukimori.nenepixel.measurement

internal data class P2MeasuredPackedCandidateExecution(
    val latencyNanos: Long,
    val runtimeDelta: ArtRuntimeDelta,
    val outcome: P2PackedCandidateOutcome,
)

internal class P2AndroidPackedCandidateWorkload private constructor(
    private val spec: P2PackedCandidateSpec,
    private val initial: P2PackedCandidateSnapshot,
    private val rawPositions: IntArray,
    private val prepared: P2PackedCandidatePreparedUndoRedo?,
) {
    fun executeMeasured(): P2MeasuredPackedCandidateExecution {
        val runtimeBefore = ArtRuntimeSnapshot.capture()
        val startedAtNanos = System.nanoTime()
        val result = execute()
        val latencyNanos = System.nanoTime() - startedAtNanos
        val runtimeAfter = ArtRuntimeSnapshot.capture()
        val outcome = result.outcome()
        P2PackedCandidateReferenceSink.consume(result.snapshot)
        return P2MeasuredPackedCandidateExecution(latencyNanos, runtimeAfter.deltaFrom(runtimeBefore), outcome)
    }

    fun executeAndVerifyFully(): P2PackedCandidateOutcome {
        val result = execute()
        verifyPixels(result.snapshot)
        return result.outcome()
    }

    private fun execute(): P2PackedCandidateResult =
        when (spec.workload) {
            P2PackedCandidateWorkload.SparseApply,
            P2PackedCandidateWorkload.DenseApply,
            -> applyRawPath()

            P2PackedCandidateWorkload.DenseNoOp -> applyNoOp()

            P2PackedCandidateWorkload.DenseUndo -> undo()

            P2PackedCandidateWorkload.DenseRedo -> redo()
        }

    private fun applyRawPath(): P2PackedCandidateResult {
        val patch = requireNotNull(P2PackedCandidatePatch.fromRawPath(initial, rawPositions, TARGET_RGBA))
        return P2PackedCandidateResult(initial.apply(patch), patch.changeCount)
    }

    private fun applyNoOp(): P2PackedCandidateResult {
        val patch = P2PackedCandidatePatch.fromRawPath(initial, rawPositions, TARGET_RGBA)
        check(patch == null) { "Candidate no-op produced a patch." }
        return P2PackedCandidateResult(initial, 0)
    }

    private fun undo(): P2PackedCandidateResult {
        val fixture = requireNotNull(prepared)
        val snapshot = fixture.applied.apply(fixture.reverse)
        return P2PackedCandidateResult(snapshot, fixture.forward.changeCount)
    }

    private fun redo(): P2PackedCandidateResult {
        val fixture = requireNotNull(prepared)
        val snapshot = fixture.restored.apply(fixture.forward)
        return P2PackedCandidateResult(snapshot, fixture.forward.changeCount)
    }

    private fun verifyPixels(snapshot: P2PackedCandidateSnapshot) {
        when (spec.workload) {
            P2PackedCandidateWorkload.SparseApply -> snapshot.verifySparse(TARGET_RGBA)

            P2PackedCandidateWorkload.DenseApply,
            P2PackedCandidateWorkload.DenseRedo,
            -> snapshot.verifyUniform(TARGET_RGBA)

            P2PackedCandidateWorkload.DenseNoOp -> snapshot.verifyUniform(TARGET_RGBA)

            P2PackedCandidateWorkload.DenseUndo -> snapshot.verifyUniform(SOURCE_RGBA)
        }
    }

    private fun P2PackedCandidateResult.outcome(): P2PackedCandidateOutcome =
        P2PackedCandidateOutcome(
            revision = snapshot.revision,
            firstPixel = snapshot.packedAt(0),
            lastPixel = snapshot.packedAt(P2AndroidPackedCandidateProtocol.PIXEL_COUNT - 1),
            changeCount = changeCount,
        )

    companion object {
        private const val TARGET_RGBA: Int = P2AndroidPackedCandidateProtocol.TARGET_RGBA
        private const val SOURCE_RGBA: Int = P2AndroidPackedCandidateProtocol.SOURCE_RGBA

        fun create(spec: P2PackedCandidateSpec): P2AndroidPackedCandidateWorkload {
            val initialColor =
                if (spec.workload == P2PackedCandidateWorkload.DenseNoOp) TARGET_RGBA else SOURCE_RGBA
            val initial =
                spec.candidate.initialSnapshot(
                    IntArray(P2AndroidPackedCandidateProtocol.PIXEL_COUNT) {
                        initialColor
                    },
                )
            val rawPositions = spec.workload.rawPositions()
            val prepared = preparedUndoRedo(spec.workload, initial, rawPositions)
            return P2AndroidPackedCandidateWorkload(spec, initial, rawPositions, prepared)
        }

        private fun preparedUndoRedo(
            workload: P2PackedCandidateWorkload,
            initial: P2PackedCandidateSnapshot,
            rawPositions: IntArray,
        ): P2PackedCandidatePreparedUndoRedo? =
            if (workload == P2PackedCandidateWorkload.DenseUndo || workload == P2PackedCandidateWorkload.DenseRedo) {
                val forward = requireNotNull(P2PackedCandidatePatch.fromRawPath(initial, rawPositions, TARGET_RGBA))
                val reverse = forward.inverse()
                val applied = initial.apply(forward)
                P2PackedCandidatePreparedUndoRedo(forward, reverse, applied, applied.apply(reverse))
            } else {
                null
            }
    }
}

private data class P2PackedCandidateResult(
    val snapshot: P2PackedCandidateSnapshot,
    val changeCount: Int,
)

private data class P2PackedCandidatePreparedUndoRedo(
    val forward: P2PackedCandidatePatch,
    val reverse: P2PackedCandidatePatch,
    val applied: P2PackedCandidateSnapshot,
    val restored: P2PackedCandidateSnapshot,
)

private fun P2PackedCandidateKind.initialSnapshot(pixels: IntArray): P2PackedCandidateSnapshot =
    when (this) {
        P2PackedCandidateKind.Flat -> P2FlatPackedCandidateSnapshot.initial(pixels)
        P2PackedCandidateKind.TiledCow16 -> P2TiledCow16CandidateSnapshot.initial(pixels)
    }

private fun P2PackedCandidateWorkload.rawPositions(): IntArray =
    when (this) {
        P2PackedCandidateWorkload.SparseApply -> {
            IntArray(P2AndroidPackedCandidateProtocol.CANVAS_EDGE) { coordinate ->
                coordinate * P2AndroidPackedCandidateProtocol.CANVAS_EDGE + coordinate
            }
        }

        P2PackedCandidateWorkload.DenseApply,
        P2PackedCandidateWorkload.DenseNoOp,
        P2PackedCandidateWorkload.DenseUndo,
        P2PackedCandidateWorkload.DenseRedo,
        -> {
            IntArray(P2AndroidPackedCandidateProtocol.PIXEL_COUNT) { index -> index }
        }
    }

private object P2PackedCandidateReferenceSink {
    @Volatile
    private var retained: Any? = null

    fun consume(value: Any) {
        retained = value
    }
}
