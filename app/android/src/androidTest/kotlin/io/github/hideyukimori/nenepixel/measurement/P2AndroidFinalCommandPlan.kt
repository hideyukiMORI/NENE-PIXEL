package io.github.hideyukimori.nenepixel.measurement

internal data class P2AndroidFinalCommandPlan(
    val identity: Identity,
    val workload: Workload,
    val output: Output,
) {
    data class Identity(
        val candidateId: String,
        val runIndex: Int,
    )

    data class Workload(
        val canvasWidth: Int,
        val canvasHeight: Int,
        val warmupIterations: Int,
        val samplesPerWorkload: Int,
        val schema: String,
    )

    data class Output(
        val outputIdentity: String,
        val relativePath: String,
        val publicationPolicy: PublicationPolicy,
    )

    enum class PublicationPolicy {
        OverwriteExisting,
        FailIfExists,
    }

    val candidateId: String
        get() = identity.candidateId

    val runIndex: Int
        get() = identity.runIndex

    val canvasWidth: Int
        get() = workload.canvasWidth

    val canvasHeight: Int
        get() = workload.canvasHeight

    val warmupIterations: Int
        get() = workload.warmupIterations

    val samplesPerWorkload: Int
        get() = workload.samplesPerWorkload

    val outputIdentity: String
        get() = output.outputIdentity

    val outputRelativePath: String
        get() = output.relativePath

    val publicationPolicy: PublicationPolicy
        get() = output.publicationPolicy

    val schema: String
        get() = workload.schema

    val specs: List<P2CommandWorkloadSpec>
        get() = P2CommandWorkloadCatalog.shapeSpecs(canvasWidth, canvasHeight)

    val workloadNames: List<String>
        get() = specs.map { spec -> spec.kind.metricName }

    val workloadCount: Int
        get() = specs.size

    val totalSampleCount: Int
        get() = workloadCount * samplesPerWorkload

    val checkpointCount: Int
        get() =
            2 +
                totalSampleCount / P2AndroidPhysicalCheckpointPolicy.CHECKPOINT_INTERVAL

    val canvasMetadata: String
        get() = "${canvasWidth}x$canvasHeight"

    val fullCanvasRegion: P2CommandRegionDescriptor
        get() = P2CommandRegionDescriptor(0, 0, canvasWidth, canvasHeight)

    val sparseRegion: P2CommandRegionDescriptor
        get() {
            val edge = minOf(canvasWidth, canvasHeight)
            return P2CommandRegionDescriptor(0, 0, edge, edge)
        }
}
