package io.github.hideyukimori.nenepixel.measurement

import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class P2AndroidMeasurementEnvironment(
    val targetContext: Context,
    val profileId: String,
    val evidenceClass: String,
    val emulatorDetection: EmulatorDetection,
    val warmupIterations: Int,
    val sampleCount: Int,
    val frameWarmupIterations: Int,
    val frameSampleCount: Int,
) {
    val outputFile: File
        get() = File(targetContext.filesDir, OUTPUT_RELATIVE_PATH)

    val frameOutputFile: File
        get() = File(targetContext.filesDir, FRAME_OUTPUT_RELATIVE_PATH)

    fun finalCommandOutputFile(plan: P2AndroidFinalCommandPlan): File =
        File(targetContext.filesDir, plan.outputRelativePath)

    fun memoryRunOutputFile(runIndex: Int): File =
        File(
            targetContext.filesDir,
            "p2-measurements/p2-android-memory-run-${runIndex.toString().padStart(2, '0')}.csv",
        )

    val memoryAggregateOutputFile: File
        get() = File(targetContext.filesDir, MEMORY_AGGREGATE_OUTPUT_RELATIVE_PATH)

    companion object {
        fun fromRunnerArguments(): P2AndroidMeasurementEnvironment {
            val arguments = InstrumentationRegistry.getArguments()
            val profileId = arguments.requiredString(PROFILE_ID_ARGUMENT)
            val allowAuxiliaryEmulator = arguments.boolean(AUXILIARY_EMULATOR_ARGUMENT, false)
            val detection = EmulatorDetector.detect()
            check(!detection.isEmulator || allowAuxiliaryEmulator) {
                "Emulator measurements are auxiliary only. Set runner argument " +
                    "'$AUXILIARY_EMULATOR_ARGUMENT' to 'true' explicitly to run them."
            }
            return P2AndroidMeasurementEnvironment(
                targetContext = InstrumentationRegistry.getInstrumentation().targetContext,
                profileId = profileId,
                evidenceClass = if (detection.isEmulator) AUXILIARY_EVIDENCE else PHYSICAL_EVIDENCE,
                emulatorDetection = detection,
                warmupIterations =
                    arguments.positiveInt(
                        WARMUP_ARGUMENT,
                        DEFAULT_WARMUP_ITERATIONS,
                        MAX_WARMUP_ITERATIONS,
                    ),
                sampleCount =
                    arguments.positiveInt(
                        SAMPLE_COUNT_ARGUMENT,
                        DEFAULT_SAMPLE_COUNT,
                        MAX_SAMPLE_COUNT,
                    ),
                frameWarmupIterations =
                    arguments.positiveInt(
                        FRAME_WARMUP_ARGUMENT,
                        DEFAULT_FRAME_WARMUP_ITERATIONS,
                        MAX_WARMUP_ITERATIONS,
                    ),
                frameSampleCount =
                    arguments.positiveInt(
                        FRAME_SAMPLE_COUNT_ARGUMENT,
                        DEFAULT_FRAME_SAMPLE_COUNT,
                        MAX_SAMPLE_COUNT,
                    ),
            )
        }

        const val PROFILE_ID_ARGUMENT: String = "nene.p2.physicalProfileId"
        const val AUXILIARY_EMULATOR_ARGUMENT: String = "nene.p2.allowEmulatorAuxiliary"
        const val WARMUP_ARGUMENT: String = "nene.p2.warmupIterations"
        const val SAMPLE_COUNT_ARGUMENT: String = "nene.p2.sampleCount"
        const val FRAME_WARMUP_ARGUMENT: String = "nene.p2.frameWarmupIterations"
        const val FRAME_SAMPLE_COUNT_ARGUMENT: String = "nene.p2.frameSampleCount"

        private const val OUTPUT_RELATIVE_PATH: String =
            "p2-measurements/p2-android-command-measurement.csv"
        private const val FRAME_OUTPUT_RELATIVE_PATH: String =
            "p2-measurements/p2-android-frame-measurement.csv"
        private const val MEMORY_AGGREGATE_OUTPUT_RELATIVE_PATH: String =
            "p2-measurements/p2-android-memory-aggregate.csv"
        private const val PHYSICAL_EVIDENCE: String = "physical_device"
        private const val AUXILIARY_EVIDENCE: String = "auxiliary_emulator"
        private const val DEFAULT_WARMUP_ITERATIONS: Int = 5
        private const val DEFAULT_SAMPLE_COUNT: Int = 20
        private const val DEFAULT_FRAME_WARMUP_ITERATIONS: Int = 5
        private const val DEFAULT_FRAME_SAMPLE_COUNT: Int = 200
        private const val MAX_WARMUP_ITERATIONS: Int = 100
        private const val MAX_SAMPLE_COUNT: Int = 1_000
    }
}

