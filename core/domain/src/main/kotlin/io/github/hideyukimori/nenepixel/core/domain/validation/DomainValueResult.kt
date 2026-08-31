package io.github.hideyukimori.nenepixel.core.domain.validation

public sealed interface DomainValueResult<out T> {
    public data class Created<out T> internal constructor(
        public val value: T,
    ) : DomainValueResult<T>

    public data class Rejected internal constructor(
        public val rejection: DomainValueRejection,
    ) : DomainValueResult<Nothing>
}

internal fun <T> created(value: T): DomainValueResult<T> = DomainValueResult.Created(value)

internal fun rejected(rejection: DomainValueRejection): DomainValueResult<Nothing> =
    DomainValueResult.Rejected(rejection)
