package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspectionFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRollbackOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

internal class AndroidRecoveryRecordAdapterTest {
    @Test
    fun `missing lineage publishes and verifies real retired generation one`() =
        runBlocking {
            val file = MemoryRecoveryAtomicFile()
            val adapter = adapter(file)

            val result = adapter.retire(ExpectedRecoveryLineage.Missing)

            assertEquals(
                RecoveryRetirementOutcome.Retired(PersistenceTestValues.generation(1L)),
                result,
            )
            assertEquals(1, file.startCalls)
            assertEquals(1, file.syncCalls)
            assertEquals(1, file.finishCalls)
            assertEquals(0, file.failCalls)
            assertArrayEquals(encodedRetired(1L), file.bytes)
        }

    @Test
    fun `inspect decodes candidate and retired records`() =
        runBlocking {
            val candidate = encodedCandidate(7L)
            val file = MemoryRecoveryAtomicFile(candidate)
            val adapter = adapter(file)

            assertEquals(
                RecoveryInspection.Candidate(
                    PersistenceTestValues.generation(7L),
                    PersistenceTestValues.minimalDocument,
                ),
                adapter.inspect(),
            )
            file.bytes = encodedRetired(8L)
            assertEquals(
                RecoveryInspection.Retired(PersistenceTestValues.generation(8L)),
                adapter.inspect(),
            )
        }

    @Test
    fun `stale exhaustion and corrupt inspection never start a write`() =
        runBlocking {
            val staleFile = MemoryRecoveryAtomicFile(encodedCandidate(2L))
            val stale = adapter(staleFile).retire(ExpectedRecoveryLineage.Present(PersistenceTestValues.generation(1L)))
            assertSame(RecoveryRetirementOutcome.Stale, stale)
            assertEquals(0, staleFile.startCalls)

            val exhaustedFile = MemoryRecoveryAtomicFile(encodedRetired(Long.MAX_VALUE))
            val exhausted =
                adapter(exhaustedFile).retire(
                    ExpectedRecoveryLineage.Present(PersistenceTestValues.generation(Long.MAX_VALUE)),
                )
            assertSame(RecoveryRetirementOutcome.GenerationExhausted, exhausted)
            assertEquals(0, exhaustedFile.startCalls)

            val corruptFile = MemoryRecoveryAtomicFile(byteArrayOf(1, 2, 3))
            val corrupt = adapter(corruptFile).retire(ExpectedRecoveryLineage.Missing)
            assertEquals(
                RecoveryRetirementOutcome.Failed(
                    RecoveryRetirementFailure.CURRENT_RECORD_INSPECTION,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                ),
                corrupt,
            )
            assertEquals(0, corruptFile.startCalls)
        }

    @Test
    fun `pre-finish failures roll back and failed rollback is uncertain`() =
        runBlocking {
            val startFile = MemoryRecoveryAtomicFile(fault = RecoveryFault.START)
            assertEquals(
                RecoveryRetirementOutcome.Failed(
                    RecoveryRetirementFailure.START_WRITE,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                ),
                adapter(startFile).retire(ExpectedRecoveryLineage.Missing),
            )
            assertEquals(0, startFile.failCalls)

            val writeFile = MemoryRecoveryAtomicFile(fault = RecoveryFault.WRITE)
            assertEquals(
                RecoveryRetirementOutcome.Failed(
                    RecoveryRetirementFailure.WRITE,
                    RecoveryRollbackOutcome.COMPLETED,
                ),
                adapter(writeFile).retire(ExpectedRecoveryLineage.Missing),
            )
            assertEquals(1, writeFile.failCalls)

            val syncFile = MemoryRecoveryAtomicFile(fault = RecoveryFault.SYNC_AND_FAIL)
            assertEquals(
                RecoveryRetirementOutcome.Uncertain(
                    RecoveryRetirementFailure.SYNC,
                    RecoveryRollbackOutcome.FAILED,
                ),
                adapter(syncFile).retire(ExpectedRecoveryLineage.Missing),
            )
            assertEquals(1, syncFile.failCalls)
        }

