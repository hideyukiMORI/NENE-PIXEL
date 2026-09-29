package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.render.DocumentComposite
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

/**
 * The visible picture of [this] document as packed RGBA8888, row-major.
 *
 * Acceptance tests read what the user sees only through the application composite
 * (ADR 0030), so a pixel without any contribution is `0x00000000` rather than a palette color.
 */
internal fun DocumentState.visiblePixels(): IntArray = DocumentComposite.render(this).copyPackedRgba8888()
