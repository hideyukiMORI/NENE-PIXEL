package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor

internal enum class P2CandidateNativePatchWorkloadKind(
    val csvName: String,
    val pathKind: P2CandidatePathKind,
) {
    DenseFullCanvasAnchor("dense_full_canvas_anchor", P2CandidatePathKind.FullCanvasSerpentine),
    OnePixel("one_pixel", P2CandidatePathKind.OnePixel),
    Diagonal("diagonal", P2CandidatePathKind.Diagonal),
    FullRow("full_row", P2CandidatePathKind.FullRow),
    FullColumn("full_column", P2CandidatePathKind.FullColumn),
    QuarterSerpentine("quarter_serpentine", P2CandidatePathKind.QuarterSerpentine),
    HalfSerpentine("half_serpentine", P2CandidatePathKind.HalfSerpentine),
    FullCanvasSerpentine("full_canvas_serpentine", P2CandidatePathKind.FullCanvasSerpentine),
    ;

    companion object {
        val SparseRectangular: List<P2CandidateNativePatchWorkloadKind> = entries.drop(1)
    }
}

internal class P2CandidateWorkloadFixture private constructor(
    val shape: P2CanvasShape,
    val pathKind: P2CandidatePathKind,
    val initialPixels: IntArray,
    val pathPositions: IntArray,
    val canonicalPositions: IntArray,
    val reverseCanonicalPositions: IntArray,
    val reverseCanonicalAfter: List<PixelColor>,
    val appliedPixels: IntArray,
    val conflictedPixels: IntArray,
    val affectedRegion: P2CandidateAffectedRegion,
) {
    val changeCount: Int
        get() = canonicalPositions.size

    val conflictPosition: Int
        get() = canonicalPositions.last()

    val unaffectedPixelCount: Int
        get() = shape.pixelCount.toInt() - changeCount

    fun afterColors(positions: IntArray): List<PixelColor> =
        positions.map { position -> P2PackedRgba8888.unpack(afterPacked(position)) }

    fun afterPacked(position: Int): Int = initialPixels[position] xor ALPHA_XOR_MASK

    fun assertUnmodified() {
        check(initialPixels.contentEquals(highEntropyPixels(shape))) { "Candidate initial fixture was modified." }
        check(pathPositions.contentEquals(pathKind.positions(shape))) {
            "Candidate path fixture was modified."
        }
        check(canonicalPositions.contentEquals(pathPositions.sortedArray())) {
            "Candidate canonical fixture was modified."
        }
        check(reverseCanonicalPositions.contentEquals(canonicalPositions.reversedArray())) {
            "Candidate reverse fixture was modified."
        }
        check(
            reverseCanonicalAfter.map(P2PackedRgba8888::pack) ==
                reverseCanonicalPositions.map(::afterPacked),
        ) {
            "Candidate reverse after fixture was modified."
        }
        check(appliedPixels.contentEquals(expectedAppliedPixels())) { "Candidate applied fixture was modified." }
        check(conflictedPixels.contentEquals(expectedConflictedPixels())) { "Candidate conflict fixture was modified." }
    }

    private fun expectedAppliedPixels(): IntArray =
        initialPixels.copyOf().also { pixels ->
            canonicalPositions.forEach { position -> pixels[position] = afterPacked(position) }
        }

    private fun expectedConflictedPixels(): IntArray =
        initialPixels.copyOf().also { pixels -> pixels[conflictPosition] = afterPacked(conflictPosition) }

    companion object {
        fun create(
            shape: P2CanvasShape,
            pathKind: P2CandidatePathKind,
        ): P2CandidateWorkloadFixture {
            require(pathKind != P2CandidatePathKind.None) { "Native patch workload must contain changes." }
            val initial = highEntropyPixels(shape)
            val pathPositions = pathKind.positions(shape)
            val canonicalPositions = pathPositions.sortedArray()
            val reversePositions = canonicalPositions.reversedArray()
            val reverseAfter =
                reversePositions.map { position ->
                    P2PackedRgba8888.unpack(initial[position] xor ALPHA_XOR_MASK)
                }
            val applied = initial.copyOf()
            canonicalPositions.forEach { position -> applied[position] = initial[position] xor ALPHA_XOR_MASK }
            val conflictPosition = canonicalPositions.last()
            val conflicted = initial.copyOf().also { pixels -> pixels[conflictPosition] = applied[conflictPosition] }
            return P2CandidateWorkloadFixture(
                shape,
                pathKind,
                initial,
                pathPositions,
                canonicalPositions,
                reversePositions,
                reverseAfter,
                applied,
                conflicted,
                affectedRegion(shape, canonicalPositions),
            )
        }

        fun highEntropyPixels(shape: P2CanvasShape): IntArray = IntArray(shape.pixelCount.toInt(), ::highEntropyPacked)

        private fun affectedRegion(
            shape: P2CanvasShape,
            positions: IntArray,
        ): P2CandidateAffectedRegion {
            val x = positions.map { position -> position % shape.width }
            val y = positions.map { position -> position / shape.width }
            return P2CandidateAffectedRegion(
                left = x.min(),
                top = y.min(),
                width = x.max() - x.min() + 1,
                height = y.max() - y.min() + 1,
            )
        }

        fun highEntropyPacked(index: Int): Int =
            ((index and CHANNEL_MASK) shl RED_SHIFT) or
                ((index ushr BYTE_BITS and CHANNEL_MASK) shl GREEN_SHIFT) or
                ((index * BLUE_MULTIPLIER and CHANNEL_MASK) shl BLUE_SHIFT) or
                (index * ALPHA_MULTIPLIER and CHANNEL_MASK)

        private const val RED_SHIFT: Int = 24
        private const val GREEN_SHIFT: Int = 16
        private const val BLUE_SHIFT: Int = 8
        private const val BYTE_BITS: Int = 8
        private const val CHANNEL_MASK: Int = 0xff
        private const val BLUE_MULTIPLIER: Int = 29
        private const val ALPHA_MULTIPLIER: Int = 43
        private const val ALPHA_XOR_MASK: Int = 0x000000ff
    }
}

