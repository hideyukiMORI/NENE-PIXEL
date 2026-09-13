package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hideyukimori.nenepixel.core.application.persistence.ExpectedRecoveryLineage
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryPublicationOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementOutcome
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class LegacyConversionDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun palettePreviewRequiresAcknowledgementAndResetsItForEveryNewReduction() {
        val fixture = PresentationTestValues.fixture()
        val storage = LegacyStorage(legacySource(fixture.initialDocument.id))
        val presets = LegacyPalettePresets(definition(0x000000ff), definition(0x202020ff), definition(0x404040ff))

        composeRule.setContent {
            TestNenePixelEditor(
                fixture.controller,
                projectStorage = storage,
                presets = presets,
            )
        }
        composeRule.onNodeWithTag("editor_file").performClick()
        composeRule.onNodeWithTag("editor_load").performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodes(hasTestTag("legacy_palette_dusk")).fetchSemanticsNodes().size == 1
        }

        composeRule.onNodeWithTag("legacy_copy").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) { storage.copyCount == 1 }
        composeRule.onNodeWithTag("legacy_apply").assertIsNotEnabled()

        composeRule.onNodeWithTag("legacy_palette_dusk").performClick()
        awaitReduction()
        composeRule.onNodeWithTag("legacy_acknowledge").performClick()
        composeRule.onNodeWithTag("legacy_apply").assertIsEnabled()

        composeRule.onNodeWithTag("legacy_palette_grayscale").performScrollTo().performClick()
        awaitReduction()
        composeRule.onNodeWithTag("legacy_apply").assertIsNotEnabled()
        composeRule.onNodeWithTag("legacy_acknowledge").performClick()
        composeRule.onNodeWithTag("legacy_apply").assertIsEnabled()

        composeRule.onNodeWithTag("legacy_palette_dusk").performScrollTo().performClick()
        awaitReduction()
        composeRule.onNodeWithTag("legacy_apply").assertIsNotEnabled()
    }

    @Test
    fun recoveryConversionCannotApplyUntilItsOriginalCopyIsVerified() {
        val fixture = PresentationTestValues.fixture()
        val source = legacySource(fixture.initialDocument.id)
        val storage = LegacyStorage(source)
        val presets = LegacyPalettePresets(definition(0x000000ff), definition(0x202020ff), definition(0x404040ff))

        composeRule.setContent {
            TestNenePixelEditor(
                fixture.controller,
                projectStorage = storage,
                presets = presets,
                recoveryRecord = RecoveryLegacyRecord(source),
            )
        }
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodes(hasTestTag("editor_recovery_offer")).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag("editor_recover_unsaved").performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodes(hasTestTag("legacy_palette_dusk")).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag("legacy_palette_dusk").performScrollTo().performClick()
        awaitReduction()
        composeRule.onNodeWithTag("legacy_acknowledge").performClick()
        composeRule.onNodeWithTag("legacy_apply").assertIsNotEnabled()
        composeRule.onNodeWithTag("legacy_copy").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) { storage.copyCount == 1 }
        composeRule.onNodeWithTag("legacy_apply").assertIsEnabled()
    }

    @Test
    fun cancellingAnInFlightCopyKeepsTheModalUntilWorkflowDrainCompletes() {
        val fixture = PresentationTestValues.fixture()
        val storage = BlockingLegacyStorage(legacySource(fixture.initialDocument.id))
        val presets = LegacyPalettePresets(definition(0x000000ff), definition(0x202020ff), definition(0x404040ff))

        composeRule.setContent {
            TestNenePixelEditor(fixture.controller, projectStorage = storage, presets = presets)
        }
        composeRule.onNodeWithTag("editor_file").performClick()
        composeRule.onNodeWithTag("editor_load").performClick()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodes(hasTestTag("legacy_palette_dusk")).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag("legacy_copy").performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT) { storage.copyStarted }
        composeRule.onNodeWithTag("legacy_cancel").performClick()
        composeRule.onNodeWithTag("legacy_cancel").assertIsNotEnabled()
        composeRule.onNodeWithTag("legacy_cancel").assertExists()
        storage.releaseCopy()
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodes(hasTestTag("legacy_cancel")).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun awaitReduction() {
        composeRule.waitUntil(TIMEOUT) {
            composeRule.onAllNodes(hasTestTag("legacy_acknowledge") and isEnabled()).fetchSemanticsNodes().size == 1
        }
    }

    private fun legacySource(id: io.github.hideyukimori.nenepixel.core.domain.document.DocumentId): LegacyRgbaSource {
        val size =
            CanvasSize.create(
                CanvasWidth.create(17).value(),
                CanvasHeight.create(16).value(),
            )
        val pixels = IntArray(size.pixelCount.toInt()) { index -> ((index % 257) shl 8) or 0xff }
        return LegacyRgbaSource.createPackedRgba8888(id, Revision.initial(), size, pixels).value()
    }

    private fun definition(color: Int): PaletteDefinition =
        PaletteDefinition
            .create(
                Palette.create(listOf(PixelColor.fromPackedRgba8888(color), PixelColor.blank)).value(),
                PaletteIndex.first,
            ).value()

    private fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid legacy dialog fixture: $rejection")
        }

    private companion object {
        const val TIMEOUT: Long = 10_000L
    }
}

private class LegacyStorage(
    private val source: LegacyRgbaSource,
) : ProjectStoragePort {
    var copyCount: Int = 0
        private set

    override suspend fun save(
        document: io.github.hideyukimori.nenepixel.core.domain.document.DocumentState,
    ): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

    override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Loaded(DocumentImportSource.Legacy(source))

    override suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome {
        copyCount += 1
        return LegacySourceCopyOutcome.Copied
    }
}

private class RecoveryLegacyRecord(
    private val source: LegacyRgbaSource,
) : RecoveryRecordPort {
    override suspend fun inspect(): RecoveryInspection =
        RecoveryInspection.Candidate(generation(1L), DocumentImportSource.Legacy(source))

    override suspend fun retire(expected: ExpectedRecoveryLineage): RecoveryRetirementOutcome =
        RecoveryRetirementOutcome.Retired(generation(2L))

    override suspend fun publishCandidate(
        expected: ExpectedRecoveryLineage,
        document: io.github.hideyukimori.nenepixel.core.domain.document.DocumentState,
    ): RecoveryPublicationOutcome = RecoveryPublicationOutcome.Published(generation(2L))

    private fun generation(value: Long): RecoveryGeneration =
        when (val result = RecoveryGeneration.create(value)) {
            is RecoveryGenerationResult.Created -> result.generation
            RecoveryGenerationResult.Rejected -> error("Invalid recovery generation fixture: $value")
        }
}

private class BlockingLegacyStorage(
    private val source: LegacyRgbaSource,
) : ProjectStoragePort {
    private val copyRelease = CompletableDeferred<Unit>()
    var copyStarted: Boolean = false
        private set

    override suspend fun save(
        document: io.github.hideyukimori.nenepixel.core.domain.document.DocumentState,
    ): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

    override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Loaded(DocumentImportSource.Legacy(source))

    override suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome {
        copyStarted = true
        copyRelease.await()
        return LegacySourceCopyOutcome.Copied
    }

    fun releaseCopy() {
        copyRelease.complete(Unit)
    }
}
