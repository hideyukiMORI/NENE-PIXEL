package io.github.hideyukimori.nenepixel.buildlogic

internal class SuppressionTargetResolver {
    fun waiverCommentLine(
        extension: String,
        lines: List<String>,
        lineIndex: Int,
    ): String? {
        val referenceIndex =
            if (extension == "xml") {
                findXmlElementStartIndex(lines, lineIndex)?.minus(1)
            } else {
                lineIndex - 1
            }
        return referenceIndex?.let(lines::getOrNull)
    }

    fun target(
        extension: String,
        lines: List<String>,
        lineIndex: Int,
    ): String? =
        when (extension) {
            "kt", "kts" -> kotlinTarget(lines, lineIndex)
            "xml" -> xmlTarget(lines, lineIndex)
            else -> null
        }

    private fun kotlinTarget(
        lines: List<String>,
        lineIndex: Int,
    ): String? =
        lines
            .asSequence()
            .drop(lineIndex)
            .take(DECLARATION_SEARCH_LINE_COUNT)
            .mapNotNull { line -> declarationRegex.find(line)?.groupValues?.get(1) }
            .firstOrNull()
            ?.removeSurrounding("`")

    private fun xmlTarget(
        lines: List<String>,
        lineIndex: Int,
    ): String? {
        val startIndex = findXmlElementStartIndex(lines, lineIndex) ?: return null
        val element =
            lines
                .drop(startIndex)
                .take(XML_ELEMENT_SEARCH_LINE_COUNT)
                .takeUntilInclusive { line -> '>' in line }
                .joinToString("\n")
        return xmlIdRegex.find(element)?.groupValues?.get(1)
            ?: xmlElementStartRegex
                .find(element)
                ?.groupValues
                ?.get(1)
                ?.substringAfterLast('.')
    }

    private fun findXmlElementStartIndex(
        lines: List<String>,
        lineIndex: Int,
    ): Int? =
        (lineIndex downTo maxOf(0, lineIndex - XML_ELEMENT_SEARCH_LINE_COUNT))
            .firstOrNull { index -> xmlElementStartRegex.containsMatchIn(lines[index]) }

    private fun <T> Iterable<T>.takeUntilInclusive(predicate: (T) -> Boolean): List<T> =
        buildList {
            for (element in this@takeUntilInclusive) {
                add(element)
                if (predicate(element)) break
            }
        }

    private companion object {
        val declarationRegex =
            Regex("\\b(?:class|object|interface|fun|val|var|typealias)\\s+(?:<[^>]+>\\s*)?(`[^`]+`|[A-Za-z_]\\w*)")
        val xmlElementStartRegex = Regex("^\\s*<([A-Za-z][\\w.:-]*)\\b")
        val xmlIdRegex = Regex("\\bandroid:id\\s*=\\s*\"@\\+?id/([^\"]+)\"")
        const val DECLARATION_SEARCH_LINE_COUNT = 8
        const val XML_ELEMENT_SEARCH_LINE_COUNT = 16
    }
}
