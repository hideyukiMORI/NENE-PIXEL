package io.github.hideyukimori.nenepixel.presentation.compose.editor

/** ADR 0021: app preference, independent of project and workspace state. */
public enum class AppLanguage(
    public val languageTag: String,
) {
    System(""),
    English("en"),
    Japanese("ja"),
    SimplifiedChinese("zh-Hans"),
}
