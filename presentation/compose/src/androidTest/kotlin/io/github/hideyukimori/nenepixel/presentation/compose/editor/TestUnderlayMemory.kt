package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryPort
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryWorkflow

/** Runs one request against the shown editor's underlay memory workflow, then brings the screen to the runtime. */
internal typealias UnderlayMemoryRequester = (request: suspend (UnderlayMemoryWorkflow) -> Unit) -> Unit

/**
 * The underlay memory of a test editor (ADR 0034): the [port] its workflow uses, and requests that stand in for the
 * app's scheduler, which this module does not have. [publish] and [recall] run only when a test asks for them; call
 * them on the main thread, for example inside `runOnIdle`. One instance may serve editors shown one after another.
 */
internal class TestUnderlayMemory(
    val port: UnderlayMemoryPort,
) {
    private var requester: UnderlayMemoryRequester? = null

    fun publish() {
        required()({ workflow -> workflow.publish() })
    }

    fun recall() {
        required()({ workflow -> workflow.recall() })
    }

    /** Called by the shown editor; a later editor replaces an earlier one. */
    fun attach(requester: UnderlayMemoryRequester) {
        this.requester = requester
    }

    /** Called when an editor leaves the composition; a requester attached since then is kept. */
    fun detach(requester: UnderlayMemoryRequester) {
        if (this.requester === requester) {
            this.requester = null
        }
    }

    private fun required(): UnderlayMemoryRequester =
        checkNotNull(requester) { "No test editor with this underlay memory is shown" }
}
