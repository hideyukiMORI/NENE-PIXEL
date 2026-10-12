package io.github.hideyukimori.nenepixel.presentation.compose.editor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** The fixture boundary choosing the in-place scheduling used by native tests. */
internal class TestCoroutineDispatchers(
    val inline: CoroutineDispatcher = Dispatchers.Unconfined,
)
