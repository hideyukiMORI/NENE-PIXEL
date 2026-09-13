package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyImportPlanner
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyImportResult
import kotlinx.coroutines.runBlocking

private const val WARMUP_COUNT: Int = 5
private const val SAMPLE_COUNT: Int = 20
private const val FIXTURE_COUNT: Int = WARMUP_COUNT + SAMPLE_COUNT
private const val SAMPLE_ANOMALY_NANOS: Long = 1_000_000_000L
private const val CANDIDATE_ROLE: String = "candidate"
private const val MAXIMUM_EDGE: Int = 256
private const val MAXIMUM_PIXEL_COUNT: Int = MAXIMUM_EDGE * MAXIMUM_EDGE

internal fun main(arguments: Array<String>) =
    runBlocking {
        require(arguments.contentEquals(arrayOf(CANDIDATE_ROLE))) {
            "Candidate legacy-import evidence requires sole role argument"
        }
        val fixture = LegacyImportHostEvidenceFixture.create()
        reportMetadata()
        val observations = fixture.groups().flatMap { group -> group.measure() }
        reportSummaries(observations)
    }

internal fun verifyLegacyImportHostEvidenceFixtures() =
    runBlocking {
        LegacyImportHostEvidenceFixture.create().groups().forEach { group -> group.verifyOnly() }
    }

private class LegacyImportHostEvidenceFixture private constructor(
    private val lossless: LegacyRgbaSource,
    private val boundary: LegacyRgbaSource,
    private val maximum: LegacyRgbaSource,
    private val destination: PaletteDefinition,
) {
    suspend fun groups(): List<LegacyImportHostEvidenceGroup<*>> =
        listOf(
            losslessGroup(),
            conversionGroup("classify_257_conversion_required", boundary, 257),
            conversionGroup("classify_65536_conversion_required", maximum, MAXIMUM_PIXEL_COUNT),
            reductionGroup(),
        )

    private suspend fun losslessGroup(): LegacyImportHostEvidenceGroup<PersistenceRequestResult> =
        LegacyImportHostEvidenceGroup(
            "classify_256_lossless",
            List(FIXTURE_COUNT) { LosslessLoadFixture.create(lossless) },
            { verifyLosslessBoundary(lossless) },
        )

    private suspend fun conversionGroup(
        name: String,
        source: LegacyRgbaSource,
        distinctCount: Int,
    ): LegacyImportHostEvidenceGroup<PersistenceRequestResult> =
        LegacyImportHostEvidenceGroup(
            name,
            List(FIXTURE_COUNT) { ConversionLoadFixture.create(source, distinctCount) },
            { verifyConversionBoundary(source, distinctCount) },
        )

    private suspend fun reductionGroup(): LegacyImportHostEvidenceGroup<LegacyReductionRequestResult> =
        LegacyImportHostEvidenceGroup(
            "reduce_65536_to_256",
            List(FIXTURE_COUNT) { ReductionFixture.create(maximum, destination) },
            { verifyReductionBoundary(maximum, destination) },
        )

    companion object {
        fun create(): LegacyImportHostEvidenceFixture =
            LegacyImportHostEvidenceFixture(
                legacySource(256, MAXIMUM_PIXEL_COUNT) { position -> ((position and 0xff) + 1) shl 8 or 0xff },
                legacySource(257, 512) { position -> minOf(position, 256) shl 8 or 0xff },
                legacySource(MAXIMUM_PIXEL_COUNT, MAXIMUM_PIXEL_COUNT) { position -> position shl 8 or 0xff },
                destination(),
            )
    }
}

private interface LegacyImportMeasuredFixture<T> {
    suspend fun execute(): T

    fun verifyFacts(result: T)
}

