package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

internal data class P2CandidatePatchMeasurementWork(
    val canvas: P2CanvasShape,
    val workload: P2CandidateNativePatchWorkloadKind,
    val operation: P2CandidatePatchOperationKind,
    val rotationIndex: Int,
)

internal object P2CandidatePatchMeasurementMatrix {
    val sparseCanvases: List<P2CanvasShape> = P2CandidateCanvasMatrix.sparseShapes

    val sparseOperations: List<P2CandidatePatchOperationKind> =
        listOf(
            P2CandidatePatchOperationKind.CreateShuffled,
            P2CandidatePatchOperationKind.CreateInverse,
            P2CandidatePatchOperationKind.ApplyInverse,
            P2CandidatePatchOperationKind.RoundTrip,
            P2CandidatePatchOperationKind.ApplyLateConflict,
        )

    val works: List<P2CandidatePatchMeasurementWork> = denseWorks() + sparseWorks()

    fun validate(descriptors: List<P2CandidatePatchMeasurementDescriptor>) {
        check(descriptors.size == METRIC_COUNT) { "Candidate native patch matrix size changed." }
        val expectedKeys = works.flatMap(::expectedKeys)
        val actualKeys = descriptors.map(::key)
        check(actualKeys.size == actualKeys.toSet().size) {
            "Candidate native patch matrix contained a duplicate."
        }
        check(actualKeys.toSet() == expectedKeys.toSet()) { "Candidate native patch matrix had a missing pair." }
        works.forEach { work -> validateRotation(work, descriptors) }
    }

    private fun denseWorks(): List<P2CandidatePatchMeasurementWork> =
        P2CandidatePatchOperationKind.entries.mapIndexed { operationIndex, operation ->
            P2CandidatePatchMeasurementWork(
                P2CandidateCanvasMatrix.denseAnchor,
                P2CandidateNativePatchWorkloadKind.DenseFullCanvasAnchor,
                operation,
                operationIndex,
            )
        }

    private fun sparseWorks(): List<P2CandidatePatchMeasurementWork> =
        sparseCanvases.flatMapIndexed { shapeIndex, canvas ->
            P2CandidateNativePatchWorkloadKind.SparseRectangular.flatMapIndexed { workloadIndex, workload ->
                sparseOperations.mapIndexed { operationIndex, operation ->
                    val workIndex =
                        ((shapeIndex * WORKLOAD_COUNT) + workloadIndex) * OPERATION_COUNT + operationIndex
                    P2CandidatePatchMeasurementWork(canvas, workload, operation, workIndex)
                }
            }
        }

    private fun expectedKeys(work: P2CandidatePatchMeasurementWork): List<P2CandidatePatchMeasurementKey> =
        P2CandidateConfiguration.entries.map { configuration ->
            P2CandidatePatchMeasurementKey(work.canvas, work.workload, work.operation, configuration)
        }

    private fun key(descriptor: P2CandidatePatchMeasurementDescriptor): P2CandidatePatchMeasurementKey =
        P2CandidatePatchMeasurementKey(
            descriptor.canvas,
            descriptor.workload,
            descriptor.operation,
            descriptor.configuration,
        )

    private fun validateRotation(
        work: P2CandidatePatchMeasurementWork,
        descriptors: List<P2CandidatePatchMeasurementDescriptor>,
    ) {
        val actual =
            descriptors.filter { descriptor ->
                descriptor.canvas == work.canvas &&
                    descriptor.workload == work.workload &&
                    descriptor.operation == work.operation
            }
        val ordered = actual.sortedBy { descriptor -> descriptor.protocol.executionOrder }
        val configurations = P2CandidateConfiguration.entries
        val expected =
            configurations.indices.map { index ->
                configurations[(index + work.rotationIndex) % configurations.size]
            }
        check(ordered.map { descriptor -> descriptor.configuration } == expected) {
            "Candidate native patch configuration rotation changed."
        }
        check(ordered.map { descriptor -> descriptor.protocol.executionOrder } == configurations.indices.toList()) {
            "Candidate native patch execution order changed."
        }
    }

    const val SPARSE_METRIC_COUNT: Int = 1_050
    const val SPARSE_RAW_SAMPLE_COUNT: Int = 10_500
    const val METRIC_COUNT: Int = 1_080
    const val RAW_SAMPLE_COUNT: Int = 10_800
    private const val WORKLOAD_COUNT: Int = 7
    private const val OPERATION_COUNT: Int = 5
}

private data class P2CandidatePatchMeasurementKey(
    val canvas: P2CanvasShape,
    val workload: P2CandidateNativePatchWorkloadKind,
    val operation: P2CandidatePatchOperationKind,
    val configuration: P2CandidateConfiguration,
)
