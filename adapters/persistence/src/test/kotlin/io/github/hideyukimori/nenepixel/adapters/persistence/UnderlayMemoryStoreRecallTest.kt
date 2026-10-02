package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.document
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.image
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.imageName
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.recalled
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.stateName
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.underlay
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UnderlayMemoryStoreRecallTest {
    private val files = InMemoryUnderlayRecordFiles()

    @Test
    fun `a shown and a hidden underlay each round trip value by value`() {
        UnderlayVisibility.entries.forEachIndexed { number, visibility ->
            val original = underlay(image(3, 2, number * 100), 1.5 + number, visibility)
            UnderlayMemoryStore(files).remember(document(number), original)

            val restored = recalled(UnderlayMemoryStore(files).recall(document(number)))

            assertNotSame(original.image, restored.image)
            assertEquals(original.image.width, restored.image.width)
            assertEquals(original.image.height, restored.image.height)
            assertArrayEquals(original.image.copyPackedRgba8888(), restored.image.copyPackedRgba8888())
            assertEquals(original.placement, restored.placement)
            assertEquals(original.opacity, restored.opacity)
            assertEquals(original.visibility, restored.visibility)
        }
    }

    @Test
    fun `a successful recall marks the state record as used`() {
        UnderlayMemoryStore(files).remember(document(1), firstUnderlay())
        files.setUsedAt(stateName(1), 0L)

        recalled(UnderlayMemoryStore(files).recall(document(1)))

        assertTrue(files.usedAt(stateName(1)) > 0L)
        assertEquals("markUsed ${stateName(1)}", files.operations.last())
    }

    @Test
    fun `a missing pair is absent and deletes nothing`() {
        val result = UnderlayMemoryStore(files).recall(document(1))

        assertEquals(UnderlayRecollection.Absent, result)
        assertTrue(files.operations.none { operation -> operation.startsWith("delete") })
    }

    @Test
    fun `forget deletes both records and the next recall is absent`() {
        val store = UnderlayMemoryStore(files)
        store.remember(document(1), firstUnderlay())

        store.forget(document(1))

        assertNull(files.contentOf(imageName(1)))
        assertNull(files.contentOf(stateName(1)))
        assertEquals(UnderlayRecollection.Absent, store.recall(document(1)))
    }

    @Test
    fun `forgetting a work that is not remembered changes nothing`() {
        UnderlayMemoryStore(files).remember(document(1), firstUnderlay())
        val image = files.contentOf(imageName(1))
        val state = files.contentOf(stateName(1))

        UnderlayMemoryStore(files).forget(document(2))

        assertArrayEquals(image, files.contentOf(imageName(1)))
        assertArrayEquals(state, files.contentOf(stateName(1)))
        assertEquals(listOf(imageName(1), stateName(1)), files.names().sorted())
    }

    private fun firstUnderlay() = underlay(image(3, 2, 7), 4.0, UnderlayVisibility.Shown)
}
