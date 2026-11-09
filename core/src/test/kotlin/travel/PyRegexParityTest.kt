package travel

import java.io.File
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class PyRegexParityTest {
    private val json = Json
    private val root = File("src/test/resources/parity/regex")
    private fun group(g: PyRegex.Group): JsonObject = buildJsonObject {
        put("value", g.value?.let(::JsonPrimitive) ?: JsonNull)
        put("span", JsonArray(g.span.map(::JsonPrimitive)))
    }
    private fun match(m: PyRegex.Match?): JsonElement = if (m == null) JsonNull else buildJsonObject {
        put("groups", JsonArray(m.groups.map(::group)))
        put("named", JsonObject(m.named.mapValues { group(it.value) }))
    }
    @Test fun unmatchedNamedReplacementIsEmpty() {
        assertEquals("[]", PyRegex("(?P<x>a)?b").sub("b", "[\\g<x>]"))
        assertEquals("[]", PyRegex("(a)?b").sub("b", "[\\g<1>]"))
    }
    @TestFactory fun patterns(): List<DynamicTest> {
        val manifest = json.parseToJsonElement(File(root, "manifest.json").readText(Charsets.UTF_8)).jsonObject
        val corpus = manifest["corpus"]!!.jsonArray.map { it.jsonPrimitive.content }
        val patterns = manifest["patterns"]!!.jsonArray
        val records = File(root, "results.jsonl").useLines { lines -> lines.map { json.parseToJsonElement(it).jsonObject }.groupBy { it["p"]!!.jsonPrimitive.int } }
        return patterns.mapIndexed { index, entry ->
            DynamicTest.dynamicTest("pattern $index ${entry.jsonObject["origins"]}") {
                val item = entry.jsonObject
                val regex = PyRegex(item["pattern"]!!.jsonPrimitive.content, item["flags"]!!.jsonPrimitive.int)
                for (record in records.getValue(index)) {
                    val ci = record["c"]!!.jsonPrimitive.int; val text = corpus[ci]
                    val actual = buildJsonObject {
                        put("search", match(regex.search(text))); put("match", match(regex.match(text))); put("fullmatch", match(regex.fullmatch(text)))
                        put("finditer", JsonArray(regex.finditer(text).map(::match)))
                        put("split", JsonArray(regex.split(text).map { it?.let(::JsonPrimitive) ?: JsonNull }))
                        put("sub", regex.sub(text))
                        val templates = item["replacements"]!!.jsonArray.map { it.jsonPrimitive.content }
                        put("sub_templates", JsonObject(templates.associateWith { JsonPrimitive(regex.sub(text, it)) }))
                    }
                    val expected = JsonObject(record.filterKeys { it != "p" && it != "c" })
                    assertEquals(expected, actual, "pattern=$index corpus=$ci text=$text")
                }
            }
        }
    }
}
