package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.PartialOutputCleanup
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure

internal sealed interface FreshOutputResult {
    data object Written : FreshOutputResult

    data object Cancelled : FreshOutputResult

    data class Failed(
        val failure: ProjectStorageFailure,
        val cleanup: PartialOutputCleanup,
    ) : FreshOutputResult
}
