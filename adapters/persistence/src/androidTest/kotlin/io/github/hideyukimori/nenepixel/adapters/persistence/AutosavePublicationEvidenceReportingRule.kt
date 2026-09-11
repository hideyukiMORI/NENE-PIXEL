package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.Context
import android.util.Log
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

internal class AutosavePublicationEvidenceReportingRule(
    private val enabled: Boolean,
    private val journal: AutosavePublicationEvidenceJournal,
    private val reporter: (List<String>, Boolean) -> Unit,
    private val prepare: () -> Unit = {},
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                check(enabled) { "Collection requires explicit instrumentation opt-in" }
                var primary: Throwable? = null
                var prepared = false
                try {
                    prepare()
                    prepared = true
                    base.evaluate()
                    check(journal.isFull()) { "Evidence collection completed with missing rows" }
                } catch (failure: Throwable) {
                    primary = failure
                }
                val rows = journal.freeze()
                if (prepared) {
                    try {
                        reporter(rows, primary == null && journal.isFull())
                    } catch (reportFailure: Throwable) {
                        if (primary == null) primary = reportFailure else primary.addSuppressed(reportFailure)
                    }
                }
                primary?.let { throw it }
            }
        }
}

internal fun reserveAutosavePublicationEvidenceOutputs(context: Context): AutosavePublicationEvidenceOutputReservation =
    reserveAutosavePublicationEvidenceOutputs(context.filesDir)

internal fun reserveAutosavePublicationEvidenceOutputs(directory: File): AutosavePublicationEvidenceOutputReservation {
    val csv = File(directory, OUTPUT_FILE_NAME)
    val status = File(directory, STATUS_FILE_NAME)
    check(!csv.exists() && !status.exists()) { "Evidence output already exists" }
    check(csv.createNewFile()) { "Could not reserve evidence CSV" }
    try {
        check(status.createNewFile()) { "Could not reserve evidence status" }
        status.writeText(INVALID_STATUS)
    } catch (failure: Throwable) {
        csv.delete()
        throw failure
    }
    return AutosavePublicationEvidenceOutputReservation(csv, status)
}

internal fun publishAutosaveEvidenceReport(
    reservation: AutosavePublicationEvidenceOutputReservation,
    rows: List<String>,
    complete: Boolean,
) {
    val lines = listOf(HEADER_ROW) + rows
    lines.forEach { line -> Log.i(LOG_TAG, line) }
    reservation.csv.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    reservation.status.writeText(if (complete) COMPLETE_STATUS else INVALID_STATUS)
}

private const val LOG_TAG: String = "nene-p3-autosave-evidence"
private const val OUTPUT_FILE_NAME: String = "m3-autosave-publication-device-v2.csv"
private const val STATUS_FILE_NAME: String = "m3-autosave-publication-device-v2.status"
private const val HEADER_ROW: String = "schema,group,index,kind,elapsed_ns,generation,outcome"
private const val COMPLETE_STATUS: String = "complete"
private const val INVALID_STATUS: String = "invalid"
