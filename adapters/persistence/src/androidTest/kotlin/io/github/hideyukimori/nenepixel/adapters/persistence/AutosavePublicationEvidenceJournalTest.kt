package io.github.hideyukimori.nenepixel.adapters.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

public class AutosavePublicationEvidenceJournalTest {
    @Test
    public fun journalKeepsOrderAndRejectsRowsAfterFreeze() {
        val journal = AutosavePublicationEvidenceJournal(maxRows = 2)
        assertTrue(journal.append("first"))
        assertTrue(journal.append("second"))
        assertFalse(journal.append("full"))
        assertEquals(listOf("first", "second"), journal.freeze())
        assertFalse(journal.append("after-freeze"))
        assertEquals(listOf("first", "second"), journal.snapshot())
    }

    @Test
    public fun reportingRulePublishesCompleteRowsInOrder() {
        val journal = AutosavePublicationEvidenceJournal(maxRows = 2)
        val reports = ArrayList<Pair<List<String>, Boolean>>()
        val statement =
            LambdaStatement {
                check(journal.append("one"))
                check(journal.append("two"))
            }
        apply(
            AutosavePublicationEvidenceReportingRule(
                enabled = true,
                journal = journal,
                reporter = { rows, complete -> reports += rows to complete },
            ),
            statement,
        ).evaluate()
        assertEquals(listOf("one", "two"), reports.single().first)
        assertTrue(reports.single().second)
    }

    @Test
    public fun reportingRulePreservesPrimaryFailureWhenReportFails() {
        val journal = AutosavePublicationEvidenceJournal(maxRows = 2)
        val primary = IllegalStateException("primary")
        val reportFailure = IllegalArgumentException("report")
        val statement =
            LambdaStatement {
                check(journal.append("prefix"))
                throw primary
            }
        try {
            apply(
                AutosavePublicationEvidenceReportingRule(
                    enabled = true,
                    journal = journal,
                    reporter = { _, _ -> throw reportFailure },
                ),
                statement,
            ).evaluate()
        } catch (failure: Throwable) {
            assertSame(primary, failure)
            assertEquals(listOf(reportFailure), failure.suppressed.toList())
            return
        }
        error("Expected primary failure")
    }

    @Test
    public fun timeoutFreezesPrefixAndDoesNotStartFollowOnOperation() {
        val journal = AutosavePublicationEvidenceJournal(maxRows = 2)
        val reports = ArrayList<List<String>>()
        val started = CountDownLatch(1)
        val statement =
            LambdaStatement {
                check(journal.append("prefix"))
                started.countDown()
                Thread.sleep(1_000L)
                check(journal.append("late"))
            }
        var timeout: Throwable? = null
        try {
            apply(
                AutosavePublicationEvidenceReportingRule(
                    enabled = true,
                    journal = journal,
                    reporter = { rows, _ -> reports += rows },
                ),
                Timeout.millis(150L).apply(statement, Description.createTestDescription(javaClass, "timeout")),
            ).evaluate()
        } catch (failure: Throwable) {
            timeout = failure
        }
        assertTrue(started.count == 0L)
        assertEquals("TestTimedOutException", timeout?.javaClass?.simpleName)
        assertEquals(listOf("prefix"), reports.single())
        assertFalse(journal.append("after-timeout"))
    }

    @Test
    public fun disabledCollectionDoesNotReportOrObserveRows() {
        val journal = AutosavePublicationEvidenceJournal()
        val reports = AtomicInteger()
        val baseCalls = AtomicInteger()
        val statement = LambdaStatement { baseCalls.incrementAndGet() }
        try {
            apply(
                AutosavePublicationEvidenceReportingRule(
                    enabled = false,
                    journal = journal,
                    reporter = { _, _ -> reports.incrementAndGet() },
                ),
                statement,
            ).evaluate()
        } catch (_: IllegalStateException) {
            // Explicit rejection is expected.
        }
        assertEquals(0, baseCalls.get())
        assertEquals(0, reports.get())
        assertTrue(journal.snapshot().isEmpty())
    }

