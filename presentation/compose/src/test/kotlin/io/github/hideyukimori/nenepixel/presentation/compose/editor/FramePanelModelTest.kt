package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FramePanelModelTest {
    @Test
    fun `global record capacity gates frame and layer additions independently of local row count`() {
        val nearlyFull = FrameCapacity(frameCount = 2, layerCount = 15)
        assertTrue(nearlyFull.canAdd)
        assertTrue(nearlyFull.canDuplicate(1))
        assertFalse(nearlyFull.canDuplicate(2))
        val full = FrameCapacity(frameCount = 2, layerCount = 16)
        assertFalse(full.canAdd)
        assertFalse(full.canDuplicate(1))
        val row = LayerRowModel(LayerId.first(), LayerName.empty, LayerVisibility.Visible)
        assertFalse(LayerRowEntry(row, true, LayerRowOrder(0, 1), 16).canDuplicate)
        assertTrue(LayerRowEntry(row, true, LayerRowOrder(0, 1), 16).only)
    }

    @Test
    fun `duration text admits only complete bounded milliseconds`() {
        listOf("", "abc", "9", "60001", "2147483648").forEach {
            assertEquals(FrameDurationInputResult.Invalid, frameDuration(it))
        }
        listOf(FrameDuration.MIN_MILLISECONDS, FrameDuration.MAX_MILLISECONDS).forEach {
            val result = assertInstanceOf(FrameDurationInputResult.Valid::class.java, frameDuration(it.toString()))
            assertEquals(it, result.duration.value)
        }
    }
}
