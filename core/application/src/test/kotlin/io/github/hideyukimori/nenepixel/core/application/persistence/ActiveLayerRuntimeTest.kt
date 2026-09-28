package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class ActiveLayerRuntimeTest {
    private val size = canvas(2, 2)
    private val topId = LayerId.create(2).value()

    @Test
    fun `an installed document starts on its top layer and keeps it after a command`() =
        runBlocking {
            val fixture = initializedFixture()
            val document = twoLayers(documentId('c'))
            fixture.storage.loadHandler = { ProjectLoadOutcome.Loaded(DocumentImportSource.Current(document)) }

            fixture.workflow.load()

            assertEquals(document, fixture.runtime.state.documentState)
            assertEquals(topId, fixture.runtime.state.workspaceState.activeLayerId)
            val command =
                ApplyStrokeCommand.create(
                    fixture.runtime.captureSource(),
                    topId,
                    stroke(size, listOf(position(0, 0)), redIndex),
                )
            assertInstanceOf(CommandResult.Applied::class.java, fixture.runtime.execute(command))
            assertEquals(topId, fixture.runtime.state.workspaceState.activeLayerId)
        }

    @Test
    fun `a new single-layer document installs its only layer`() =
        runBlocking {
            val fixture = initializedFixture()
            val document = twoLayers(documentId('d'))
            fixture.storage.loadHandler = { ProjectLoadOutcome.Loaded(DocumentImportSource.Current(document)) }
            fixture.workflow.load()

            fixture.workflow.createNewDocument(newRequest(3, 2))

            assertEquals(canvas(3, 2), fixture.runtime.state.documentState.size)
            assertEquals(LayerId.first(), fixture.runtime.state.workspaceState.activeLayerId)
        }

    private fun twoLayers(id: DocumentId): DocumentState =
        DocumentState
            .createLayered(
                id,
                Revision.initial(),
                defaultDefinition,
                listOf(
                    Layer.create(LayerId.first(), LayerName.empty, LayerVisibility.Visible, snapshot(size)),
                    Layer.create(topId, LayerName.empty, LayerVisibility.Visible, PixelSnapshot.createEmpty(size)),
                ),
            ).value()

    private fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Expected a created value but was $rejection")
        }
}
