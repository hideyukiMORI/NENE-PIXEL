package io.github.hideyukimori.nenepixel.measurement

import android.app.ActivityManager
import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyPreviewColorResult
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyReductionProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyReductionRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourcePreview
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInitializationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

@RunWith(AndroidJUnit4::class)
internal class P4LegacyImportRetentionMeasurementTest {
    @Test
    fun measureP4LegacyImportRetentionOnPhysicalProfile() =
        runBlocking {
            val environment = P2AndroidMeasurementEnvironment.fromRunnerArguments()
            val runIndex = requiredRunIndex()
            val buildCommit = requiredBuildCommit()
            val workload = P4LegacyImportRetentionWorkload()
            workload.initialize()
            reportProcessIdentity(runIndex, buildCommit, environment)
            assertPhysicalEnvironment(environment)
            workload.assertBaselineOwners()
            val baseline = PostGcMemorySnapshot.captureBaseline(workload.workflow)

            workload.populate()
            workload.assertRetainedOwners()
            val retained = PostGcMemorySnapshot.captureRetainedMemory(workload.workflow)

            workload.replacePreviewTenTimes()
            workload.assertPostCycleOwners()
            val afterCycles = PostGcMemorySnapshot.captureRetainedMemory(workload.workflow)
            workload.assertOldPublicProjectionsReleased()
            val reportText = report(runIndex, buildCommit, environment, baseline, retained, afterCycles)
            println(reportText)
            InstrumentationRegistry.getInstrumentation().sendStatus(
                REPORT_STATUS_CODE,
                Bundle().apply { putString(REPORT_BUNDLE_KEY, reportText) },
            )
        }

    private fun reportProcessIdentity(
        runIndex: Int,
        buildCommit: String,
        environment: P2AndroidMeasurementEnvironment,
    ) {
        val processId = Process.myPid()
        val processStart = Process.getStartElapsedRealtime()
        check(processId > 0 && processStart > 0L)
        InstrumentationRegistry.getInstrumentation().sendStatus(
            PROCESS_IDENTITY_STATUS_CODE,
            Bundle().apply {
                putString("p4MemoryFamily", P4_CANDIDATE_LEGACY_IMPORT)
                putString("p4MemoryBuildCommit", buildCommit)
                putInt("p4MemoryRunIndex", runIndex)
                putInt("p4MemoryProcessId", processId)
                putLong("p4MemoryProcessStartElapsedRealtimeMillis", processStart)
                putLong("p4MemoryRuntimeMaxMemoryBytes", Runtime.getRuntime().maxMemory())
                putInt(
                    "p4MemoryClassMebibytes",
                    environment.targetContext.getSystemService(ActivityManager::class.java).memoryClass,
                )
            },
        )
    }

