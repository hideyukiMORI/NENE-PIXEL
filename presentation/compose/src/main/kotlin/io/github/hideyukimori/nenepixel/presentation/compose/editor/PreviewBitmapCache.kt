package io.github.hideyukimori.nenepixel.presentation.compose.editor

import android.graphics.Bitmap
import io.github.hideyukimori.nenepixel.core.application.render.StrokePreviewComposite
import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer

/**
 * One disposable rendering of the canvas while a gesture runs (Issues #124, #143), kept beside
 * [CommittedBitmapCache] with the same lifetime: the committed picture with every gesture position
 * replaced by the colour it will have once committed. The raster is repainted only when the preview
 * reference, the committed picture or the stroke composite changes, so an unchanged frame costs
 * nothing and a moved gesture costs one bitmap transfer of the changed rows.
 */
internal class PreviewBitmapCache {
    private var raster: PreviewRaster? = null
    private var rendered: Bitmap? = null
    private var base: Bitmap? = null
    private var source: ToolGesture? = null
    private var sourceComposite: StrokePreviewComposite? = null
    private var composite: StrokePreviewComposite? = null
    private var compositeDocument: DocumentState? = null
    private var compositeLayerId: LayerId? = null
    private var compositeFrameId: FrameId? = null
    private var compositeEffect: StrokeEffect? = null
    private val selection = SelectionBitmapCache()
    private var selectionDocument: DocumentState? = null
    private var selectionTransfer: PixelTransfer? = null
    private var selectionLayer: LayerId? = null
    private var selectionFrame: FrameId? = null

    internal val selectionPreparationCount: Int
        get() = selection.preparationCount

    /** The canvas bitmap while [EditorRenderState.preview] runs; `null` when there is no preview. */
    fun render(
        state: EditorRenderState,
        committed: CommittedBitmapCache,
    ): Bitmap? {
        val session = state.selectionSession
        val transfer = session?.previewTransfer
        if (transfer != null) {
            return if (transfer.isIdentity) {
                selection.hide(state.document, state.activeFrameId, session.layerId, transfer.source)
                selectionDocument = null
                selectionTransfer = null
                selectionLayer = null
                selectionFrame = null
                forgetSources()
            } else {
                renderSelection(
                    FrameImageInputs.committed(state.document, state.activeFrameId),
                    session.layerId,
                    transfer,
                    committed,
                )
            }
        }
        selection.forget()
        selectionDocument = null
        selectionTransfer = null
        selectionLayer = null
        selectionFrame = null
        return state.preview?.let { preview ->
            renderStroke(state.picture, preview, committed)
        } ?: forgetSources()
    }

    /** The ordinary stroke path shares the same raster/bitmap and base restoration as selection. */
    private fun renderStroke(
        picture: FrameImageInputs,
        preview: ToolGesture,
        committed: CommittedBitmapCache,
    ): Bitmap {
        val committedBitmap = committed.render(picture)
        val target = rasterFor(picture.document.size.width.value, picture.document.size.height.value)
        if (base !== committedBitmap) {
            base = committedBitmap
            source = null
            target.replaceBase(committed::copyRenderedArgbInto)
        }
        val colors = compositeFor(picture, preview)
        if (source !== preview || sourceComposite !== colors) {
            source = preview
            sourceComposite = colors
            target.paint(preview) { position -> colors.packedRgba8888At(position).rgbaToArgb8888() }
        }
        transfer(target)
        return requireNotNull(rendered)
    }

    private fun renderSelection(
        picture: FrameImageInputs,
        layerId: LayerId,
        pixelTransfer: PixelTransfer,
        committed: CommittedBitmapCache,
    ): Bitmap {
        if (sameSelectionSource(picture, layerId) && selectionTransfer === pixelTransfer) {
            return requireNotNull(rendered)
        }
        val target = rasterFor(picture.document.size.width.value, picture.document.size.height.value)
        // A later ordinary stroke must restore its base, rather than reuse a retained selection bitmap.
        forgetSources()
        val baseReplaced = selection.paint(SelectionBitmapInput(picture, layerId, pixelTransfer, committed), target)
        transfer(target)
        target.transferChangedRegions { pixels, region ->
            if (!baseReplaced) {
                val stride = target.width
                requireNotNull(rendered).setPixels(
                    pixels,
                    region.origin.y.value * stride + region.origin.x.value,
                    stride,
                    region.origin.x.value,
                    region.origin.y.value,
                    region.size.width.value,
                    region.size.height.value,
                )
            }
        }
        selectionDocument = picture.document
        selectionFrame = picture.frameId
        selectionTransfer = pixelTransfer
        selectionLayer = layerId
        return requireNotNull(rendered)
    }

    /** Document/frame/layer identify the prepared source; transfer identity identifies its current placement. */
    private fun sameSelectionSource(
        picture: FrameImageInputs,
        layerId: LayerId,
    ): Boolean =
        selectionDocument === picture.document && selectionFrame == picture.frameId && selectionLayer == layerId

    /**
     * Drops every reference to the document, its composite and the committed bitmap once no gesture
     * runs, so this cache never keeps a superseded document alive. The raster and its bitmap stay.
     */
    private fun forgetSources(): Bitmap? {
        base = null
        source = null
        sourceComposite = null
        composite = null
        compositeDocument = null
        compositeLayerId = null
        compositeFrameId = null
        compositeEffect = null
        return null
    }

    /** Reuses the composite while the document reference, target layer and effect stay the same. */
    private fun compositeFor(
        picture: FrameImageInputs,
        preview: ToolGesture,
    ): StrokePreviewComposite {
        val reusable =
            compositeDocument === picture.document &&
                compositeFrameId == picture.frameId &&
                compositeLayerId == preview.layerId &&
                compositeEffect == preview.effect
        return composite?.takeIf { reusable }
            ?: StrokePreviewComposite.prepare(picture.document, picture.frameId, preview).also { prepared ->
                composite = prepared
                compositeDocument = picture.document
                compositeFrameId = picture.frameId
                compositeLayerId = preview.layerId
                compositeEffect = preview.effect
            }
    }

    private fun transfer(target: PreviewRaster) {
        val bitmap = requireNotNull(rendered)
        target.transferChangedRows { pixels, rows ->
            val stride = target.width
            bitmap.setPixels(pixels, rows.first * stride, stride, 0, rows.first, stride, rows.count())
        }
    }

    private fun rasterFor(
        canvasWidth: Int,
        canvasHeight: Int,
    ): PreviewRaster {
        val current = raster
        return if (current != null && current.width == canvasWidth && current.height == canvasHeight) {
            current
        } else {
            base = null
            source = null
            sourceComposite = null
            rendered = blankMutableBitmap(canvasWidth, canvasHeight)
            PreviewRaster(canvasWidth, canvasHeight).also { raster = it }
        }
    }
}

/**
 * Uses the platform colour-array factory already used by the committed projection and copies it
 * once into a mutable bitmap, so no undeclared KTX dependency is introduced (Issue #124). The bitmap
 * is marked as having alpha so the backdrop shows through transparent positions (Issue #147).
 */
private fun blankMutableBitmap(
    width: Int,
    height: Int,
): Bitmap =
    Bitmap
        .createBitmap(IntArray(width * height), width, height, Bitmap.Config.ARGB_8888)
        .copy(Bitmap.Config.ARGB_8888, true)
        .apply { setHasAlpha(true) }
