package io.github.hideyukimori.nenepixel.core.projectformat.palette

internal class PaletteJsonCursor(
    private val source: String,
) {
    private var position: Int = if (source.startsWith('\uFEFF')) 1 else 0

    fun consume(expected: Char): Boolean {
        skipWhitespace()
        if (source.getOrNull(position) != expected) return false
        position += 1
        return true
    }

    fun expect(expected: Char): PaletteJsonResult<Unit> = if (consume(expected)) accepted(Unit) else invalid()

    fun end(): PaletteJsonResult<Unit> {
        skipWhitespace()
        return if (position == source.length) accepted(Unit) else invalid()
    }

    fun invalid(): PaletteJsonResult<Nothing> = rejected(PaletteJsonRejection.InvalidJson(position))

    fun readString(): PaletteJsonResult<String> = expect('"').andThen { readQuotedSpan() }

    private fun readQuotedSpan(): PaletteJsonResult<String> {
        val start = position
        while (position < source.length && source[position] >= ' ' && source[position] != '"') {
            position += if (source[position] == '\\') 2 else 1
        }
        return if (source.getOrNull(position) == '"') {
            PaletteJsonString.decode(source.substring(start, position++), start)
        } else {
            invalid()
        }
    }

    fun readInteger(): PaletteJsonResult<Int> {
        skipWhitespace()
        val start = position
        return readIntegerToken().andThen { token ->
            val value = token.toIntOrNull()
            if (value == null) rejected(PaletteJsonRejection.IntegerOverflow(start)) else accepted(value)
        }
    }

    private fun readIntegerToken(): PaletteJsonResult<String> {
        val start = position
        if (source.getOrNull(position) == '-') position += 1
        when (source.getOrNull(position)) {
            '0' -> position += 1
            in '1'..'9' -> while (source.getOrNull(position) in '0'..'9') position += 1
            else -> return invalid()
        }
        return if (nextIn("0123456789.eE")) invalid() else accepted(source.substring(start, position))
    }

    private fun skipWhitespace() {
        while (nextIn(" \t\r\n")) position += 1
    }

    private fun nextIn(characters: String): Boolean = source.getOrNull(position)?.let { it in characters } ?: false
}
