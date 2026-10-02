package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.canvas
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.recollection
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.underlay
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.workA
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UnderlayMemoryTrackingRecallTest {
    private val fresh: UnderlayMemoryTracking = UnderlayMemoryTracking.initial().installed(workA, null)

    @Test
    fun `a fresh process starts settled with nothing known`() {
        val tracking = UnderlayMemoryTracking.initial()

        assertEquals(UnderlayMemoryProjection.Settled, tracking.projection(null))
        assertEquals(UnderlayStoreKnowledge.Known(null), tracking.store)
        assertEquals(0L, tracking.installation)
    }

    @Test
    fun `an installation makes the recall pending whatever the workspace holds`() {
        assertInstanceOf(UnderlayMemoryProjection.RecallPending::class.java, fresh.projection(null))
        assertInstanceOf(UnderlayMemoryProjection.RecallPending::class.java, fresh.projection(underlay()))
        assertEquals(UnderlayStoreKnowledge.Unknown, fresh.store)
        assertEquals(1L, fresh.installation)
    }

    @Test
    fun `nothing is published while the store is unknown`() {
        val touched = fresh.underlayReduced()

        listOf(fresh, touched).forEach { tracking ->
            listOf(null, underlay(), underlay().adjusting()).forEach { workspace ->
                UnderlayPublicationMode.entries.forEach { mode ->
                    assertTrue(tracking.publication(workA, workspace, mode).writes.isEmpty())
                }
            }
        }
    }

    @Test
    fun `an untouched recall restores the value and then settles`() {
        val original = underlay().withPlacement(3.0, 4.0, 1.0)

        val step = fresh.recallCompleted(1, recollection(original), canvas)

        val restore = assertInstanceOf(UnderlayRecallResolution.Restore::class.java, step.resolution)
        assertEquals(original, restore.underlay)
        assertEquals(UnderlayInteraction.Resting, restore.underlay.interaction)
        assertEquals(UnderlayMemoryProjection.Settled, step.tracking.projection(restore.underlay))
        val flushed = step.tracking.publication(workA, restore.underlay, UnderlayPublicationMode.Flush)
        assertTrue(flushed.writes.isEmpty())
    }

    @Test
    fun `a value clamped by the restore is not published again`() {
        val wide = ApplicationTestValues.canvas(256, 256)
        val far = ReferenceUnderlay.placed(UnderlayMemoryFixtures.image(), wide).withPlacement(200.0, 200.0, 1.0)
        val remembered = RememberedUnderlay.of(far)

        val step = fresh.recallCompleted(1, UnderlayRecollection.Remembered(remembered), canvas)

        val restore = assertInstanceOf(UnderlayRecallResolution.Restore::class.java, step.resolution)
        assertNotEquals(remembered, RememberedUnderlay.of(restore.underlay))
        assertEquals(UnderlayMemoryProjection.Settled, step.tracking.projection(restore.underlay))
    }

    @Test
    fun `an image chosen before the recall is kept and published`() {
        val chosen = underlay()
        val touched = fresh.underlayReduced()

        val step = touched.recallCompleted(1, recollection(underlay()), canvas)

        assertEquals(UnderlayRecallResolution.Keep, step.resolution)
        assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, step.tracking.projection(chosen))
        assertEquals(
            listOf(UnderlayMemoryWrite.Remember(workA, RememberedUnderlay.of(chosen))),
            step.tracking.publication(workA, chosen, UnderlayPublicationMode.Publish).writes,
        )
    }

    @Test
    fun `choose and remove before a remembered recall publishes a forget`() {
        val touched = fresh.underlayReduced()

        val step = touched.recallCompleted(1, recollection(underlay()), canvas)

        assertEquals(UnderlayRecallResolution.Keep, step.resolution)
        assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, step.tracking.projection(null))
        assertEquals(
            listOf(UnderlayMemoryWrite.Forget(workA)),
            step.tracking.publication(workA, null, UnderlayPublicationMode.Publish).writes,
        )
    }

    @Test
    fun `choose and remove before an absent recall settles`() {
        val step = fresh.underlayReduced().recallCompleted(1, UnderlayRecollection.Absent, canvas)

        assertEquals(UnderlayRecallResolution.Keep, step.resolution)
        assertEquals(UnderlayMemoryProjection.Settled, step.tracking.projection(null))
    }

    @Test
    fun `a recall for another installation is dropped`() {
        val step = fresh.recallCompleted(0, recollection(underlay()), canvas)

        assertEquals(UnderlayRecallResolution.Dropped, step.resolution)
        assertSame(fresh, step.tracking)
    }

    @Test
    fun `a second completion for a known store is dropped`() {
        val known = fresh.recallCompleted(1, UnderlayRecollection.Absent, canvas).tracking

        val step = known.recallCompleted(1, recollection(underlay()), canvas)

        assertEquals(UnderlayRecallResolution.Dropped, step.resolution)
        assertSame(known, step.tracking)
    }

    @Test
    fun `two installations in a row drop the first recall and keep the second pending`() {
        val second = fresh.installed(workA, null)
        val first = second.recallCompleted(1, recollection(underlay()), canvas)

        assertEquals(UnderlayRecallResolution.Dropped, first.resolution)
        assertInstanceOf(UnderlayMemoryProjection.RecallPending::class.java, second.projection(null))
        val step = second.recallCompleted(2, recollection(underlay()), canvas)
        assertInstanceOf(UnderlayRecallResolution.Restore::class.java, step.resolution)
    }
}
