package io.github.hideyukimori.nenepixel

import kotlinx.coroutines.CompletableDeferred

/** A virtual clock for the underlay memory quiet wait: a wait ends only when the test advances past it. */
internal class ManualQuietTime {
    private var nowMillis: Long = 0L
    private val waits: MutableList<Pair<Long, CompletableDeferred<Unit>>> = mutableListOf()

    suspend fun await(millis: Long) {
        val wait = (nowMillis + millis) to CompletableDeferred<Unit>()
        waits += wait
        try {
            wait.second.await()
        } finally {
            waits -= wait
        }
    }

    fun advanceBy(millis: Long) {
        nowMillis += millis
        waits.filter { (due, _) -> due <= nowMillis }.forEach { (_, wait) -> wait.complete(Unit) }
    }
}
