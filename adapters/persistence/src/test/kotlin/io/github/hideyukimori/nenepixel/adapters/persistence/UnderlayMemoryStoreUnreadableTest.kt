package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.document
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.image
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.imageName
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.stateName
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.underlay
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class UnderlayMemoryStoreUnreadableTest {
    private val files = InMemoryUnderlayRecordFiles()
    private val remembered = underlay(image(3, 2, 7), 4.0, UnderlayVisibility.Shown)
    private val imageRecord = UnderlayImageRecord.encode(remembered.image)
    private val stateRecord = UnderlayStateRecord.encode(UnderlayImageRecord.checksum(imageRecord), remembered)

    @Test
    fun `a truncated state record is absent and both records are deleted`() {
        rememberWork()
        files.put(stateName(1), stateRecord.copyOf(UnderlayMemoryLayout.STATE_BYTE_COUNT - 1))

        assertUnreadablePairIsDeleted()
    }

    @Test
    fun `an image record with a wrong CRC-32 is absent and both records are deleted`() {
        rememberWork()
        val corrupted = imageRecord.copyOf()
        val firstPixel = UnderlayMemoryLayout.IMAGE_HEADER_BYTE_COUNT
        corrupted[firstPixel] = (corrupted[firstPixel] + 1).toByte()
        files.put(imageName(1), corrupted)

        assertUnreadablePairIsDeleted()
    }

    @Test
    fun `a state that names another image is absent and both records are deleted`() {
        rememberWork()
        val other = UnderlayImageRecord.encode(image(3, 2, 999))
        files.put(stateName(1), UnderlayStateRecord.encode(UnderlayImageRecord.checksum(other), remembered))

        assertUnreadablePairIsDeleted()
    }

    @Test
    fun `a state record of version 2 is absent and both records are deleted`() {
        rememberWork()
        val state = stateRecord.copyOf()
        state[UnderlayMemoryLayout.VERSION_OFFSET + 1] = 2
        UnderlayMemoryLayout.seal(state)
        files.put(stateName(1), state)

        assertUnreadablePairIsDeleted()
    }

    @Test
    fun `an image record without its state is absent and the image record is deleted`() {
        rememberWork()
        files.delete(stateName(1))

        assertUnreadablePairIsDeleted()
    }

    @Test
    fun `a state record without its image is absent and the state record is deleted`() {
        rememberWork()
        files.delete(imageName(1))

        assertUnreadablePairIsDeleted()
    }

    private fun rememberWork() {
        UnderlayMemoryStore(files).remember(document(1), remembered)
    }

    private fun assertUnreadablePairIsDeleted() {
        val result = UnderlayMemoryStore(files).recall(document(1))

        assertEquals(UnderlayRecollection.Absent, result)
        assertNull(files.contentOf(imageName(1)))
        assertNull(files.contentOf(stateName(1)))
    }
}
