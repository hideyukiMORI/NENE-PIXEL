package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.hideyukimori.nenepixel.presentation.compose.R

/**
 * The name a layer shows (#144): its own name, or "Layer {id}" (`layer_default_name`) while the name is empty.
 * The chip and the panel rows both label a layer through this one mapping.
 */
@Composable
internal fun layerLabel(row: LayerRowModel): String =
    row.name.value.ifEmpty { stringResource(R.string.layer_default_name, row.id.value) }
