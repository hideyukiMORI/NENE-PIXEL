package io.github.hideyukimori.nenepixel.measurement

internal enum class P2PackedCandidateKind(
    val candidateId: String,
) {
    Flat("flat-packed-rgba8888-v1"),
    TiledCow16("tiled-cow-rgba8888-t16-v1"),
}

internal enum class P2PackedCandidateWorkload(
    val metricName: String,
) {
    SparseApply("sparse_apply"),
    DenseApply("dense_apply"),
    DenseNoOp("dense_no_op"),
    DenseUndo("dense_undo"),
    DenseRedo("dense_redo"),
}

internal data class P2PackedCandidateOutcome(
    val revision: Long,
    val firstPixel: Int,
    val lastPixel: Int,
    val changeCount: Int,
)

internal sealed class P2PackedCandidateSnapshot {
    abstract val kind: P2PackedCandidateKind
    abstract val revision: Long

    abstract fun packedAt(index: Int): Int

    abstract fun apply(patch: P2PackedCandidatePatch): P2PackedCandidateSnapshot

    fun verifyUniform(expected: Int) {
        repeat(P2AndroidPackedCandidateProtocol.PIXEL_COUNT) { index ->
            check(packedAt(index) == expected) { "Candidate pixel mismatch at $index." }
        }
    }

    fun verifySparse(expectedChanged: Int) {
        repeat(P2AndroidPackedCandidateProtocol.PIXEL_COUNT) { index ->
            val coordinate = index / P2AndroidPackedCandidateProtocol.CANVAS_EDGE
            val expected =
                if (index == coordinate * P2AndroidPackedCandidateProtocol.CANVAS_EDGE + coordinate) {
                    expectedChanged
                } else {
                    P2AndroidPackedCandidateProtocol.SOURCE_RGBA
                }
            check(packedAt(index) == expected) { "Candidate sparse pixel mismatch at $index." }
        }
    }
}

internal class P2FlatPackedCandidateSnapshot private constructor(
    override val revision: Long,
    private val pixels: IntArray,
) : P2PackedCandidateSnapshot() {
    override val kind: P2PackedCandidateKind = P2PackedCandidateKind.Flat

    override fun packedAt(index: Int): Int = pixels[index]

    override fun apply(patch: P2PackedCandidatePatch): P2PackedCandidateSnapshot {
        patch.validateAgainst(this)
        val next = pixels.copyOf()
        patch.writeTo(next)
        return P2FlatPackedCandidateSnapshot(patch.afterRevision, next)
    }

    companion object {
        fun initial(pixels: IntArray): P2FlatPackedCandidateSnapshot =
            P2FlatPackedCandidateSnapshot(P2AndroidPackedCandidateProtocol.INITIAL_REVISION, pixels.copyOf())
    }
}

internal class P2TiledCow16CandidateSnapshot private constructor(
    override val revision: Long,
    private val tiles: Array<IntArray>,
) : P2PackedCandidateSnapshot() {
    override val kind: P2PackedCandidateKind = P2PackedCandidateKind.TiledCow16

    override fun packedAt(index: Int): Int = tiles[tileIndex(index)][localIndex(index)]

    override fun apply(patch: P2PackedCandidatePatch): P2PackedCandidateSnapshot {
        patch.validateAgainst(this)
        val next = tiles.copyOf()
        val copied = BooleanArray(next.size)
        repeat(patch.changeCount) { index ->
            val position = patch.positionAt(index)
            val tileIndex = tileIndex(position)
            if (!copied[tileIndex]) {
                next[tileIndex] = next[tileIndex].copyOf()
                copied[tileIndex] = true
            }
            next[tileIndex][localIndex(position)] = patch.afterAt(index)
        }
        return P2TiledCow16CandidateSnapshot(patch.afterRevision, next)
    }

    private fun tileIndex(index: Int): Int {
        val x = index % P2AndroidPackedCandidateProtocol.CANVAS_EDGE
        val y = index / P2AndroidPackedCandidateProtocol.CANVAS_EDGE
        return (y / TILE_EDGE) * TILE_COLUMNS + (x / TILE_EDGE)
    }

    private fun localIndex(index: Int): Int {
        val x = index % P2AndroidPackedCandidateProtocol.CANVAS_EDGE
        val y = index / P2AndroidPackedCandidateProtocol.CANVAS_EDGE
        return (y % TILE_EDGE) * TILE_EDGE + (x % TILE_EDGE)
    }

    companion object {
        private const val TILE_EDGE: Int = 16
        private const val TILE_COLUMNS: Int = P2AndroidPackedCandidateProtocol.CANVAS_EDGE / TILE_EDGE

        fun initial(pixels: IntArray): P2TiledCow16CandidateSnapshot {
            val tiles = Array(TILE_COLUMNS * TILE_COLUMNS) { IntArray(TILE_EDGE * TILE_EDGE) }
            val initial = P2TiledCow16CandidateSnapshot(P2AndroidPackedCandidateProtocol.INITIAL_REVISION, tiles)
            pixels.forEachIndexed { index, pixel ->
                tiles[initial.tileIndex(index)][initial.localIndex(index)] = pixel
            }
            return initial
        }
    }
}

