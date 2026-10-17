package io.github.hideyukimori.nenepixel

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

/** Immutable host rendezvous and byte snapshots; called only from the instrumentation thread. */
internal class RecoveryProcessJournal(
    private val admission: RecoveryProcessAdmission,
) {
    fun ready(
        fixture: RecoveryProcessFixture,
        deadline: Long,
    ) {
        fixture.callbacks.requireStopped()
        check(fixture.callbacks.taskId > 0)
        val record = identity("nene-pixel-process-death-ready-v1")
        record.put("deadlineElapsedRealtimeMillis", deadline)
        record.put("readyElapsedRealtimeMillis", SystemClock.elapsedRealtime())
        record.put("callbacks", JSONArray(fixture.callbacks.observations()))
        record.put("taskId", fixture.callbacks.taskId)
        record.put(
            "viewModelScopeActive",
            fixture.model.viewModelScope.coroutineContext[Job]
                ?.isActive,
        )
        record.put(
            "workerAvailability",
            fixture.model.operationAvailability.value
                .toString(),
        )
        recordPublication(record, fixture)
        record.put("c1Sha256", hash(File(admission.directory, "c1-record.bin")))
        record.put("baseSha256", preserve(fixture.file.baseFile, "ready-record.base") ?: JSONObject.NULL)
        record.put("partialSha256", preserve(fixture.file.temporaryFile, "ready-record.new") ?: JSONObject.NULL)
        writeNew("phase-ready", record)
        check(SystemClock.elapsedRealtime() < deadline) { "Ready phase missed the bounded gate" }
        InstrumentationRegistry.getInstrumentation().sendStatus(
            2,
            Bundle().apply { putString("processDeathReady", record.toString()) },
        )
    }

    private fun recordPublication(
        record: JSONObject,
        fixture: RecoveryProcessFixture,
    ) {
        val preStop = requireNotNull(fixture.callbacks.beforeStop)
        record.put("preStopStartCalls", preStop.startCalls)
        record.put("preStopAutosave", preStop.autosave.toString())
        record.put("preStopPendingIsC2", preStop.autosave.pendingStateToken == fixture.second.token)
        record.put("preStopPublishingIsAbsent", preStop.autosave.publishingStateToken == null)
        val autosave = fixture.workflow.autosave.value
        record.put("autosave", autosave.toString())
        record.put("pendingIsC2", autosave.pendingStateToken == fixture.second.token)
        record.put("publishingIsC2", autosave.publishingStateToken == fixture.second.token)
        record.put("publishedIsC1", autosave.publishedStateToken == fixture.first.token)
        record.put("publishedIsC2", autosave.publishedStateToken == fixture.second.token)
        record.put(
            "operation",
            fixture.workflow.operation.value
                .toString(),
        )
        record.put("startCalls", fixture.file.startCalls)
        record.put("finishCalls", fixture.file.finishCalls)
    }

    fun readReady(priorPid: Int): JSONObject {
        val record = JSONObject(File(admission.directory, "phase-ready.json").readText())
        assertEquals("nene-pixel-process-death-ready-v1", record.getString("schema"))
        assertEquals(priorPid, record.getInt("pid"))
        assertEquals(admission.run, record.getString("run"))
        assertEquals(admission.case.argument, record.getString("case"))
        assertEquals(RecoveryProcessAdmission.FIXTURE, record.getString("fixture"))
        assertEquals(2L, record.getLong("originalGeneration"))
        assertEquals(admission.case.expectedGeneration, record.getLong("expectedGeneration"))
        return record
    }

    fun expired() {
        writeNew("phase-timeout", identity("nene-pixel-process-death-timeout-v1"))
    }

    fun recovered(priorPid: Int) {
        val record = identity("nene-pixel-process-death-recovered-v1")
        record.put("priorPid", priorPid)
        record.put("normalFactory", true)
        writeNew("recovered", record)
        InstrumentationRegistry.getInstrumentation().sendStatus(
            2,
            Bundle().apply { putString("processDeathRecovered", record.toString()) },
        )
    }

    private fun identity(schema: String): JSONObject =
        JSONObject()
            .put("schema", schema)
            .put("pid", Process.myPid())
            .put("run", admission.run)
            .put("case", admission.case.argument)
            .put("fixture", RecoveryProcessAdmission.FIXTURE)
            .put("originalGeneration", 2L)
            .put("expectedGeneration", admission.case.expectedGeneration)

    private fun preserve(
        source: File,
        name: String,
    ): String? {
        if (!source.isFile) return null
        return hash(source.copyTo(File(admission.directory, name)))
    }

    private fun hash(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
            "%02x".format(Locale.ROOT, it)
        }

    private fun writeNew(
        name: String,
        record: JSONObject,
    ) {
        val target = File(admission.directory, "$name.json")
        val pending = File(admission.directory, "$name.pending")
        check(!target.exists() && pending.createNewFile()) { "Journal destination already exists" }
        FileOutputStream(pending).use { stream ->
            stream.write((record.toString() + "\n").toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        check(pending.renameTo(target)) { "Could not publish the write-once journal" }
    }
}
