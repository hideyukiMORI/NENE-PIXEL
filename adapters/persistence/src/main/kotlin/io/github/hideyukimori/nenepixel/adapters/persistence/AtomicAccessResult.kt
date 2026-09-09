package io.github.hideyukimori.nenepixel.adapters.persistence

import kotlinx.coroutines.CancellationException

internal sealed interface AtomicAccessResult<out T> {
    data class Accepted<T>(
        val value: T,
    ) : AtomicAccessResult<T>

    data object Failed : AtomicAccessResult<Nothing>

    companion object {
        fun <T> of(operation: () -> T): AtomicAccessResult<T> =
            try {
                Accepted(operation())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // AtomicFile and filesystem exceptions are normalized only at this adapter boundary.
                Failed
            }
    }
}
