package io.github.hideyukimori.nenepixel.measurement

import io.github.hideyukimori.nenepixel.core.application.persistence.PartialOutputCleanup
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import java.io.File

/** Pure journal/report checks only; no Android URI, grant or storage method is executed. */
internal object P4LayerSafHostContract {
    @JvmStatic
    fun main(arguments: Array<String>) {
        check(arguments.size == 1)
        val root = File(arguments.single())
        check(root.isAbsolute && root.mkdir())
        val rows = completedJournal()
        refusedJournalTransitions()
        failureRows()
        partialOutput(root)
        completeOutput(root, rows)
        println("PASS five SAF host contract groups; all fixture files retained")
    }

    private fun completedJournal(): List<String> {
        val journal = P4LayerSafJournal()
        repeat(25) { check(journal.append(sample(it))) }
        check(!journal.append(sample(25)))
        journal.summarize()
        check(journal.isComplete())
        val rows = journal.freeze()
        check(rows.size == 27)
        check(rows[25] == rows[5].replace(",sample,", ",summary_min,"))
        check(rows[26] == rows[5].replace(",sample,", ",summary_max,"))
        check(!journal.isOpen() && !journal.append(sample(25)))
        rejects { journal.summarize() }
        return rows
    }

    private fun refusedJournalTransitions() {
        val journal = P4LayerSafJournal()
        check(!journal.append(sample(1)))
        check(journal.append(sample(0)))
        check(!journal.append(sample(1).copy(destination = sample(0).destination)))
        rejects { journal.summarize() }
        Thread.currentThread().interrupt()
        try {
            check(!journal.isOpen() && !journal.append(sample(1)))
        } finally {
            check(Thread.interrupted())
        }
        check(journal.freeze().size == 1)
        check(!journal.append(sample(1)))
    }

    private fun failureRows() {
        val cancelled = sample(0).copy(outcome = P4LayerSafOutcome(ProjectSaveOutcome.Cancelled, false))
        check(!cancelled.accepted() && cancelled.row().endsWith(",0,not_verified,false,cancelled,not_needed"))
        val failure = ProjectSaveOutcome.Failed(ProjectStorageFailure.InvalidProject, PartialOutputCleanup.DELETED)
        val failed = sample(0).copy(outcome = P4LayerSafOutcome(failure, true))
        check(!failed.accepted() && failed.row().endsWith(",0,not_verified,true,failed,deleted"))
        check(sample(0).copy(elapsedNanos = 5_000_000_000L).accepted())
        check(!sample(0).copy(elapsedNanos = 5_000_000_001L).accepted())
        check(!sample(0).copy(outcome = P4LayerSafOutcome(ProjectSaveOutcome.Saved, false)).accepted())
    }

    private fun partialOutput(root: File) {
        val prefix = "p4-layer-saf-aaaaaaaaaaaa"
        val output = P4LayerSafOutput.reserve(root, prefix)
        output.recordSetup("source", 0, sample(0).destination)
        output.recordSetup("warmup", 0, sample(1).destination)
        rejects { output.publishIdentity("too-early") }
        rejects { output.report(emptyList(), true) }
        output.report(emptyList(), false)
        val directory = File(root, prefix)
        check(File(directory, "save.status").readText() == "invalid")
        check(File(directory, "identity.txt").length() == 0L)
        check(File(directory, "setup.csv").readLines().size == 3)
        val files = checkNotNull(directory.listFiles()).associate { it.name to it.readBytes().toList() }
        rejects { P4LayerSafOutput.reserve(root, prefix) }
        rejects { output.report(emptyList(), false) }
        rejects { output.recordSetup("warmup", 1, sample(2).destination) }
        check(checkNotNull(directory.listFiles()).associate { it.name to it.readBytes().toList() } == files)
    }

    private fun completeOutput(
        root: File,
        rows: List<String>,
    ) {
        val prefix = "p4-layer-saf-bbbbbbbbbbbb"
        val output = P4LayerSafOutput.reserve(root, prefix)
        output.recordSetup("source", 0, sample(100).destination)
        repeat(25) {
            output.recordSetup(if (it < 5) "warmup" else "sample", if (it < 5) it else it - 5, sample(it).destination)
        }
        output.publishIdentity("complete-synthetic-identity")
        rejects { output.publishIdentity("second-identity") }
        output.report(rows, true)
        check(File(root, "$prefix/save.status").readText() == "complete")
        check(File(root, "$prefix/save.csv").readLines() == listOf(P4LayerSafSample.HEADER) + rows)
    }

    private fun sample(ordinal: Int): P4LayerSafSample =
        P4LayerSafSample(
            ordinal,
            if (ordinal < 5) 9_000L else 1_000L,
            P4LayerSafDestination("content://synthetic/destination-$ordinal", 10001, 10002),
            P4LayerSafOutcome(ProjectSaveOutcome.Saved, true),
        )

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
