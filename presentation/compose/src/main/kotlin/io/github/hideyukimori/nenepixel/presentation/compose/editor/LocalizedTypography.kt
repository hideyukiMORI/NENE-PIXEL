package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.intl.LocaleList

/** Explicit text locales select the appropriate OS CJK glyph forms on the legacy context path. */
@Composable
internal fun localizedTypography(): Typography {
    val tags = LocalConfiguration.current.locales.toLanguageTags()
    return remember(tags) {
        val locales = LocaleList(tags)
        val defaults = Typography()
        defaults.copy(
            displayLarge = defaults.displayLarge.copy(localeList = locales),
            displayMedium = defaults.displayMedium.copy(localeList = locales),
            displaySmall = defaults.displaySmall.copy(localeList = locales),
            headlineLarge = defaults.headlineLarge.copy(localeList = locales),
            headlineMedium = defaults.headlineMedium.copy(localeList = locales),
            headlineSmall = defaults.headlineSmall.copy(localeList = locales),
            titleLarge = defaults.titleLarge.copy(localeList = locales),
            titleMedium = defaults.titleMedium.copy(localeList = locales),
            titleSmall = defaults.titleSmall.copy(localeList = locales),
            bodyLarge = defaults.bodyLarge.copy(localeList = locales),
            bodyMedium = defaults.bodyMedium.copy(localeList = locales),
            bodySmall = defaults.bodySmall.copy(localeList = locales),
            labelLarge = defaults.labelLarge.copy(localeList = locales),
            labelMedium = defaults.labelMedium.copy(localeList = locales),
            labelSmall = defaults.labelSmall.copy(localeList = locales),
        )
    }
}
