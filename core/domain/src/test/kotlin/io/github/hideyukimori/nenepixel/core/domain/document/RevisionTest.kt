package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class RevisionTest {
    @Test
    fun `revision has one zero initial value and monotonic advance`() {
        val initial = Revision.initial()
        val same = created(Revision.create(0L))
        val advanced = created(initial.advance())

        assertEquals(0L, initial.value)
        assertEquals(initial, same)
        assertEquals(1L, advanced.value)
    }

    @Test
    fun `negative revision is rejected`() {
        assertInstanceOf(
            DomainValueRejection.NegativeRevision::class.java,
            rejected(Revision.create(-1L)),
        )
    }

    @Test
    fun `maximum revision rejects overflow`() {
        val maximum = created(Revision.create(Long.MAX_VALUE))

        assertEquals(DomainValueRejection.RevisionOverflow, rejected(maximum.advance()))
    }
}
