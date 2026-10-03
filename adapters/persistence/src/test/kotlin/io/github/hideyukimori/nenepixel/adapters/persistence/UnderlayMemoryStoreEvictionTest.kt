package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.document
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.image
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.imageName
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.recalled
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.stateName
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.underlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UnderlayMemoryStoreEvictionTest {
    private val files = InMemoryUnderlayRecordFiles()
    private val store = UnderlayMemoryStore(files)

    @Test
    fun `the seventeenth work deletes the least recently used pair`() {
        val small = image(1, 1, 1)
        rememberWorks(0..16, small)

        assertEquals(storedIds(1..16), storedPairIds())
        assertEquals(2 * UnderlayMemoryLayout.MAX_REMEMBERED_WORKS, files.names().size)
    }

    @Test
    fun `a recalled work becomes the most recently used and stays`() {
        val small = image(1, 1, 1)
        rememberWorks(0..15, small)
        recalled(UnderlayMemoryStore(files).recall(document(0)))

        rememberWorks(16..16, small)

        assertEquals(storedIds(listOf(0) + (2..16)), storedPairIds())
    }

    @Test
    fun `equal times delete the smaller id first`() {
        val small = image(1, 1, 1)
        rememberWorks(0..15, small)
        files.setUsedAt(stateName(5), 0L)
        files.setUsedAt(stateName(3), 0L)

        rememberWorks(16..16, small)

        assertEquals(storedIds((0..16).filter { number -> number != 3 }), storedPairIds())
    }

    @Test
    fun `nine of the largest images leave eight within the byte limit`() {
        val largest = image(ReferenceImage.MAX_SIDE, ReferenceImage.MAX_SIDE, 3)

        rememberWorks(0..8, largest)

        assertEquals(storedIds(1..8), storedPairIds())
        assertTrue(imageByteCount() <= UnderlayMemoryLayout.MAX_IMAGE_RECORD_TOTAL_BYTE_COUNT)
    }

    @Test
    fun `the pair just written stays even when it looks the oldest`() {
        val largest = image(ReferenceImage.MAX_SIDE, ReferenceImage.MAX_SIDE, 4)
        rememberWorks(0..7, largest)
        (0..7).forEach { number -> files.setUsedAt(stateName(number), 1_000L + number) }

        rememberWorks(8..8, largest)

        assertTrue(files.usedAt(stateName(8)) < 1_000L)
        assertEquals(storedIds(1..8), storedPairIds())
    }

    @Test
    fun `files outside complete pairs are deleted after remember`() {
        val lone = UnderlayImageRecord.encode(image(1, 1, 2))
        listOf("notes.txt", imageName(20), stateName(21), imageName(22) + ".new", imageName(2).uppercase())
            .forEach { name -> files.put(name, lone) }

        rememberWorks(1..1, image(1, 1, 1))

        assertEquals(listOf(imageName(1), stateName(1)), files.names().sorted())
    }

    private fun rememberWorks(
        numbers: IntRange,
        image: ReferenceImage,
    ) {
        numbers.forEach { number ->
            store.remember(document(number), underlay(image, number.toDouble(), UnderlayVisibility.Shown))
        }
    }

    private fun storedPairIds(): Set<String> = UnderlayRecordNames.completePairIds(files.names())

    private fun storedIds(numbers: Iterable<Int>): Set<String> =
        numbers.map { number -> document(number).value }.toSet()

    private fun imageByteCount(): Long =
        UnderlayRecordNames
            .completePairIds(files.names())
            .sumOf { id -> files.length(UnderlayRecordNames.image(id)) }
}