internal data class EmulatorDetection(
    val isEmulator: Boolean,
    val signals: List<String>,
    val kernelQemu: String,
    val bootQemu: String,
)

private object EmulatorDetector {
    fun detect(): EmulatorDetection {
        val kernelQemu = AndroidSystemProperty.read("ro.kernel.qemu")
        val bootQemu = AndroidSystemProperty.read("ro.boot.qemu")
        val signals =
            buildList {
                if (kernelQemu == QEMU_ENABLED) add("ro.kernel.qemu=1")
                if (bootQemu == QEMU_ENABLED) add("ro.boot.qemu=1")
                addBuildSignals()
            }
        return EmulatorDetection(
            isEmulator = signals.isNotEmpty(),
            signals = signals,
            kernelQemu = kernelQemu,
            bootQemu = bootQemu,
        )
    }

    private fun MutableList<String>.addBuildSignals() {
        addSignal("fingerprint", Build.FINGERPRINT, listOf("generic", "emulator"))
        addSignal("hardware", Build.HARDWARE, listOf("goldfish", "ranchu"))
        addSignal("model", Build.MODEL, listOf("emulator", "sdk_gphone"))
        addSignal("product", Build.PRODUCT, listOf("sdk_gphone", "generic"))
    }

    private fun MutableList<String>.addSignal(
        name: String,
        value: String,
        markers: List<String>,
    ) {
        val normalized = value.lowercase(Locale.ROOT)
        if (markers.any(normalized::contains)) add("$name=$value")
    }

    private const val QEMU_ENABLED: String = "1"
}

private object AndroidSystemProperty {
    fun read(name: String): String {
        val process = ProcessBuilder(GETPROP_PATH, name).redirectErrorStream(true).start()
        val completed = process.waitFor(GETPROP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        check(completed) {
            process.destroyForcibly()
            "Timed out while reading Android system property '$name'."
        }
        check(process.exitValue() == 0) { "Failed to read Android system property '$name'." }
        return process.inputStream.bufferedReader().use { reader -> reader.readText().trim() }
    }

    private const val GETPROP_PATH: String = "/system/bin/getprop"
    private const val GETPROP_TIMEOUT_SECONDS: Long = 2L
}

private fun android.os.Bundle.requiredString(name: String): String =
    requireNotNull(getString(name)?.trim()?.takeIf(String::isNotEmpty)) {
        "Runner argument '$name' is required for every Android measurement run."
    }

private fun android.os.Bundle.boolean(
    name: String,
    default: Boolean,
): Boolean {
    val raw = getString(name) ?: return default
    return requireNotNull(raw.toBooleanStrictOrNull()) {
        "Runner argument '$name' must be 'true' or 'false'."
    }
}

private fun android.os.Bundle.positiveInt(
    name: String,
    default: Int,
    maximum: Int,
): Int {
    val raw = getString(name) ?: return default
    val parsed = raw.toIntOrNull()
    return requireNotNull(parsed?.takeIf { value -> value in 1..maximum }) {
        "Runner argument '$name' must be between 1 and $maximum."
    }
}
