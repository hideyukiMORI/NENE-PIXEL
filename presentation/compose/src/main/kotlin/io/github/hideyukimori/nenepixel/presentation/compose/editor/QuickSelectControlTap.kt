package io.github.hideyukimori.nenepixel.presentation.compose.editor

/**
 * What a tap on the quick-select control means in the state it started in (ADR 0029 "Floating control"). A
 * pointer tap and the accessibility click both decide here, so the two never disagree.
 */
internal enum class QuickSelectControlTap {
    /** Armed: the tap disarms the eyedropper. */
    Disarm,

    /** Idle: the menu stays open in tap mode. */
    EnterTapMode,

    /** Tap mode: the tap closes the menu without a selection. */
    Cancel,
    ;

    companion object {
        fun of(session: QuickSelectGestureSession): QuickSelectControlTap =
            when {
                session.armed -> Disarm
                session.tapMode -> Cancel
                else -> EnterTapMode
            }
    }
}

/** Applies [tap] once the menu, when [tap] needs one, is already open. */
internal fun EditorQuickSelectCallbacks.applyControlTap(
    tap: QuickSelectControlTap,
    onTapMode: (Boolean) -> Unit,
) {
    when (tap) {
        QuickSelectControlTap.Disarm -> {
            onDisarm()
        }

        QuickSelectControlTap.EnterTapMode -> {
            onTapMode(true)
            onHighlight(null)
        }

        QuickSelectControlTap.Cancel -> {
            onTapMode(false)
            onCancel()
        }
    }
}

/**
 * The control's accessibility click: the pointer press opens the menu on its down, so here an idle click opens it
 * first; a refused open leaves everything unchanged.
 */
internal fun EditorQuickSelectCallbacks.clickControl(
    session: QuickSelectGestureSession,
    onTapMode: (Boolean) -> Unit,
) {
    val tap = QuickSelectControlTap.of(session)
    if (tap != QuickSelectControlTap.EnterTapMode || onOpen().quickSelection.menu != null) {
        applyControlTap(tap, onTapMode)
    }
}
