package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.document
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.image
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.imageName
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.recalled
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.stateName
import io.github.hideyukimori.nenepixel.adapters.persistence.UnderlayMemoryStoreTestValues.underlay
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

internal class UnderlayMemoryStoreRememberTest {
    private val files = InMemoryUnderlayRecordFiles()
    private val store = UnderlayMemoryStore(files)
    private val firstImage = image(4, 3, 11)

    @Test
    fun `the same image instance is neither read nor written again`() {
        store.remember(document(1), underlay(firstImage, 1.0, UnderlayVisibility.Shown))
        val before = files.operations.size

        store.remember(document(1), underlay(firstImage, 2.0, UnderlayVisibility.Shown))

        assertEquals(emptyList<String>(), imageOperationsSince(before))
    }

    @Test
    fun `a recalled image instance is neither read nor written again`() {
        store.remember(document(1), underlay(firstImage, 1.0, UnderlayVisibility.Shown))
        val other = UnderlayMemoryStore(files)
        val restored = recalled(other.recall(document(1)))
        val before = files.operations.size

        other.remember(document(1), underlay(restored.image, 5.0, UnderlayVisibility.Hidden))

        assertEquals(emptyList<String>(), imageOperationsSince(before))
    }

    @Test
    fun `another instance with the same pixels is not written`() {
        store.remember(document(1), underlay(firstImage, 1.0, UnderlayVisibility.Shown))
        val before = files.operations.size

        store.remember(document(1), underlay(image(4, 3, 11), 1.0, UnderlayVisibility.Shown))

        assertEquals(listOf("read ${imageName(1)}"), imageOperationsSince(before))
    }

    @Test
    fun `an image with other pixels is written`() {
        store.remember(document(1), underlay(firstImage, 1.0, UnderlayVisibility.Shown))
        val before = files.operations.size

        store.remember(document(1), underlay(image(4, 3, 12), 1.0, UnderlayVisibility.Shown))

        assertEquals(listOf("read ${imageName(1)}", "write ${imageName(1)}"), imageOperationsSince(before))
    }

    @Test
    fun `moving the underlay writes only the state record once`() {
        store.remember(document(1), underlay(firstImage, 1.0, UnderlayVisibility.Shown))
        val before = files.operations.size

        store.remember(document(1), underlay(firstImage, 9.0, UnderlayVisibility.Shown))
        store.remember(document(1), underlay(firstImage, 9.0, UnderlayVisibility.Shown))

        val writes = files.operations.drop(before).filter { operation -> operation.startsWith("write") }
        assertEquals(listOf("write ${stateName(1)}"), writes)
    }

    @Test
    fun `a failed write comes out of remember unchanged`() {
        files.failingWrites = setOf(imageName(1))

        assertThrows(IOException::class.java) {
            store.remember(document(1), underlay(firstImage, 1.0, UnderlayVisibility.Shown))
        }
        assertNull(files.contentOf(imageName(1)))
    }

    @Test
    fun `an image written before a failed state write is deleted by the next recall`() {
        files.failingWrites = setOf(stateName(1))

        assertThrows(IOException::class.java) {
            store.remember(document(1), underlay(firstImage, 1.0, UnderlayVisibility.Shown))
        }
        files.failingWrites = emptySet()

        assertEquals(UnderlayRecollection.Absent, store.recall(document(1)))
        assertNull(files.contentOf(imageName(1)))
        assertNull(files.contentOf(stateName(1)))
    }

    @Test
    fun `after forget the same image instance is written again`() {
        store.remember(document(1), underlay(firstImage, 1.0, UnderlayVisibility.Shown))
        store.forget(document(1))
        val before = files.operations.size

        store.remember(document(1), underlay(firstImage, 1.0, UnderlayVisibility.Shown))

        assertEquals(listOf("read ${imageName(1)}", "write ${imageName(1)}"), imageOperationsSince(before))
    }

    private fun imageOperationsSince(count: Int): List<String> =
        files.operations
            .drop(count)
            .filter { operation -> operation == "read ${imageName(1)}" || operation == "write ${imageName(1)}" }
}
