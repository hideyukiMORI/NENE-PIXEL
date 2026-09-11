package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.adapters.persistence.DocumentCreationRequest
import io.github.hideyukimori.nenepixel.adapters.persistence.DocumentOutputFormat
import io.github.hideyukimori.nenepixel.adapters.persistence.ProjectPickerResult
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ProjectPickerBrokerTest {
    @Test
    fun `unclaimed cancellation removes request and next request may start`() =
        runBlocking {
            val broker = ProjectPickerBroker()
            val first = async(start = CoroutineStart.UNDISPATCHED) { broker.createDocument(request()) }
            val old = checkNotNull(broker.pendingRequest.value)
            first.cancelAndJoin()
            assertNull(broker.pendingRequest.value)
            assertFalse(broker.claim(old))
            val second = async(start = CoroutineStart.UNDISPATCHED) { broker.createDocument(request()) }
            assertTrue(broker.claim(checkNotNull(broker.pendingRequest.value)))
            broker.completeCreate(ProjectPickerResult.Cancelled)
            assertEquals(ProjectPickerResult.Cancelled, second.await())
        }

    @Test
    fun `claimed cancellation drains the old result before next create`() =
        runBlocking {
            val broker = ProjectPickerBroker()
            val first = async(start = CoroutineStart.UNDISPATCHED) { broker.createDocument(request()) }
            assertTrue(broker.claim(checkNotNull(broker.pendingRequest.value)))
            first.cancel()
            yield()
            assertFalse(first.isCompleted)
            assertInstanceOf(ProjectPickerResult.Failed::class.java, broker.createDocument(request()))
            broker.completeCreate(ProjectPickerResult.Cancelled)
            first.join()
            val next =
                async(
                    start = CoroutineStart.UNDISPATCHED,
                ) { broker.createDocument(request(DocumentOutputFormat.PROJECT)) }
            assertTrue(broker.claim(checkNotNull(broker.pendingRequest.value)))
            assertFalse(next.isCompleted)
            broker.completeCreate(ProjectPickerResult.Cancelled)
            assertEquals(ProjectPickerResult.Cancelled, next.await())
        }

    @Test
    fun `launch failure drains cancelled claimed request`() =
        runBlocking {
            val broker = ProjectPickerBroker()
            val pending = async(start = CoroutineStart.UNDISPATCHED) { broker.createDocument(request()) }
            val claimed = checkNotNull(broker.pendingRequest.value)
            broker.claim(claimed)
            pending.cancel()
            yield()
            broker.failLaunch(claimed, ProjectStorageFailure.InvalidPickerResult)
            pending.join()
            assertTrue(pending.isCancelled)
            assertNull(broker.pendingRequest.value)
        }

    @Test
    fun `completed result keeps slot until original waiter consumes it`() =
        runBlocking {
            val broker = ProjectPickerBroker()
            val pending = async(start = CoroutineStart.UNDISPATCHED) { broker.createDocument(request()) }
            broker.claim(checkNotNull(broker.pendingRequest.value))
            broker.completeCreate(ProjectPickerResult.Cancelled)
            assertInstanceOf(ProjectPickerResult.Failed::class.java, broker.createDocument(request()))
            pending.await()
            assertNull(broker.pendingRequest.value)
        }

    private fun request(format: DocumentOutputFormat = DocumentOutputFormat.PNG) =
        DocumentCreationRequest("drawing", format)
}
