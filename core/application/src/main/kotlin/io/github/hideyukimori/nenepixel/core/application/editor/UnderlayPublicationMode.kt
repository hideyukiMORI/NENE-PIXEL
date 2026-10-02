package io.github.hideyukimori.nenepixel.core.application.editor

/** Whether a publication leaves out an underlay being adjusted (ADR 0034). */
internal enum class UnderlayPublicationMode {
    /** The scheduled publication: an underlay being adjusted is not written. */
    Publish,

    /** The flush at `ON_STOP`: the value of an underlay being adjusted is also written. */
    Flush,
}
