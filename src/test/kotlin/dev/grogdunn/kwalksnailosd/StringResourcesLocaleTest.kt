package dev.grogdunn.kwalksnailosd

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringResourcesLocaleTest {
    @Test
    fun localesDefineTheSameKeysAndFormatArgsAsEnglish() {
        val root = Path.of("src", "main", "composeResources")
        val defaultCatalog = readCatalog(root.resolve("values"))
        assertTrue(defaultCatalog.strings.isNotEmpty(), "default strings.xml is empty")
        assertTrue(defaultCatalog.plurals.isNotEmpty(), "default plurals are empty")

        for (locale in LOCALES) {
            val catalog = readCatalog(root.resolve("values-$locale"))
            assertEquals(defaultCatalog.strings.keys, catalog.strings.keys, "$locale string keys")
            assertEquals(defaultCatalog.plurals.keys, catalog.plurals.keys, "$locale plural keys")
            for ((name, template) in defaultCatalog.strings) {
                val translated = catalog.strings.getValue(name)
                assertEquals(placeholders(template), placeholders(translated), "$locale string $name")
                assertTrue(translated.isNotBlank(), "$locale string $name is blank")
            }
            for ((name, items) in defaultCatalog.plurals) {
                val translated = catalog.plurals.getValue(name)
                assertEquals(items.keys, translated.keys, "$locale plural $name quantities")
                for ((quantity, template) in items) {
                    assertEquals(
                        placeholders(template),
                        placeholders(translated.getValue(quantity)),
                        "$locale plural $name $quantity",
                    )
                }
            }
            assertEquals(1, catalog.strings.getValue("menu_file_mnemonic").length, "$locale file mnemonic")
        }
    }

    private fun readCatalog(directory: Path): Catalog {
        val files = Files.list(directory).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".xml") }.sorted().toList()
        }
        assertTrue(files.isNotEmpty(), "no string files in $directory")
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Map<String, String>>()
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val builder = factory.newDocumentBuilder()
        for (file in files) {
            val document = builder.parse(file.toFile())
            val nodes = document.documentElement.childNodes
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                when (node.nodeName) {
                    "string" -> {
                        val name = node.attributes.getNamedItem("name").nodeValue
                        val previous = strings.put(name, node.textContent)
                        assertTrue(previous == null, "duplicate string $name in $file")
                        assertNoBarePlaceholders(name, node.textContent)
                    }
                    "plurals" -> {
                        val name = node.attributes.getNamedItem("name").nodeValue
                        val items = linkedMapOf<String, String>()
                        val children = node.childNodes
                        for (childIndex in 0 until children.length) {
                            val child = children.item(childIndex)
                            if (child.nodeName != "item") continue
                            val quantity = child.attributes.getNamedItem("quantity").nodeValue
                            val previous = items.put(quantity, child.textContent)
                            assertTrue(previous == null, "duplicate quantity $quantity in plural $name")
                            assertNoBarePlaceholders("$name/$quantity", child.textContent)
                        }
                        val previous = plurals.put(name, items)
                        assertTrue(previous == null, "duplicate plural $name in $file")
                    }
                }
            }
        }
        return Catalog(strings, plurals)
    }

    private fun assertNoBarePlaceholders(name: String, text: String) {
        val invalid = INVALID_PLACEHOLDER.find(text)
        assertTrue(invalid == null, "$name uses ${invalid?.value}, which Compose Resources does not substitute")
    }

    private fun placeholders(text: String): List<String> =
        PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

    private data class Catalog(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
    )

    private companion object {
        val LOCALES = listOf("it", "es", "de", "fr")
        val PLACEHOLDER: Regex = Regex("""%\d+\$[ds]""")
        val INVALID_PLACEHOLDER: Regex = Regex("""%(?!\d+\$[ds])(?:\d+\$)?[a-zA-Z]""")
    }
}
