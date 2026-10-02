package io.github.hideyukimori.nenepixel.core.application.editor

/** Whether anything is to be written to the underlay memory now (ADR 0034). */
internal sealed interface UnderlayPublicationStart {
    /** Write [publication]'s writes in order, then complete the same instance. */
    data class Start(
        val publication: UnderlayPublication,
    ) : UnderlayPublicationStart

    data object NotNeeded : UnderlayPublicationStart
}
