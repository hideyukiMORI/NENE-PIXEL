package io.github.hideyukimori.nenepixel.adapters.persistence

/**
 * The tidying after each `remember` (ADR 0034): deletes every file that does not belong to a complete pair,
 * then whole pairs, least recently used first (equal times by id), until at most 16 works and at most
 * 33,554,544 bytes of image records remain. The pair of [keptId] is never deleted.
 */
internal class UnderlayMemoryHousekeeping(
    private val files: UnderlayRecordFiles,
) {
    fun tidy(keptId: String) {
        val names = files.names()
        val pairIds = UnderlayRecordNames.completePairIds(names)
        val pairNames =
            pairIds
                .flatMap { id -> listOf(UnderlayRecordNames.image(id), UnderlayRecordNames.state(id)) }
                .toSet()
        names.filterNot { name -> name in pairNames }.forEach(files::delete)
        evict(pairIds.map(::storedPair), keptId)
    }

    private fun evict(
        pairs: List<StoredPair>,
        keptId: String,
    ) {
        var count = pairs.size
        var imageByteCount = pairs.sumOf { pair -> pair.imageByteCount }
        val candidates =
            pairs
                .filter { pair -> pair.id != keptId }
                .sortedWith(compareBy<StoredPair> { pair -> pair.usedAt }.thenBy { pair -> pair.id })
                .iterator()
        while (isOverLimit(count, imageByteCount) && candidates.hasNext()) {
            val oldest = candidates.next()
            files.delete(UnderlayRecordNames.image(oldest.id))
            files.delete(UnderlayRecordNames.state(oldest.id))
            count -= 1
            imageByteCount -= oldest.imageByteCount
        }
    }

    private fun isOverLimit(
        count: Int,
        imageByteCount: Long,
    ): Boolean =
        count > UnderlayMemoryLayout.MAX_REMEMBERED_WORKS ||
            imageByteCount > UnderlayMemoryLayout.MAX_IMAGE_RECORD_TOTAL_BYTE_COUNT

    private fun storedPair(id: String): StoredPair =
        StoredPair(
            id = id,
            usedAt = files.usedAt(UnderlayRecordNames.state(id)),
            imageByteCount = files.length(UnderlayRecordNames.image(id)),
        )

    private class StoredPair(
        val id: String,
        val usedAt: Long,
        val imageByteCount: Long,
    )
}
