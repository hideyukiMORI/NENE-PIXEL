package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission

/** Runtime entry after atomically selecting the admitted layer; presentation cannot manufacture it. */
internal data class BeginSelectionSession(
    val admission: CommandSourceAdmission,
) : WorkspaceAction.SelectionAction
