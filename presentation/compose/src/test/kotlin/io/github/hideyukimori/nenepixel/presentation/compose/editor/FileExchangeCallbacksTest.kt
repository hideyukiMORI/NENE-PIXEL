package io.github.hideyukimori.nenepixel.presentation.compose.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FileExchangeCallbacksTest {
    @Test
    fun `each file operation forwards once to its own capability`() {
        val calls = mutableListOf<String>()
        val exchange =
            FileExchangeCallbacks(
                PngExchangeCallbacks(
                    exportPng = { calls.add("png-export") },
                    importPng = { calls.add("png-import") },
                    openAsNewWork = { calls.add("png-new-work") },
                ),
                PaletteExchangeCallbacks(
                    exportJson = { calls.add("palette-export") },
                    importJson = { calls.add("palette-import") },
                ),
                pickReferenceImage = { calls.add("reference-pick") },
            )

        exchange.exportPng()
        exchange.exportPaletteJson()
        exchange.importPaletteJson()
        exchange.pickReferenceImage()
        exchange.importPng()
        exchange.openPngAsNewWork()

        assertEquals(
            listOf("png-export", "palette-export", "palette-import", "reference-pick", "png-import", "png-new-work"),
            calls,
        )
    }
}
