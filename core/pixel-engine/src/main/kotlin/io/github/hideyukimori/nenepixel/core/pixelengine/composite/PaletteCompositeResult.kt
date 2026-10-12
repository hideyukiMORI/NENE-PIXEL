package io.github.hideyukimori.nenepixel.core.pixelengine.composite

/** Preparation uses the same layer/palette validation as the canonical Composite. */
public sealed interface PaletteCompositeResult {
    public class Prepared internal constructor(
        public val composite: PaletteComposite,
    ) : PaletteCompositeResult

    public class Rejected internal constructor(
        public val rejection: CompositeRejection,
    ) : PaletteCompositeResult
}
