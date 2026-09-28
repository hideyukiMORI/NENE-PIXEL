package io.github.hideyukimori.nenepixel.core.domain.layer

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LayerNameTest {
    @Test
    fun `empty name is accepted and equals the empty constant`() {
        assertEquals("", LayerName.empty.value)
        assertEquals(LayerName.empty, created(LayerName.create("")))
    }

    @Test
    fun `thirty two code points are accepted and thirty three are rejected`() {
        assertEquals("a".repeat(32), created(LayerName.create("a".repeat(32))).value)
        assertEquals(DomainValueRejection.LayerNameTooLong(33), rejected(LayerName.create("a".repeat(33))))
    }

    @Test
    fun `surrogate pair emoji count as one code point each and fit the utf8 budget`() {
        val maximum = EMOJI.repeat(LayerLimits.MAX_NAME_CODE_POINTS)
        val name = created(LayerName.create(maximum))

        assertEquals(64, maximum.length)
        assertEquals(LayerLimits.MAX_NAME_UTF8_BYTES, name.value.toByteArray(Charsets.UTF_8).size)
        assertTrue(name.value.toByteArray(Charsets.UTF_8).size <= LayerLimits.MAX_NAME_UTF8_BYTES)
        assertEquals(DomainValueRejection.LayerNameTooLong(33), rejected(LayerName.create(maximum + EMOJI)))
    }

    @Test
    fun `control characters are rejected at their code point index`() {
        assertEquals(DomainValueRejection.LayerNameControlCharacter(0), rejected(LayerName.create("\n")))
        assertEquals(
            DomainValueRejection.LayerNameControlCharacter(2),
            rejected(LayerName.create(EMOJI + "a\u007F")),
        )
        assertEquals(DomainValueRejection.LayerNameControlCharacter(1), rejected(LayerName.create("a\u0085")))
    }

    @Test
    fun `lone surrogates are rejected at their char index`() {
        assertEquals(DomainValueRejection.LayerNameInvalidSurrogate(0), rejected(LayerName.create("\uD83D")))
        assertEquals(DomainValueRejection.LayerNameInvalidSurrogate(1), rejected(LayerName.create("a\uDE00")))
        assertEquals(
            DomainValueRejection.LayerNameInvalidSurrogate(0),
            rejected(LayerName.create("\uD83D\uD83D\uDE00")),
        )
        assertEquals(
            DomainValueRejection.LayerNameInvalidSurrogate(2),
            rejected(LayerName.create("\uD83D\uDE00\uDE00")),
        )
    }

    private companion object {
        const val EMOJI: String = "\uD83D\uDE00"
    }
}