    @Test
    fun `finish and post-finish failures are uncertain without rollback claim`() =
        runBlocking {
            val finishFile = MemoryRecoveryAtomicFile(fault = RecoveryFault.FINISH)
            assertEquals(
                RecoveryRetirementOutcome.Uncertain(
                    RecoveryRetirementFailure.FINISH,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                ),
                adapter(finishFile).retire(ExpectedRecoveryLineage.Missing),
            )
            assertEquals(0, finishFile.failCalls)

            val readFile = MemoryRecoveryAtomicFile(fault = RecoveryFault.READ_BACK)
            assertEquals(
                RecoveryRetirementOutcome.Uncertain(
                    RecoveryRetirementFailure.READ_BACK,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                ),
                adapter(readFile).retire(ExpectedRecoveryLineage.Missing),
            )
            assertEquals(0, readFile.failCalls)

            val mismatchFile = MemoryRecoveryAtomicFile(fault = RecoveryFault.READ_BACK_MISMATCH)
            assertEquals(
                RecoveryRetirementOutcome.Uncertain(
                    RecoveryRetirementFailure.READ_BACK_MISMATCH,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                ),
                adapter(mismatchFile).retire(ExpectedRecoveryLineage.Missing),
            )
            assertEquals(0, mismatchFile.failCalls)
        }

    @Test
    fun `missing read-back record is uncertain without rollback claim`() =
        runBlocking {
            val missingFile = MemoryRecoveryAtomicFile(fault = RecoveryFault.READ_BACK_MISSING)

            assertEquals(
                RecoveryRetirementOutcome.Uncertain(
                    RecoveryRetirementFailure.READ_BACK,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                ),
                adapter(missingFile).retire(ExpectedRecoveryLineage.Missing),
            )
            assertEquals(0, missingFile.failCalls)
        }

    @Test
    fun `write cancellation rolls back before rethrow`() {
        val file = MemoryRecoveryAtomicFile(fault = RecoveryFault.WRITE_CANCELLATION)

        assertThrows(CancellationException::class.java) {
            runBlocking { adapter(file).retire(ExpectedRecoveryLineage.Missing) }
        }
        assertEquals(1, file.failCalls)
    }

    @Test
    fun `finish cancellation rolls back before rethrow`() {
        val file = MemoryRecoveryAtomicFile(fault = RecoveryFault.FINISH_CANCELLATION)

        assertThrows(CancellationException::class.java) {
            runBlocking { adapter(file).retire(ExpectedRecoveryLineage.Missing) }
        }
        assertEquals(1, file.failCalls)
    }

    @Test
    fun `unreadable record open is a read failure`() =
        runBlocking {
            val file = MemoryRecoveryAtomicFile(encodedRetired(1L), fault = RecoveryFault.OPEN_READ)

            assertEquals(
                RecoveryInspection.Failed(RecoveryInspectionFailure.READ_FAILED),
                adapter(file).inspect(),
            )
            assertEquals(1, file.openReadCalls)
        }

    @Test
    fun `read cancellation closes the record stream before rethrow`() {
        val file = MemoryRecoveryAtomicFile(encodedRetired(1L), fault = RecoveryFault.READ_CANCELLATION)

        assertThrows(CancellationException::class.java) {
            runBlocking { adapter(file).inspect() }
        }
        assertEquals(1, file.readCloseCalls)
    }

    @Test
    fun `all adapter instances share one process writer`() =
        runBlocking {
            val writeEntered = CountDownLatch(1)
            val releaseWrite = CountDownLatch(1)
            val file = MemoryRecoveryAtomicFile(writeEntered = writeEntered, releaseWrite = releaseWrite)
            val first = async(Dispatchers.Default) { adapter(file).retire(ExpectedRecoveryLineage.Missing) }
            writeEntered.await()
            val secondStarted = CountDownLatch(1)
            val second =
                async(Dispatchers.Default) {
                    secondStarted.countDown()
                    adapter(file).retire(ExpectedRecoveryLineage.Missing)
                }
            secondStarted.await()
            releaseWrite.countDown()

            assertTrue(first.await() is RecoveryRetirementOutcome.Retired)
            assertSame(RecoveryRetirementOutcome.Stale, second.await())
            assertEquals(1, file.startCalls)
            assertEquals(1, file.maximumConcurrentAccess)
        }

    @Test
    fun `inspection distinguishes resource read and close failures`() =
        runBlocking {
            val oversized = MemoryRecoveryAtomicFile(ByteArray(RecoveryRecordCodec.MAX_PROBE_BYTE_COUNT))
            assertEquals(
                RecoveryInspection.Failed(RecoveryInspectionFailure.RESOURCE_LIMIT_EXCEEDED),
                adapter(oversized).inspect(),
            )
            assertEquals(1, oversized.openReadCalls)

            val closeFailed = MemoryRecoveryAtomicFile(encodedRetired(1L), fault = RecoveryFault.CLOSE_READ)
            assertEquals(
                RecoveryInspection.Failed(RecoveryInspectionFailure.CLOSE_FAILED),
                adapter(closeFailed).inspect(),
            )
        }

