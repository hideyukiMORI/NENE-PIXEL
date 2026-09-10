// Issue #86 / P3-03 private recovery record host evidence.
//
// This init script is applied for one reviewed collection invocation only. It never becomes part of
// the repository build: it registers one private JavaExec task on :adapters:persistence and borrows
// the already locked testDebugUnitTest runtime classpath, so it adds no dependency, configuration,
// plugin, or lock/verification-metadata change.
//
//   ./gradlew -I docs/quality/measurements/issue86-host-evidence.init.gradle.kts \
//       :adapters:persistence:issue86HostEvidence --offline

import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

val evidenceProjectPath = ":adapters:persistence"
val evidenceMainClass = "io.github.hideyukimori.nenepixel.adapters.persistence.RecoveryRecordHostEvidenceKt"
val evidenceTimeoutSeconds = 60L

gradle.projectsEvaluated {
    // An init script also applies to the build-logic included build, which has no adapters module.
    val evidenceProject = rootProject.findProject(evidenceProjectPath) ?: return@projectsEvaluated
    val debugUnitTest = evidenceProject.tasks.named<Test>("testDebugUnitTest")
    val outputDirectory =
        evidenceProject.layout.buildDirectory
            .dir("reports/issue86-host-evidence")
            .get()
            .asFile
    val standardOutputFile = File(outputDirectory, "m3-recovery-record-host-latency-v1.csv")
    val errorOutputFile = File(outputDirectory, "m3-recovery-record-host-evidence-stderr.txt")

    evidenceProject.tasks.register<JavaExec>("issue86HostEvidence") {
        group = "verification"
        description = "Runs the Issue #86 private recovery record host evidence runner exactly once."
        dependsOn(evidenceProject.tasks.named("compileDebugUnitTestKotlin"))
        mainClass.set(evidenceMainClass)
        classpath = debugUnitTest.get().classpath + debugUnitTest.get().testClassesDirs
        timeout.set(Duration.ofSeconds(evidenceTimeoutSeconds))
        outputs.upToDateWhen { false }
        doFirst {
            outputDirectory.mkdirs()
            val execTask = this as JavaExec
            execTask.standardOutput = FileOutputStream(standardOutputFile)
            execTask.errorOutput = FileOutputStream(errorOutputFile)
        }
        doLast {
            val execTask = this as JavaExec
            execTask.standardOutput.close()
            execTask.errorOutput.close()
        }
    }
}
