package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyReductionPreview

public class LegacyImportProjection internal constructor(
    public val operation: PersistenceOperationHandle,
    source: LegacyImportSourceProjection,
    preservation: LegacyImportPreservationProjection,
    destination: LegacyImportDestinationProjection,
) {
    public val source: LegacySourcePreview = source.preview
    public val distinctColorCount: Int = source.distinctColorCount
    public val origin: LegacyImportOrigin = source.origin
    public val originalCopy: LegacyOriginalCopyStatus = preservation.originalCopy
    public val latestCopyOutcome: LegacyCopyAttemptOutcome = preservation.latestCopyOutcome
    public val selectedDestination: PaletteDefinition? = destination.selected
    public val reduction: LegacyReductionProjection? = destination.reduction
}

internal data class LegacyImportSourceProjection(
    val preview: LegacySourcePreview,
    val distinctColorCount: Int,
    val origin: LegacyImportOrigin,
)

internal data class LegacyImportPreservationProjection(
    val originalCopy: LegacyOriginalCopyStatus,
    val latestCopyOutcome: LegacyCopyAttemptOutcome,
)

internal data class LegacyImportDestinationProjection(
    val selected: PaletteDefinition?,
    val reduction: LegacyReductionProjection?,
)

public enum class LegacyImportOrigin {
    USER_FILE,
    RECOVERY,
}

public enum class LegacyOriginalCopyStatus {
    OPTIONAL,
    REQUIRED,
    VERIFIED,
}

public sealed interface LegacyCopyAttemptOutcome {
    public data object NotAttempted : LegacyCopyAttemptOutcome

    public data object Copied : LegacyCopyAttemptOutcome

    public data object Cancelled : LegacyCopyAttemptOutcome

    public data class Failed internal constructor(
        public val failure: ProjectStorageFailure,
        public val cleanup: PartialOutputCleanup,
    ) : LegacyCopyAttemptOutcome
}

/** Exact read-only RGBA projection. It exposes no owned array or transport identity. */
public class LegacySourcePreview internal constructor(
    source: LegacyRgbaSource,
) {
    public val size: CanvasSize = source.size
    private val source: LegacyRgbaSource = source

    public fun colorAt(position: PixelPosition): LegacyPreviewColorResult =
        when (val result = source.colorAt(position)) {
            is DomainValueResult.Created -> LegacyPreviewColorResult.Color(result.value)
            is DomainValueResult.Rejected -> LegacyPreviewColorResult.OutsideCanvas
        }

    public override fun toString(): String = "LegacySourcePreview(size=$size)"
}

public class LegacyReductionProjection internal constructor(
    public val handle: LegacyReductionHandle,
    preview: LegacyReductionPreview,
) {
    public val size: CanvasSize = preview.snapshot.size
    public val definition: PaletteDefinition = preview.definition
    private val snapshot = preview.snapshot

    public fun colorAt(position: PixelPosition): LegacyPreviewColorResult =
        when (val index = snapshot.indexAt(position)) {
            is DomainValueResult.Created -> {
                when (val entry = definition.palette.entryAt(index.value)) {
                    is DomainValueResult.Created -> LegacyPreviewColorResult.Color(entry.value.color)
                    is DomainValueResult.Rejected -> error("Validated reduction preview lost palette membership")
                }
            }

            is DomainValueResult.Rejected -> {
                LegacyPreviewColorResult.OutsideCanvas
            }
        }

    public override fun toString(): String = "LegacyReductionProjection(size=$size, definition=$definition)"
}

public sealed interface LegacyPreviewColorResult {
    public data class Color(
        public val value: PixelColor,
    ) : LegacyPreviewColorResult

    public data object OutsideCanvas : LegacyPreviewColorResult
}

public class LegacyReductionHandle internal constructor(
    internal val operation: PersistenceOperationHandle,
    internal val destinationEpoch: Long,
) {
    public override fun equals(other: Any?): Boolean =
        other is LegacyReductionHandle && operation == other.operation && destinationEpoch == other.destinationEpoch

    public override fun hashCode(): Int = 31 * operation.hashCode() + destinationEpoch.hashCode()

    public override fun toString(): String = "LegacyReductionHandle"
}

public sealed interface LegacySourceCopyRequestResult {
    public data object Copied : LegacySourceCopyRequestResult

    public data object Cancelled : LegacySourceCopyRequestResult

    public data object Stale : LegacySourceCopyRequestResult

    public data object TooLate : LegacySourceCopyRequestResult

    public data class Failed internal constructor(
        public val failure: ProjectStorageFailure,
        public val cleanup: PartialOutputCleanup,
    ) : LegacySourceCopyRequestResult
}

public sealed interface LegacyReductionRequestResult {
    public data class Ready internal constructor(
        public val handle: LegacyReductionHandle,
    ) : LegacyReductionRequestResult

    public data object Stale : LegacyReductionRequestResult

    public data object TooLate : LegacyReductionRequestResult

    public data object IdentityExhausted : LegacyReductionRequestResult
}
