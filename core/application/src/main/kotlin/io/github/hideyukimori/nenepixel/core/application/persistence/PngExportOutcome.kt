package io.github.hideyukimori.nenepixel.core.application.persistence

public sealed interface PngExportOutcome {
    public data object Exported : PngExportOutcome

    public data object Cancelled : PngExportOutcome

    public data class Failed(
        public val failure: ProjectStorageFailure,
        public val cleanup: PartialOutputCleanup,
    ) : PngExportOutcome
}
