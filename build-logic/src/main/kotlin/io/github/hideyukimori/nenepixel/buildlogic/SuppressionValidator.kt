package io.github.hideyukimori.nenepixel.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText

internal class SuppressionValidator(
    private val repositoryRoot: Path,
    private val validationDate: LocalDate,
    private val targetResolver: SuppressionTargetResolver = SuppressionTargetResolver(),
) {
    fun validate(): List<ArchitectureViolation> {
        val activeWaivers = loadActiveWaivers()
        return RepositoryFileTraversal
            .regularFiles(repositoryRoot)
            .asSequence()
            .filter(::isScannableFile)
            .flatMap { path -> validateFile(path, activeWaivers).asSequence() }
            .sorted()
            .toList()
    }

    private fun validateFile(
        path: Path,
        activeWaivers: Map<String, ActiveWaiver>,
    ): List<ArchitectureViolation> {
        val relativePath = relative(path)
        val lines = path.readLines()
        return lines.mapIndexedNotNull { index, line ->
            val extension = path.extension.lowercase()
            val suppression = suppressionAt(extension, line) ?: return@mapIndexedNotNull null
            validateSuppression(relativePath, extension, index, lines, suppression, activeWaivers)
        }
    }

    private fun validateSuppression(
        relativePath: String,
        extension: String,
        lineIndex: Int,
        lines: List<String>,
        suppression: Suppression,
        activeWaivers: Map<String, ActiveWaiver>,
    ): ArchitectureViolation? {
        val location = "$relativePath:${lineIndex + 1}"
        return if (suppression.fileLevel) {
            ArchitectureViolation(location, "KOT-022 prohibits file-level suppression.")
        } else {
            validateWaiverReference(location, relativePath, extension, lineIndex, lines, activeWaivers)
        }
    }

    private fun validateWaiverReference(
        location: String,
        relativePath: String,
        extension: String,
        lineIndex: Int,
        lines: List<String>,
        activeWaivers: Map<String, ActiveWaiver>,
    ): ArchitectureViolation? {
        val waiverId =
            targetResolver
                .waiverCommentLine(extension, lines, lineIndex)
                ?.let(waiverCommentRegex::find)
                ?.groupValues
                ?.get(1)
        val waiver = waiverId?.let(activeWaivers::get)
        return when {
            waiverId == null -> {
                ArchitectureViolation(
                    location,
                    "KOT-022 suppression requires an adjacent active waiver comment.",
                )
            }

            waiver == null -> {
                ArchitectureViolation(
                    location,
                    "KOT-022 references inactive or missing waiver '$waiverId'.",
                )
            }

            !waiver.covers(relativePath) -> {
                ArchitectureViolation(
                    location,
                    "KOT-022 waiver '$waiverId' does not cover '$relativePath'.",
                )
            }

            !waiver.covers(relativePath, targetResolver.target(extension, lines, lineIndex)) -> {
                ArchitectureViolation(
                    location,
                    "KOT-022 waiver '$waiverId' does not cover the suppressed declaration.",
                )
            }

            else -> {
                null
            }
        }
    }

    private fun suppressionAt(
        extension: String,
        line: String,
    ): Suppression? =
        when (extension) {
            "kt", "kts" -> {
                val annotation = kotlinSuppressionRegex.find(line)
                when {
                    annotation != null -> Suppression(fileLevel = annotation.groupValues[1].isNotEmpty())
                    noInspectionRegex.containsMatchIn(line) -> Suppression(fileLevel = false)
                    else -> null
                }
            }

            "xml" -> {
                if (xmlSuppressionRegex.containsMatchIn(line)) Suppression(fileLevel = false) else null
            }

            else -> {
                null
            }
        }

    private fun loadActiveWaivers(): Map<String, ActiveWaiver> {
        val waiverDirectory = repositoryRoot.resolve("docs/waivers")
        if (!Files.isDirectory(waiverDirectory)) return emptyMap()
        return Files.list(waiverDirectory).use { paths ->
            paths
                .filter { path -> path.name.matches(waiverFileRegex) }
                .map(::readActiveWaiver)
                .toList()
                .filterNotNull()
                .associateBy(ActiveWaiver::id)
        }
    }

    private fun readActiveWaiver(path: Path): ActiveWaiver? {
        val content = path.readText()
        val isActive = activeStatusRegex.containsMatchIn(content)
        val expiry =
            expiryRegex
                .find(content)
                ?.groupValues
                ?.get(1)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val scope = scopeRegex.find(content)?.groupValues
        val id = path.name.substringBefore('-') + "-" + path.name.substringAfter('-').substringBefore('-')
        return if (isActive && expiry?.isAfter(validationDate) == true && scope != null) {
            ActiveWaiver(id, scope[1], scope[2])
        } else {
            null
        }
    }

    private fun isScannableFile(path: Path): Boolean = path.extension.lowercase() in scannableExtensions

    private fun relative(path: Path): String = repositoryRoot.relativize(path).toString().replace('\\', '/')

    private data class Suppression(
        val fileLevel: Boolean,
    )

    private data class ActiveWaiver(
        val id: String,
        val sourcePath: String,
        val declaration: String,
    ) {
        fun covers(relativePath: String): Boolean = sourcePath == relativePath

        fun covers(
            relativePath: String,
            suppressedDeclaration: String?,
        ): Boolean = sourcePath == relativePath && declaration == suppressedDeclaration
    }

    private companion object {
        val scannableExtensions = setOf("kt", "kts", "xml")
        val kotlinSuppressionRegex = Regex("^\\s*@[\\w.]*?(file:)?(?:Suppress|SuppressLint)\\b")
        val noInspectionRegex = Regex("^\\s*//\\s*noinspection\\b")
        val xmlSuppressionRegex = Regex("\\btools:ignore\\s*=")
        val waiverCommentRegex = Regex("Waiver:\\s*(WVR-\\d{4})")
        val waiverFileRegex = Regex("WVR-\\d{4}-[^/]+\\.md")
        val activeStatusRegex = Regex("(?m)^- Status: active$")
        val expiryRegex = Regex("(?m)^- Expires: (\\d{4}-\\d{2}-\\d{2})$")
        val scopeRegex = Regex("(?m)^- Scope: `([^`#]+)#([^`#]+)`$")
    }
}
