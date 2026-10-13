package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/** Captured once at Begin and shared by every immutable extension; no live workspace is reread. */
internal class ToolGestureAdmission(
    val canvas: CanvasSize,
    val effect: StrokeEffect,
    val layerId: LayerId,
    val admission: CommandSourceAdmission,
)
