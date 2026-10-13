package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.importing.NewWorkImportPlan
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyReductionPreview

/** Captured construction paths for the replacement runtime, without owning the current document. */
internal class SwitchOwnerFactories(
    val newDocument: (NewDocumentRequest) -> RuntimeOwners,
    val loaded: (DocumentState) -> RuntimeOwners,
    val derived: (LegacyReductionPreview) -> RuntimeOwners,
    val imported: (NewWorkImportPlan) -> RuntimeOwners,
)
