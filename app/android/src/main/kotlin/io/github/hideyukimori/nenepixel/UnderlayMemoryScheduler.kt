package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryWorkflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The platform half of the ADR 0034 underlay memory. It owns the waiting only: what to read or write, and
 * whether anything is due, is decided inside [UnderlayMemoryWorkflow], so this class looks at nothing but the
 * shape of [UnderlayMemoryWorkflow.states]. A new projection cancels the quiet wait and starts it again. At
 * most one workflow request runs at a time, and requests run on the scope the scheduler was launched in, so
 * they survive configuration changes.
 *
 * [onRecalled] runs after every recall, because a restored underlay changes the workspace.
 */
internal class UnderlayMemoryScheduler(
    private val workflow: UnderlayMemoryWorkflow,
    private val onRecalled: () -> Unit,
    private val policy: UnderlayMemoryPolicy = UnderlayMemoryPolicy.DEFAULT,
    private val quietWait: suspend (Long) -> Unit = { millis -> delay(millis) },
) {
    private val requests: Mutex = Mutex()
    private val flushes: Channel<Unit> = Channel(Channel.CONFLATED)

    fun launchIn(scope: CoroutineScope): Job =
        scope.launch {
            launch { flushes.consumeAsFlow().collect { requests.withLock { workflow.flush() } } }
            workflow.states.collectLatest { projection -> serve(projection) }
        }

    /** Requests an immediate flush, including an underlay being adjusted; the ADR 0034 `ON_STOP` flush. */
    fun flush() {
        flushes.trySend(Unit)
    }

    private suspend fun serve(projection: UnderlayMemoryProjection) {
        when (projection) {
            is UnderlayMemoryProjection.RecallPending -> recall()
            is UnderlayMemoryProjection.PublishPending -> publishAfterQuietWindow()
            UnderlayMemoryProjection.Settled -> Unit
        }
    }

    private suspend fun recall() {
        try {
            requests.withLock { workflow.recall() }
        } finally {
            onRecalled()
        }
    }

    private suspend fun publishAfterQuietWindow() {
        quietWait(policy.quietMillis)
        requests.withLock { workflow.publish() }
    }
}
