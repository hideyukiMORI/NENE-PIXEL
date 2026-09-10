package io.github.hideyukimori.nenepixel

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import io.github.hideyukimori.nenepixel.adapters.persistence.ProjectPickerIntents
import io.github.hideyukimori.nenepixel.adapters.persistence.ProjectPickerResult

internal class CreateProjectDocumentContract : ActivityResultContract<String, ProjectPickerResult>() {
    override fun createIntent(
        context: Context,
        input: String,
    ): Intent = ProjectPickerIntents.createDocument(input)

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): ProjectPickerResult = ProjectPickerIntents.parseResult(resultCode, intent)
}

internal class OpenProjectDocumentContract : ActivityResultContract<Unit, ProjectPickerResult>() {
    override fun createIntent(
        context: Context,
        input: Unit,
    ): Intent = ProjectPickerIntents.openDocument()

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): ProjectPickerResult = ProjectPickerIntents.parseResult(resultCode, intent)
}
