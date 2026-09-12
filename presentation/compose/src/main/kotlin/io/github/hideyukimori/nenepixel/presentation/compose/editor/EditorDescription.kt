package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Resource names are stable test identities; their localized values remain human semantics. */
@Composable
internal fun Modifier.editorDescription(
    resource: Int,
    vararg arguments: Any,
    identity: String? = null,
): Modifier {
    val description = stringResource(resource, *arguments)
    val tag = identity ?: ("editor_" + LocalResources.current.getResourceEntryName(resource))
    return testTag(tag).semantics { contentDescription = description }
}
