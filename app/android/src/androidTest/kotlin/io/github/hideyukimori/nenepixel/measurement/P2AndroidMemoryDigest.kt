package io.github.hideyukimori.nenepixel.measurement

import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import java.security.MessageDigest

internal object P2AndroidMemoryDigest {
    fun finalPixels(snapshot: PixelSnapshot): String {
        val digest = sha256()
        digest.updateInt(P2AndroidMemoryProtocol.PIXEL_COUNT)
        repeat(P2AndroidMemoryProtocol.PIXEL_COUNT) { index ->
            val position = P2AndroidMemoryValues.positionAt(index)
            val color = snapshot.colorAt(position).memoryValue()
            digest.updateInt(position.x.value)
            digest.updateInt(position.y.value)
            digest.updateInt(color.argb())
        }
        return digest.hex()
    }

    fun entryDescriptors(changeSets: List<ChangeSet>): String {
        val digest = sha256()
        digest.updateInt(changeSets.size)
        changeSets.forEachIndexed { index, changeSet ->
            val expected = P2AndroidMemoryValues.entryExpectation(index)
            val region = changeSet.renderInvalidation
            digest.updateInt(index)
            digest.updateInt(expected.blockIndex)
            digest.updateInt(P2AndroidMemoryProtocol.CHANGE_COUNT_PER_ENTRY)
            digest.updateInt(expected.targetArgb)
            digest.updateLong(changeSet.beforeRevision.value)
            digest.updateLong(changeSet.afterRevision.value)
            digest.updateInt(region.origin.x.value)
            digest.updateInt(region.origin.y.value)
            digest.updateInt(region.size.width.value)
            digest.updateInt(region.size.height.value)
        }
        return digest.hex()
    }

    fun file(file: java.io.File): String =
        file.inputStream().buffered().use { input ->
            val digest = sha256()
            val buffer = ByteArray(FILE_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.hex()
        }

    private fun PixelColor.argb(): Int =
        (alpha.value.toInt() shl ALPHA_SHIFT) or
            (red.value.toInt() shl RED_SHIFT) or
            (green.value.toInt() shl GREEN_SHIFT) or
            blue.value.toInt()

    private fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

    private fun MessageDigest.updateInt(value: Int) {
        update((value ushr BYTE_3_SHIFT).toByte())
        update((value ushr BYTE_2_SHIFT).toByte())
        update((value ushr BYTE_1_SHIFT).toByte())
        update(value.toByte())
    }

    private fun MessageDigest.updateLong(value: Long) {
        updateInt((value ushr Integer.SIZE).toInt())
        updateInt(value.toInt())
    }

    private fun MessageDigest.hex(): String = digest().hex()

    private fun ByteArray.hex(): String =
        buildString(size * 2) {
            this@hex.forEach { byte ->
                val value = byte.toInt() and UBYTE_MASK
                append(HEX_DIGITS[value ushr NIBBLE_SHIFT])
                append(HEX_DIGITS[value and NIBBLE_MASK])
            }
        }

    private const val FILE_BUFFER_SIZE: Int = 8_192
    private const val ALPHA_SHIFT: Int = 24
    private const val RED_SHIFT: Int = 16
    private const val GREEN_SHIFT: Int = 8
    private const val BYTE_3_SHIFT: Int = 24
    private const val BYTE_2_SHIFT: Int = 16
    private const val BYTE_1_SHIFT: Int = 8
    private const val UBYTE_MASK: Int = 0xff
    private const val NIBBLE_MASK: Int = 0x0f
    private const val NIBBLE_SHIFT: Int = 4
    private const val HEX_DIGITS: String = "0123456789ABCDEF"
}

internal data class P2MemoryEntryExpectation(
    val blockIndex: Int,
    val targetArgb: Int,
)

internal object P2AndroidMemoryValues {
    fun entryExpectation(index: Int): P2MemoryEntryExpectation =
        P2MemoryEntryExpectation(
            blockIndex = index % P2AndroidMemoryProtocol.BLOCK_COUNT,
            targetArgb =
                if (index / P2AndroidMemoryProtocol.BLOCK_COUNT % 2 == 0) {
                    P2AndroidMemoryProtocol.OPAQUE_RED_ARGB
                } else {
                    P2AndroidMemoryProtocol.OPAQUE_WHITE_ARGB
                },
        )

    fun positionAt(index: Int): PixelPosition =
        memoryPosition(
            x = index % P2AndroidMemoryProtocol.CANVAS_EDGE,
            y = index / P2AndroidMemoryProtocol.CANVAS_EDGE,
        )
}
