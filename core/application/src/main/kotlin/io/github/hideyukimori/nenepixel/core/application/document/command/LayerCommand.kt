package io.github.hideyukimori.nenepixel.core.application.document.command

/** A persistent, undoable change to the ordered layer list (ADR 0030). Positions count from the bottom, from 0. */
public sealed interface LayerCommand : DocumentCommand {
    public val admission: CommandSourceAdmission
}

/** One owner lookup covers every command in this closed layer family. */
internal val LayerCommand.targetLayerId: io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
    get() =
        when (this) {
            is AddLayerCommand -> aboveLayerId
            is DuplicateLayerCommand -> layerId
            is DeleteLayerCommand -> layerId
            is RenameLayerCommand -> layerId
            is MoveLayerCommand -> layerId
            is SetLayerVisibilityCommand -> layerId
        }