private class LosslessLoadFixture private constructor(
    private val fixture: Fixture,
    private val source: LegacyRgbaSource,
) : LegacyImportMeasuredFixture<PersistenceRequestResult> {
    override suspend fun execute(): PersistenceRequestResult = fixture.workflow.load()

    override fun verifyFacts(result: PersistenceRequestResult) {
        val completed = result as PersistenceRequestResult.Completed
        check(completed.outcome == PersistenceLastOutcome.Loaded)
        val state = fixture.runtime.state
        check(state.documentState.id == source.id && state.documentState.revision == source.revision)
        check(state.documentState.size == source.size)
        check(state.documentState.definition.palette.entryCount == 256)
        check(state.documentState.definition.defaultIndex == PaletteIndex.first)
        check(state.dirtyState == DocumentDirtyState.Clean && state.historyAvailability == HistoryAvailability.None)
    }

    fun document(): DocumentState = fixture.runtime.state.documentState

    companion object {
        suspend fun create(source: LegacyRgbaSource): LosslessLoadFixture =
            LosslessLoadFixture(configuredFixture(source), source)
    }
}

private class ConversionLoadFixture private constructor(
    private val fixture: Fixture,
    private val source: LegacyRgbaSource,
    private val distinctCount: Int,
    private val initialDocument: DocumentState,
) : LegacyImportMeasuredFixture<PersistenceRequestResult> {
    override suspend fun execute(): PersistenceRequestResult = fixture.workflow.load()

    override fun verifyFacts(result: PersistenceRequestResult) {
        val operation = (result as PersistenceRequestResult.LegacyConversionRequired).operation
        val phase = fixture.workflow.operation.value.phase as PersistenceOperationPhase.LegacyConversionRequired
        check(phase.import.operation == operation)
        check(phase.import.source.size == source.size && phase.import.distinctColorCount == distinctCount)
        check(phase.import.reduction == null)
        check(fixture.runtime.state.documentState === initialDocument)
    }

    fun sourcePreview(): LegacySourcePreview =
        (fixture.workflow.operation.value.phase as PersistenceOperationPhase.LegacyConversionRequired).import.source

    companion object {
        suspend fun create(
            source: LegacyRgbaSource,
            distinctCount: Int,
        ): ConversionLoadFixture {
            val fixture = configuredFixture(source)
            return ConversionLoadFixture(fixture, source, distinctCount, fixture.runtime.state.documentState)
        }
    }
}

private data class AdmittedLegacyOperation(
    val handle: PersistenceOperationHandle,
    val source: LegacySourcePreview,
)

private class ReductionFixture private constructor(
    private val fixture: Fixture,
    private val source: LegacyRgbaSource,
    private val destination: PaletteDefinition,
    private val admitted: AdmittedLegacyOperation,
) : LegacyImportMeasuredFixture<LegacyReductionRequestResult> {
    override suspend fun execute(): LegacyReductionRequestResult =
        fixture.workflow.legacyImport.previewSource(admitted.handle, destination)

    override fun verifyFacts(result: LegacyReductionRequestResult) {
        val handle = (result as LegacyReductionRequestResult.Ready).handle
        val phase = fixture.workflow.operation.value.phase as PersistenceOperationPhase.LegacyConversionRequired
        val reduction = checkNotNull(phase.import.reduction)
        check(phase.import.operation == admitted.handle && phase.import.source === admitted.source)
        check(phase.import.selectedDestination === destination)
        check(handle.operation == admitted.handle && handle.destinationEpoch == 1L)
        check(reduction.handle == handle && reduction.size == source.size)
        check(reduction.definition === destination && destination.defaultIndex.value == 255)
    }

    fun reduction(): LegacyReductionProjection =
        checkNotNull(
            (fixture.workflow.operation.value.phase as PersistenceOperationPhase.LegacyConversionRequired)
                .import
                .reduction,
        )

    companion object {
        suspend fun create(
            source: LegacyRgbaSource,
            destination: PaletteDefinition,
        ): ReductionFixture {
            val fixture = configuredFixture(source)
            val result = fixture.workflow.load() as PersistenceRequestResult.LegacyConversionRequired
            val phase = fixture.workflow.operation.value.phase as PersistenceOperationPhase.LegacyConversionRequired
            return ReductionFixture(
                fixture,
                source,
                destination,
                AdmittedLegacyOperation(result.operation, phase.import.source),
            )
        }
    }
}

