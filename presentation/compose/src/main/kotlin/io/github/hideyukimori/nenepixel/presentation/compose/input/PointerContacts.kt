package io.github.hideyukimori.nenepixel.presentation.compose.input

/** Contacts observed in one event: currently pressed IDs and IDs released in that event. */
internal data class PointerContacts(
    val pressedIds: Set<Long>,
    val releasedIds: Set<Long> = emptySet(),
)
