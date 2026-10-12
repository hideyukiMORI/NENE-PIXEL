package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.PersistenceTransition
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeOwnerEffect
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeOwners
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal object FrameTestValues {
    fun id(value: Int): FrameId = required(FrameId.create(value))

    fun duration(value: Int): FrameDuration = required(FrameDuration.create(value))

    fun frame(
        frameId: Int,
        layers: List<Layer>,
        duration: FrameDuration = FrameDuration.default,
    ): Frame = required(Frame.create(id(frameId), duration, layers))

    fun state(vararg frames: Frame): DocumentState =
        required(DocumentState.create(defaultDocumentId, revision(0), defaultDefinition, frames.toList()))

    fun runtime(document: DocumentState): EditorRuntime {
        val fixture =
            io.github.hideyukimori.nenepixel.core.application.persistence
                .Fixture()
        kotlinx.coroutines.runBlocking { fixture.initialize() }
        val runtime = fixture.runtime
        runtime.transact { transaction ->
            PersistenceTransition(
                transaction.coordination,
                Unit,
                RuntimeOwnerEffect.ReplaceOwners(RuntimeOwners.create(document)),
            )
        }
        return runtime
    }

    fun <T> required(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid frame fixture: ${result.rejection}")
        }
}