    @Test
    fun `missing lineage publishes and verifies a real candidate generation one`() =
        runBlocking {
            val file = MemoryRecoveryAtomicFile()
            val adapter = adapter(file)
            val document = PersistenceTestValues.minimalDocument

            val result = adapter.publishCandidate(ExpectedRecoveryLineage.Missing, document)

            assertEquals(
                RecoveryPublicationOutcome.Published(PersistenceTestValues.generation(1L)),
                result,
            )
            assertEquals(1, file.startCalls)
            assertEquals(1, file.syncCalls)
            assertEquals(1, file.finishCalls)
            assertEquals(0, file.failCalls)
            assertArrayEquals(encodedCandidate(1L), file.bytes)
            assertEquals(
                RecoveryInspection.Candidate(PersistenceTestValues.generation(1L), document),
                adapter.inspect(),
            )
        }

    @Test
    fun `candidate publication on a mismatched lineage is stale without a write`() =
        runBlocking {
            val file = MemoryRecoveryAtomicFile(encodedCandidate(2L))

            val result =
                adapter(file).publishCandidate(
                    ExpectedRecoveryLineage.Present(PersistenceTestValues.generation(1L)),
                    PersistenceTestValues.minimalDocument,
                )

            assertSame(RecoveryPublicationOutcome.Stale, result)
            assertEquals(0, file.startCalls)
        }

    @Test
    fun `candidate publication rolls back a failed write`() =
        runBlocking {
            val file = MemoryRecoveryAtomicFile(fault = RecoveryFault.WRITE)

            val result =
                adapter(file).publishCandidate(
                    ExpectedRecoveryLineage.Missing,
                    PersistenceTestValues.minimalDocument,
                )

            assertEquals(
                RecoveryPublicationOutcome.Failed(
                    RecoveryRetirementFailure.WRITE,
                    RecoveryRollbackOutcome.COMPLETED,
                ),
                result,
            )
            assertEquals(1, file.failCalls)
        }

    @Test
    fun `candidate read-back mismatch after finish is uncertain without rollback claim`() =
        runBlocking {
            val file = MemoryRecoveryAtomicFile(fault = RecoveryFault.READ_BACK_MISMATCH)

            val result =
                adapter(file).publishCandidate(
                    ExpectedRecoveryLineage.Missing,
                    PersistenceTestValues.minimalDocument,
                )

            assertEquals(
                RecoveryPublicationOutcome.Uncertain(
                    RecoveryRetirementFailure.READ_BACK_MISMATCH,
                    RecoveryRollbackOutcome.NOT_NEEDED,
                ),
                result,
            )
            assertEquals(0, file.failCalls)
        }

    @Test
    fun `maximum document publishes and reads back as the same candidate`() =
        runBlocking {
            val file = MemoryRecoveryAtomicFile()
            val adapter = adapter(file)
            val document = PersistenceTestValues.maximumDocument()

            val result = adapter.publishCandidate(ExpectedRecoveryLineage.Missing, document)

            assertEquals(
                RecoveryPublicationOutcome.Published(PersistenceTestValues.generation(1L)),
                result,
            )
            assertEquals(RecoveryRecordCodec.MAX_RECORD_BYTE_COUNT, file.bytes?.size)
            assertEquals(
                RecoveryInspection.Candidate(PersistenceTestValues.generation(1L), document),
                adapter.inspect(),
            )
        }

    @Test
    fun `candidate publication at the maximum generation is exhausted without a write`() =
        runBlocking {
            val file = MemoryRecoveryAtomicFile(encodedRetired(Long.MAX_VALUE))

            val result =
                adapter(file).publishCandidate(
                    ExpectedRecoveryLineage.Present(PersistenceTestValues.generation(Long.MAX_VALUE)),
                    PersistenceTestValues.minimalDocument,
                )

            assertSame(RecoveryPublicationOutcome.GenerationExhausted, result)
            assertEquals(0, file.startCalls)
        }

    private fun adapter(file: RecoveryAtomicFileAccess) =
        AndroidRecoveryRecordAdapter.create(file, Dispatchers.Unconfined)

    private fun encodedRetired(generation: Long): ByteArray =
        (
            RecoveryRecordCodec.encodeRetired(
                PersistenceTestValues.generation(generation),
            ) as RecoveryEncodeResult.Encoded
        ).bytes

    private fun encodedCandidate(generation: Long): ByteArray =
        (
            RecoveryRecordCodec.encodeCandidate(
                PersistenceTestValues.generation(generation),
                PersistenceTestValues.minimalDocument,
            ) as RecoveryEncodeResult.Encoded
        ).bytes
}

private enum class RecoveryFault {
    NONE,
    START,
    WRITE,
    WRITE_CANCELLATION,
    SYNC_AND_FAIL,
    FINISH,
    FINISH_CANCELLATION,
    READ_BACK,
    READ_BACK_MISSING,
    READ_BACK_MISMATCH,
    OPEN_READ,
    READ_CANCELLATION,
    CLOSE_READ,
}

