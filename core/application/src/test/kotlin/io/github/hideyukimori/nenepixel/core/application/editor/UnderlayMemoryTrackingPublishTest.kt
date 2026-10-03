package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.known
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.underlay
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.workA
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryProjection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UnderlayMemoryTrackingPublishTest {
    private val stored = underlay()
    private val tracking: UnderlayMemoryTracking = known(RememberedUnderlay.of(stored))

    @Test
    fun `an unchanged workspace settles and writes nothing`() {
        assertEquals(UnderlayMemoryProjection.Settled, tracking.projection(stored))
        assertTrue(tracking.publication(workA, stored, UnderlayPublicationMode.Flush).writes.isEmpty())
    }

    @Test
    fun `a resting change is published`() {
        val moved = stored.withPlacement(5.0, 6.0, 1.0)

        assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, tracking.projection(moved))
        assertEquals(
            listOf(UnderlayMemoryWrite.Remember(workA, RememberedUnderlay.of(moved))),
            tracking.publication(workA, moved, UnderlayPublicationMode.Publish).writes,
        )
    }

    @Test
    fun `nothing is requested while adjusting and one request follows the rest`() {
        val adjusting = stored.adjusting().withPlacement(7.0, 8.0, 1.0)

        assertEquals(UnderlayMemoryProjection.Settled, tracking.projection(adjusting))
        assertTrue(tracking.publication(workA, adjusting, UnderlayPublicationMode.Publish).writes.isEmpty())
        val rested = adjusting.rested()
        assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, tracking.projection(rested))
        assertEquals(1, tracking.publication(workA, rested, UnderlayPublicationMode.Publish).writes.size)
    }

    @Test
    fun `a flush writes the value of an underlay being adjusted`() {
        val adjusting = stored.adjusting().withPlacement(7.0, 8.0, 1.0)

        val publication = tracking.publication(workA, adjusting, UnderlayPublicationMode.Flush)

        assertEquals(listOf(UnderlayMemoryWrite.Remember(workA, RememberedUnderlay.of(adjusting))), publication.writes)
    }

    @Test
    fun `removing the underlay publishes a forget`() {
        assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, tracking.projection(null))
        assertEquals(
            listOf(UnderlayMemoryWrite.Forget(workA)),
            tracking.publication(workA, null, UnderlayPublicationMode.Publish).writes,
        )
    }

    @Test
    fun `a completed publication settles`() {
        val moved = stored.withOpacity(UnderlayOpacity.create(200))
        val publication = tracking.publication(workA, moved, UnderlayPublicationMode.Publish)

        val completed = tracking.publicationCompleted(publication)

        assertEquals(UnderlayStoreKnowledge.Known(RememberedUnderlay.of(moved)), completed.store)
        assertEquals(UnderlayMemoryProjection.Settled, completed.projection(moved))
    }

    @Test
    fun `a failed value is not requested again until the underlay changes`() {
        val moved = stored.withPlacement(5.0, 6.0, 1.0)
        val publication = tracking.publication(workA, moved, UnderlayPublicationMode.Publish)
        val completed = tracking.publicationCompleted(publication)

        assertEquals(UnderlayMemoryProjection.Settled, completed.projection(moved))
        assertTrue(completed.publication(workA, moved, UnderlayPublicationMode.Flush).writes.isEmpty())
        val again = moved.withPlacement(1.0, 2.0, 1.0)
        assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, completed.projection(again))
    }

    @Test
    fun `a change during the write stays pending after the completion`() {
        val moved = stored.withPlacement(5.0, 6.0, 1.0)
        val publication = tracking.publication(workA, moved, UnderlayPublicationMode.Publish)
        val later = moved.withPlacement(9.0, 9.0, 1.0)

        val completed = tracking.publicationCompleted(publication)

        assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, completed.projection(later))
    }

    @Test
    fun `a completion after another installation leaves the store unknown`() {
        val moved = stored.withPlacement(5.0, 6.0, 1.0)
        val publication = tracking.publication(workA, moved, UnderlayPublicationMode.Publish)
        val installed = tracking.installed(workA, moved)

        val completed = installed.publicationCompleted(publication)

        assertEquals(UnderlayStoreKnowledge.Unknown, completed.store)
        assertEquals(installed.installation, completed.installation)
    }
}
