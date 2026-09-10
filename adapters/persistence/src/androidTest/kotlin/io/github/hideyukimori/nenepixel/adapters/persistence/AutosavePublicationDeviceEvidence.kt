package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.Random

// Bounded device observation defined by docs/quality/M3_AUTOSAVE_PUBLICATION_EVIDENCE.md.
// Collection runs only under the runner argument below, so focused runs and CI skip this class.
@RunWith(AndroidJUnit4::class)
public class AutosavePublicationDeviceEvidence {
    @Test
    public fun collectsBoundedCandidatePublicationLatency() {
        Assume.assumeTrue(COLLECT_VALUE == collectArgument())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.noBackupFilesDir.toPath(), DIRECTORY_PREFIX).toFile()
        try {
            val file = AndroidRecoveryAtomicFileAccess(AtomicFile(File(directory, RECORD_FILE_NAME)))
            report(context, AutosavePublicationEvidenceRun(file).collect())
        } finally {
            directory.listFiles()?.forEach { entry -> entry.delete() }
            directory.delete()
        }
    }

    private fun collectArgument(): String? = InstrumentationRegistry.getArguments().getString(COLLECT_ARGUMENT)

    private fun report(
        context: Context,
        rows: List<String>,
    ) {
        val lines = listOf(HEADER_ROW) + rows
        lines.forEach { line -> Log.i(LOG_TAG, line) }
        File(context.filesDir, OUTPUT_FILE_NAME).writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }
}

private class AutosavePublicationEvidenceRun(
    file: RecoveryAtomicFileAccess,
) {
    private val reader = RecoveryRecordReader(file)
    private val writer = RecoveryRecordWriter(file, reader)
    private var nextGeneration: Long = FIRST_GENERATION

    fun collect(): List<String> =
        measureGroup(MAX_GROUP, maximumDocument()) +
            measureGroup(MIN_GROUP, minimalDocument())

    private fun measureGroup(
        group: String,
        document: DocumentState,
    ): List<String> {
        val warmups = List(WARMUP_COUNT) { index -> row(group, index, WARMUP_KIND, publish(document)) }
        val samples = List(SAMPLE_COUNT) { publish(document) }
        verifyFinalRecord(document)
        val sampleRows = samples.mapIndexed { index, sample -> row(group, index, SAMPLE_KIND, sample) }
        return warmups + sampleRows + summaries(group, samples)
    }

    private fun publish(document: DocumentState): PublicationSample {
        val generation = generation(nextGeneration)
        val start = System.nanoTime()
        val bytes = encoded(RecoveryRecordCodec.encodeCandidate(generation, document))
        val result =
            writer.publish(bytes, generation) { record ->
                isPublishedCandidate(record, generation, document)
            }
        val elapsedNanos = System.nanoTime() - start
        check(result == RecordWriteResult.Written(generation)) { "Publication outcome was not Written: $result" }
        check(elapsedNanos <= SAMPLE_ANOMALY_NANOS) { "Publication sample exceeded the anomaly bound" }
        nextGeneration += 1L
        return PublicationSample(elapsedNanos, generation.value)
    }

    private fun verifyFinalRecord(document: DocumentState) {
        val inspection = reader.inspect()
        check(inspection is InternalInspection.Record) { "Final record was not decodable: $inspection" }
        val record = inspection.record
        check(record is RecoveryRecord.Candidate) { "Final record was not a Candidate" }
        check(record.document == document) { "Final record payload did not decode to the published document" }
    }

    private fun summaries(
        group: String,
        samples: List<PublicationSample>,
    ): List<String> {
        val minimum = samples.withIndex().minBy { entry -> entry.value.elapsedNanos }
        val maximum = samples.withIndex().maxBy { entry -> entry.value.elapsedNanos }
        return listOf(
            row(group, minimum.index, SUMMARY_MIN_KIND, minimum.value),
            row(group, maximum.index, SUMMARY_MAX_KIND, maximum.value),
        )
    }

    private fun row(
        group: String,
        index: Int,
        kind: String,
        sample: PublicationSample,
    ): String = "$SCHEMA,$group,$index,$kind,${sample.elapsedNanos},${sample.generation},$WRITTEN_OUTCOME"

    private fun maximumDocument(): DocumentState {
        val random = Random(PSEUDO_RANDOM_SEED)
        val pixels = IntArray(MAX_CANVAS_EDGE * MAX_CANVAS_EDGE) { random.nextInt() or OPAQUE_ALPHA }
        return document(MAX_DOCUMENT_ID, MAX_CANVAS_EDGE, pixels)
    }

    private fun minimalDocument(): DocumentState =
        document(MIN_DOCUMENT_ID, MIN_CANVAS_EDGE, intArrayOf(MIN_DOCUMENT_PIXEL))

    private fun document(
        id: String,
        edge: Int,
        pixels: IntArray,
    ): DocumentState {
        val size = CanvasSize.create(created(CanvasWidth.create(edge)), created(CanvasHeight.create(edge)))
        val revision = created(Revision.create(DOCUMENT_REVISION))
        val snapshot = created(PixelSnapshot.createPackedRgba8888(size, revision, pixels))
        return DocumentState.create(created(DocumentId.create(id)), snapshot)
    }
}

