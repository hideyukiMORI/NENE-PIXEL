package io.github.hideyukimori.nenepixel.adapters.persistence

import java.io.InputStream

internal object BoundedStreamReader {
    fun read(
        source: InputStream,
        knownByteCount: Long?,
        maximumByteCount: Int,
    ): BoundedReadResult =
        if (knownByteCount != null && knownByteCount > maximumByteCount) {
            BoundedReadResult.ResourceLimitExceeded
        } else {
            fill(source, ByteArray(maximumByteCount), knownByteCount)
        }

    private fun fill(
        source: InputStream,
        buffer: ByteArray,
        knownByteCount: Long?,
    ): BoundedReadResult {
        var byteCount = 0
        while (byteCount < buffer.size) {
            when (val step = step(source, buffer, byteCount, knownByteCount)) {
                is BoundedReadStep.Progressed -> byteCount = step.byteCount
                is BoundedReadStep.Terminal -> return step.result
            }
        }
        return probe(source, buffer, knownByteCount)
    }

    private fun step(
        source: InputStream,
        buffer: ByteArray,
        byteCount: Int,
        knownByteCount: Long?,
    ): BoundedReadStep {
        val read = source.read(buffer, byteCount, buffer.size - byteCount)
        return when {
            read < 0 -> BoundedReadStep.Terminal(completed(buffer, byteCount, knownByteCount))
            read == 0 -> BoundedReadStep.Terminal(BoundedReadResult.ZeroProgress)
            else -> BoundedReadStep.Progressed(byteCount + read)
        }
    }

    private fun probe(
        source: InputStream,
        buffer: ByteArray,
        knownByteCount: Long?,
    ): BoundedReadResult =
        if (source.read() < 0) {
            completed(buffer, buffer.size, knownByteCount)
        } else {
            BoundedReadResult.ResourceLimitExceeded
        }

    private fun completed(
        buffer: ByteArray,
        byteCount: Int,
        knownByteCount: Long?,
    ): BoundedReadResult =
        if (knownByteCount != null && byteCount.toLong() < knownByteCount) {
            BoundedReadResult.PrematureEnd
        } else {
            BoundedReadResult.Bytes(buffer.copyOf(byteCount))
        }
}

internal sealed interface BoundedReadResult {
    data class Bytes(
        val value: ByteArray,
    ) : BoundedReadResult

    data object ResourceLimitExceeded : BoundedReadResult

    data object ZeroProgress : BoundedReadResult

    data object PrematureEnd : BoundedReadResult
}

private sealed interface BoundedReadStep {
    data class Progressed(
        val byteCount: Int,
    ) : BoundedReadStep

    data class Terminal(
        val result: BoundedReadResult,
    ) : BoundedReadStep
}
