package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

internal data class P2CandidateRawPathMeasurementWork(
    val operation: P2CandidateRawPathOperationKind,
    val canvas: P2CanvasShape,
    val repeatFactor: Int,
    val rotationIndex: Int,
) {
    val pixelCount: Int
        get() = canvas.pixelCount.toInt()

    val pathPositions: Int
        get() = Math.multiplyExact(pixelCount, repeatFactor)

    val duplicatePathPositions: Int
        get() = if (operation == P2CandidateRawPathOperationKind.DuplicateChanged) pathPositions - pixelCount else 0

    val changeCount: Int
        get() = if (operation.isNoOp()) 0 else pixelCount
}

internal object P2CandidateRawPathMeasurementMatrix {
    val duplicateFactors: List<Int> = listOf(1, 2, 4, 8)

    val duplicateWorks: List<P2CandidateRawPathMeasurementWork> =
        P2CandidateCanvasMatrix.shapes.flatMapIndexed { shapeIndex, canvas ->
            duplicateFactors.mapIndexed { factorIndex, factor ->
                P2CandidateRawPathMeasurementWork(
                    operation = P2CandidateRawPathOperationKind.DuplicateChanged,
                    canvas = canvas,
                    repeatFactor = factor,
                    rotationIndex = shapeIndex * duplicateFactors.size + factorIndex,
                )
            }
        }

    val works: List<P2CandidateRawPathMeasurementWork> = duplicateWorks + legacyWorks()

    fun validate(descriptors: List<P2CandidateRawPathMeasurementDescriptor>) {
        check(descriptors.size == METRIC_COUNT) { "Candidate raw-path matrix size changed." }
        val expectedKeys = works.flatMap(::expectedKeys)
        val actualKeys = descriptors.map(::key)
        check(actualKeys.size == actualKeys.toSet().size) {
            "Candidate raw-path matrix contained a duplicate."
        }
        check(actualKeys.toSet() == expectedKeys.toSet()) { "Candidate raw-path matrix had a missing pair." }
        works.forEach { work -> validateRotation(work, descriptors) }
    }

    private fun legacyWorks(): List<P2CandidateRawPathMeasurementWork> =
        P2CandidateRawPathOperationKind.entries.drop(1).map { operation ->
            P2CandidateRawPathMeasurementWork(
                operation = operation,
                canvas = P2CandidateCanvasMatrix.denseAnchor,
                repeatFactor = 1,
                rotationIndex = operation.ordinal,
            )
        }

    private fun expectedKeys(work: P2CandidateRawPathMeasurementWork): List<P2CandidateRawPathMeasurementKey> =
        P2CandidateConfiguration.entries.map { configuration -> work.key(configuration) }

    private fun key(descriptor: P2CandidateRawPathMeasurementDescriptor): P2CandidateRawPathMeasurementKey =
        P2CandidateRawPathMeasurementKey(
            operation = descriptor.operation,
            canvas = descriptor.canvas,
            counts = descriptor.counts(),
            configuration = descriptor.configuration,
        )

    private fun P2CandidateRawPathMeasurementWork.key(
        configuration: P2CandidateConfiguration,
    ): P2CandidateRawPathMeasurementKey =
        P2CandidateRawPathMeasurementKey(
            operation = operation,
            canvas = canvas,
            counts = counts(),
            configuration = configuration,
        )

    private fun P2CandidateRawPathMeasurementWork.counts(): P2CandidateRawPathCounts =
        P2CandidateRawPathCounts(
            pathPositions = pathPositions,
            uniquePathPositions = pixelCount,
            duplicatePathPositions = duplicatePathPositions,
            changeCount = changeCount,
        )

    private fun P2CandidateRawPathMeasurementDescriptor.counts(): P2CandidateRawPathCounts =
        P2CandidateRawPathCounts(
            pathPositions = pathPositions,
            uniquePathPositions = uniquePathPositions,
            duplicatePathPositions = duplicatePathPositions,
            changeCount = changeCount,
        )

    private fun validateRotation(
        work: P2CandidateRawPathMeasurementWork,
        descriptors: List<P2CandidateRawPathMeasurementDescriptor>,
    ) {
        val workKey = work.key(P2CandidateConfiguration.entries.first()).copy(configuration = null)
        val actual = descriptors.filter { descriptor -> key(descriptor).copy(configuration = null) == workKey }
        val ordered = actual.sortedBy { descriptor -> descriptor.protocol.executionOrder }
        val configurations = P2CandidateConfiguration.entries
        val expected =
            configurations.indices.map { index ->
                configurations[(index + work.rotationIndex) % configurations.size]
            }
        check(ordered.map { descriptor -> descriptor.configuration } == expected) {
            "Candidate raw-path configuration rotation changed."
        }
        check(ordered.map { descriptor -> descriptor.protocol.executionOrder } == configurations.indices.toList()) {
            "Candidate raw-path execution order changed."
        }
    }

    const val DUPLICATE_METRIC_COUNT: Int = 140
    const val DUPLICATE_RAW_SAMPLE_COUNT: Int = 1_400
    const val ADDED_METRIC_COUNT: Int = 135
    const val ADDED_RAW_SAMPLE_COUNT: Int = 1_350
    const val METRIC_COUNT: Int = 155
    const val RAW_SAMPLE_COUNT: Int = 1_550
}

private data class P2CandidateRawPathCounts(
    val pathPositions: Int,
    val uniquePathPositions: Int,
    val duplicatePathPositions: Int,
    val changeCount: Int,
)

private data class P2CandidateRawPathMeasurementKey(
    val operation: P2CandidateRawPathOperationKind,
    val canvas: P2CanvasShape,
    val counts: P2CandidateRawPathCounts,
    val configuration: P2CandidateConfiguration?,
)