    private fun requiredRunIndex(): Int {
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString(P4_MEMORY_FAMILY_ARGUMENT) == P4_CANDIDATE_LEGACY_IMPORT)
        val runIndex = arguments.getString(P4_MEMORY_RUN_INDEX_ARGUMENT)?.toIntOrNull()
        return requireNotNull(runIndex?.takeIf { it in 1..P4_MEMORY_RUN_COUNT })
    }

    private fun requiredBuildCommit(): String {
        val value = InstrumentationRegistry.getArguments().getString(P4_MEMORY_BUILD_COMMIT_ARGUMENT)?.lowercase()
        return requireNotNull(value?.takeIf(P4_COMMIT_PATTERN::matches))
    }

    private fun assertPhysicalEnvironment(environment: P2AndroidMeasurementEnvironment) {
        check(!environment.emulatorDetection.isEmulator)
        check(!environment.auxiliaryEmulatorArgumentPresent)
        check(environment.profileId == P4_MEMORY_PROFILE)
        val display = P2AndroidPhysicalCheckpointCapture.defaultDisplay(environment.targetContext)
        P2AndroidPhysicalCheckpointCapture
            .capture(environment.targetContext, display, P4_CANDIDATE_LEGACY_IMPORT, sampleIndex = 0)
            .assertInitialValidity()
    }

    private fun report(
        runIndex: Int,
        buildCommit: String,
        environment: P2AndroidMeasurementEnvironment,
        baseline: PostGcMemorySnapshot,
        retained: PostGcMemorySnapshot,
        afterCycles: PostGcMemorySnapshot,
    ): String =
        listOf(
            "P4_LEGACY_IMPORT_RETENTION",
            "schema=$P4_LEGACY_IMPORT_SCHEMA",
            "family=$P4_CANDIDATE_LEGACY_IMPORT",
            "run=$runIndex",
            "run_status=valid",
            "measurement_build_commit=$buildCommit",
            "profile=${environment.profileId}",
            "current_pixels=${PixelLimits.MAX_CANVAS_PIXELS}",
            "legacy_pixels=$LEGACY_PIXEL_COUNT",
            "distinct_rgba=$LEGACY_PIXEL_COUNT",
            "destination_entries=$PALETTE_SIZE",
            "owner_inventory=current_document:1,operation:1,source:1,selected_destination:1,reduction:1",
            "fixture_inventory=destination_definitions:2,source_copy:0,preview_copy:0",
            "old_projection_weak_refs_cleared=$PREVIEW_REPLACEMENT_COUNT",
            "baseline_java_bytes=${baseline.javaHeapUsedBytes}",
            "retained_java_bytes=${retained.javaHeapUsedBytes}",
            "retained_java_delta_bytes=${retained.javaHeapUsedBytes - baseline.javaHeapUsedBytes}",
            "after_cycles_java_bytes=${afterCycles.javaHeapUsedBytes}",
            "baseline_pss_kib=${baseline.totalPssKilobytes}",
            "retained_pss_kib=${retained.totalPssKilobytes}",
            "retained_pss_delta_kib=${retained.totalPssKilobytes - baseline.totalPssKilobytes}",
            "after_cycles_pss_kib=${afterCycles.totalPssKilobytes}",
        ).joinToString(" ")

    private companion object {
        const val PROCESS_IDENTITY_STATUS_CODE: Int = 3
        const val REPORT_STATUS_CODE: Int = 4
        const val REPORT_BUNDLE_KEY: String = "p4MemoryReport"
        const val LEGACY_PIXEL_COUNT: Int = 65_536
        const val PALETTE_SIZE: Int = 256
        const val PREVIEW_REPLACEMENT_COUNT: Int = 10
        val P4_COMMIT_PATTERN: Regex = Regex("[0-9a-f]{40}")
        const val P4_MEMORY_PROFILE: String = "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36"
    }
}

private class P4LegacyImportRetentionWorkload {
    private val size =
        CanvasSize.create(
            CanvasWidth.create(PixelLimits.MAX_CANVAS_AXIS).required(),
            CanvasHeight.create(PixelLimits.MAX_CANVAS_AXIS).required(),
        )
    private val destinationA by lazy { destination(colorOffset = 0x00010000) }
    private val destinationB by lazy { destination(colorOffset = 0x00020000) }
    private val storage = P4LegacyProjectStorage(size)
    private val runtime =
        EditorRuntime.create(
            size,
            destination(colorOffset = 0),
            P4SequentialDocumentIdSource(),
        )
    val workflow: EditorPersistenceWorkflow =
        EditorPersistenceWorkflow.create(
            runtime,
            PersistencePorts(
                storage,
                P4MissingRecoveryRecord(),
                P4CancelledPngExport(),
                P4CancelledPaletteJsonExport(),
                P4CancelledPaletteJsonImport(),
            ),
            Dispatchers.Unconfined,
        )
    private val oldProjections = mutableListOf<WeakReference<LegacyReductionProjection>>()
    private lateinit var operation: PersistenceOperationHandle

    suspend fun initialize() {
        assertSame(RecoveryInitializationResult.Ready, workflow.initializeRecovery())
    }

    fun assertBaselineOwners() {
        assertSame(PersistenceOperationPhase.Idle, workflow.operation.value.phase)
        assertEquals(size, runtime.state.documentState.size)
    }

    suspend fun populate() {
        val result = workflow.load()
        operation = (result as PersistenceRequestResult.LegacyConversionRequired).operation
        val ready = workflow.legacyImport.previewSource(operation, destinationA) as LegacyReductionRequestResult.Ready
        val projection = currentImport()
        assertEquals(LEGACY_PIXEL_COUNT, projection.distinctColorCount)
        assertEquals(operation, projection.operation)
        assertSame(destinationA, projection.selectedDestination)
        assertEquals(ready.handle, requireNotNull(projection.reduction).handle)
        assertSourceBoundary(projection.source)
    }

    fun assertRetainedOwners() {
        val projection = currentImport()
        assertEquals(operation, projection.operation)
        assertSame(destinationA, projection.selectedDestination)
        assertEquals(PALETTE_SIZE, requireNotNull(projection.reduction).definition.palette.entryCount)
    }

