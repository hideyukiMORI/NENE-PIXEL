package io.github.hideyukimori.nenepixel

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import java.io.File

/** Arguments bind a host-admitted fresh userdata lane; they alone are not preservation admission. */
internal class RecoveryProcessAdmission {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    val context = instrumentation.targetContext
    val run = requireNotNull(arguments.getString("processDeathRun"))
    val case = RecoveryProcessCase.entries.single { it.argument == arguments.getString("processDeathCase") }
    val directory = File(context.noBackupFilesDir, "issue-251-$run/${case.argument}")
    val recoveryPath = File(context.noBackupFilesDir, "nene-pixel-recovery-v1")

    init {
        check(Build.VERSION.SDK_INT == 35)
        assertEquals("io.github.hideyukimori.nenepixel", context.packageName)
        assertEquals("isolated-api35-v1", arguments.getString("processDeathAdmission"))
        assertEquals(FIXTURE, arguments.getString("processDeathFixture"))
        assertEquals("2", arguments.getString("processDeathOriginalGeneration"))
        check(Regex("[a-z0-9][a-z0-9-]{2,63}").matches(run))
    }

    fun prepare() {
        check(!requireNotNull(directory.parentFile).exists()) { "Run directory already exists" }
        listOf("", ".new", ".bak").forEach { suffix ->
            check(!File(recoveryPath.path + suffix).exists()) { "Ordinary recovery path is occupied" }
        }
        check(!File(context.noBackupFilesDir, "reference-underlays").exists())
        check(directory.mkdirs())
    }

    fun priorPid(): Int {
        check(directory.isDirectory)
        return requireNotNull(arguments.getString("processDeathPriorPid")).toInt().also { check(it > 0) }
    }

    companion object {
        const val FIXTURE = "two-frame-three-layer-v1"
        const val GATE_MILLIS = 10_000L
    }
}
