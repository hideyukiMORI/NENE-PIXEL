package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorTheme

/** Shared surfaces deliberately have no Material tonal overlay (ADR 0020). */
internal fun EditorTheme.colorScheme(): ColorScheme =
    when (this) {
        EditorTheme.Dark -> DARK_COLORS
        EditorTheme.Light -> LIGHT_COLORS
    }

internal fun EditorTheme.railColor(): Color =
    when (this) {
        EditorTheme.Dark -> DARK_RAIL
        EditorTheme.Light -> LIGHT_RAIL
    }

private val AUBERGINE = Color(0xFF5E2750)
private val DARK_RAIL = Color(0xFF1C101A)
private val LIGHT_RAIL = Color(0xFFFCFAFC)
private val DEEP_AUBERGINE = Color(0xFF2C001E)
private val DARK_TEXT = Color(0xFFEDE7EB)
private val LIGHT_TEXT = Color(0xFF241C22)
private val DARK_COLORS =
    darkColorScheme(
        primary = Color(0xFFA8699B),
        onPrimary = Color(0xFF11090F),
        primaryContainer = AUBERGINE,
        onPrimaryContainer = DARK_TEXT,
        secondary = Color(0xFFA8699B),
        onSecondary = Color(0xFF11090F),
        secondaryContainer = Color(0xFF3B1E35),
        onSecondaryContainer = DARK_TEXT,
        background = Color(0xFF140C13),
        onBackground = DARK_TEXT,
        surface = Color(0xFF11090F),
        onSurface = DARK_TEXT,
        surfaceVariant = Color(0xFF0E080D),
        tertiaryContainer = DEEP_AUBERGINE,
        onSurfaceVariant = Color(0xFFA79DA5),
        outline = Color(0xFF504150),
        outlineVariant = Color(0xFF3B2F3B),
        surfaceTint = Color.Transparent,
        surfaceContainer = Color(0xFF11090F),
        surfaceContainerHigh = Color(0xFF11090F),
        surfaceContainerHighest = Color(0xFF11090F),
        error = Color(0xFFE0705F),
    )
private val LIGHT_COLORS =
    lightColorScheme(
        primary = AUBERGINE,
        onPrimary = Color(0xFFFDFAFC),
        primaryContainer = AUBERGINE,
        onPrimaryContainer = Color(0xFFFDFAFC),
        secondary = AUBERGINE,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE6DAE4),
        onSecondaryContainer = LIGHT_TEXT,
        background = Color(0xFFF5F1F4),
        onBackground = LIGHT_TEXT,
        surface = Color.White,
        onSurface = LIGHT_TEXT,
        surfaceVariant = Color(0xFFEEE7EC),
        tertiaryContainer = DEEP_AUBERGINE,
        onSurfaceVariant = Color(0xFF6B5F68),
        outline = Color(0xFFB3A2B0),
        outlineVariant = Color(0xFFD8CCD6),
        surfaceTint = Color.Transparent,
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color.White,
        surfaceContainerHighest = Color.White,
    )

@Composable
internal fun editorButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
