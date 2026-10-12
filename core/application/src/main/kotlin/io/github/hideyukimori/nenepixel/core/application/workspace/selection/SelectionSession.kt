package io.github.hideyukimori.nenepixel.core.application.workspace.selection

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelOrientation
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelPlacement
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransferMode
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** A bounded geometry-only draft for one admitted, visible layer; never a clipboard or pixel owner. */
public class SelectionSession private constructor(
    internal val admission: CommandSourceAdmission,
    public val layerId: LayerId,
    public val mode: PixelTransferMode,
    internal val geometry: SelectionGeometry,
) {
    /** Derived once per session value; unchanged frames reuse the same transfer reference. */
    public val previewTransfer: PixelTransfer? = geometry.draft?.let { draft -> transfer(draft) }

    public val sourceRegion: PixelRegion?
        get() = previewTransfer?.source

    public val targetRegion: PixelRegion?
        get() = previewTransfer?.destinationRegion

    public val interaction: SelectionInteraction
        get() =
            when (geometry.pointer) {
                null -> SelectionInteraction.Idle
                is SelectionPointer.Selecting -> SelectionInteraction.Selecting
                is SelectionPointer.Translating -> SelectionInteraction.Translating
            }

    /** Completed non-identity geometry can be submitted; content may still be a command no-op. */
    public val canApply: Boolean
        get() =
            interaction == SelectionInteraction.Idle &&
                previewTransfer?.let { !it.isIdentity } == true

    /** Whether an operation on the displayed rectangle fits without clipping or interrupting a pointer. */
    public fun canTransform(operation: PixelOrientation): Boolean =
        geometry.pointer == null &&
            geometry.draft?.let { draft ->
                SelectionTransformPolicy.fits(admission.document.size, draft, operation)
            } == true

    internal fun withGeometry(geometry: SelectionGeometry): SelectionSession =
        SelectionSession(admission, layerId, mode, geometry)

    internal fun withMode(mode: PixelTransferMode): SelectionSession =
        SelectionSession(admission, layerId, mode, geometry)

    internal fun cancelPointer(): SelectionSession =
        geometry.pointer?.let { pointer -> withGeometry(SelectionGeometry(pointer.previous, null)) } ?: this

    private fun transfer(draft: SelectionDraft): PixelTransfer =
        when (
            val result =
                PixelTransfer.create(
                    admission.document.size,
                    draft.source,
                    PixelPlacement.create(draft.destination, draft.orientation),
                    mode,
                )
        ) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Selection geometry violated its canvas: ${result.rejection}")
        }

    internal companion object {
        fun begin(
            admission: CommandSourceAdmission,
            layerId: LayerId,
        ): SelectionSession =
            SelectionSession(admission, layerId, PixelTransferMode.Move, SelectionGeometry(null, null))
    }
}