private class MemoryRecoveryAtomicFile(
    initialBytes: ByteArray? = null,
    private val fault: RecoveryFault = RecoveryFault.NONE,
    private val writeEntered: CountDownLatch? = null,
    private val releaseWrite: CountDownLatch? = null,
) : RecoveryAtomicFileAccess {
    @Volatile
    var bytes: ByteArray? = initialBytes?.copyOf()

    private val startCount = AtomicInteger()
    private val syncCount = AtomicInteger()
    private val finishCount = AtomicInteger()
    private val failCount = AtomicInteger()
    private val openReadCount = AtomicInteger()
    private val readCloseCount = AtomicInteger()
    private val concurrentAccess = AtomicInteger()
    private val maximumAccess = AtomicInteger()

    val startCalls: Int get() = startCount.get()
    val syncCalls: Int get() = syncCount.get()
    val finishCalls: Int get() = finishCount.get()
    val failCalls: Int get() = failCount.get()
    val openReadCalls: Int get() = openReadCount.get()
    val readCloseCalls: Int get() = readCloseCount.get()
    val maximumConcurrentAccess: Int get() = maximumAccess.get()

    override fun openRead(): InputStream =
        access {
            openReadCount.incrementAndGet()
            failOpenRead()
            val current = bytes?.copyOf() ?: throw FileNotFoundException("missing")
            if (fault == RecoveryFault.READ_BACK_MISMATCH && finishCalls > 0) current[0] = 0
            recordStream(current)
        }

    private fun failOpenRead() {
        val failure = openReadFailure()
        if (failure != null) throw failure
    }

    private fun openReadFailure(): IOException? =
        when {
            fault == RecoveryFault.OPEN_READ -> IOException("open read")
            fault == RecoveryFault.READ_BACK && finishCalls > 0 -> IOException("read back")
            fault == RecoveryFault.READ_BACK_MISSING && finishCalls > 0 -> FileNotFoundException("read back missing")
            else -> null
        }

    override fun startWrite(): RecoveryWriteSession =
        access {
            startCount.incrementAndGet()
            if (fault == RecoveryFault.START) throw IOException("start")
            MemoryRecoveryWriteSession()
        }

    private fun recordStream(current: ByteArray): InputStream =
        object : ByteArrayInputStream(current) {
            override fun read(
                buffer: ByteArray,
                offset: Int,
                length: Int,
            ): Int {
                if (fault == RecoveryFault.READ_CANCELLATION) throw CancellationException("read")
                return super.read(buffer, offset, length)
            }

            override fun close() {
                readCloseCount.incrementAndGet()
                if (fault == RecoveryFault.CLOSE_READ) throw IOException("close")
                super.close()
            }
        }

    private fun <T> access(block: () -> T): T {
        val entered = concurrentAccess.incrementAndGet()
        maximumAccess.accumulateAndGet(entered) { current, candidate -> maxOf(current, candidate) }
        return try {
            block()
        } finally {
            concurrentAccess.decrementAndGet()
        }
    }

    private inner class MemoryRecoveryWriteSession : RecoveryWriteSession {
        private val written = ByteArrayOutputStream()
        override val output: OutputStream =
            object : OutputStream() {
                override fun write(value: Int) {
                    access {
                        writeEntered?.countDown()
                        releaseWrite?.await()
                        when (fault) {
                            RecoveryFault.WRITE -> throw IOException("write")
                            RecoveryFault.WRITE_CANCELLATION -> throw CancellationException("write")
                            else -> written.write(value)
                        }
                    }
                }

                override fun write(
                    source: ByteArray,
                    offset: Int,
                    length: Int,
                ) {
                    access {
                        writeEntered?.countDown()
                        releaseWrite?.await()
                        when (fault) {
                            RecoveryFault.WRITE -> throw IOException("write")
                            RecoveryFault.WRITE_CANCELLATION -> throw CancellationException("write")
                            else -> written.write(source, offset, length)
                        }
                    }
                }
            }

        override fun sync() {
            syncCount.incrementAndGet()
            if (fault == RecoveryFault.SYNC_AND_FAIL) throw IOException("sync")
        }

        override fun finish() {
            finishCount.incrementAndGet()
            if (fault == RecoveryFault.FINISH) throw IOException("finish")
            if (fault == RecoveryFault.FINISH_CANCELLATION) throw CancellationException("finish")
            bytes = written.toByteArray()
        }

        override fun fail() {
            failCount.incrementAndGet()
            if (fault == RecoveryFault.SYNC_AND_FAIL) throw IOException("fail")
        }
    }
}
