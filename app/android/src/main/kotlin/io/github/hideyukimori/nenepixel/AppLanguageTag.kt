package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguage
import java.util.Locale

/** OS language settings may return regional variants of a supported language/script. */
internal fun readAppLanguageTag(tags: String): AppLanguageRead {
    if (tags.isEmpty()) return AppLanguageRead.Loaded(AppLanguage.System)
    val locale = Locale.forLanguageTag(tags.substringBefore(','))
    return when (locale.language) {
        "en" -> {
            AppLanguageRead.Loaded(AppLanguage.English)
        }

        "ja" -> {
            AppLanguageRead.Loaded(AppLanguage.Japanese)
        }

        "zh" -> {
            if (locale.script == "Hant" ||
                (locale.script.isEmpty() && locale.country in TRADITIONAL_CHINESE_REGIONS)
            ) {
                AppLanguageRead.Failed
            } else {
                AppLanguageRead.Loaded(AppLanguage.SimplifiedChinese)
            }
        }

        else -> {
            AppLanguageRead.Failed
        }
    }
}

private val TRADITIONAL_CHINESE_REGIONS = setOf("TW", "HK", "MO")
