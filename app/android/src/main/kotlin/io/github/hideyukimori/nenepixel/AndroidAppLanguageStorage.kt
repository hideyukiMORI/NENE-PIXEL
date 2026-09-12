package io.github.hideyukimori.nenepixel

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** One backing per OS version. All preference access stays on the injected I/O dispatcher. */
internal class AndroidAppLanguageStorage(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,
) : AppLanguageStorage {
    override suspend fun read(): AppLanguageRead =
        withContext(dispatcher) {
            try {
                readFromPlatform()
            } catch (_: SecurityException) {
                AppLanguageRead.Failed
            } catch (_: ClassCastException) {
                AppLanguageRead.Failed
            } catch (_: IllegalStateException) {
                AppLanguageRead.Failed
            }
        }

    override suspend fun write(language: AppLanguage): AppLanguageWrite =
        withContext(dispatcher) {
            try {
                writeToPlatform(language)
            } catch (_: SecurityException) {
                AppLanguageWrite.Failed
            } catch (_: ClassCastException) {
                AppLanguageWrite.Failed
            } catch (_: IllegalStateException) {
                AppLanguageWrite.Failed
            }
        }

    private fun readFromPlatform(): AppLanguageRead =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val tags = context.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags()
            if (tags == null) AppLanguageRead.Failed else readAppLanguageTag(tags)
        } else {
            legacyPreference().read()
        }

    private fun writeToPlatform(language: AppLanguage): AppLanguageWrite {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val service = context.getSystemService(LocaleManager::class.java) ?: return AppLanguageWrite.Failed
            service.applicationLocales = LocaleList.forLanguageTags(language.languageTag)
            AppLanguageWrite.Saved
        } else {
            legacyPreference().write(language)
        }
    }

    private fun legacyPreference(): LegacyAppLanguagePreference {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return LegacyAppLanguagePreference(
            readTag = { preferences.getString(LANGUAGE, null) },
            commitTag = { tag -> commitPreference(preferences, tag) },
        )
    }

    private fun commitPreference(
        preferences: SharedPreferences,
        tag: String?,
    ): Boolean {
        // KTX edit returns Unit; this adapter must inspect the synchronous commit result.
        val transaction = preferences.edit()
        transaction.putString(LANGUAGE, tag)
        val committed = transaction.commit()
        return committed
    }
}

private const val PREFERENCES = "nene-pixel-app-language-v1"
private const val LANGUAGE = "language"
