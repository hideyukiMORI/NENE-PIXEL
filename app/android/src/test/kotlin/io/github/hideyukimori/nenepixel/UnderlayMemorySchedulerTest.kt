package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.UnderlayMemorySchedulerFixture.Companion.QUIET_MILLIS
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** ADR 0034: the scheduler only waits, and asks the workflow once the projection calls for it. */
internal class UnderlayMemorySchedulerTest {
    @Test
    fun recallPendingRecallsWithoutWaiting() {
        runBlocking {
            val fixture = UnderlayMemorySchedulerFixture()
            val job = fixture.launchIn(this)
            try {
                fixture.installNewWork()

                assertEquals(listOf(RecordedUnderlayCall.Recall), fixture.port.calls)
                assertEquals(1, fixture.recalledCount)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun aPlacedUnderlayIsRememberedOnceTheQuietWindowElapses() {
        runBlocking {
            val fixture = UnderlayMemorySchedulerFixture()
            val job = fixture.launchIn(this)
            try {
                val placed = fixture.place()
                fixture.advanceBy(QUIET_MILLIS - 1L)
                assertEquals(emptyList<RecordedUnderlayCall>(), fixture.port.calls)

                fixture.advanceBy(1L)
                assertEquals(listOf(remembered(placed)), fixture.port.calls)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun changesInsideTheQuietWindowBecomeOneWriteOfTheLastValue() {
        runBlocking {
            val fixture = UnderlayMemorySchedulerFixture()
            val job = fixture.launchIn(this)
            try {
                fixture.place(opacityStep = 0)
                fixture.advanceBy(CHANGE_INTERVAL_MILLIS)
                fixture.place(opacityStep = 1)
                fixture.advanceBy(CHANGE_INTERVAL_MILLIS)
                val last = fixture.place(opacityStep = 2)
                fixture.advanceBy(QUIET_MILLIS - 1L)
                assertEquals(emptyList<RecordedUnderlayCall>(), fixture.port.calls)

                fixture.advanceBy(1L)
                assertEquals(listOf(remembered(last)), fixture.port.calls)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun aFlushWritesWithoutWaitingAndThePendingPublicationWritesNothingMore() {
        runBlocking {
            val fixture = UnderlayMemorySchedulerFixture()
            val job = fixture.launchIn(this)
            try {
                val placed = fixture.place()
                fixture.scheduler.flush()
                fixture.settle()
                assertEquals(listOf(remembered(placed)), fixture.port.calls)

                fixture.advanceBy(QUIET_MILLIS)
                assertEquals(listOf(remembered(placed)), fixture.port.calls)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun aFlushWritesTheValueOfAnUnderlayBeingAdjusted() {
        runBlocking {
            val fixture = UnderlayMemorySchedulerFixture()
            val job = fixture.launchIn(this)
            try {
                val placed = fixture.place()
                fixture.advanceBy(QUIET_MILLIS)
                val adjusting = placed.withPlacement(-1.0, 0.0, 1.0).adjusting()
                fixture.set(adjusting)
                fixture.advanceBy(QUIET_MILLIS)
                assertEquals(listOf(remembered(placed)), fixture.port.calls)

                fixture.scheduler.flush()
                fixture.settle()
                assertEquals(listOf(remembered(placed), remembered(adjusting)), fixture.port.calls)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun aFlushDuringAHeldRecallWaitsForItAndCallsNeverOverlap() {
        runBlocking {
            val fixture = UnderlayMemorySchedulerFixture()
            val gate = CompletableDeferred<Unit>()
            fixture.port.recallGate = gate
            val job = fixture.launchIn(this)
            try {
                fixture.installNewWork()
                val placed = fixture.place()
                fixture.scheduler.flush()
                fixture.settle()
                assertEquals(listOf(RecordedUnderlayCall.Recall), fixture.port.calls)

                gate.complete(Unit)
                fixture.settle()
                assertEquals(listOf(RecordedUnderlayCall.Recall, remembered(placed)), fixture.port.calls)
                assertEquals(1, fixture.port.mostRunningAtOnce)
            } finally {
                gate.complete(Unit)
                job.cancelAndJoin()
            }
        }
    }

    @Test
    fun aFailedWriteIsNotRetriedUntilTheValueChanges() {
        runBlocking {
            val fixture = UnderlayMemorySchedulerFixture()
            fixture.port.failNextRemember = true
            val job = fixture.launchIn(this)
            try {
                val failed = fixture.place()
                fixture.advanceBy(QUIET_MILLIS)
                fixture.advanceBy(QUIET_MILLIS * 2L)
                assertEquals(listOf(remembered(failed)), fixture.port.calls)

                val changed = failed.withOpacity(UnderlayOpacity.MAX)
                fixture.set(changed)
                fixture.advanceBy(QUIET_MILLIS)
                assertEquals(listOf(remembered(failed), remembered(changed)), fixture.port.calls)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    private companion object {
        const val CHANGE_INTERVAL_MILLIS: Long = 300L
    }
}
