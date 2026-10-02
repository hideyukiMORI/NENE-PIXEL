package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId

/** Whether the installed work's underlay is to be recalled now (ADR 0034). */
internal sealed interface UnderlayRecallStart {
    /** Recall [document]'s record, then complete it for [installation]. */
    data class Start(
        val installation: Long,
        val document: DocumentId,
    ) : UnderlayRecallStart

    data object NotNeeded : UnderlayRecallStart
}
