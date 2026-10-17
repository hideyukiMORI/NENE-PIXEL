package io.github.hideyukimori.nenepixel

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveProjection
import java.util.concurrent.CopyOnWriteArrayList

/** Main-thread callbacks only construct the one VM or append bounded in-memory observations. */
internal class RecoveryProcessCallbacks(
    private val attach: (MainActivity) -> Unit,
    private val observeBeforeStop: () -> RecoveryProcessStopObservation,
) : Application.ActivityLifecycleCallbacks {
    private val events = CopyOnWriteArrayList<String>()
    var attachmentCount = 0
        private set
    var taskId = -1
        private set
    var beforeStop: RecoveryProcessStopObservation? = null
        private set

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is MainActivity) return
        check(attachmentCount == 0) { "Preparation must attach exactly one MainActivity" }
        attachmentCount += 1
        attach(activity)
        record(activity, "pre-created")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = record(activity, "created")

    override fun onActivityStarted(activity: Activity) = record(activity, "started")

    override fun onActivityResumed(activity: Activity) = record(activity, "resumed")

    override fun onActivityPaused(activity: Activity) = record(activity, "paused")

    override fun onActivityStopped(activity: Activity) = record(activity, "stopped-super")

    override fun onActivityPreStopped(activity: Activity) {
        if (activity !is MainActivity) return
        check(beforeStop == null) { "Preparation must stop once" }
        beforeStop = observeBeforeStop()
        record(activity, "pre-stopped")
    }

    override fun onActivityPostStopped(activity: Activity) = record(activity, "post-stopped")

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =
        record(activity, "save-instance-state")

    override fun onActivityDestroyed(activity: Activity) = record(activity, "destroyed")

    fun observations(): List<String> = events.toList()

    fun requireStopped() {
        check(events.any { it.startsWith("post-stopped ") }) { "Actual onStop has not completed" }
    }

    private fun record(activity: Activity, event: String) {
        if (activity !is MainActivity) return
        taskId = activity.taskId
        check(events.size < 32) { "Unexpected lifecycle loop" }
        events.add(
            "$event elapsed=${SystemClock.elapsedRealtime()} task=${activity.taskId} " +
                "finishing=${activity.isFinishing} changing=${activity.isChangingConfigurations}",
        )
    }
}

internal data class RecoveryProcessStopObservation(
    val startCalls: Int,
    val autosave: AutosaveProjection,
)
