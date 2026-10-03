package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.document
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.image
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.imageName
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.recalled
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.stateName
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.underlay
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class AndroidUnderlayMemoryAdapterTest {
    @Test
    fun `an IOException while recalling answers Absent`() {
        val adapter = adapter(ThrowingUnderlayRecordFiles { IOException("read failed") })

        val recollection = runBlocking { adapter.recall(document(1)) }

        assertSame(UnderlayRecollection.Absent, recollection)
    }

    @Test
    fun `an IOException while remembering answers Failed`() {
        val adapter = adapter(ThrowingUnderlayRecordFiles { IOException("write failed") })

        val outcome = runBlocking { adapter.remember(document(1), shownUnderlay()) }

        assertEquals(UnderlayMemoryOutcome.Failed, outcome)
    }

    @Test
    fun `an IOException while forgetting answers Failed`() {
        val adapter = adapter(ThrowingUnderlayRecordFiles { IOException("delete failed") })

        val outcome = runBlocking { adapter.forget(document(1)) }

        assertEquals(UnderlayMemoryOutcome.Failed, outcome)
    }

    @Test
    fun `a SecurityException while recalling answers Absent`() {
        val adapter = adapter(ThrowingUnderlayRecordFiles { SecurityException("read refused") })

        val recollection = runBlocking { adapter.recall(document(1)) }

        assertSame(UnderlayRecollection.Absent, recollection)
    }

    @Test
    fun `running out of memory while recalling answers Absent`() {
        val adapter = adapter(ThrowingUnderlayRecordFiles { OutOfMemoryError("read buffer refused") })

        val recollection = runBlocking { adapter.recall(document(1)) }

        assertSame(UnderlayRecollection.Absent, recollection)
    }

    @Test
    fun `a SecurityException while remembering answers Failed`() {
        val adapter = adapter(ThrowingUnderlayRecordFiles { SecurityException("write refused") })

        val outcome = runBlocking { adapter.remember(document(1), shownUnderlay()) }

        assertEquals(UnderlayMemoryOutcome.Failed, outcome)
    }

    @Test
    fun `a SecurityException while forgetting answers Failed`() {
        val adapter = adapter(ThrowingUnderlayRecordFiles { SecurityException("delete refused") })

        val outcome = runBlocking { adapter.forget(document(1)) }

        assertEquals(UnderlayMemoryOutcome.Failed, outcome)
    }

    @Test
    fun `working files store, recall and forget through the adapter`() {
        val adapter = adapter(InMemoryUnderlayRecordFiles())
        val remembered = shownUnderlay()

        val stored = runBlocking { adapter.remember(document(1), remembered) }
        val recollection = runBlocking { adapter.recall(document(1)) }
        val forgotten = runBlocking { adapter.forget(document(1)) }
        val afterForget = runBlocking { adapter.recall(document(1)) }

        assertEquals(UnderlayMemoryOutcome.Stored, stored)
        val restored = recalled(recollection)
        assertArrayEquals(remembered.image.copyPackedRgba8888(), restored.image.copyPackedRgba8888())
        assertEquals(remembered.placement, restored.placement)
        assertEquals(remembered.visibility, restored.visibility)
        assertEquals(UnderlayMemoryOutcome.Stored, forgotten)
        assertSame(UnderlayRecollection.Absent, afterForget)
    }

    @Test
    fun `a second call starts no file operation while the first one is inside the store`() {
        val files = GatedUnderlayRecordFiles()
        val adapter = AndroidUnderlayMemoryAdapter(UnderlayMemoryStore(files), Dispatchers.IO)

        val operationsWhileHeld =
            runBlocking {
                val first = async(Dispatchers.Default) { adapter.recall(document(1)) }
                assertTrue(files.entered.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                val second = async(Dispatchers.Default) { adapter.forget(document(2)) }
                delay(SECOND_CALL_GRACE_MILLIS)
                val held = files.operations
                files.release()
                first.await()
                second.await()
                held
            }

        assertEquals(listOf("read ${stateName(1)}"), operationsWhileHeld)
        assertEquals(
            listOf("read ${stateName(1)}", "read ${imageName(1)}", "delete ${imageName(2)}", "delete ${stateName(2)}"),
            files.operations,
        )
    }

    private fun adapter(files: UnderlayRecordFiles): AndroidUnderlayMemoryAdapter =
        AndroidUnderlayMemoryAdapter(UnderlayMemoryStore(files), Dispatchers.IO)

    private fun shownUnderlay() = underlay(image(2, 2, 7), 1.5, UnderlayVisibility.Shown)

    private companion object {
        const val LATCH_TIMEOUT_SECONDS: Long = 10L
        const val SECOND_CALL_GRACE_MILLIS: Long = 200L
    }
}
