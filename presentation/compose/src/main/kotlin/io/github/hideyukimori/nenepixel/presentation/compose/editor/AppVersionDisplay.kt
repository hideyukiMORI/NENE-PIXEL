package io.github.hideyukimori.nenepixel.presentation.compose.editor

/**
 * ADR 0021: app identity belongs to the host platform boundary. Presentation receives the already
 * normalized outcome, so a failed platform lookup stays a typed value instead of a null or a throw.
 */
public sealed interface AppVersionDisplay {
    /**
     * [versionCode] is the platform long version code, which Android reports from API 28 only; it
     * is absent, never substituted, on the older supported releases.
     */
    public data class Available(
        public val versionName: String,
        public val versionCode: Long?,
    ) : AppVersionDisplay

    public data object Unavailable : AppVersionDisplay
}
