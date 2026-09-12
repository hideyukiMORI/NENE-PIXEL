package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentDimension
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRejection
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequestResult
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
            exportPng = {},
            saveAs = {},
            load = {},
            createNewDocument = createNewDocument,
            confirm = {},
            cancel = {},
            acceptRecovery = {},
            declineRecovery = {},
        )
}
