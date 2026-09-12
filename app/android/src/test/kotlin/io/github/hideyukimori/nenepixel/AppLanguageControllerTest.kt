package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AppLanguageControllerTest {
    @Test
    fun startupReadsStoredSelectionAndEqualSelectionDoesNotWrite() =
        runBlocking {
            val storage = LanguageStorageFixture(AppLanguage.Japanese)
            val controller = AppLanguageController(storage, this)
            assertEquals(AppLanguageStatus.Loading, controller.settings.value.status)
            controller.refresh()
            yield()
            assertEquals(AppLanguage.Japanese, controller.settings.value.selection)
            controller.select(AppLanguage.Japanese)
            yield()
            assertEquals(0, storage.writes.size)
        }

    @Test
    fun selectionPublishesOnlyAfterWriteAndRejectsOverlappingSelection() =
        runBlocking {
            val storage = LanguageStorageFixture(AppLanguage.English)
            val controller = AppLanguageController(storage, this)
            controller.refresh()
            yield()
            val completion = CompletableDeferred<AppLanguageWrite>()
            storage.nextWrite = completion
            controller.select(AppLanguage.Japanese)
            controller.select(AppLanguage.SimplifiedChinese)
            yield()
            assertEquals(listOf(AppLanguage.Japanese), storage.writes)
            assertEquals(AppLanguage.English, controller.settings.value.selection)
            assertEquals(AppLanguageStatus.Applying, controller.settings.value.status)
            completion.complete(AppLanguageWrite.Saved)
            yield()
            assertEquals(AppLanguage.Japanese, controller.settings.value.selection)
            assertEquals(AppLanguageStatus.Ready, controller.settings.value.status)
        }

    @Test
    fun failedWriteRetainsAppliedLanguageAndRetryRepeatsTheRequestedSelection() =
        runBlocking {
            val storage = LanguageStorageFixture(AppLanguage.English)
            val controller = AppLanguageController(storage, this)
            controller.refresh()
            yield()
            storage.nextWrite = CompletableDeferred(AppLanguageWrite.Failed)
            controller.select(AppLanguage.SimplifiedChinese)
            yield()
            assertEquals(AppLanguage.English, controller.settings.value.selection)
            assertEquals(AppLanguageStatus.WriteFailed, controller.settings.value.status)
            storage.nextWrite = CompletableDeferred(AppLanguageWrite.Saved)
            controller.retry()
            yield()
            assertEquals(listOf(AppLanguage.SimplifiedChinese, AppLanguage.SimplifiedChinese), storage.writes)
            assertEquals(AppLanguage.SimplifiedChinese, controller.settings.value.selection)
        }

    @Test
    fun failedStartupReadIsRetryableAndExternalSelectionIsReconciled() =
        runBlocking {
            val storage = LanguageStorageFixture(AppLanguage.System)
            storage.readResult = AppLanguageRead.Failed
            val controller = AppLanguageController(storage, this)
            controller.refresh()
            yield()
            assertEquals(AppLanguageStatus.ReadFailed, controller.settings.value.status)
            storage.readResult = AppLanguageRead.Loaded(AppLanguage.Japanese)
            controller.retry()
            yield()
            assertEquals(AppLanguage.Japanese, controller.settings.value.selection)
            storage.readResult = AppLanguageRead.Loaded(AppLanguage.System)
            controller.refresh()
            yield()
            assertEquals(AppLanguage.System, controller.settings.value.selection)
        }

    @Test
    fun activityRefreshDuringWriteIsReplayedAfterTheWriteCompletes() =
        runBlocking {
            val storage = LanguageStorageFixture(AppLanguage.English)
            val controller = AppLanguageController(storage, this)
            controller.refresh()
            yield()
            val completion = CompletableDeferred<AppLanguageWrite>()
            storage.nextWrite = completion
            controller.select(AppLanguage.Japanese)
            controller.refresh()
            yield()
            assertEquals(1, storage.reads)
            completion.complete(AppLanguageWrite.Saved)
            yield()
            yield()
            assertEquals(2, storage.reads)
            assertEquals(AppLanguage.Japanese, controller.settings.value.selection)
        }
}

private class LanguageStorageFixture(
    language: AppLanguage,
) : AppLanguageStorage {
    var readResult: AppLanguageRead = AppLanguageRead.Loaded(language)
    var nextWrite = CompletableDeferred(AppLanguageWrite.Saved)
    val writes = mutableListOf<AppLanguage>()
    var reads = 0

    override suspend fun read(): AppLanguageRead {
        reads += 1
        return readResult
    }

    override suspend fun write(language: AppLanguage): AppLanguageWrite {
        writes.add(language)
        val result = nextWrite.await()
        if (result == AppLanguageWrite.Saved) readResult = AppLanguageRead.Loaded(language)
        return result
    }
}
