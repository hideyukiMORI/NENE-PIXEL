package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceConfirmationRequest
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle

public class PersistenceDecisionCallbacks(
    internal val confirm: (PersistenceConfirmationRequest) -> Unit,
    internal val cancel: (PersistenceOperationHandle) -> Unit,
    internal val acceptRecovery: () -> Unit,
    internal val declineRecovery: () -> Unit,
)
