package io.github.hideyukimori.nenepixel.core.application.document.command

public sealed interface CommandFailure {
    public data object PersistenceBusy : CommandFailure

    /** A palette draft is open; document commands wait until it is applied or cancelled (ADR 0022). */
    public data object PaletteSessionActive : CommandFailure
}
