package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayPublication
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayPublicationMode
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayPublicationStart
import io.github.hideyukimori.nenepixel.core.application.editor.UnderlayRecallStart
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf

/** Runtime-level helpers for the underlay memory (ADR 0034); the 4 x 4 canvas is the fixture's. */
internal fun memoryUnderlay(width: Int = 8): ReferenceUnderlay =
    ReferenceUnderlay.placed(referenceImage(width, 4), canvas(4, 4))

internal fun remembered(underlay: ReferenceUnderlay?): RememberedUnderlay? = underlay?.let(RememberedUnderlay::of)

internal fun recollectionOf(underlay: ReferenceUnderlay): UnderlayRecollection =
    UnderlayRecollection.Remembered(RememberedUnderlay.of(underlay))

internal fun placeUnderlay(
    fixture: Fixture,
    underlay: ReferenceUnderlay,
) {
    fixture.runtime.reduce(WorkspaceAction.SetReferenceUnderlay(underlay))
}

internal suspend fun installNewDocument(fixture: Fixture) {
    fixture.recovery.retireHandler = { expected -> RecoveryRetirementOutcome.Retired(nextGeneration(expected)) }
    assertCompleted(PersistenceLastOutcome.NewDocumentCreated, fixture.workflow.createNewDocument(newRequest(4, 4)))
}

internal fun assertRecallPending(fixture: Fixture) {
    assertInstanceOf(UnderlayMemoryProjection.RecallPending::class.java, fixture.runtime.underlayMemory.value)
}

internal fun assertPublishPending(fixture: Fixture) {
    assertInstanceOf(UnderlayMemoryProjection.PublishPending::class.java, fixture.runtime.underlayMemory.value)
}

internal fun assertSettled(fixture: Fixture) {
    assertEquals(UnderlayMemoryProjection.Settled, fixture.runtime.underlayMemory.value)
}

internal fun beginRecall(fixture: Fixture): UnderlayRecallStart.Start =
    assertInstanceOf(
        UnderlayRecallStart.Start::class.java,
        fixture.runtime.underlayMemoryOperations.beginRecall(),
    )

internal fun beginPublication(
    fixture: Fixture,
    mode: UnderlayPublicationMode = UnderlayPublicationMode.Publish,
): UnderlayPublication =
    assertInstanceOf(
        UnderlayPublicationStart.Start::class.java,
        fixture.runtime.underlayMemoryOperations.beginPublication(mode),
    ).publication
