package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationResult

internal sealed interface LayerIndexChanges {
    val changeCount: Int
    val retainedByteCount: Long

    fun inverse(): LayerIndexChanges

    fun applyTo(
        layerId: LayerId,
        snapshot: PixelSnapshot,
    ): Application

    data class Sparse(
        val patch: PixelPatch,
    ) : LayerIndexChanges {
        override val changeCount: Int
            get() = patch.changeCount

        override val retainedByteCount: Long
            get() = sparseBytes(changeCount)

        override fun inverse(): LayerIndexChanges = Sparse(patch.inverse())

        override fun applyTo(
            layerId: LayerId,
            snapshot: PixelSnapshot,
        ): Application =
            when (val result = patch.applyTo(snapshot)) {
                is PixelPatchApplicationResult.Applied -> Application.Applied(result.snapshot)
                is PixelPatchApplicationResult.Rejected -> Application.Rejected(result.rejection.toReason())
            }
    }

    data class Dense(
        val before: PixelSnapshot,
        val after: PixelSnapshot,
    ) : LayerIndexChanges {
        override val changeCount: Int = 0

        override val retainedByteCount: Long
            get() = denseBytes(before.size.pixelCount)

        override fun inverse(): LayerIndexChanges = Dense(after, before)

        override fun applyTo(
            layerId: LayerId,
            snapshot: PixelSnapshot,
        ): Application =
            if (snapshot == before) {
                Application.Applied(after)
            } else {
                Application.Rejected(RejectionReason.LayerSnapshotMismatch(layerId))
            }
    }

    sealed interface Application {
        data class Applied(
            val snapshot: PixelSnapshot,
        ) : Application

        data class Rejected(
            val reason: RejectionReason,
        ) : Application
    }

    companion object {
        /**
         * The one deterministic Sparse / Dense choice shared by stroke and palette remap (ADR 0030).
         * [patch] must have been produced from [before].
         */
        fun select(
            before: PixelSnapshot,
            patch: PixelPatch,
        ): LayerIndexChanges =
            if (sparseBytes(patch.changeCount) <= denseBytes(before.size.pixelCount)) {
                Sparse(patch)
            } else {
                when (val result = patch.applyTo(before)) {
                    is PixelPatchApplicationResult.Applied -> Dense(before, result.snapshot)
                    is PixelPatchApplicationResult.Rejected -> error("A patch did not apply to its source: $result")
                }
            }
    }
}

private const val SPARSE_CHANGE_BYTES: Long = 6L
private const val DENSE_PLANE_COUNT: Long = 2L
private const val BITS_PER_BYTE: Long = 8L

private fun sparseBytes(changeCount: Int): Long = SPARSE_CHANGE_BYTES * changeCount

private fun denseBytes(pixelCount: Long): Long =
    DENSE_PLANE_COUNT * (pixelCount + (pixelCount + BITS_PER_BYTE - 1L) / BITS_PER_BYTE)

private fun PixelPatchApplicationRejection.toReason(): RejectionReason =
    when (this) {
        is PixelPatchApplicationRejection.CanvasMismatch -> {
            RejectionReason.CanvasMismatch(expected, actual)
        }

        is PixelPatchApplicationRejection.BeforeValueMismatch -> {
            RejectionReason.PixelBeforeValueMismatch(
                position,
                expected,
                actual,
            )
        }
    }
