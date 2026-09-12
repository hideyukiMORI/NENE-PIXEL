package io.github.hideyukimori.nenepixel.core.projectformat.palette

internal object PaletteJsonString {
    fun decode(
        raw: String,
        offset: Int,
    ): PaletteJsonResult<String> {
        val decoded = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            val character = raw[index++]
            if (character != '\\') {
                decoded.append(character)
                continue
            }
            when (val escape = readEscape(raw, index, offset)) {
                is PaletteJsonResult.Rejected -> return escape
                is PaletteJsonResult.Accepted -> decoded.append(escape.value)
            }
            index += if (raw[index] == 'u') UNICODE_ESCAPE_LENGTH else 1
        }
        return accepted(decoded.toString())
    }

    private fun readEscape(
        raw: String,
        index: Int,
        offset: Int,
    ): PaletteJsonResult<Char> =
        when (raw.getOrNull(index)) {
            '"' -> accepted('"')
            '\\' -> accepted('\\')
            '/' -> accepted('/')
            'b' -> accepted('\b')
            'f' -> accepted('\u000C')
            'n' -> accepted('\n')
            'r' -> accepted('\r')
            't' -> accepted('\t')
            'u' -> readUnicodeEscape(raw, index, offset)
            else -> rejected(PaletteJsonRejection.InvalidJson(offset + index))
        }

    private fun readUnicodeEscape(
        raw: String,
        index: Int,
        offset: Int,
    ): PaletteJsonResult<Char> {
        val end = index + UNICODE_ESCAPE_LENGTH
        if (end > raw.length) return rejected(PaletteJsonRejection.InvalidJson(offset + index))
        val digits = raw.substring(index + 1, end)
        return if (digits.any { it !in HEX_DIGITS }) {
            rejected(PaletteJsonRejection.InvalidJson(offset + index))
        } else {
            accepted(digits.toInt(HEX_RADIX).toChar())
        }
    }

    private const val UNICODE_ESCAPE_LENGTH: Int = 5
    private const val HEX_RADIX: Int = 16
    private const val HEX_DIGITS: String = "0123456789abcdefABCDEF"
}
