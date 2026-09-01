package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class P2CandidateRepresentationContractTest {
    @Test
    fun `RGBA8888 packing preserves every channel including hidden RGB at alpha zero`() {
        CHANNEL_EDGES.forEachIndexed { index, red ->
            val green = CHANNEL_EDGES[(index + 1) % CHANNEL_EDGES.size]
            val blue = CHANNEL_EDGES[(index + 2) % CHANNEL_EDGES.size]
            val alpha = if (index == 0) 0 else CHANNEL_EDGES[(index + 3) % CHANNEL_EDGES.size]
            val expected = pixelColor(red, green, blue, alpha)
            val packed = P2PackedRgba8888.pack(expected)

            assertEquals(expected, P2PackedRgba8888.unpack(packed))
        }
    }

    @Test
    fun `candidate snapshots own input and expose one semantic equality contract`() {
        val shape = P2CanvasShape(width = 4, height = 3)
        val packed = deterministicPixels(shape)
        val objects = packed.map(P2PackedRgba8888::unpack).toMutableList()
        val snapshots = candidateSnapshots(shape, objects, packed)
        val expectedDigest = snapshots.first().semanticDigest()

        objects[0] = pixelColor(255, 255, 255, 255)
        packed[0] = P2PackedRgba8888.pack(objects[0])

        snapshots.forEach { snapshot ->
            assertEquals(expectedDigest, snapshot.semanticDigest())
            assertEquals(snapshots.first(), snapshot)
            assertEquals(snapshots.first().hashCode(), snapshot.hashCode())
        }
    }

    @Test
    fun `flat and tiled candidates apply canonical changes and round trip through shared inverse data`() {
        val shape = P2CanvasShape(width = 32, height = 32)
        val packed = deterministicPixels(shape)
        val snapshots = candidateSnapshots(shape, packed.map(P2PackedRgba8888::unpack), packed)
        val positions = intArrayOf(shape.pixelCount.toInt() - 1, 17, 0, 16)
        val after = positions.map { index -> packed[index] xor ALPHA_XOR_MASK }.toIntArray()
        val changes = P2CandidateChanges.create(snapshots.first(), positions, after)

        assertEquals(positions.sorted(), List(changes.changeCount, changes::positionAt))
        snapshots.forEach { initial -> assertRoundTrip(initial, changes) }
    }

    @Test
    fun `tiled copy on write reports touched copied and shared tile units`() {
        val shape = P2CanvasShape(width = 32, height = 32)
        val packed = deterministicPixels(shape)
        val initial = P2TiledCowCandidateSnapshot.create(shape, 0L, packed, tileEdge = 16)
        val positions = intArrayOf(0, 16, 17, shape.pixelCount.toInt() - 1)
        val after = positions.map { index -> packed[index] xor ALPHA_XOR_MASK }.toIntArray()

        val applied = initial.apply(P2CandidateChanges.create(initial, positions, after))

        assertEquals(3, applied.touchedUnits)
        assertEquals(3, applied.copiedUnits)
        assertEquals(1, applied.sharedUnits)
        assertEquals(initial.storage.primitivePayloadBytes, applied.snapshot.storage.primitivePayloadBytes)
    }

    @Test
    fun `palette U8 uses unsigned index 255 and rejects the 257th semantic color`() {
        val shape256 = P2CanvasShape(width = 16, height = 16)
        val packed256 = IntArray(256, ::distinctPackedColor)
        val created =
            assertInstanceOf(
                P2PaletteCandidateResult.Created::class.java,
                P2PaletteCandidateSnapshot.create(shape256, packed256),
            )

        assertEquals(256, created.snapshot.colorCardinality)
        assertEquals(packed256[255], created.snapshot.packedAt(255))

        val rejected =
            assertInstanceOf(
                P2PaletteCandidateResult.Rejected::class.java,
                P2PaletteCandidateSnapshot.create(P2CanvasShape(257, 1), IntArray(257, ::distinctPackedColor)),
            )
        assertEquals(P2PaletteCandidateRejection.MoreThan256SemanticColors, rejected.rejection)
    }

    @Test
    fun `candidate changes reject duplicate outside and unchanged entries before apply`() {
        val shape = P2CanvasShape(width = 4, height = 4)
        val packed = deterministicPixels(shape)
        val snapshot = P2FlatPackedCandidateSnapshot.create(shape, 0L, packed)
        val changed = packed[0] xor ALPHA_XOR_MASK

        assertThrows(IllegalArgumentException::class.java) {
            P2CandidateChanges.create(snapshot, intArrayOf(0, 0), intArrayOf(changed, changed))
        }
        assertThrows(IllegalArgumentException::class.java) {
            P2CandidateChanges.create(snapshot, intArrayOf(packed.size), intArrayOf(changed))
        }
        assertThrows(IllegalArgumentException::class.java) {
            P2CandidateChanges.create(snapshot, intArrayOf(0), intArrayOf(packed[0]))
        }
    }

    private fun assertRoundTrip(
        initial: P2CandidateSnapshot,
        changes: P2CandidateChanges,
    ) {
        val applied = initial.apply(changes)
        assertEquals(changes.revisions.after, applied.snapshot.revision)
        assertNotEquals(initial.semanticDigest(), applied.snapshot.semanticDigest())

        val restored = applied.snapshot.apply(changes.inverse()).snapshot
        assertEquals(initial, restored)
        assertEquals(initial.semanticDigest(), restored.semanticDigest())
    }

    private fun candidateSnapshots(
        shape: P2CanvasShape,
        objects: List<PixelColor>,
        packed: IntArray,
    ): List<P2CandidateSnapshot> =
        listOf(
            P2CurrentObjectCandidateSnapshot.create(shape, 0L, objects),
            P2FlatPackedCandidateSnapshot.create(shape, 0L, packed),
            P2TiledCowCandidateSnapshot.create(shape, 0L, packed, tileEdge = 16),
            P2TiledCowCandidateSnapshot.create(shape, 0L, packed, tileEdge = 32),
            P2TiledCowCandidateSnapshot.create(shape, 0L, packed, tileEdge = 64),
        )

    private fun deterministicPixels(shape: P2CanvasShape): IntArray =
        IntArray(shape.pixelCount.toInt()) { index -> distinctPackedColor(index) }

    private fun distinctPackedColor(index: Int): Int =
        ((index and CHANNEL_MASK) shl RED_SHIFT) or
            ((index ushr BYTE_BITS and CHANNEL_MASK) shl GREEN_SHIFT) or
            ((index * BLUE_MULTIPLIER and CHANNEL_MASK) shl BLUE_SHIFT) or
            (index * ALPHA_MULTIPLIER and CHANNEL_MASK)

    private fun pixelColor(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ): PixelColor =
        P2PackedRgba8888.unpack(
            (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or (blue shl BLUE_SHIFT) or alpha,
        )

    private companion object {
        const val RED_SHIFT: Int = 24
        const val GREEN_SHIFT: Int = 16
        const val BLUE_SHIFT: Int = 8
        const val BYTE_BITS: Int = 8
        const val CHANNEL_MASK: Int = 0xff
        const val BLUE_MULTIPLIER: Int = 29
        const val ALPHA_MULTIPLIER: Int = 43
        const val ALPHA_XOR_MASK: Int = 0x000000ff
        val CHANNEL_EDGES: List<Int> = listOf(0, 1, 127, 128, 254, 255)
    }
}
