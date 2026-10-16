package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorOperationAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Retains the user job even while it waits for the current autosave to finish. */
internal class EditorOperationWorker(
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private var active: Job? = null
    private val mutableAvailability = MutableStateFlow(EditorOperationAvailability.Available)
    val availability: StateFlow<EditorOperationAvailability> = mutableAvailability.asStateFlow()

    fun launch(block: suspend () -> Unit) {
        val job =
            synchronized(lock) {
                if (active != null) return
                scope.launch(start = CoroutineStart.LAZY) { block() }.also {
                    active = it
                    mutableAvailability.value = EditorOperationAvailability.Busy
                }
            }
        job.invokeOnCompletion {
            synchronized(lock) {
                if (active === job) {
                    active = null
                    mutableAvailability.value = EditorOperationAvailability.Available
                }
            }
        }
        job.start()
    }

    fun cancel() {
        synchronized(lock) { active }?.cancel()
    }
}
