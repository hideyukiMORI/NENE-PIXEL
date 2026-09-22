package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyReductionHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

public class LegacyConversionCallbacks(
    internal val copyOriginal: (PersistenceOperationHandle) -> Unit,
    internal val preview: (PersistenceOperationHandle, PaletteDefinition) -> Unit,
    internal val accept: (LegacyReductionHandle) -> Unit,
    internal val declineRecovery: (PersistenceOperationHandle) -> Unit,
)
