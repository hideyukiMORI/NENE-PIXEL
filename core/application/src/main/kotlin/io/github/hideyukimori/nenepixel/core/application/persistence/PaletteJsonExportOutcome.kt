package io.github.hideyukimori.nenepixel.core.application.persistence

public sealed interface PaletteJsonExportOutcome {
    public data object Exported : PaletteJsonExportOutcome

    public data object Cancelled : PaletteJsonExportOutcome

    public data class Failed(
        public val failure: ProjectStorageFailure,
        public val cleanup: PartialOutputCleanup,
    ) : PaletteJsonExportOutcome
}
