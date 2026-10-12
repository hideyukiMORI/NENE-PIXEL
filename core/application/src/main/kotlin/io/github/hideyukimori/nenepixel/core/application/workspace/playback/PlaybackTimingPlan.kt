package io.github.hideyukimori.nenepixel.core.application.workspace.playback

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** Immutable, bounded IDs/endpoints only: no document, snapshots or pixel preparation is retained. */
internal class PlaybackTimingPlan private constructor(
    private val ids: List<FrameId>,
    private val endpoints: List<Long>,
) {
    val totalNanos: Long get() = endpoints.last()

    fun prefix(frameId: FrameId): Long {
        val index = ids.indexOf(frameId)
        check(index >= 0) { "Playback starting frame was not admitted" }
        return if (index == 0) 0L else endpoints[index - 1]
    }

    fun frame(index: Int): FrameId = ids[index]

    fun endpoint(index: Int): Long = endpoints[index]

    fun indexAt(position: Long): Int = endpoints.indexOfFirst { position < it }.also { check(it >= 0) }

    companion object {
        private const val NANOS_PER_MILLISECOND: Long = 1_000_000L

        fun create(document: DocumentState): PlaybackTimingPlan {
            var end = 0L
            val endpoints =
                document.frames.map {
                    end += it.duration.value * NANOS_PER_MILLISECOND
                    end
                }
            return PlaybackTimingPlan(document.frames.map { it.id }, endpoints)
        }
    }
}
