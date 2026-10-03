package io.github.hideyukimori.nenepixel.measurement

import android.os.Bundle
import android.os.Process
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
internal class P4LayerFrameFixturePreparationTest {
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(Timeout.seconds(240)).around(compose)

    @Test
    fun stagesPinnedFrameFixtureWithActualAppGrant() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("p4LayerCollect") == "frame-fixtures-v1")
        val admission = P4LayerRunAdmission.read()
        val spec = P4LayerFrameFixtureSpec.from(admission)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.filesDir, spec.reportDirectory)
        check(directory.mkdir()) { "Frame fixture report directory is occupied" }
        val report = File(directory, "fixture.txt")
        check(report.createNewFile())
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            val documents = P4LayerFixtureDocuments(activity) { condition -> compose.waitUntil(15_000, condition) }
            val granted = documents.stage(spec.name, spec.fixture)
            val line = reportLine(admission, spec, granted)
            report.writeText("$line\n")
            instrumentation.sendStatus(3, Bundle().apply { putString("p4LayerFrameFixture", line) })
        }
    }

    private fun reportLine(
        admission: P4LayerRunAdmission,
        spec: P4LayerFrameFixtureSpec,
        granted: P4GrantedDocument,
    ): String {
        val fields =
            admission.reportFields() +
                listOf(
                    "group_id" to spec.group,
                    "fixture_name" to granted.name,
                    "fixture_asset" to spec.fixture.asset,
                    "fixture_uri" to granted.uri.toString(),
                    "fixture_bytes" to spec.fixture.byteCount.toString(),
                    "fixture_sha256" to spec.fixture.sha256,
                    "grantee_uid" to granted.granteeUid.toString(),
                    "provider_uid" to granted.providerUid.toString(),
                    "process_id" to Process.myPid().toString(),
                    "process_start_elapsed_realtime_millis" to Process.getStartElapsedRealtime().toString(),
                    "report_path" to "files/${spec.reportDirectory}/fixture.txt",
                )
        return "P4_LAYER_FRAME_FIXTURE " + fields.joinToString(" ") { (key, value) -> "$key=$value" }
    }
}
