package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource

public interface ProjectStoragePort {
    public suspend fun save(document: DocumentState): ProjectSaveOutcome

    public suspend fun load(): ProjectLoadOutcome

    public suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome
}

public sealed interface ProjectSaveOutcome {
    public data object Saved : ProjectSaveOutcome

    public data object Cancelled : ProjectSaveOutcome

    public data class Failed(
        public val failure: ProjectStorageFailure,
        public val cleanup: PartialOutputCleanup,
    ) : ProjectSaveOutcome
}

public sealed interface ProjectLoadOutcome {
    public data class Loaded(
        public val source: DocumentImportSource,
    ) : ProjectLoadOutcome

    public data object Cancelled : ProjectLoadOutcome

    public data class Failed(
        public val failure: ProjectStorageFailure,
    ) : ProjectLoadOutcome
}

public sealed interface LegacySourceCopyOutcome {
    public data object Copied : LegacySourceCopyOutcome

    public data object Cancelled : LegacySourceCopyOutcome

    public data class Failed(
        public val failure: ProjectStorageFailure,
        public val cleanup: PartialOutputCleanup,
    ) : LegacySourceCopyOutcome
}

public enum class ProjectTransportPhase {
    PICKER_RESULT,
    SOURCE_OPEN,
    SOURCE_READ,
    SOURCE_CLOSE,
    DESTINATION_OPEN,
    DESTINATION_WRITE,
    DESTINATION_CLOSE,
    READ_BACK_OPEN,
    READ_BACK_READ,
    READ_BACK_CLOSE,
    READ_BACK_VALIDATION,
}

public sealed interface ProjectStorageFailure {
    public data object InvalidPickerResult : ProjectStorageFailure

    public data object UnexpectedPickerResultCode : ProjectStorageFailure

    public data class PermissionDenied(
        public val phase: ProjectTransportPhase,
    ) : ProjectStorageFailure

    public data class ProviderUnavailable(
        public val phase: ProjectTransportPhase,
    ) : ProjectStorageFailure

    public data class IoFailure(
        public val phase: ProjectTransportPhase,
    ) : ProjectStorageFailure

    public data class UnsupportedProvider(
        public val phase: ProjectTransportPhase,
    ) : ProjectStorageFailure

    public data object ResourceLimitExceeded : ProjectStorageFailure

    public data object ZeroProgress : ProjectStorageFailure

    public data object PrematureEnd : ProjectStorageFailure

    public data object UnsupportedProjectVersion : ProjectStorageFailure

    public data object InvalidProject : ProjectStorageFailure

    public data object ReadBackMismatch : ProjectStorageFailure

    public data object InvalidPaletteJson : ProjectStorageFailure

    public data object UnsupportedPaletteJsonVersion : ProjectStorageFailure
}

public enum class PartialOutputCleanup {
    NOT_NEEDED,
    DELETED,
    DELETE_FAILED,
}
