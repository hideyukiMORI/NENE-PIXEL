package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import java.security.MessageDigest

internal object P2CandidateDigest {
    fun pixels(snapshot: P2CandidateSnapshot): String {
        val digest = sha256()
        digest.updateInt(snapshot.shape.width)
        digest.updateInt(snapshot.shape.height)
        repeat(snapshot.shape.pixelCount.toInt()) { index -> digest.updateInt(snapshot.packedAt(index)) }
        return digest.digest().hex()
    }

    fun unaffectedPixels(
        snapshot: P2CandidateSnapshot,
        patch: P2CandidatePatch,
    ): String {
        val changed = BooleanArray(snapshot.shape.pixelCount.toInt())
        repeat(patch.changeCount) { index -> changed[patch.positionAt(index)] = true }
        val digest = sha256()
        digest.updateInt(snapshot.shape.width)
        digest.updateInt(snapshot.shape.height)
        changed.indices.filterNot(changed::get).forEach { index ->
            digest.updateInt(index)
            digest.updateInt(snapshot.packedAt(index))
        }
        return digest.digest().hex()
    }

    fun allIndexedPixels(snapshot: P2CandidateSnapshot): String {
        val digest = sha256()
        digest.updateInt(snapshot.shape.width)
        digest.updateInt(snapshot.shape.height)
        repeat(snapshot.shape.pixelCount.toInt()) { index ->
            digest.updateInt(index)
            digest.updateInt(snapshot.packedAt(index))
        }
        return digest.digest().hex()
    }

    fun rawInput(
        snapshot: P2CandidateSnapshot,
        rawPositions: IntArray,
        target: PixelColor,
    ): String {
        val digest = sha256()
        digest.updateInt(RAW_INPUT_TAG)
        digest.updateInt(snapshot.shape.width)
        digest.updateInt(snapshot.shape.height)
        digest.updateLong(snapshot.revision)
        repeat(snapshot.shape.pixelCount.toInt()) { index -> digest.updateInt(snapshot.packedAt(index)) }
        digest.updateInt(rawPositions.size)
        rawPositions.forEach(digest::updateInt)
        digest.updateInt(P2PackedRgba8888.pack(target))
        return digest.digest().hex()
    }

    fun canonicalChanges(patch: P2CandidatePatch?): String {
        val digest = sha256()
        if (patch == null) {
            digest.updateInt(EMPTY_CANONICAL_CHANGES_TAG)
        } else {
            digest.updateInt(CANONICAL_CHANGES_TAG)
            digest.updateInt(patch.changeCount)
            repeat(patch.changeCount) { index ->
                digest.updateInt(patch.positionAt(index))
                digest.updateInt(patch.beforeAt(index))
                digest.updateInt(patch.afterAt(index))
            }
        }
        return digest.digest().hex()
    }

    fun canonicalOrder(patch: P2CandidatePatch): String {
        val digest = sha256()
        repeat(patch.changeCount) { index -> digest.updateInt(patch.positionAt(index)) }
        return digest.digest().hex()
    }

    fun patch(patch: P2CandidatePatch): String {
        val digest = sha256()
        digest.updateInt(patch.shape.width)
        digest.updateInt(patch.shape.height)
        digest.updateLong(patch.revisions.before)
        digest.updateLong(patch.revisions.after)
        digest.updateInt(patch.direction.ordinal)
        digest.updateInt(patch.affectedRegion.left)
        digest.updateInt(patch.affectedRegion.top)
        digest.updateInt(patch.affectedRegion.width)
        digest.updateInt(patch.affectedRegion.height)
        repeat(patch.changeCount) { index ->
            digest.updateInt(patch.positionAt(index))
            digest.updateInt(patch.beforeAt(index))
            digest.updateInt(patch.afterAt(index))
        }
        return digest.digest().hex()
    }

    fun retainedEntryChangeCounts(entries: List<P2CandidateRetainedEntryReference>): String {
        val digest = sha256()
        digest.updateInt(if (entries.isEmpty()) EMPTY_RETAINED_HISTORY_TAG else RETAINED_ENTRY_COUNTS_TAG)
        digest.updateInt(entries.size)
        entries.forEach { entry -> digest.updateInt(entry.forward.changeCount) }
        return digest.digest().hex()
    }

    fun retainedHistory(
        shape: P2CanvasShape,
        entries: List<P2CandidateRetainedEntryReference>,
        currentSnapshot: P2CandidateSnapshot,
    ): String {
        val digest = sha256()
        digest.updateInt(if (entries.isEmpty()) EMPTY_RETAINED_HISTORY_TAG else RETAINED_HISTORY_TAG)
        digest.updateInt(shape.width)
        digest.updateInt(shape.height)
        digest.updateInt(entries.size)
        entries.forEach { entry -> digest.updateRetainedEntry(entry) }
        digest.updateLong(currentSnapshot.revision)
        repeat(shape.pixelCount.toInt()) { index -> digest.updateInt(currentSnapshot.packedAt(index)) }
        return digest.digest().hex()
    }

    private fun MessageDigest.updateRetainedEntry(entry: P2CandidateRetainedEntryReference) {
        val patch = entry.forward
        updateLong(patch.revisions.before)
        updateLong(patch.revisions.after)
        updateInt(patch.affectedRegion.left)
        updateInt(patch.affectedRegion.top)
        updateInt(patch.affectedRegion.width)
        updateInt(patch.affectedRegion.height)
        updateInt(patch.changeCount)
        repeat(patch.changeCount) { index ->
            updateInt(patch.positionAt(index))
            updateInt(patch.beforeAt(index))
            updateInt(patch.afterAt(index))
        }
    }

    private fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

    private const val RAW_INPUT_TAG: Int = 0x52415731
    private const val CANONICAL_CHANGES_TAG: Int = 0x43484e31
    private const val EMPTY_CANONICAL_CHANGES_TAG: Int = 0x43484e30
    private const val RETAINED_ENTRY_COUNTS_TAG: Int = 0x52454331
    private const val RETAINED_HISTORY_TAG: Int = 0x52485431
    private const val EMPTY_RETAINED_HISTORY_TAG: Int = 0x52485430
}

internal fun assertCandidatePixels(
    snapshot: P2CandidateSnapshot,
    expected: IntArray,
) {
    check(snapshot.shape.pixelCount == expected.size.toLong()) { "Candidate semantic size mismatch." }
    expected.indices.forEach { index ->
        check(snapshot.packedAt(index) == expected[index]) { "Candidate semantic mismatch at index $index." }
    }
}

internal fun assertCandidateUnaffectedPixels(
    before: P2CandidateSnapshot,
    after: P2CandidateSnapshot,
    patch: P2CandidatePatch,
) {
    val changed = BooleanArray(before.shape.pixelCount.toInt())
    repeat(patch.changeCount) { index -> changed[patch.positionAt(index)] = true }
    changed.indices.filterNot(changed::get).forEach { index ->
        check(before.packedAt(index) == after.packedAt(index)) {
            "Candidate unaffected pixel changed at index $index."
        }
    }
}

private fun MessageDigest.updateInt(value: Int) {
    update((value ushr 24).toByte())
    update((value ushr 16).toByte())
    update((value ushr 8).toByte())
    update(value.toByte())
}

private fun MessageDigest.updateLong(value: Long) {
    updateInt((value ushr 32).toInt())
    updateInt(value.toInt())
}

private fun ByteArray.hex(): String =
    buildString(size * 2) {
        this@hex.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

private const val HEX_DIGITS: String = "0123456789ABCDEF"
