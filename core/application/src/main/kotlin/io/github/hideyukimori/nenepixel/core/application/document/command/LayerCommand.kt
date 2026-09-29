package io.github.hideyukimori.nenepixel.core.application.document.command

/** A persistent, undoable change to the ordered layer list (ADR 0030). Positions count from the bottom, from 0. */
public sealed interface LayerCommand : DocumentCommand {
    public val admission: CommandSourceAdmission
}