private data class PublicationSample(
    val elapsedNanos: Long,
    val generation: Long,
)

// Identical to the production read-back predicate in AndroidRecoveryRecordAdapter.publishCandidateRecord.
private fun isPublishedCandidate(
    record: RecoveryRecord,
    generation: RecoveryGeneration,
    document: DocumentState,
): Boolean = record == RecoveryRecord.Candidate(generation, document)

private fun encoded(result: RecoveryEncodeResult): ByteArray =
    when (result) {
        is RecoveryEncodeResult.Encoded -> result.bytes
        RecoveryEncodeResult.Rejected -> error("Candidate evidence encode was rejected")
    }

private fun generation(value: Long): RecoveryGeneration =
    when (val result = RecoveryGeneration.create(value)) {
        is RecoveryGenerationResult.Created -> result.generation
        RecoveryGenerationResult.Rejected -> error("Invalid evidence recovery generation: $value")
    }

private fun <T> created(result: DomainValueResult<T>): T =
    when (result) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> error("Invalid evidence document value: ${result.rejection}")
    }

private const val COLLECT_ARGUMENT: String = "nene.p3.autosaveEvidence"
private const val COLLECT_VALUE: String = "collect"
private const val SCHEMA: String = "nene-pixel-p3-autosave-publication-device-v1"
private const val LOG_TAG: String = "nene-p3-autosave-evidence"
private const val OUTPUT_FILE_NAME: String = "m3-autosave-publication-device-v1.csv"
private const val HEADER_ROW: String = "schema,group,index,kind,elapsed_ns,generation,outcome"
private const val DIRECTORY_PREFIX: String = "autosave-evidence-"
private const val RECORD_FILE_NAME: String = "nene-pixel-recovery-v1"
private const val WARMUP_COUNT: Int = 5
private const val SAMPLE_COUNT: Int = 20
private const val SAMPLE_ANOMALY_NANOS: Long = 5_000_000_000L
private const val FIRST_GENERATION: Long = 1L
private const val MAX_GROUP: String = "candidate_publish_max"
private const val MIN_GROUP: String = "candidate_publish_min"
private const val WARMUP_KIND: String = "warmup"
private const val SAMPLE_KIND: String = "sample"
private const val SUMMARY_MIN_KIND: String = "summary_min"
private const val SUMMARY_MAX_KIND: String = "summary_max"
private const val WRITTEN_OUTCOME: String = "written"
private const val MAX_CANVAS_EDGE: Int = 256
private const val MIN_CANVAS_EDGE: Int = 1
private const val PSEUDO_RANDOM_SEED: Long = 87L
private const val OPAQUE_ALPHA: Int = 0xff
private const val DOCUMENT_REVISION: Long = 1L
private const val MIN_DOCUMENT_PIXEL: Int = 0x336699ff
private const val MAX_DOCUMENT_ID: String = "870000000000000000000000000000aa"
private const val MIN_DOCUMENT_ID: String = "870000000000000000000000000000bb"
