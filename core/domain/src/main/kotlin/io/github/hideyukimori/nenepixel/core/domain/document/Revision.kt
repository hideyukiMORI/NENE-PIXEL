package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

@JvmInline
public value class Revision private constructor(
    public val value: Long,
) {
    public fun advance(): DomainValueResult<Revision> =
        if (value == Long.MAX_VALUE) {
            rejected(DomainValueRejection.RevisionOverflow)
        } else {
            created(Revision(value + 1L))
        }

    public companion object {
        public fun initial(): Revision = Revision(0L)

        public fun create(value: Long): DomainValueResult<Revision> =
            if (value < 0L) {
                rejected(DomainValueRejection.NegativeRevision(value))
            } else {
                created(Revision(value))
            }
    }
}
