package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.canvas
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.known
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.underlay
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.workA
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.workB
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.workC
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class UnderlayMemoryTrackingDepartingTest {
    private val stored = underlay()
    private val moved = stored.withPlacement(5.0, 6.0, 1.0)
    private val tracking: UnderlayMemoryTracking = known(RememberedUnderlay.of(stored))

    @Test
    fun `an installation captures the unpublished underlay of the departing work`() {
        val installed = tracking.installed(workA, moved)

        assertEquals(DepartingUnderlay(workA, RememberedUnderlay.of(moved)), installed.departing)
        assertEquals(UnderlayStoreKnowledge.Unknown, installed.store)
        assertEquals(tracking.installation + 1, installed.installation)
    }

    @Test
    fun `a published underlay is not captured`() {
        assertNull(tracking.installed(workA, stored).departing)
    }

    @Test
    fun `an underlay being adjusted is captured with its current value`() {
        val adjusting = stored.adjusting().withPlacement(7.0, 8.0, 1.0)

        val installed = tracking.installed(workA, adjusting)

        assertEquals(DepartingUnderlay(workA, RememberedUnderlay.of(adjusting)), installed.departing)
    }

    @Test
    fun `an underlay touched while unknown is captured and a removed one is a forget`() {
        val touched = tracking.installed(workA, stored).underlayReduced()

        val installed = touched.installed(workB, null)

        assertEquals(DepartingUnderlay(workB, null), installed.departing)
        assertEquals(UnderlayMemoryWrite.Forget(workB), installed.departing?.write())
    }

    @Test
    fun `the departing capture is written before the current value`() {
        val installed = tracking.installed(workA, moved)
        val recalled = installed.recallCompleted(installed.installation, UnderlayRecollection.Absent, canvas).tracking
        val chosen = underlay()

        val writes = recalled.publication(workB, chosen, UnderlayPublicationMode.Publish).writes

        assertEquals(
            listOf(
                UnderlayMemoryWrite.Remember(workA, RememberedUnderlay.of(moved)),
                UnderlayMemoryWrite.Remember(workB, RememberedUnderlay.of(chosen)),
            ),
            writes,
        )
    }

    @Test
    fun `a departing capture alone keeps the publication pending and its completion keeps the store`() {
        val installed = tracking.installed(workA, moved)
        val recalled = installed.recallCompleted(installed.installation, UnderlayRecollection.Absent, canvas).tracking

        assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, recalled.projection(null))
        val publication = recalled.publication(workB, null, UnderlayPublicationMode.Publish)
        val completed = recalled.publicationCompleted(publication)

        assertNull(completed.departing)
        assertEquals(recalled.store, completed.store)
        assertEquals(UnderlayMemoryProjection.Settled, completed.projection(null))
    }

    @Test
    fun `the departing capture survives an untouched installation`() {
        val installed = tracking.installed(workA, moved)

        val again = installed.installed(workB, null)

        assertSame(installed.departing, again.departing)
    }

    @Test
    fun `a completion after another installation clears the departing capture it wrote`() {
        val installed = tracking.installed(workA, moved)
        val recalled = installed.recallCompleted(installed.installation, UnderlayRecollection.Absent, canvas).tracking
        val publication = recalled.publication(workB, null, UnderlayPublicationMode.Publish)
        val next = recalled.installed(workB, null)

        val completed = next.publicationCompleted(publication)

        assertNull(completed.departing)
        assertEquals(UnderlayStoreKnowledge.Unknown, completed.store)
    }

    @Test
    fun `a newer departing capture replaces the older one and outlives its completion`() {
        val installed = tracking.installed(workA, moved)
        val recalled = installed.recallCompleted(installed.installation, UnderlayRecollection.Absent, canvas).tracking
        val publication = recalled.publication(workB, null, UnderlayPublicationMode.Publish)
        val chosen = underlay()
        val next = recalled.installed(workB, chosen)

        val completed = next.publicationCompleted(publication)

        assertEquals(DepartingUnderlay(workB, RememberedUnderlay.of(chosen)), completed.departing)
        assertSame(completed.departing, completed.installed(workC, null).departing)
    }
}
