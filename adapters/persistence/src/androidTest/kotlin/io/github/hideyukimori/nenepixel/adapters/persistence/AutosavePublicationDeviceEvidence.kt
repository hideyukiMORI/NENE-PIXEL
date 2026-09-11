package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.Context
import android.util.AtomicFile
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.Random

@RunWith(AndroidJUnit4::class)
public class AutosavePublicationDeviceEvidence {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val journal: AutosavePublicationEvidenceJournal = AutosavePublicationEvidenceJournal()
    private var outputReservation: AutosavePublicationEvidenceOutputReservation? = null

    @get:Rule
    public val rules: TestRule =
        RuleChain
            .outerRule(
                AutosavePublicationEvidenceReportingRule(
                    enabled = collectArgument() == COLLECT_VALUE,
                    journal = journal,
                    reporter = { rows, complete ->
                        publishAutosaveEvidenceReport(checkNotNull(outputReservation), rows, complete)
                    },
                    prepare = { outputReservation = reserveAutosavePublicationEvidenceOutputs(context) },
                ),
            ).around(Timeout.seconds(OUTER_TIMEOUT_SECONDS))

    @Test
    public fun collectsBoundedCandidatePublicationLatency() {
        check(collectArgument() == COLLECT_VALUE) {
            "Collection requires instrumentation argument $COLLECT_ARGUMENT=$COLLECT_VALUE"
        }
        val directory = Files.createTempDirectory(context.noBackupFilesDir.toPath(), DIRECTORY_PREFIX).toFile()
        try {
            val file = AndroidRecoveryAtomicFileAccess(AtomicFile(File(directory, RECORD_FILE_NAME)))
            AutosavePublicationEvidenceRun(file, journal).collect()
        } finally {
            directory.listFiles()?.forEach { entry -> entry.delete() }
            directory.delete()
        }
    }

    private fun collectArgument(): String? = InstrumentationRegistry.getArguments().getString(COLLECT_ARGUMENT)
}

private class AutosavePublicationEvidenceRun(
    file: RecoveryAtomicFileAccess,
    private val journal: AutosavePublicationEvidenceJournal,
) {
    private val reader = RecoveryRecordReader(file)
    private val writer = RecoveryRecordWriter(file, reader)
    private var nextGeneration: Long = FIRST_GENERATION

    fun collect() {
        measureGroup(MAX_GROUP, maximumDocument())
        measureGroup(MIN_GROUP, minimalDocument())
    }

    private fun measureGroup(
        group: String,
        document: DocumentState,
    ) {
        repeat(WARMUP_COUNT) { index ->
            val sample = publish(document)
            append(group, index, WARMUP_KIND, sample)
            check(sample.elapsedNanos <= SAMPLE_ANOMALY_NANOS) { "Publication sample exceeded the anomaly bound" }
        }
        val samples = ArrayList<PublicationSample>(SAMPLE_COUNT)
        repeat(SAMPLE_COUNT) { index ->
            check(journal.isOpenAndNotInterrupted()) { "Evidence collection closed or interrupted" }
            val sample = publish(document)
            samples += sample
            append(group, index, SAMPLE_KIND, sample)
            check(sample.elapsedNanos <= SAMPLE_ANOMALY_NANOS) { "Publication sample exceeded the anomaly bound" }
        }
        verifyFinalRecord(document)
        val minimum = samples.withIndex().minBy { entry -> entry.value.elapsedNanos }
        val maximum = samples.withIndex().maxBy { entry -> entry.value.elapsedNanos }
        append(group, minimum.index, SUMMARY_MIN_KIND, minimum.value)
        append(group, maximum.index, SUMMARY_MAX_KIND, maximum.value)
    }

    private fun append(
        group: String,
        index: Int,
        kind: String,
        sample: PublicationSample,
    ) {
        check(
            journal.append("$SCHEMA,$group,$index,$kind,${sample.elapsedNanos},${sample.generation},$WRITTEN_OUTCOME"),
        ) {
            "Evidence journal is closed, interrupted, or full"
        }
    }

    private fun publish(document: DocumentState): PublicationSample {
        check(journal.isOpenAndNotInterrupted()) { "Evidence collection closed or interrupted" }
        val generation = generation(nextGeneration)
        val start = System.nanoTime()
        val bytes = encoded(RecoveryRecordCodec.encodeCandidate(generation, document))
        val result = writer.publish(bytes, generation) { record -> isPublishedCandidate(record, generation, document) }
        val elapsedNanos = System.nanoTime() - start
        check(!Thread.currentThread().isInterrupted && journal.isOpenAndNotInterrupted()) {
            "Evidence collection was interrupted during publication"
        }
        check(result == RecordWriteResult.Written(generation)) { "Publication outcome was not Written: $result" }
        nextGeneration += 1L
        return PublicationSample(elapsedNanos, generation.value)
    }

    private fun verifyFinalRecord(document: DocumentState) {
        check(journal.isOpenAndNotInterrupted()) { "Evidence collection closed or interrupted" }
        val inspection = reader.inspect()
        check(inspection is InternalInspection.Record) { "Final record was not decodable: $inspection" }
        val record = inspection.record
        check(record is RecoveryRecord.Candidate) { "Final record was not a Candidate" }
        check(record.document == document) { "Final record payload did not decode to the published document" }
    }

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
private const val SCHEMA: String = "nene-pixel-p3-autosave-publication-device-v2"
private const val DIRECTORY_PREFIX: String = "autosave-evidence-"
private const val RECORD_FILE_NAME: String = "nene-pixel-recovery-v1"
private const val WARMUP_COUNT: Int = 5
private const val SAMPLE_COUNT: Int = 20
internal const val MAX_ROW_COUNT: Int = 54
private const val SAMPLE_ANOMALY_NANOS: Long = 5_000_000_000L
private const val OUTER_TIMEOUT_SECONDS: Long = 60L
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
