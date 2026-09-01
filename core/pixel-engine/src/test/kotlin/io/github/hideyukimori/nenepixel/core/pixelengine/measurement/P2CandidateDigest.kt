package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

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

    private fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")
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
