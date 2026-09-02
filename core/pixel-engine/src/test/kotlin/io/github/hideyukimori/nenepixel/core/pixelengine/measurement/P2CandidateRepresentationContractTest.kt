package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class P2CandidateRepresentationContractTest {
    @Test
    fun `RGBA8888 packing preserves every channel including hidden RGB at alpha zero`() {
        val colors = edgeColors()

        assertEquals(EXPECTED_EDGE_COLOR_COUNT, colors.size)
        colors.forEach(::assertPackedRoundTrip)

        val transparentBlack = domainColor(RgbaChannels(0, 0, 0, 0))
        val transparentRed = domainColor(RgbaChannels(255, 0, 0, 0))
        assertNotEquals(transparentBlack, transparentRed)
        assertNotEquals(P2PackedRgba8888.pack(transparentBlack), P2PackedRgba8888.pack(transparentRed))
        assertNotEquals(
            P2PackedRgba8888.unpack(P2PackedRgba8888.pack(transparentBlack)),
            P2PackedRgba8888.unpack(P2PackedRgba8888.pack(transparentRed)),
        )
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
        val after = positions.map { index -> P2PackedRgba8888.unpack(packed[index] xor ALPHA_XOR_MASK) }

        snapshots.forEach { initial ->
            val configuration = initial.configuration()
            val patch = P2CandidatePatchFactory.create(configuration, initial, positions, after).requiredPatch()
            assertEquals(positions.sorted(), List(patch.changeCount, patch::positionAt))
            assertRoundTrip(initial, patch)
        }
    }

    @Test
    fun `tiled copy on write reports touched copied and shared tile units`() {
        val shape = P2CanvasShape(width = 32, height = 32)
        val packed = deterministicPixels(shape)
        val initial = P2TiledCowCandidateSnapshot.create(shape, 0L, packed, tileEdge = 16)
        val positions = intArrayOf(0, 16, 17, shape.pixelCount.toInt() - 1)
        val after = positions.map { index -> P2PackedRgba8888.unpack(packed[index] xor ALPHA_XOR_MASK) }
        val patch =
            P2CandidatePatchFactory
                .create(
                    P2CandidateConfiguration.TiledCowT16SharedInverse,
                    initial,
                    positions,
                    after,
                ).requiredPatch()

        val applied = initial.apply(patch).requiredApplication()

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
    fun `candidate patches return typed duplicate outside and unchanged rejections`() {
        val shape = P2CanvasShape(width = 4, height = 4)
        val packed = deterministicPixels(shape)
        val snapshot = P2FlatPackedCandidateSnapshot.create(shape, 0L, packed)
        val changed = P2PackedRgba8888.unpack(packed[0] xor ALPHA_XOR_MASK)

        val configuration = P2CandidateConfiguration.FlatPackedSharedInverse
        assertCreationRejection<P2CandidatePatchCreationRejection.DuplicatePosition>(
            P2CandidatePatchFactory.create(configuration, snapshot, intArrayOf(0, 0), listOf(changed, changed)),
        )
        assertCreationRejection<P2CandidatePatchCreationRejection.PositionOutsideCanvas>(
            P2CandidatePatchFactory.create(configuration, snapshot, intArrayOf(packed.size), listOf(changed)),
        )
        assertCreationRejection<P2CandidatePatchCreationRejection.UnchangedPixel>(
            P2CandidatePatchFactory.create(
                configuration,
                snapshot,
                intArrayOf(0),
                listOf(P2PackedRgba8888.unpack(packed[0])),
            ),
        )
    }

    private fun assertRoundTrip(
        initial: P2CandidateSnapshot,
        patch: P2CandidatePatch,
    ) {
        val applied = initial.apply(patch).requiredApplication()
        assertEquals(patch.revisions.after, applied.snapshot.revision)
        assertNotEquals(initial.semanticDigest(), applied.snapshot.semanticDigest())

        val restored =
            applied.snapshot
                .apply(patch.inverse())
                .requiredApplication()
                .snapshot
        assertEquals(initial, restored)
        assertEquals(initial.semanticDigest(), restored.semanticDigest())
    }

    private inline fun <reified T : P2CandidatePatchCreationRejection> assertCreationRejection(
        result: P2CandidatePatchCreationResult,
    ) {
        val rejected = assertInstanceOf(P2CandidatePatchCreationResult.Rejected::class.java, result)
        assertInstanceOf(T::class.java, rejected.rejection)
    }

    private fun P2CandidateSnapshot.configuration(): P2CandidateConfiguration =
        P2CandidateConfiguration.entries.single { configuration ->
            configuration.snapshotRepresentation == representation
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

    private fun assertPackedRoundTrip(channels: RgbaChannels) {
        val expected = domainColor(channels)
        val packed = P2PackedRgba8888.pack(expected)
        val unpacked = P2PackedRgba8888.unpack(packed)

        assertEquals(channels.packed, packed)
        assertEquals(channels.red.toUByte(), unpacked.red.value)
        assertEquals(channels.green.toUByte(), unpacked.green.value)
        assertEquals(channels.blue.toUByte(), unpacked.blue.value)
        assertEquals(channels.alpha.toUByte(), unpacked.alpha.value)
        assertEquals(expected, unpacked)
    }

    private fun domainColor(channels: RgbaChannels): PixelColor =
        PixelColor.create(
            channel(channels.red),
            channel(channels.green),
            channel(channels.blue),
            channel(channels.alpha),
        )

    private fun channel(value: Int): ColorChannel =
        when (val result = ColorChannel.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Edge channel was rejected: ${result.rejection}")
        }

    private fun edgeColors(): List<RgbaChannels> =
        CHANNEL_EDGES.flatMap { red ->
            CHANNEL_EDGES.flatMap { green ->
                CHANNEL_EDGES.flatMap { blue ->
                    CHANNEL_EDGES.map { alpha -> RgbaChannels(red, green, blue, alpha) }
                }
            }
        }

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

    private data class RgbaChannels(
        val red: Int,
        val green: Int,
        val blue: Int,
        val alpha: Int,
    ) {
        val packed: Int
            get() = (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or (blue shl BLUE_SHIFT) or alpha
    }

    private companion object {
        const val EXPECTED_EDGE_COLOR_COUNT: Int = 1_296
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