internal fun P2CandidatePathKind.positions(canvas: P2CanvasShape): IntArray =
    when (this) {
        P2CandidatePathKind.None -> error("Snapshot build has no change positions.")
        P2CandidatePathKind.OnePixel -> intArrayOf(canvas.pixelCount.toInt() / 2)
        P2CandidatePathKind.Diagonal -> canvas.diagonalPositions()
        P2CandidatePathKind.FullRow -> IntArray(canvas.width) { x -> (canvas.height / 2) * canvas.width + x }
        P2CandidatePathKind.FullColumn -> IntArray(canvas.height) { y -> y * canvas.width + canvas.width / 2 }
        P2CandidatePathKind.QuarterSerpentine -> canvas.serpentinePositions(canvas.pixelCount.toInt() / 4)
        P2CandidatePathKind.HalfSerpentine -> canvas.serpentinePositions(canvas.pixelCount.toInt() / 2)
        P2CandidatePathKind.FullCanvasSerpentine -> canvas.serpentinePositions(canvas.pixelCount.toInt())
    }

internal fun P2CandidatePathKind.changeCount(canvas: P2CanvasShape): Int =
    if (this == P2CandidatePathKind.None) 0 else positions(canvas).size

private fun P2CanvasShape.diagonalPositions(): IntArray =
    IntArray(minOf(width, height)) { index -> index * width + index }

private fun P2CanvasShape.serpentinePositions(limit: Int): IntArray {
    val positions = IntArray(limit)
    var outputIndex = 0
    for (y in 0 until height) {
        val xRange = if (y % 2 == 0) 0 until width else width - 1 downTo 0
        for (x in xRange) {
            if (outputIndex == limit) return positions
            positions[outputIndex] = y * width + x
            outputIndex += 1
        }
    }
    check(outputIndex == limit) { "Serpentine candidate path was incomplete." }
    return positions
}