internal class P2PackedCandidatePatch private constructor(
    private val storage: P2PackedCandidatePatchStorage,
    private val direction: P2PackedCandidatePatchDirection,
) {
    val changeCount: Int
        get() = storage.positions.size

    val beforeRevision: Long
        get() =
            when (direction) {
                P2PackedCandidatePatchDirection.Forward -> storage.beforeRevision
                P2PackedCandidatePatchDirection.Reverse -> storage.afterRevision
            }

    val afterRevision: Long
        get() =
            when (direction) {
                P2PackedCandidatePatchDirection.Forward -> storage.afterRevision
                P2PackedCandidatePatchDirection.Reverse -> storage.beforeRevision
            }

    fun inverse(): P2PackedCandidatePatch = P2PackedCandidatePatch(storage, direction.inverse())

    fun validateAgainst(snapshot: P2PackedCandidateSnapshot) {
        check(snapshot.revision == beforeRevision) { "Candidate revision mismatch." }
        storage.positions.indices.forEach { index ->
            check(snapshot.packedAt(storage.positions[index]) == beforeAt(index)) { "Candidate before-value mismatch." }
        }
    }

    fun writeTo(target: IntArray) {
        storage.positions.indices.forEach { index -> target[storage.positions[index]] = afterAt(index) }
    }

    fun positionAt(index: Int): Int = storage.positions[index]

    private fun beforeAt(index: Int): Int =
        if (direction == P2PackedCandidatePatchDirection.Forward) storage.before[index] else storage.after[index]

    fun afterAt(index: Int): Int =
        if (direction == P2PackedCandidatePatchDirection.Forward) storage.after[index] else storage.before[index]

    companion object {
        fun fromRawPath(
            snapshot: P2PackedCandidateSnapshot,
            rawPositions: IntArray,
            target: Int,
        ): P2PackedCandidatePatch? {
            val seen = BooleanArray(P2AndroidPackedCandidateProtocol.PIXEL_COUNT)
            val effective = IntArray(rawPositions.size)
            var count = 0
            rawPositions.forEach { position ->
                check(position in seen.indices) { "Candidate raw position is outside canvas." }
                if (!seen[position]) {
                    seen[position] = true
                    if (snapshot.packedAt(position) != target) {
                        effective[count] = position
                        count += 1
                    }
                }
            }
            if (count == 0) return null
            val positions = effective.copyOf(count).also { canonical -> canonical.sort() }
            return P2PackedCandidatePatch(
                storage =
                    P2PackedCandidatePatchStorage(
                        positions = positions,
                        before = IntArray(count) { index -> snapshot.packedAt(positions[index]) },
                        after = IntArray(count) { target },
                        revisions = P2PackedCandidateRevisions(snapshot.revision, snapshot.revision + 1L),
                    ),
                direction = P2PackedCandidatePatchDirection.Forward,
            )
        }
    }
}

private data class P2PackedCandidatePatchStorage(
    val positions: IntArray,
    val before: IntArray,
    val after: IntArray,
    val revisions: P2PackedCandidateRevisions,
) {
    val beforeRevision: Long
        get() = revisions.before

    val afterRevision: Long
        get() = revisions.after
}

private data class P2PackedCandidateRevisions(
    val before: Long,
    val after: Long,
)

private enum class P2PackedCandidatePatchDirection {
    Forward,
    Reverse,
    ;

    fun inverse(): P2PackedCandidatePatchDirection = if (this == Forward) Reverse else Forward
}
