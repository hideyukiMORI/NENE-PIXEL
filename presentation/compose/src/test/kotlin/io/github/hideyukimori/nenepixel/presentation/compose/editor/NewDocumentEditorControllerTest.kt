package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentDimension
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRejection
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyReductionHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class NewDocumentEditorControllerTest {
    @Test
    fun `valid dimensions submit the typed application request exactly once`() {
        val submitted = mutableListOf<NewDocumentRequestResult>()
        val callbacks = callbacks(submitted::add)

        val result = callbacks.onCreateNewDocument("3", "2")

        assertEquals(NewDocumentSubmission.Submitted, result)
        val request = assertInstanceOf(NewDocumentRequestResult.Created::class.java, submitted.single())
        assertEquals(3, request.request.canvas.width.value)
        assertEquals(2, request.request.canvas.height.value)
    }

    @Test
    fun `invalid dimensions remain visible and submit no persistence request`() {
        val submitted = mutableListOf<NewDocumentRequestResult>()
        val callbacks = callbacks(submitted::add)

        val result = callbacks.onCreateNewDocument("257", "4")
        val rejected = assertInstanceOf(NewDocumentSubmission.Rejected::class.java, result)

        val reason =
            assertInstanceOf(
                NewDocumentRejection.OutsideSupportedRange::class.java,
                rejected.rejection,
            )
        assertEquals(
            NewDocumentDimension.Width,
            reason.dimension,
        )
        assertEquals(257, reason.attemptedValue)
        assertEquals(1, reason.minimum)
        assertEquals(256, reason.maximum)
        assertEquals(emptyList<NewDocumentRequestResult>(), submitted)
    }

    private fun callbacks(createNewDocument: (NewDocumentRequestResult) -> Unit): EditorPersistenceCallbacks =
        EditorPersistenceCallbacks.create(
            ProjectFileCallbacks(
                exportPng = {},
                saveAs = {},
                load = {},
                createNewDocument = createNewDocument,
            ),
            PersistenceDecisionCallbacks(
                confirm = { _: PersistenceConfirmationRequest -> },
                cancel = { _: PersistenceOperationHandle -> },
                acceptRecovery = {},
                declineRecovery = {},
            ),
            LegacyConversionCallbacks(
                copyOriginal = { _: PersistenceOperationHandle -> },
                preview = { _: PersistenceOperationHandle, _: PaletteDefinition -> },
                accept = { _: LegacyReductionHandle -> },
                declineRecovery = { _: PersistenceOperationHandle -> },
            ),
            presets = testPresets(),
        )

    private fun testPresets(): LegacyPalettePresets {
        val definition =
            PresentationTestValues.definition(
                listOf(PresentationTestValues.red, PresentationTestValues.green),
            )
        return LegacyPalettePresets(definition, definition, definition)
    }
}