private class LegacyImportHostEvidenceGroup<T>(
    private val name: String,
    private val fixtures: List<LegacyImportMeasuredFixture<T>>,
    private val verifyBoundary: suspend () -> Unit,
) {
    suspend fun verifyOnly() {
        verifyBoundary()
    }

    suspend fun measure(): List<LegacyImportHostObservation> {
        verifyBoundary()
        fixtures.take(WARMUP_COUNT).forEach { fixture -> fixture.verifyFacts(fixture.execute()) }
        val measured = fixtures.drop(WARMUP_COUNT)
        val observations = measured.mapIndexed { index, fixture -> measureSample(index, fixture) }
        verifyBoundary()
        return observations
    }

    private suspend fun measureSample(
        index: Int,
        fixture: LegacyImportMeasuredFixture<T>,
    ): LegacyImportHostObservation {
        val start = System.nanoTime()
        val result = fixture.execute()
        val elapsed = System.nanoTime() - start
        val observation = LegacyImportHostObservation(name, index, elapsed)
        println(LegacyImportHostEvidenceReport.sampleRow(observation))
        System.out.flush()
        fixture.verifyFacts(result)
        check(elapsed <= SAMPLE_ANOMALY_NANOS) { "$name sample $index exceeded one second" }
        return observation
    }
}

internal object LegacyImportHostEvidenceReport {
    const val SCHEMA: String = "nene-pixel-p4-legacy-import-host-v1"
    val GROUPS: List<String> =
        listOf(
            "classify_256_lossless",
            "classify_257_conversion_required",
            "classify_65536_conversion_required",
            "reduce_65536_to_256",
        )

    fun sampleRow(observation: LegacyImportHostObservation): String =
        "${observation.group},${observation.sampleIndex},${observation.latencyNanos}"

    fun parseSampleRow(row: String): LegacyImportHostObservation {
        val fields = row.split(',')
        require(fields.size == 3 && fields[0] in GROUPS)
        val sample = fields[1].toInt()
        val latency = fields[2].toLong()
        require(sample in 0 until SAMPLE_COUNT && latency >= 0L)
        return LegacyImportHostObservation(fields[0], sample, latency)
    }
}

internal data class LegacyImportHostObservation(
    val group: String,
    val sampleIndex: Int,
    val latencyNanos: Long,
)

private suspend fun configuredFixture(source: LegacyRgbaSource): Fixture {
    val fixture = Fixture()
    fixture.storage.loadHandler = { ProjectLoadOutcome.Loaded(DocumentImportSource.Legacy(source)) }
    fixture.initialize()
    return fixture
}

private suspend fun verifyLosslessBoundary(source: LegacyRgbaSource) {
    check(source.copyPackedRgba8888().none { packed -> packed == 0 })
    val fixture = LosslessLoadFixture.create(source)
    fixture.verifyFacts(fixture.execute())
    val document = (LegacyImportPlanner.classify(source) as LegacyImportResult.Lossless).document
    check(fixture.document() == document)
    verifyDocumentPixels(source, document)
}

private suspend fun verifyConversionBoundary(
    source: LegacyRgbaSource,
    distinctCount: Int,
) {
    val fixture = ConversionLoadFixture.create(source, distinctCount)
    fixture.verifyFacts(fixture.execute())
    val classified = LegacyImportPlanner.classify(source) as LegacyImportResult.ConversionRequired
    check(classified.source === source && classified.distinctColorCount == distinctCount)
    verifySourcePixels(fixture.sourcePreview(), source)
}

