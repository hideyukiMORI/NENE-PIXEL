package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

internal data class SwitchContext(
    val source: RuntimeSourceToken,
    val dirty: Boolean,
    val newDocumentOwners: (NewDocumentRequest) -> RuntimeOwners,
    val loadedOwners: (DocumentState) -> RuntimeOwners,
)
