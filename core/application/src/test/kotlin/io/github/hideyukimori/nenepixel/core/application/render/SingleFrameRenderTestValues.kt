package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import org.junit.jupiter.api.Assertions.assertInstanceOf

/** Existing fixtures name their sole frame explicitly and keep the original image assertions. */
internal object SingleFrameRenderTestValues {
    fun image(document: DocumentState): DocumentCompositeImage =
        assertInstanceOf(
            DocumentCompositeRenderResult.Rendered::class.java,
            DocumentComposite.render(document, document.frames.single().id),
        ).image
}
