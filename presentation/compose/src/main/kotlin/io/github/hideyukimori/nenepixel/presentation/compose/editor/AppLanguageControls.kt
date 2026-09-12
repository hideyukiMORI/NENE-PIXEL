package io.github.hideyukimori.nenepixel.presentation.compose.editor

import kotlinx.coroutines.flow.StateFlow

/** The host owns durable language settings; presentation only emits selection/retry intent. */
public class AppLanguageControls(
    public val settings: StateFlow<AppLanguageSettings>,
    internal val select: (AppLanguage) -> Unit,
    internal val retry: () -> Unit,
)
