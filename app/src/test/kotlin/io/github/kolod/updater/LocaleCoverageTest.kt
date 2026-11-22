package io.github.kolod.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Constitution Principle VII: every string and plural exists in all six locales, with the plural
 * categories each language needs (Ukrainian has four, Japanese only one).
 */
class LocaleCoverageTest {
    private val resDir = File(System.getProperty("updater.resDir") ?: error("updater.resDir not set"))

    private val locales = mapOf(
        "en" to "values",
        "de" to "values-de",
        "fr" to "values-fr",
        "es" to "values-es",
        "ja" to "values-ja",
        "uk" to "values-uk",
    )

    private val requiredQuantities = mapOf(
        "en" to setOf("one", "other"),
        "de" to setOf("one", "other"),
        "fr" to setOf("one", "many", "other"),
        "es" to setOf("one", "many", "other"),
        "ja" to setOf("other"),
        "uk" to setOf("one", "few", "many", "other"),
    )

    private class Strings(val strings: Set<String>, val plurals: Map<String, Set<String>>)

    private fun load(dir: String): Strings {
        val file = File(resDir, "$dir/strings.xml")
        assertTrue("missing $file", file.isFile)
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val strings = mutableSetOf<String>()
        val plurals = mutableMapOf<String, Set<String>>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            if (node.getAttribute("translatable") == "false") continue
            when (node.tagName) {
                "string" -> strings += node.getAttribute("name")
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    plurals[node.getAttribute("name")] =
                        (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet()
                }
            }
        }
        return Strings(strings, plurals)
    }

    @Test
    fun everyLocaleHasExactlyTheEnglishKeys() {
        val english = load("values")
        for ((locale, dir) in locales) {
            val other = load(dir)
            assertEquals("string keys of $locale", english.strings, other.strings)
            assertEquals("plural keys of $locale", english.plurals.keys, other.plurals.keys)
        }
    }

    @Test
    fun pluralsUseTheCategoriesOfEachLanguage() {
        for ((locale, dir) in locales) {
            val required = requiredQuantities.getValue(locale)
            for ((name, quantities) in load(dir).plurals) {
                assertTrue("$locale/$name needs $required but has $quantities", quantities.containsAll(required))
                if (locale == "ja") assertEquals("ja/$name", setOf("other"), quantities)
            }
        }
    }
}
