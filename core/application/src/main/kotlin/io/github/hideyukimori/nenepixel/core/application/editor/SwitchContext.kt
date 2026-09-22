package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyReductionPreview

internal data class SwitchContext(
    val source: RuntimeSourceToken,
    val dirty: Boolean,
    val newDocumentOwners: (NewDocumentRequest) -> RuntimeOwners,
    val loadedOwners: (DocumentState) -> RuntimeOwners,
    val derivedOwners: (LegacyReductionPreview) -> RuntimeOwners,
)
