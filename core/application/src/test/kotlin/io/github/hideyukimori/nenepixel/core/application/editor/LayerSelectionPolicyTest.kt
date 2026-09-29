package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class LayerSelectionPolicyTest {
    private val size = canvas(2, 1)
    private val topId = LayerId.create(2).value()

    @Test
    fun `install selects the top layer`() {
        assertEquals(topId, LayerSelectionPolicy.onInstall(twoLayers()))
        assertEquals(LayerId.first(), LayerSelectionPolicy.onInstall(state(size)))
    }

    @Test
    fun `a present active layer is kept after a command`() {
        assertNull(LayerSelectionPolicy.afterApplied(LayerId.first(), twoLayers()))
        assertNull(LayerSelectionPolicy.afterApplied(topId, twoLayers()))
    }

    @Test
    fun `a vanished active layer moves to the top layer`() {
        assertEquals(LayerId.first(), LayerSelectionPolicy.afterApplied(topId, state(size)))
    }

    private fun twoLayers(): DocumentState =
        DocumentState
            .createLayered(
                state(size).id,
                state(size).revision,
                defaultDefinition,
                listOf(
                    Layer.create(LayerId.first(), LayerName.empty, LayerVisibility.Visible, snapshot(size)),
                    Layer.create(topId, LayerName.empty, LayerVisibility.Visible, snapshot(size)),
                ),
            ).value()

    private fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Expected a created value but was $rejection")
        }
}
