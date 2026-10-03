package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRollbackOutcome
import java.io.File

/** Executes the actual pure report/format boundaries; no Android method or measurement is invoked. */
internal object AutosaveLayerPublicationHostContract {
    @JvmStatic
    fun main(arguments: Array<String>) {
        check(arguments.size == 1)
        val root = File(arguments.single())
        check(root.isAbsolute && root.mkdir()) { "A fresh absolute output directory is required" }
        reservations(root)
        rows()
        println("PASS phase reservation, collision, unavailable parent and legacy/layer outcome formatting")
    }

    private fun reservations(root: File) {
        val directory = File(root, "phase")
        val line = "P4_LAYER_PUBLICATION_IDENTITY protocol_id=synthetic"
        val reservation = AutosaveLayerPublicationOutputs.reserve(directory, line)
        check(reservation.csv.isFile && reservation.csv.length() == 0L)
        check(reservation.status.readText() == "invalid")
        check(File(directory, "identity.txt").readText() == "$line\n")
        val expected = checkNotNull(directory.listFiles()).associate { it.name to it.readBytes().toList() }
        rejects { AutosaveLayerPublicationOutputs.reserve(directory, "replacement") }
        check(checkNotNull(directory.listFiles()).associate { it.name to it.readBytes().toList() } == expected)
        val occupied = File(root, "occupied").also { it.writeText("untouched") }
        rejects { AutosaveLayerPublicationOutputs.reserve(occupied, line) }
        check(occupied.readText() == "untouched")
        val missing = File(root, "missing/child")
        rejects { AutosaveLayerPublicationOutputs.reserve(missing, line) }
        check(!missing.exists())
    }

    private fun rows() {
        val generation = (RecoveryGeneration.create(7L) as RecoveryGenerationResult.Created).generation
        val next = (RecoveryGeneration.create(8L) as RecoveryGenerationResult.Created).generation
        val results =
            listOf(
                RecordWriteResult.Written(generation),
                RecordWriteResult.Written(next),
                RecordWriteResult.Failed(RecoveryRetirementFailure.WRITE, RecoveryRollbackOutcome.NOT_NEEDED),
                RecordWriteResult.Uncertain(RecoveryRetirementFailure.WRITE, RecoveryRollbackOutcome.NOT_NEEDED),
            )
        results.forEachIndexed { index, result ->
            val sample = PublicationSample(123L, 7L, result)
            val legacy = AutosavePublicationEvidenceReport.sampleRow("candidate_v3_max", 1, "sample", sample)
            check(
                legacy ==
                    "nene-pixel-p4-indexed-publication-device-v1,candidate,candidate_v3_max,1,sample,123,7,written",
            )
            val layer = AutosavePublicationEvidenceFormat.LAYER.sampleRow("candidate_v3_max", 1, "sample", sample)
            val outcome = if (index == 0) "written" else "failed"
            check(
                layer == "nene-pixel-p4-layer-publication-device-v1,candidate,candidate_v3_max,1,sample,123,7,$outcome",
            )
        }
    }

    private fun rejects(action: () -> Unit) {
        var rejected = false
        try {
            action()
        } catch (_: IllegalStateException) {
            rejected = true
        }
        check(rejected)
    }
}
