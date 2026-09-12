package io.github.hideyukimori.nenepixel

import android.content.res.Configuration
import android.os.LocaleList
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import io.github.hideyukimori.nenepixel.presentation.compose.editor.AppLanguageControls

@Composable
internal fun LocalizedAppLanguage(
    controls: AppLanguageControls,
    content: @Composable () -> Unit,
) {
    val settings by controls.settings.collectAsState()
    val host = LocalContext.current
    val hostConfiguration = LocalConfiguration.current
    val configuration =
        remember(hostConfiguration, settings.selection) {
            Configuration(hostConfiguration).apply {
                settings.selection.languageTag.takeIf { it.isNotEmpty() }?.let {
                    setLocales(
                        LocaleList.forLanguageTags(it),
                    )
                }
            }
        }
    val context = remember(host, configuration) { host.createConfigurationContext(configuration) }
    val direction =
        if (configuration.layoutDirection ==
            View.LAYOUT_DIRECTION_RTL
        ) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides configuration,
        LocalResources provides context.resources,
        LocalLayoutDirection provides direction,
        content = content,
    )
}
