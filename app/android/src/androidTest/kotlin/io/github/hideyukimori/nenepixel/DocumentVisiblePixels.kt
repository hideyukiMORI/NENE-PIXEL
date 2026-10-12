package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.render.DocumentComposite
import io.github.hideyukimori.nenepixel.core.application.render.DocumentCompositeRenderResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

/**
 * The visible picture of [this] document as packed RGBA8888, row-major.
 *
 * Acceptance tests read what the user sees only through the application composite
 * (ADR 0030), so a pixel without any contribution is `0x00000000` rather than a palette color.
 */
internal fun DocumentState.visiblePixels(): IntArray =
    when (val result = DocumentComposite.render(this, frames.single().id)) {
        is DocumentCompositeRenderResult.Rendered -> result.image.copyPackedRgba8888()
        is DocumentCompositeRenderResult.FrameNotFound -> error("Fixture frame missing")
        is DocumentCompositeRenderResult.IndexOutsidePalette -> error("Fixture palette invalid")
    }
