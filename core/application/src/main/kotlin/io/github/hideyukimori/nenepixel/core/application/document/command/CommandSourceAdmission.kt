package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

public class CommandSourceAdmission internal constructor(
    internal val owner: CommandSourceOwner,
    public val document: DocumentState,
    internal val position: HistoryPosition,
)
