package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.canvas
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.known
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.recollection
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.underlay
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.workA
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.workB
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** A departing capture is written before the recall of an unknown store (M5r ruling). */
internal class UnderlayMemoryTrackingReinstallTest {
    private val stored = underlay()
    private val moved = stored.withPlacement(5.0, 6.0, 1.0)
    private val tracking: UnderlayMemoryTracking = known(RememberedUnderlay.of(stored))
    private val installed: UnderlayMemoryTracking = tracking.installed(workA, moved)
    private val departed: UnderlayMemoryTracking =
        installed.publicationCompleted(installed.publication(workB, null, UnderlayPublicationMode.Publish))
    private val departingWrite: UnderlayMemoryWrite = UnderlayMemoryWrite.Remember(workA, RememberedUnderlay.of(moved))

    @Test
    fun `an installation with a departing capture publishes before the recall`() {
        listOf(installed, installed.underlayReduced()).forEach { pending ->
            listOf(null, underlay(), underlay().adjusting()).forEach { workspace ->
                assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, pending.projection(workspace))
            }
        }
    }

    @Test
    fun `the first publication writes the departing capture alone`() {
        listOf(installed, installed.underlayReduced()).forEach { pending ->
            listOf(null, underlay(), underlay().adjusting()).forEach { workspace ->
                UnderlayPublicationMode.entries.forEach { mode ->
                    val publication = pending.publication(workB, workspace, mode)

                    assertEquals(listOf(departingWrite), publication.writes)
                    assertNull(publication.current)
                }
            }
        }
    }

    @Test
    fun `an underlay captured while adjusting is written alone`() {
        val adjusting = stored.adjusting().withPlacement(7.0, 8.0, 1.0)
        val pending = tracking.installed(workA, adjusting)

        val writes = pending.publication(workB, null, UnderlayPublicationMode.Publish).writes

        assertEquals(listOf(UnderlayMemoryWrite.Remember(workA, RememberedUnderlay.of(adjusting))), writes)
    }

    @Test
    fun `the departing completion keeps the store unknown and makes the recall pending`() {
        val completed = departed

        assertNull(completed.departing)
        assertEquals(UnderlayStoreKnowledge.Unknown, completed.store)
        assertEquals(installed.installation, completed.installation)
        assertInstanceOf(UnderlayMemoryProjection.RecallPending::class.java, completed.projection(null))
    }

    @Test
    fun `after the departing completion an untouched recall restores`() {
        val completed = departed
        val remembered = underlay().withPlacement(3.0, 4.0, 1.0)

        val step = completed.recallCompleted(completed.installation, recollection(remembered), canvas)

        val restore = assertInstanceOf(UnderlayRecallResolution.Restore::class.java, step.resolution)
        assertEquals(remembered, restore.underlay)
        assertEquals(UnderlayMemoryProjection.Settled, step.tracking.projection(restore.underlay))
    }

    @Test
    fun `after the departing completion a touched recall keeps the workspace`() {
        val touched = installed.underlayReduced()
        val publication = touched.publication(workB, null, UnderlayPublicationMode.Publish)
        val completed = touched.publicationCompleted(publication)

        assertEquals(UnderlayStoreKnowledge.UnknownTouched, completed.store)
        assertInstanceOf(UnderlayMemoryProjection.RecallPending::class.java, completed.projection(null))
        val step = completed.recallCompleted(completed.installation, recollection(underlay()), canvas)
        assertEquals(UnderlayRecallResolution.Keep, step.resolution)
    }

    @Test
    fun `after the departing completion a stale recall is dropped`() {
        val completed = departed

        val step = completed.recallCompleted(tracking.installation, recollection(underlay()), canvas)

        assertEquals(UnderlayRecallResolution.Dropped, step.resolution)
        assertSame(completed, step.tracking)
    }

    @Test
    fun `reinstalling the same work writes its unpublished value before recalling`() {
        val changed = stored.withPlacement(9.0, 2.0, 1.0)
        val reinstalled = tracking.installed(workA, changed)

        val publication = reinstalled.publication(workA, null, UnderlayPublicationMode.Publish)
        val completed = reinstalled.publicationCompleted(publication)

        assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, reinstalled.projection(null))
        val write = UnderlayMemoryWrite.Remember(workA, RememberedUnderlay.of(changed))
        assertEquals(listOf(write), publication.writes)
        assertInstanceOf(UnderlayMemoryProjection.RecallPending::class.java, completed.projection(null))
        val step = completed.recallCompleted(completed.installation, recollection(changed), canvas)
        val restore = assertInstanceOf(UnderlayRecallResolution.Restore::class.java, step.resolution)
        assertEquals(changed, restore.underlay)
    }

    @Test
    fun `an installation without a departing capture makes the recall pending`() {
        val unchanged = tracking.installed(workA, stored)

        assertNull(unchanged.departing)
        assertInstanceOf(UnderlayMemoryProjection.RecallPending::class.java, unchanged.projection(moved))
        val writes = unchanged.publication(workA, moved, UnderlayPublicationMode.Flush).writes
        assertEquals(emptyList<UnderlayMemoryWrite>(), writes)
    }

    @Test
    fun `the departing publication token differs from the following recall token`() {
        val publish = assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, installed.projection(null))
        val completed = departed

        val recall = assertInstanceOf(UnderlayMemoryProjection.RecallPending::class.java, completed.projection(null))

        assertNotEquals(publish.token, recall.token)
    }

    @Test
    fun `the departing publication token ignores the unknown current value`() {
        val first = assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, installed.projection(null))
        val second = assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, installed.projection(moved))

        assertEquals(first.token, second.token)
    }
}
