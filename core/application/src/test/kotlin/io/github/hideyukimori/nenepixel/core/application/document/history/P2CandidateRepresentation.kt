package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal enum class P2CandidateRepresentation(
    val candidateId: String,
) {
    CurrentObjectList("current-object-list-fixture-v1"),
    FlatPackedRgba8888("flat-packed-rgba8888-v1"),
    TiledCowRgba8888T16("tiled-cow-rgba8888-t16-v1"),
    TiledCowRgba8888T32("tiled-cow-rgba8888-t32-v1"),
    TiledCowRgba8888T64("tiled-cow-rgba8888-t64-v1"),
    PaletteValueU8("palette-value-u8-v1"),
}

internal data class P2CandidateRevisionTransition(
    val before: Long,
    val after: Long,
) {
    fun inverse(): P2CandidateRevisionTransition = P2CandidateRevisionTransition(after, before)
}

internal data class P2CandidateStorageCounts(
    val primitivePayloadBytes: Long,
    val referenceSlots: Long,
    val copiedPrimitiveBytes: Long,
    val copiedReferenceSlots: Long,
)

internal data class P2CandidateApplication(
    val snapshot: P2CandidateSnapshot,
    val touchedUnits: Int,
    val copiedUnits: Int,
    val sharedUnits: Int,
)

internal class P2CandidateChanges private constructor(
    private val storage: P2CandidateChangeStorage,
    val revisions: P2CandidateRevisionTransition,
    private val inverse: Boolean,
) {
    val changeCount: Int
        get() = storage.positions.size

    val primitivePayloadBytes: Long
        get() = storage.positions.size.toLong() * CHANGE_FIELD_COUNT * Int.SIZE_BYTES

    fun positionAt(index: Int): Int = storage.positions[index]

    fun beforeAt(index: Int): Int = if (inverse) storage.after[index] else storage.before[index]

    fun afterAt(index: Int): Int = if (inverse) storage.before[index] else storage.after[index]

    fun inverse(): P2CandidateChanges = P2CandidateChanges(storage, revisions.inverse(), !inverse)

    fun assertApplicableTo(snapshot: P2CandidateSnapshot) {
        check(snapshot.revision == revisions.before) { "Candidate revision mismatch." }
        repeat(changeCount) { index ->
            check(snapshot.packedAt(positionAt(index)) == beforeAt(index)) {
                "Candidate before-value mismatch at row-major index ${positionAt(index)}."
            }
        }
    }

    companion object {
        fun create(
            snapshot: P2CandidateSnapshot,
            positions: IntArray,
            after: IntArray,
        ): P2CandidateChanges {
            require(positions.size == after.size) { "Candidate positions and values must have equal size." }
            require(positions.isNotEmpty()) { "Candidate changes must not be empty." }
            val ordered = positions.indices.sortedBy(positions::get)
            val canonicalPositions = IntArray(ordered.size) { index -> positions[ordered[index]] }
            require(canonicalPositions.toSet().size == canonicalPositions.size) {
                "Candidate changes must have unique positions."
            }
            require(canonicalPositions.all { position -> position in 0 until snapshot.shape.pixelCount.toInt() }) {
                "Candidate change is outside the canvas."
            }
            val canonicalAfter = IntArray(ordered.size) { index -> after[ordered[index]] }
            val before = IntArray(ordered.size) { index -> snapshot.packedAt(canonicalPositions[index]) }
            require(before.indices.all { index -> before[index] != canonicalAfter[index] }) {
                "Candidate change must alter every position."
            }
            val revisions = P2CandidateRevisionTransition(snapshot.revision, Math.addExact(snapshot.revision, 1L))
            return P2CandidateChanges(
                storage = P2CandidateChangeStorage(canonicalPositions, before, canonicalAfter),
                revisions = revisions,
                inverse = false,
            )
        }

        private const val CHANGE_FIELD_COUNT: Long = 3L
    }
}

private data class P2CandidateChangeStorage(
    val positions: IntArray,
    val before: IntArray,
    val after: IntArray,
)

