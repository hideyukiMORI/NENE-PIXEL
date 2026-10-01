package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class FakeReferenceImagePortTest {
    @Test
    fun `default handler cancels and counts the call`() {
        val port = FakeReferenceImagePort()

        val outcome = runBlocking { port.pick() }

        assertSame(ReferenceImageOutcome.Cancelled, outcome)
        assertEquals(1, port.calls)
    }

    @Test
    fun `handler decides the picked image`() {
        val image = image()
        val port = FakeReferenceImagePort()
        port.handler = { ReferenceImageOutcome.Picked(image) }

        val outcome = runBlocking { port.pick() }

        assertEquals(ReferenceImageOutcome.Picked(image), outcome)
        assertSame(image, (outcome as ReferenceImageOutcome.Picked).image)
    }

    @Test
    fun `handler decides rejection and every pick is counted`() {
        val port = FakeReferenceImagePort()
        port.handler = { ReferenceImageOutcome.Rejected(ReferenceImageSourceRejection.TooManyPixels) }

        val outcomes = runBlocking { listOf(port.pick(), port.pick()) }

        val expected = ReferenceImageOutcome.Rejected(ReferenceImageSourceRejection.TooManyPixels)
        assertEquals(listOf(expected, expected), outcomes)
        assertEquals(2, port.calls)
    }

    private fun image(): ReferenceImage {
        val result = ReferenceImage.create(2, 2, IntArray(4))
        check(result is ReferenceImageResult.Created) { "expected Created, got $result" }
        return result.image
    }
}
