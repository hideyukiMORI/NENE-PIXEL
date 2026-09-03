package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import java.util.UUID

internal class UuidDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId {
        val canonicalValue = UUID.randomUUID().toString().replace("-", "")
        return when (val result = DocumentId.create(canonicalValue)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("UUID adapter produced an invalid document ID: ${result.rejection}")
        }
    }
}
