package io.github.hideyukimori.nenepixel.core.projectformat.palette

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.MINIMAL
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.carrier
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.decode
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.rejected
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class PaletteJsonRejectionTest {
    @Test
    fun `every truncation before root closure is rejected`() {
        MINIMAL.trimEnd().indices.forEach { end ->
            assertInstanceOf(PaletteJsonResult.Rejected::class.java, decode(MINIMAL.substring(0, end)), "end=$end")
        }
    }

    @Test
    fun `schema rejects unknown missing duplicate and escaped duplicate names`() {
        assertEquals(PaletteJsonRejection.UnknownField, rejected(decode(MINIMAL.replace("format", "other"))))
        assertEquals(PaletteJsonRejection.MissingField, rejected(decode(MINIMAL.replace("\"version\":1,", ""))))
        listOf("version", "\\u0076ersion").forEach { key ->
            val duplicate = MINIMAL.replace("\"version\":1", "\"version\":1,\"$key\":1")
            assertEquals(PaletteJsonRejection.DuplicateField, rejected(decode(duplicate)))
        }
        assertEquals(PaletteJsonRejection.UnsupportedFormat, rejected(decode(MINIMAL.replace("nene-pixel", "other"))))
        assertEquals(
            PaletteJsonRejection.UnsupportedVersion,
            rejected(decode(MINIMAL.replace("\"version\":1", "\"version\":2"))),
        )
    }

    @Test
    fun `wrong JSON shapes and delimiters fail closed`() {
        val malformed =
            listOf(
                "[]",
                "null",
                "true",
                "{}[]",
                "\uFEFF\uFEFF$MINIMAL",
                "$MINIMAL false",
                MINIMAL.replace("1,", "1,,"),
                MINIMAL.replace("]}", "],}"),
                MINIMAL.replace("]}", ",]}"),
                MINIMAL.replace("1,", "1/*comment*/,"),
                MINIMAL.replace("0,", "false,"),
                MINIMAL.replace("0,", "null,"),
                MINIMAL.replace("[#", "[[#").replace("[\"#", "[[\"#"),
                MINIMAL.replace("{", "{\u000B"),
                MINIMAL.replace("1,", "1\u00A0,"),
            )
        malformed.forEach { text -> assertInstanceOf(PaletteJsonResult.Rejected::class.java, decode(text), text) }
    }

    @Test
    fun `integer grammar rejects floats exponent plus and leading zeros`() {
        listOf("01", "-01", "+1", "1.0", "1e0", "1E0", "-", "--1", "\"1\"").forEach { token ->
            assertInstanceOf(
                PaletteJsonRejection.InvalidJson::class.java,
                rejected(decode(MINIMAL.replace("\"version\":1", "\"version\":$token"))),
                token,
            )
        }
        assertInstanceOf(
            PaletteJsonRejection.IntegerOverflow::class.java,
            rejected(decode(MINIMAL.replace("\"version\":1", "\"version\":2147483648"))),
        )
    }

    @Test
    fun `default index semantic limits remain typed`() {
        listOf(-1, Int.MIN_VALUE, 2, 256, Int.MAX_VALUE).forEach { index ->
            val invalid =
                assertInstanceOf(
                    PaletteJsonRejection.InvalidDefinition::class.java,
                    rejected(decode(MINIMAL.replace("\"defaultIndex\":0", "\"defaultIndex\":$index"))),
                )
            val type =
                if (index < 0) {
                    DomainValueRejection.NegativePaletteIndex::class.java
                } else {
                    DomainValueRejection.PaletteIndexOutsidePalette::class.java
                }
            assertInstanceOf(type, invalid.rejection)
        }
    }

    @Test
    fun `zero one and 257 entries are rejected without silently resizing`() {
        listOf(0, 1, 257).forEach { count ->
            val colors = List(count) { "\"#00000000\"" }.joinToString(",")
            val text = MINIMAL.substringBefore('[') + "[$colors]}"
            val rejection = rejected(decode(text))
            when (count) {
                257 -> assertEquals(PaletteJsonRejection.TooManyColors, rejection)
                else -> assertInstanceOf(PaletteJsonRejection.InvalidDefinition::class.java, rejection)
            }
        }
    }

    @Test
    fun `color requires exactly eight hex digits and explicit alpha`() {
        listOf("red", "#fff", "#ffffff", "#gg000000", "#000000000", "000000000", "#１２３４５６７８").forEach { color ->
            assertInstanceOf(
                PaletteJsonRejection.InvalidColor::class.java,
                rejected(decode(MINIMAL.replace("#00000000", color))),
                color,
            )
        }
    }

    @Test
    fun `JSON standard escapes parse then fail schema while malformed escapes fail syntax`() {
        val escapes = listOf("\\\"", "\\\\", "\\/", "\\b", "\\f", "\\n", "\\r", "\\t", "\\u65e5", "\\uD83D\\uDE00")
        escapes.forEach { escape ->
            assertInstanceOf(
                PaletteJsonRejection.InvalidColor::class.java,
                rejected(decode(MINIMAL.replace("#00000000", escape))),
                escape,
            )
        }
        listOf("\\x", "\\u123", "\\u12x4", "\n", "\u0000").forEach { escape ->
            assertInstanceOf(
                PaletteJsonRejection.InvalidJson::class.java,
                rejected(decode(MINIMAL.replace("#00000000", escape))),
                escape,
            )
        }
    }

    @Test
    fun `invalid UTF8 includes overlong surrogate and truncated encodings`() {
        listOf(
            listOf(0xc0, 0x80),
            listOf(0xed, 0xa0, 0x80),
            listOf(0xf4, 0x90, 0x80, 0x80),
            listOf(0xe3, 0x81),
        ).forEach { raw ->
            val bytes = raw.map(Int::toByte).toByteArray()
            assertEquals(PaletteJsonRejection.InvalidUtf8, rejected(PaletteJsonCodec.decode(carrier(bytes))))
        }
    }

    @Test
    fun `fixed malformed byte corpus always returns typed rejection`() {
        val random = Random(105)
        repeat(1_024) { index ->
            val bytes = random.nextBytes(index % 257)
            assertInstanceOf(PaletteJsonResult.Rejected::class.java, PaletteJsonCodec.decode(carrier(bytes)))
        }
    }
}
