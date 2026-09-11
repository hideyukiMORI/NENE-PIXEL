package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryStatus

/**
 * The ADR 0018 startup offer for an unadopted recovery Candidate. It replaces the two status texts
 * inside the existing status row, so the screen keeps one control row and one status row.
 */
@Composable
internal fun RecoveryOffer(
    enabled: Boolean,
    callbacks: EditorPersistenceCallbacks,
) {
    Text(
        text = "Unsaved work from the last session",
        modifier = Modifier.semantics { contentDescription = RECOVERY_OFFER_DESCRIPTION },
    )
    TextButton(
        enabled = enabled,
        onClick = callbacks::onAcceptRecovery,
        modifier = Modifier.semantics { contentDescription = RECOVER_DESCRIPTION },
    ) {
        Text("Recover")
    }
    TextButton(
        enabled = enabled,
        onClick = callbacks::onDeclineRecovery,
        modifier = Modifier.semantics { contentDescription = DISCARD_DESCRIPTION },
    ) {
        Text("Discard")
    }
}

internal fun PersistenceOperationProjection.offersRecovery(): Boolean =
    recoveryStatus is RecoveryStatus.UnadoptedCandidate

/** Adoption and decline both need the operation lease, so the offer waits out any active operation. */
internal fun PersistenceOperationProjection.recoveryOfferEnabled(): Boolean = phase is PersistenceOperationPhase.Idle

internal const val RECOVERY_OFFER_DESCRIPTION: String = "Recovery offer"
internal const val RECOVER_DESCRIPTION: String = "Recover unsaved work"
internal const val DISCARD_DESCRIPTION: String = "Discard unsaved work"