    @Test
    public fun injectedFailureReportsPrefixAsInvalid() {
        val journal = AutosavePublicationEvidenceJournal(maxRows = 2)
        val reports = ArrayList<Pair<List<String>, Boolean>>()
        val primary = IllegalStateException("injected")
        try {
            apply(
                AutosavePublicationEvidenceReportingRule(
                    enabled = true,
                    journal = journal,
                    reporter = { rows, complete ->
                        reports +=
                            rows to complete
                    },
                ),
                LambdaStatement {
                    check(journal.append("prefix"))
                    throw primary
                },
            ).evaluate()
        } catch (failure: Throwable) {
            assertSame(primary, failure)
        }
        assertEquals(listOf("prefix"), reports.single().first)
        assertFalse(reports.single().second)
    }

    @Test
    public fun prepareFailureRejectsBeforeBaseAndPreservesExistingOutputs() {
        val journal = AutosavePublicationEvidenceJournal()
        val baseCalls = AtomicInteger()
        val reports = AtomicInteger()
        try {
            apply(
                AutosavePublicationEvidenceReportingRule(
                    enabled = true,
                    journal = journal,
                    reporter = { _, _ -> reports.incrementAndGet() },
                    prepare = { error("output already exists") },
                ),
                LambdaStatement { baseCalls.incrementAndGet() },
            ).evaluate()
        } catch (_: IllegalStateException) {
            // Preflight rejection is expected.
        }
        assertEquals(0, baseCalls.get())
        assertEquals(0, reports.get())
    }

    @Test
    public fun existingOutputReservationFailsWithoutChangingEitherFile() {
        val directory =
            java.nio.file.Files
                .createTempDirectory("autosave-existing-")
                .toFile()
        val csv = java.io.File(directory, "m3-autosave-publication-device-v2.csv")
        val status = java.io.File(directory, "m3-autosave-publication-device-v2.status")
        csv.writeText("old-csv")
        status.writeText("old-status")
        try {
            var rejected = false
            try {
                reserveAutosavePublicationEvidenceOutputs(directory)
            } catch (_: IllegalStateException) {
                // Existing output rejection is expected.
                rejected = true
            }
            assertTrue(rejected)
            assertEquals("old-csv", csv.readText())
            assertEquals("old-status", status.readText())
        } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }

    @Test
    public fun successfulBaseWithMissingRowsFailsAndReportsInvalid() {
        val journal = AutosavePublicationEvidenceJournal(maxRows = 2)
        val reports = ArrayList<Pair<List<String>, Boolean>>()
        try {
            apply(
                AutosavePublicationEvidenceReportingRule(
                    true,
                    journal,
                    { rows, complete -> reports += rows to complete },
                ),
                LambdaStatement {},
            ).evaluate()
        } catch (failure: Throwable) {
            assertTrue(failure.message.orEmpty().contains("missing rows"))
        }
        assertEquals(emptyList<String>(), reports.single().first)
        assertFalse(reports.single().second)
    }

    @Test
    public fun reservedFilesStartInvalidAndCompletePublishWritesBothFiles() {
        val directory =
            java.nio.file.Files
                .createTempDirectory("autosave-contract-")
                .toFile()
        try {
            val reservation = reserveAutosavePublicationEvidenceOutputs(directory)
            assertEquals(INVALID_STATUS_TEXT, reservation.status.readText())
            publishAutosaveEvidenceReport(reservation, listOf("row"), complete = true)
            assertTrue(reservation.csv.readText().contains("row"))
            assertEquals(COMPLETE_STATUS_TEXT, reservation.status.readText())
        } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }

    private class LambdaStatement(
        private val body: () -> Unit,
    ) : Statement() {
        override fun evaluate() = body()
    }

    private companion object {
        const val INVALID_STATUS_TEXT: String = "invalid"
        const val COMPLETE_STATUS_TEXT: String = "complete"
    }

    private fun apply(
        rule: org.junit.rules.TestRule,
        statement: Statement,
    ): Statement = rule.apply(statement, Description.createTestDescription(javaClass, "synthetic"))
}
