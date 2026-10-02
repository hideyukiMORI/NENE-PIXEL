package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.known
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.underlay
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryFixtures.workA
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayMemoryTracking
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class UnderlayMemoryProjectionTest {
    private val stored = underlay()
    private val tracking: UnderlayMemoryTracking = known(RememberedUnderlay.of(stored))

    @Test
    fun `the same pending value gives equal tokens`() {
        val moved = stored.withPlacement(5.0, 6.0, 1.0)

        val first = publishToken(tracking, moved)
        val second = publishToken(tracking, stored.withPlacement(5.0, 6.0, 1.0))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `a changed value changes the token`() {
        val first = publishToken(tracking, stored.withPlacement(5.0, 6.0, 1.0))
        val second = publishToken(tracking, stored.withPlacement(5.0, 7.0, 1.0))

        assertNotEquals(first, second)
    }

    @Test
    fun `an equal image of another instance changes the token`() {
        val first = publishToken(tracking, underlay())
        val second = publishToken(tracking, underlay())

        assertNotEquals(first, second)
    }

    @Test
    fun `a departing capture changes the token of the same value`() {
        val moved = stored.withPlacement(5.0, 6.0, 1.0)
        val departing = tracking.copy(departing = tracking.installed(workA, null).departing)

        assertNotEquals(publishToken(tracking, moved), publishToken(departing, moved))
    }

    @Test
    fun `a recall token changes with the installation only`() {
        val first = tracking.installed(workA, stored)
        val second = first.installed(workA, null)

        assertEquals(recallToken(first, null), recallToken(first, stored))
        assertNotEquals(recallToken(first, null), recallToken(second, null))
    }

    @Test
    fun `a recall token differs from a publication token of the same installation`() {
        val recall = UnderlayMemoryToken.recall(1)
        val publication = UnderlayMemoryToken.publication(1, null)

        assertNotEquals(recall, publication)
        assertEquals("UnderlayMemoryToken", publication.toString())
    }

    private fun publishToken(
        tracking: UnderlayMemoryTracking,
        workspace: ReferenceUnderlay?,
    ): UnderlayMemoryToken =
        assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, tracking.projection(workspace)).token

    private fun recallToken(
        tracking: UnderlayMemoryTracking,
        workspace: ReferenceUnderlay?,
    ): UnderlayMemoryToken =
        assertInstanceOf(UnderlayMemoryProjection.RecallPending::class.java, tracking.projection(workspace)).token
}