internal abstract class P2CandidateSnapshot {
    abstract val representation: P2CandidateRepresentation
    abstract val shape: P2CanvasShape
    abstract val revision: Long
    abstract val storage: P2CandidateStorageCounts

    abstract fun packedAt(index: Int): Int

    abstract fun apply(changes: P2CandidateChanges): P2CandidateApplication

    fun semanticDigest(): Int {
        var digest = shape.hashCode()
        digest = HASH_MULTIPLIER * digest + revision.hashCode()
        repeat(shape.pixelCount.toInt()) { index -> digest = HASH_MULTIPLIER * digest + packedAt(index) }
        return digest
    }

    final override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is P2CandidateSnapshot &&
                    shape == other.shape &&
                    revision == other.revision &&
                    semanticPixelsEqual(other)
            )

    final override fun hashCode(): Int = semanticDigest()

    private fun semanticPixelsEqual(other: P2CandidateSnapshot): Boolean =
        (0 until shape.pixelCount.toInt()).all { index -> packedAt(index) == other.packedAt(index) }

    protected fun P2CandidateChanges.requireApplicable() {
        assertApplicableTo(this@P2CandidateSnapshot)
    }

    private companion object {
        const val HASH_MULTIPLIER: Int = 31
    }
}

internal class P2CurrentObjectCandidateSnapshot private constructor(
    override val shape: P2CanvasShape,
    override val revision: Long,
    private val pixels: List<PixelColor>,
) : P2CandidateSnapshot() {
    override val representation: P2CandidateRepresentation = P2CandidateRepresentation.CurrentObjectList
    override val storage: P2CandidateStorageCounts =
        P2CandidateStorageCounts(0L, pixels.size.toLong(), 0L, 0L)

    override fun packedAt(index: Int): Int = P2PackedRgba8888.pack(pixels[index])

    override fun apply(changes: P2CandidateChanges): P2CandidateApplication {
        changes.requireApplicable()
        val next = pixels.toMutableList()
        repeat(changes.changeCount) { index ->
            next[changes.positionAt(index)] = P2PackedRgba8888.unpack(changes.afterAt(index))
        }
        return P2CandidateApplication(
            snapshot = P2CurrentObjectCandidateSnapshot(shape, changes.revisions.after, next.toList()),
            touchedUnits = changes.changeCount,
            copiedUnits = pixels.size,
            sharedUnits = 0,
        )
    }

    companion object {
        fun create(
            shape: P2CanvasShape,
            revision: Long,
            pixels: List<PixelColor>,
        ): P2CurrentObjectCandidateSnapshot {
            require(shape.pixelCount == pixels.size.toLong()) { "Object candidate pixel count mismatch." }
            return P2CurrentObjectCandidateSnapshot(shape, revision, pixels.toList())
        }
    }
}

internal class P2FlatPackedCandidateSnapshot private constructor(
    override val shape: P2CanvasShape,
    override val revision: Long,
    private val pixels: IntArray,
) : P2CandidateSnapshot() {
    override val representation: P2CandidateRepresentation = P2CandidateRepresentation.FlatPackedRgba8888
    override val storage: P2CandidateStorageCounts =
        P2CandidateStorageCounts(pixels.size.toLong() * Int.SIZE_BYTES, 0L, 0L, 0L)

    override fun packedAt(index: Int): Int = pixels[index]

    override fun apply(changes: P2CandidateChanges): P2CandidateApplication {
        changes.requireApplicable()
        val next = pixels.copyOf()
        repeat(changes.changeCount) { index -> next[changes.positionAt(index)] = changes.afterAt(index) }
        return P2CandidateApplication(
            snapshot = P2FlatPackedCandidateSnapshot(shape, changes.revisions.after, next),
            touchedUnits = changes.changeCount,
            copiedUnits = pixels.size,
            sharedUnits = 0,
        )
    }

    companion object {
        fun create(
            shape: P2CanvasShape,
            revision: Long,
            pixels: IntArray,
        ): P2FlatPackedCandidateSnapshot {
            require(shape.pixelCount == pixels.size.toLong()) { "Packed candidate pixel count mismatch." }
            return P2FlatPackedCandidateSnapshot(shape, revision, pixels.copyOf())
        }
    }
}