    suspend fun replacePreviewTenTimes() {
        repeat(PREVIEW_REPLACEMENT_COUNT) { replacement ->
            val previous = requireNotNull(currentImport().reduction)
            oldProjections += WeakReference(previous)
            val destination = if (replacement % 2 == 0) destinationB else destinationA
            val ready =
                workflow.legacyImport.previewSource(operation, destination) as LegacyReductionRequestResult.Ready
            assertSame(PersistenceRequestResult.Stale, workflow.legacyImport.acceptReduction(previous.handle))
            val current = currentImport()
            assertEquals(operation, current.operation)
            assertSame(destination, current.selectedDestination)
            assertEquals(ready.handle, requireNotNull(current.reduction).handle)
            assertNotEquals(previous.handle, ready.handle)
        }
    }

    fun assertPostCycleOwners() {
        val projection = currentImport()
        assertEquals(operation, projection.operation)
        assertSame(destinationA, projection.selectedDestination)
        assertEquals(PALETTE_SIZE, requireNotNull(projection.reduction).definition.palette.entryCount)
        assertEquals(PREVIEW_REPLACEMENT_COUNT, oldProjections.size)
    }

    fun assertOldPublicProjectionsReleased() {
        assertTrue(oldProjections.all { reference -> reference.get() == null })
    }

    private fun currentImport() =
        (workflow.operation.value.phase as PersistenceOperationPhase.LegacyConversionRequired).import

    private fun assertSourceBoundary(source: LegacySourcePreview) {
        assertEquals(size, source.size)
        assertEquals(
            LegacyPreviewColorResult.Color(PixelColor.fromPackedRgba8888(0)),
            source.colorAt(position(0, 0)),
        )
        assertEquals(
            LegacyPreviewColorResult.Color(PixelColor.fromPackedRgba8888(LEGACY_PIXEL_COUNT - 1)),
            source.colorAt(position(PixelLimits.MAX_CANVAS_AXIS - 1, PixelLimits.MAX_CANVAS_AXIS - 1)),
        )
    }

    private fun destination(colorOffset: Int): PaletteDefinition =
        PaletteDefinition
            .create(
                Palette
                    .create(
                        List(PALETTE_SIZE) { slot ->
                            PixelColor.fromPackedRgba8888(colorOffset + slot)
                        },
                    ).required(),
                PaletteIndex.create(PALETTE_SIZE - 1).required(),
            ).required()

    private fun position(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(PixelX.create(x).required(), PixelY.create(y).required())

    private companion object {
        const val LEGACY_PIXEL_COUNT: Int = 65_536
        const val PALETTE_SIZE: Int = 256
        const val PREVIEW_REPLACEMENT_COUNT: Int = 10
    }
}

private class P4LegacyProjectStorage(
    private val size: CanvasSize,
) : ProjectStoragePort {
    override suspend fun save(document: DocumentState): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

    override suspend fun load(): ProjectLoadOutcome =
        ProjectLoadOutcome.Loaded(
            DocumentImportSource.Legacy(
                LegacyRgbaSource
                    .createPackedRgba8888(
                        DocumentId.create(LEGACY_DOCUMENT_ID).required(),
                        Revision.create(41L).required(),
                        size,
                        IntArray(size.pixelCount.toInt()) { it },
                    ).required(),
            ),
        )

    override suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome =
        LegacySourceCopyOutcome.Cancelled

    private companion object {
        const val LEGACY_DOCUMENT_ID: String = "99999999999999999999999999999999"
    }
}

private class P4MissingRecoveryRecord : RecoveryRecordPort {
    override suspend fun inspect(): RecoveryInspection = RecoveryInspection.Missing

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        RecoveryRetirementOutcome.Stale

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: DocumentState,
    ): RecoveryPublicationOutcome =
        RecoveryPublicationOutcome.Failed(
            io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure.START_WRITE,
            io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRollbackOutcome.NOT_NEEDED,
        )
}

private class P4CancelledPngExport : PngExportPort {
    override suspend fun export(document: DocumentState): PngExportOutcome = PngExportOutcome.Cancelled
}

private class P4CancelledPaletteJsonExport : PaletteJsonExportPort {
    override suspend fun export(definition: PaletteDefinition): PaletteJsonExportOutcome =
        PaletteJsonExportOutcome.Cancelled
}

private class P4CancelledPaletteJsonImport : PaletteJsonImportPort {
    override suspend fun import(): PaletteJsonImportOutcome = PaletteJsonImportOutcome.Cancelled
}

private class P4SequentialDocumentIdSource : DocumentIdSource {
    private var next = 0

    override fun nextDocumentId(): DocumentId =
        DocumentId.create(('a'.code + next++).toChar().toString().repeat(32)).required()
}
