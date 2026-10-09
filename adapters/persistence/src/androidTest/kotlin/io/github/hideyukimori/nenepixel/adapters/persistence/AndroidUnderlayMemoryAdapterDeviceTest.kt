package io.github.hideyukimori.nenepixel.adapters.persistence

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacement
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedPlacementResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

/**
 * One round trip of the largest underlay through the real adapter (ADR 0034). The elapsed times of
 * `remember` and `recall` are reported as the instrumentation result `underlay-memory-cost` and in every
 * assertion message, not in logcat.
 */
@RunWith(AndroidJUnit4::class)
public class AndroidUnderlayMemoryAdapterDeviceTest {
    private val dispatchers = TestCoroutineDispatchers()

    @Test
    public fun theLargestUnderlayRoundTripsThroughTheRealFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parent = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "underlay-memory-").toFile()
        try {
            roundTrip(parent)
        } finally {
            parent.deleteRecursively()
        }
    }

    private fun roundTrip(noBackupDirectory: File) {
        val adapter = AndroidUnderlayMemoryAdapter.create(noBackupDirectory, dispatchers.io)
        val original = underlay()
        val rememberStart = System.nanoTime()
        val outcome = runBlocking { adapter.remember(document(), original) }
        val rememberNanos = System.nanoTime() - rememberStart
        val recallStart = System.nanoTime()
        val recollection = runBlocking { adapter.recall(document()) }
        val recallNanos = System.nanoTime() - recallStart
        val cost = "remember=${rememberNanos / NANOS_PER_MILLI} ms, recall=${recallNanos / NANOS_PER_MILLI} ms"
        report(cost)

        assertEquals(cost, UnderlayMemoryOutcome.Stored, outcome)
        assertTrue(cost, recollection is UnderlayRecollection.Remembered)
        val restored = (recollection as UnderlayRecollection.Remembered).underlay
        assertEquals(cost, ReferenceImage.MAX_SIDE, restored.image.width)
        assertEquals(cost, ReferenceImage.MAX_SIDE, restored.image.height)
        assertArrayEquals(cost, original.image.copyPackedRgba8888(), restored.image.copyPackedRgba8888())
        assertEquals(cost, original.placement, restored.placement)
        assertEquals(cost, original.opacity, restored.opacity)
        assertEquals(cost, original.visibility, restored.visibility)
    }

    private fun report(cost: String) {
        InstrumentationRegistry.getInstrumentation().addResults(Bundle().apply { putString(COST_KEY, cost) })
    }

    private fun document(): DocumentId =
        when (val result = DocumentId.create(DOCUMENT_ID)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> throw AssertionError("invalid document id: ${result.rejection}")
        }

    private fun underlay(): RememberedUnderlay {
        val side = ReferenceImage.MAX_SIDE
        val pixels = IntArray(side * side) { index -> index * PIXEL_STEP }
        val image =
            when (val result = ReferenceImage.create(side, side, pixels)) {
                is ReferenceImageResult.Created -> result.image
                is ReferenceImageResult.Rejected -> throw AssertionError("invalid image: ${result.reason}")
            }
        val placement =
            when (val result = RememberedPlacement.create(PLACEMENT_LEFT, PLACEMENT_TOP, PLACEMENT_SCALE)) {
                is RememberedPlacementResult.Created -> result.placement
                is RememberedPlacementResult.Rejected -> throw AssertionError("invalid placement: ${result.reason}")
            }
        return RememberedUnderlay.create(image, placement, UnderlayOpacity.create(OPACITY), UnderlayVisibility.Shown)
    }

    private companion object {
        const val COST_KEY: String = "underlay-memory-cost"
        const val DOCUMENT_ID: String = "0123456789abcdef0123456789abcdef"
        const val NANOS_PER_MILLI: Long = 1_000_000L
        const val PIXEL_STEP: Int = 0x01030507
        const val PLACEMENT_LEFT: Double = -12.5
        const val PLACEMENT_TOP: Double = 3.0
        const val PLACEMENT_SCALE: Double = 0.25
        const val OPACITY: Int = 128
    }
}