internal class P2TiledCowCandidateSnapshot private constructor(
    override val shape: P2CanvasShape,
    override val revision: Long,
    private val layout: P2CandidateTileLayout,
) : P2CandidateSnapshot() {
    override val representation: P2CandidateRepresentation = layout.representation
    override val storage: P2CandidateStorageCounts =
        P2CandidateStorageCounts(layout.primitivePayloadBytes, layout.tiles.size.toLong(), 0L, 0L)

    override fun packedAt(index: Int): Int = layout.packedAt(shape, index)

    override fun apply(changes: P2CandidateChanges): P2CandidateApplication {
        changes.requireApplicable()
        val copiedTiles = layout.tiles.copyOf()
        val touched = changes.touchedTileIndexes(shape, layout.tileEdge)
        touched.forEach { tileIndex -> copiedTiles[tileIndex] = copiedTiles[tileIndex].copyOf() }
        repeat(changes.changeCount) { index ->
            layout.write(copiedTiles, shape, changes.positionAt(index), changes.afterAt(index))
        }
        val nextLayout = P2CandidateTileLayout(layout.tileEdge, copiedTiles)
        return P2CandidateApplication(
            snapshot = P2TiledCowCandidateSnapshot(shape, changes.revisions.after, nextLayout),
            touchedUnits = touched.size,
            copiedUnits = touched.size,
            sharedUnits = layout.tiles.size - touched.size,
        )
    }

    companion object {
        fun create(
            shape: P2CanvasShape,
            revision: Long,
            pixels: IntArray,
            tileEdge: Int,
        ): P2TiledCowCandidateSnapshot {
            require(shape.pixelCount == pixels.size.toLong()) { "Tiled candidate pixel count mismatch." }
            val layout = P2CandidateTileLayout.create(shape, pixels, tileEdge)
            return P2TiledCowCandidateSnapshot(shape, revision, layout)
        }
    }
}

private data class P2CandidateTileLayout(
    val tileEdge: Int,
    val tiles: Array<IntArray>,
) {
    val primitivePayloadBytes: Long
        get() = tiles.sumOf { tile -> tile.size.toLong() * Int.SIZE_BYTES }

    val representation: P2CandidateRepresentation
        get() =
            when (tileEdge) {
                16 -> P2CandidateRepresentation.TiledCowRgba8888T16
                32 -> P2CandidateRepresentation.TiledCowRgba8888T32
                64 -> P2CandidateRepresentation.TiledCowRgba8888T64
                else -> error("Unsupported candidate tile edge $tileEdge.")
            }

    fun packedAt(
        shape: P2CanvasShape,
        index: Int,
    ): Int {
        val x = index % shape.width
        val y = index / shape.width
        return tiles[tileIndex(shape, x, y)][localIndex(x, y)]
    }

    fun write(
        target: Array<IntArray>,
        shape: P2CanvasShape,
        index: Int,
        value: Int,
    ) {
        val x = index % shape.width
        val y = index / shape.width
        target[tileIndex(shape, x, y)][localIndex(x, y)] = value
    }

    private fun tileIndex(
        shape: P2CanvasShape,
        x: Int,
        y: Int,
    ): Int = (y / tileEdge) * tileColumns(shape) + (x / tileEdge)

    private fun localIndex(
        x: Int,
        y: Int,
    ): Int = (y % tileEdge) * tileEdge + (x % tileEdge)

    private fun tileColumns(shape: P2CanvasShape): Int = (shape.width + tileEdge - 1) / tileEdge

    companion object {
        fun create(
            shape: P2CanvasShape,
            pixels: IntArray,
            tileEdge: Int,
        ): P2CandidateTileLayout {
            require(tileEdge in SUPPORTED_TILE_EDGES) { "Unsupported candidate tile edge $tileEdge." }
            val columns = (shape.width + tileEdge - 1) / tileEdge
            val rows = (shape.height + tileEdge - 1) / tileEdge
            val tiles = Array(columns * rows) { IntArray(tileEdge * tileEdge) }
            val layout = P2CandidateTileLayout(tileEdge, tiles)
            pixels.indices.forEach { index -> layout.write(tiles, shape, index, pixels[index]) }
            return layout
        }

        private val SUPPORTED_TILE_EDGES: Set<Int> = setOf(16, 32, 64)
    }
}

