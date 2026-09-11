package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken

internal data class AutosaveStateObservation(
    val pending: AutosaveStateToken?,
    val published: AutosaveStateToken?,
    val publishing: AutosaveStateToken?,
) {
    val requestable: Boolean
        get() = pending != null && pending != publishing
}
