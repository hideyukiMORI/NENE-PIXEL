package io.github.hideyukimori.nenepixel.presentation.compose.editor

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The single place where tests choose the dispatcher they inject.
 *
 * Choose by meaning: [inline] runs in place, [parallel] runs on other threads in parallel, [io] runs file I/O.
 */
internal class TestCoroutineDispatchers(
    val inline: CoroutineDispatcher = Dispatchers.Unconfined,
    val parallel: CoroutineDispatcher = Dispatchers.Default,
    val io: CoroutineDispatcher = Dispatchers.IO,
)
