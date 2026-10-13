package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.importing.NewWorkImportPlan
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyReductionPreview

internal data class SwitchContext(
    val source: RuntimeSourceToken,
    val dirty: Boolean,
    val ownerFactories: SwitchOwnerFactories,
) {
    val newDocumentOwners: (NewDocumentRequest) -> RuntimeOwners get() = ownerFactories.newDocument
    val loadedOwners: (DocumentState) -> RuntimeOwners get() = ownerFactories.loaded
    val derivedOwners: (LegacyReductionPreview) -> RuntimeOwners get() = ownerFactories.derived
    val importedOwners: (NewWorkImportPlan) -> RuntimeOwners get() = ownerFactories.imported
}
