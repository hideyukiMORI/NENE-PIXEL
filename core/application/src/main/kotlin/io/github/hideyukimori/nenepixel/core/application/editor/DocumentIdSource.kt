package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

public fun interface DocumentIdSource {
    public fun nextDocumentId(): DocumentId
}
