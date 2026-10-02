package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacement
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacementResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FakeUnderlayMemoryPortTest {
    private val first = document("a".repeat(DOCUMENT_ID_LENGTH))
    private val second = document("b".repeat(DOCUMENT_ID_LENGTH))

    @Test
    fun `an unknown work is absent and a remembered underlay comes back as the same instance`() {
        val port = FakeUnderlayMemoryPort()
        val underlay = underlay()

        val before = runBlocking { port.recall(first) }
        val outcome = runBlocking { port.remember(first, underlay) }
        val after = runBlocking { port.recall(first) }

        assertSame(UnderlayRecollection.Absent, before)
        assertSame(UnderlayMemoryOutcome.Stored, outcome)
        assertSame(underlay, (after as UnderlayRecollection.Remembered).underlay)
        assertSame(UnderlayRecollection.Absent, runBlocking { port.recall(second) })
    }

    @Test
    fun `forget removes only that work and a missing record is stored`() {
        val port = FakeUnderlayMemoryPort()
        val kept = underlay()
        port.seed(first, underlay())
        port.seed(second, kept)

        val outcomes = runBlocking { listOf(port.forget(first), port.forget(first)) }

        assertEquals(listOf(UnderlayMemoryOutcome.Stored, UnderlayMemoryOutcome.Stored), outcomes)
        assertNull(port.stored(first))
        assertSame(kept, port.stored(second))
    }

    @Test
    fun `failNextWrite fails one write without changing the store`() {
        val port = FakeUnderlayMemoryPort()
        val seeded = underlay()
        port.seed(first, seeded)
        port.failNextWrite = true

        val failed = runBlocking { port.remember(first, underlay()) }
        assertSame(seeded, port.stored(first))
        port.failNextWrite = true
        val failedForget = runBlocking { port.forget(first) }
        val stored = runBlocking { port.forget(first) }

        assertSame(UnderlayMemoryOutcome.Failed, failed)
        assertSame(UnderlayMemoryOutcome.Failed, failedForget)
        assertSame(UnderlayMemoryOutcome.Stored, stored)
        assertFalse(port.failNextWrite)
        assertNull(port.stored(first))
    }

    @Test
    fun `calls are recorded in order and an earlier snapshot does not change`() {
        val port = FakeUnderlayMemoryPort()

        runBlocking { port.recall(first) }
        val snapshot = port.calls
        runBlocking {
            port.remember(second, underlay())
            port.forget(first)
        }

        assertEquals(listOf(FakeUnderlayMemoryCall.Recall(first)), snapshot)
        val expected =
            listOf(
                FakeUnderlayMemoryCall.Recall(first),
                FakeUnderlayMemoryCall.Remember(second),
                FakeUnderlayMemoryCall.Forget(first),
            )
        assertEquals(expected, port.calls)
    }

    @Test
    fun `a held recall waits for release and then reads the store`() {
        val port = FakeUnderlayMemoryPort()
        val underlay = underlay()
        val gate = port.holdNextRecall()

        runBlocking {
            val pending = async { port.recall(first) }
            gate.awaitStarted()
            assertTrue(gate.hasStarted)
            assertFalse(pending.isCompleted)
            assertEquals(listOf(FakeUnderlayMemoryCall.Recall(first)), port.calls)
            port.seed(first, underlay)
            gate.release()
            val answer = pending.await()
            assertSame(underlay, (answer as UnderlayRecollection.Remembered).underlay)
        }
        assertEquals(UnderlayRecollection.Remembered(underlay), runBlocking { port.recall(first) })
    }

    private fun underlay(): RememberedUnderlay {
        val image = ReferenceImage.create(2, 2, IntArray(PIXELS))
        check(image is ReferenceImageResult.Created) { "expected Created, got $image" }
        val placement = RememberedPlacement.create(0.0, 0.0, 1.0)
        check(placement is RememberedPlacementResult.Created) { "expected Created, got $placement" }
        return RememberedUnderlay.create(
            image.image,
            placement.placement,
            UnderlayOpacity.create(OPACITY),
            UnderlayVisibility.Shown,
        )
    }

    private fun document(value: String): DocumentId {
        val result = DocumentId.create(value)
        check(result is DomainValueResult.Created) { "expected Created, got $result" }
        return result.value
    }

    private companion object {
        const val DOCUMENT_ID_LENGTH: Int = 32
        const val PIXELS: Int = 4
        const val OPACITY: Int = 128
    }
}
