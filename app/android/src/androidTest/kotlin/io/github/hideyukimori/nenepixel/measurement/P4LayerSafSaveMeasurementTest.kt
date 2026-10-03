package io.github.hideyukimori.nenepixel.measurement

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class P4LayerSafSaveMeasurementTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun savesMaximumProjectToTwentyFiveFreshGrantedDestinations() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("p4LayerCollect") == "saf-save-v1")
        val admission = P4LayerRunAdmission.read()
        check(admission.artifactRole == "candidate" && admission.slotId == "saf-save-layers16-candidate")
        P4LayerSafRun(compose, admission).collect()
    }
}