private suspend fun verifyReductionBoundary(
    source: LegacyRgbaSource,
    destination: PaletteDefinition,
) {
    val fixture = ReductionFixture.create(source, destination)
    fixture.verifyFacts(fixture.execute())
    val candidate = LegacyImportPlanner.classify(source) as LegacyImportResult.ConversionRequired
    val expected = LegacyImportPlanner.reduce(candidate, destination)
    val actual = fixture.reduction()
    check(actual.definition == expected.definition && actual.size == expected.snapshot.size)
    verifyReductionPixels(actual, expected)
}

private fun verifyDocumentPixels(
    source: LegacyRgbaSource,
    document: DocumentState,
) {
    val packed = source.copyPackedRgba8888()
    val indices = document.snapshot.copyPackedIndices()
    val colors =
        document.definition.palette
            .entries()
            .map { entry -> entry.color.toPackedRgba8888() }
    check(IntArray(indices.size) { index -> colors[indices[index].toInt() and 0xff] }.contentEquals(packed))
}

private fun verifySourcePixels(
    actual: LegacySourcePreview,
    expected: LegacyRgbaSource,
) {
    val packed = expected.copyPackedRgba8888()
    packed.indices.forEach { index ->
        val color = PixelColor.fromPackedRgba8888(packed[index])
        check(
            actual.colorAt(position(index % MAXIMUM_EDGE, index / MAXIMUM_EDGE)) ==
                LegacyPreviewColorResult.Color(color),
        )
    }
}

private fun verifyReductionPixels(
    actual: LegacyReductionProjection,
    expected: io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyReductionPreview,
) {
    val indices = expected.snapshot.copyPackedIndices()
    val colors =
        expected.definition.palette
            .entries()
            .map { entry -> entry.color }
    indices.indices.forEach { index ->
        val expectedColor = colors[indices[index].toInt() and 0xff]
        val actualColor = actual.colorAt(position(index % MAXIMUM_EDGE, index / MAXIMUM_EDGE))
        check(actualColor == LegacyPreviewColorResult.Color(expectedColor))
    }
}

private fun legacySource(
    distinctCount: Int,
    pixelCount: Int,
    valueAt: (Int) -> Int,
): LegacyRgbaSource {
    val height = pixelCount / MAXIMUM_EDGE
    val values = IntArray(pixelCount, valueAt)
    check(values.toSet().size == distinctCount)
    return required(
        LegacyRgbaSource.createPackedRgba8888(
            documentId('e'),
            revision(41L),
            canvas(MAXIMUM_EDGE, height),
            values,
        ),
    )
}

private fun destination(): PaletteDefinition {
    val colors = List(256) { index -> PixelColor.fromPackedRgba8888(index shl 8 or 0xfe) }
    return required(PaletteDefinition.create(required(Palette.create(colors)), required(PaletteIndex.create(255))))
}

private fun <T> required(result: DomainValueResult<T>): T =
    when (result) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> error("Invalid legacy-import evidence fixture: ${result.rejection}")
    }

private fun reportMetadata() {
    println("schema,${LegacyImportHostEvidenceReport.SCHEMA}")
    println("role,$CANDIDATE_ROLE")
    println("java_version,${System.getProperty("java.version")}")
    println("java_vm,${System.getProperty("java.vm.name")}")
    println("os,${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
    println("warmups,$WARMUP_COUNT")
    println("samples_per_group,$SAMPLE_COUNT")
    println("group,sample,latency_nanos")
    System.out.flush()
}

private fun reportSummaries(observations: List<LegacyImportHostObservation>) {
    observations.groupBy(LegacyImportHostObservation::group).forEach { (group, rows) ->
        println("summary_min,$group,${rows.minOf(LegacyImportHostObservation::latencyNanos)}")
        println("summary_max,$group,${rows.maxOf(LegacyImportHostObservation::latencyNanos)}")
    }
    System.out.flush()
}
