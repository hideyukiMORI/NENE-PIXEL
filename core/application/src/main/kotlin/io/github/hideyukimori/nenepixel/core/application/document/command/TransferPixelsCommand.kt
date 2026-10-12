package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer

/** One exact same-layer rectangular Move/Copy, planned from its admitted immutable source (ADR 0039). */
public data class TransferPixelsCommand private constructor(
    public val admission: CommandSourceAdmission,
    public val layerId: LayerId,
    public val transfer: PixelTransfer,
) : DocumentCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            layerId: LayerId,
            transfer: PixelTransfer,
        ): TransferPixelsCommand = TransferPixelsCommand(admission, layerId, transfer)
    }
}
