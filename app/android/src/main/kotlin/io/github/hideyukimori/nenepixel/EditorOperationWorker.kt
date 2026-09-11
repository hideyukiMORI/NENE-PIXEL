package io.github.hideyukimori.nenepixel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Retains the user job even while it waits for the current autosave to finish. */
internal class EditorOperationWorker(
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private var active: Job? = null

    fun launch(block: suspend () -> Unit) {
        val job =
            synchronized(lock) {
                if (active != null) return
                scope.launch(start = CoroutineStart.LAZY) { block() }.also { active = it }
            }
        job.invokeOnCompletion { synchronized(lock) { if (active === job) active = null } }
        job.start()
    }

    fun cancel() {
        synchronized(lock) { active }?.cancel()
    }
}
