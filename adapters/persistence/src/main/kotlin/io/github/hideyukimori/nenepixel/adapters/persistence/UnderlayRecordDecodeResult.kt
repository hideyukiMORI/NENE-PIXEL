package io.github.hideyukimori.nenepixel.adapters.persistence

/**
 * The closed result of reading an underlay memory record. Every reason a record cannot be read is
 * [Unreadable]: the memory is disposable and an unreadable record is discarded (ADR 0034).
 */
internal sealed interface UnderlayRecordDecodeResult<out T> {
    data class Decoded<out T>(
        val value: T,
    ) : UnderlayRecordDecodeResult<T>

    data object Unreadable : UnderlayRecordDecodeResult<Nothing>
}