internal sealed interface P2PaletteCandidateResult {
    data class Created(
        val snapshot: P2PaletteCandidateSnapshot,
    ) : P2PaletteCandidateResult

    data class Rejected(
        val rejection: P2PaletteCandidateRejection,
    ) : P2PaletteCandidateResult
}

internal enum class P2PaletteCandidateRejection {
    MoreThan256SemanticColors,
}

internal class P2PaletteCandidateSnapshot private constructor(
    val shape: P2CanvasShape,
    private val palette: IntArray,
    private val indexes: ByteArray,
) {
    val primitivePayloadBytes: Long
        get() = palette.size.toLong() * Int.SIZE_BYTES + indexes.size

    val colorCardinality: Int
        get() = palette.size

    fun packedAt(index: Int): Int = palette[indexes[index].toUByte().toInt()]

    fun semanticDigest(): Int {
        var digest = shape.hashCode()
        repeat(indexes.size) { index -> digest = HASH_MULTIPLIER * digest + packedAt(index) }
        return digest
    }

    companion object {
        fun create(
            shape: P2CanvasShape,
            pixels: IntArray,
        ): P2PaletteCandidateResult {
            require(shape.pixelCount == pixels.size.toLong()) { "Palette candidate pixel count mismatch." }
            val paletteIndex = linkedMapOf<Int, Int>()
            val indexes = ByteArray(pixels.size)
            pixels.forEachIndexed { index, packed ->
                val assigned = paletteIndex[packed] ?: paletteIndex.size.also { paletteIndex[packed] = it }
                if (assigned >= MAX_U8_COLORS) {
                    return P2PaletteCandidateResult.Rejected(
                        P2PaletteCandidateRejection.MoreThan256SemanticColors,
                    )
                }
                indexes[index] = assigned.toByte()
            }
            return P2PaletteCandidateResult.Created(
                P2PaletteCandidateSnapshot(shape, paletteIndex.keys.toIntArray(), indexes),
            )
        }

        private const val MAX_U8_COLORS: Int = 256
        private const val HASH_MULTIPLIER: Int = 31
    }
}

internal object P2PackedRgba8888 {
    fun pack(color: PixelColor): Int =
        (color.red.value.toInt() shl RED_SHIFT) or
            (color.green.value.toInt() shl GREEN_SHIFT) or
            (color.blue.value.toInt() shl BLUE_SHIFT) or
            color.alpha.value.toInt()

    fun unpack(packed: Int): PixelColor =
        PixelColor.create(
            red = channel(packed ushr RED_SHIFT and CHANNEL_MASK),
            green = channel(packed ushr GREEN_SHIFT and CHANNEL_MASK),
            blue = channel(packed ushr BLUE_SHIFT and CHANNEL_MASK),
            alpha = channel(packed and CHANNEL_MASK),
        )

    private fun channel(value: Int): ColorChannel =
        when (val result = ColorChannel.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Packed candidate channel was invalid: ${result.rejection}")
        }

    private const val RED_SHIFT: Int = 24
    private const val GREEN_SHIFT: Int = 16
    private const val BLUE_SHIFT: Int = 8
    private const val CHANNEL_MASK: Int = 0xff
}

private fun P2CandidateChanges.touchedTileIndexes(
    shape: P2CanvasShape,
    tileEdge: Int,
): Set<Int> {
    val tileColumns = (shape.width + tileEdge - 1) / tileEdge
    return (0 until changeCount)
        .map { index ->
            val position = positionAt(index)
            val x = position % shape.width
            val y = position / shape.width
            (y / tileEdge) * tileColumns + (x / tileEdge)
        }.toSet()
}
