package io.github.hideyukimori.nenepixel

/**
 * The only autosave timing numbers in the code base, fixed by ADR 0018: request a Candidate
 * publication once the quiet window has elapsed since the latest capture, and never later than the
 * latency cap after the oldest still-unpublished capture.
 */
internal data class AutosavePolicy(
    val quietMillis: Long,
    val latencyCapMillis: Long,
) {
    val quietNanos: Long
        get() = quietMillis * NANOS_PER_MILLI

    val latencyCapNanos: Long
        get() = latencyCapMillis * NANOS_PER_MILLI

    companion object {
        val DEFAULT: AutosavePolicy =
            AutosavePolicy(
                quietMillis = AUTOSAVE_QUIET_MS,
                latencyCapMillis = AUTOSAVE_LATENCY_CAP_MS,
            )

        private const val AUTOSAVE_QUIET_MS: Long = 1_000L
        private const val AUTOSAVE_LATENCY_CAP_MS: Long = 5_000L
        private const val NANOS_PER_MILLI: Long = 1_000_000L
    }
}
