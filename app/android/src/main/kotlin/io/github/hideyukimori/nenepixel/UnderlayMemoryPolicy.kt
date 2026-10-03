package io.github.hideyukimori.nenepixel

/**
 * The only underlay memory timing number in the code base (ADR 0034): a publication is requested once the
 * projection has stayed `PublishPending` for the quiet window, so one slider drag becomes one write.
 */
internal data class UnderlayMemoryPolicy(
    val quietMillis: Long,
) {
    companion object {
        val DEFAULT: UnderlayMemoryPolicy = UnderlayMemoryPolicy(quietMillis = UNDERLAY_MEMORY_QUIET_MS)

        private const val UNDERLAY_MEMORY_QUIET_MS: Long = 500L
    }
}
