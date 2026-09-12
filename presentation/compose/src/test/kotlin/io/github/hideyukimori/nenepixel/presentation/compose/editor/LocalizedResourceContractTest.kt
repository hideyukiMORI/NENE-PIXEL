package io.github.hideyukimori.nenepixel.presentation.compose.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

internal class LocalizedResourceContractTest {
    @Test
    fun shippedTranslationsHaveEveryKeyAndMatchingPositionalArguments() {
        val english = resources("values")
        assertFalse(english.isEmpty())
        assertEquals(
            arguments(english.getValue("palette_count/one")),
            arguments(english.getValue("palette_count/other")),
        )
        val cjkKeys = english.keys - "palette_count/one"
        listOf("values-ja", "values-b+zh+Hans").forEach { directory ->
            val translated = resources(directory)
            assertEquals(cjkKeys, translated.keys, directory)
            translated.forEach { (key, value) ->
                assertEquals(arguments(english.getValue(key)), arguments(value), "$directory/$key")
                assertFalse(translated.getValue(key).isBlank(), "$directory/$key")
            }
        }
    }

    private fun resources(directory: String): Map<String, String> {
        val file = Path.of("src/main/res", directory, "strings.xml").toFile()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = linkedMapOf<String, String>()
        val children = document.documentElement.childNodes
        repeat(children.length) { index ->
            val element = children.item(index) as? Element
            if (element != null) addResource(result, element)
        }
        return result
    }

    private fun addResource(
        result: MutableMap<String, String>,
        element: Element,
    ) {
        val name = element.getAttribute("name")
        if (element.tagName == "plurals") {
            val items = element.getElementsByTagName("item")
            repeat(items.length) { index ->
                val item = items.item(index) as Element
                result["$name/${item.getAttribute("quantity")}"] = item.textContent
            }
        } else {
            assertFalse(result.containsKey(name), "Duplicate key: $name")
            result[name] = element.textContent
        }
    }

    private fun arguments(value: String): List<String> =
        Regex("%[0-9]+\\$[ds]")
            .findAll(value)
            .map { it.value }
            .sorted()
            .toList()
}
