package io.github.hideyukimori.nenepixel.core.domain.layer

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LayerIdTest {
    @Test
    fun `zero is rejected and one is the first layer id`() {
        assertEquals(DomainValueRejection.InvalidLayerId(0), rejected(LayerId.create(0)))
        assertEquals(DomainValueRejection.InvalidLayerId(-1), rejected(LayerId.create(-1)))
        assertEquals(1, created(LayerId.create(1)).value)
        assertEquals(LayerId.first(), created(LayerId.create(1)))
    }

    @Test
    fun `next advances by one`() {
        assertEquals(2, created(LayerId.first().next()).value)
    }

    @Test
    fun `maximum layer id is accepted and rejects overflow`() {
        val maximum = created(LayerId.create(Int.MAX_VALUE))

        assertEquals(Int.MAX_VALUE, maximum.value)
        assertEquals(DomainValueRejection.LayerIdOverflow, rejected(maximum.next()))
    }
}
