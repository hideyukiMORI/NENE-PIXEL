package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch

public class ChangeSet private constructor(
    internal val patch: PixelPatch,
    internal val inversePatch: PixelPatch,
) {
    public val beforeRevision: Revision
        get() = patch.beforeRevision

    public val afterRevision: Revision
        get() = patch.afterRevision

    public val renderInvalidation: PixelRegion
        get() = patch.affectedRegion

    internal val retainedChangeCount: Int
        get() = patch.changeCount

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ChangeSet && patch == other.patch && inversePatch == other.inversePatch)

    override fun hashCode(): Int = patch.hashCode() * HASH_MULTIPLIER + inversePatch.hashCode()

    override fun toString(): String =
        "ChangeSet(beforeRevision=$beforeRevision, afterRevision=$afterRevision, " +
            "renderInvalidation=$renderInvalidation)"

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        internal fun create(patch: PixelPatch): ChangeSet =
            ChangeSet(
                patch = patch,
                inversePatch = patch.inverse(),
            )
    }
}
