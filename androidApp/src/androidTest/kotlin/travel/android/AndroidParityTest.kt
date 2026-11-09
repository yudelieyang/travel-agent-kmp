package travel.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import travel.*

/** Device-side replay: the regular-expression engine is Android's actual ICU implementation. */
@RunWith(AndroidJUnit4::class)
class AndroidParityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().context
    private val json = Json { explicitNulls = true; encodeDefaults = false }
    private fun group(g: PyRegex.Group): JsonObject = buildJsonObject {
        put("value", g.value?.let(::JsonPrimitive) ?: JsonNull); put("span", JsonArray(g.span.map(::JsonPrimitive)))
    }
    private fun match(m: PyRegex.Match?): JsonElement = if (m == null) JsonNull else buildJsonObject {
        put("groups", JsonArray(m.groups.map(::group))); put("named", JsonObject(m.named.mapValues { group(it.value) }))
    }
    private fun write(name: String, total: Int, failures: List<String>) {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(folder, name).writeText("total=$total\npassed=${total - failures.size}\nfailed=${failures.size}\n" + failures.joinToString("\n"), Charsets.UTF_8)
    }
    @Test fun regexParity() {
        val manifest = context.assets.open("parity/regex/manifest.json").bufferedReader().use { json.parseToJsonElement(it.readText()).jsonObject }
        val corpus = manifest["corpus"]!!.jsonArray.map { it.jsonPrimitive.content }
        val patterns = manifest["patterns"]!!.jsonArray
        val cache = mutableMapOf<Int, Result<PyRegex>>()
        val failures = mutableListOf<String>(); var total = 0
        context.assets.open("parity/regex/results.jsonl").bufferedReader().useLines { lines ->
            for (line in lines) {
                val record = json.parseToJsonElement(line).jsonObject
                val pi = record["p"]!!.jsonPrimitive.int; val ci = record["c"]!!.jsonPrimitive.int
                val item = patterns[pi].jsonObject; val text = corpus[ci]; total++
                val test = runCatching {
                    val regex = cache.getOrPut(pi) { runCatching { PyRegex(item["pattern"]!!.jsonPrimitive.content, item["flags"]!!.jsonPrimitive.int) } }.getOrThrow()
                    val actual = buildJsonObject {
                        put("search", match(regex.search(text))); put("match", match(regex.match(text))); put("fullmatch", match(regex.fullmatch(text)))
                        put("finditer", JsonArray(regex.finditer(text).map(::match)))
                        put("split", JsonArray(regex.split(text).map { it?.let(::JsonPrimitive) ?: JsonNull }))
                        put("sub", regex.sub(text))
                        val templates = item["replacements"]!!.jsonArray.map { it.jsonPrimitive.content }
                        put("sub_templates", JsonObject(templates.associateWith { JsonPrimitive(regex.sub(text, it)) }))
                    }
                    val expected = JsonObject(record.filterKeys { it != "p" && it != "c" })
                    if (expected != actual) "pattern=$pi corpus=$ci origin=${item["origins"]} query=${JsonPrimitive(text)} expected=$expected actual=$actual" else null
                }
                val error = test.exceptionOrNull()
                if (error != null) failures.add("pattern=$pi corpus=$ci origin=${item["origins"]} error=${error.javaClass.name}: ${error.message}")
                else test.getOrNull()?.let(failures::add)
            }
        }
        write("regex-parity.txt", total, failures)
        assertTrue("regex: ${total - failures.size}/$total passed; see regex-parity.txt", failures.isEmpty())
    }
    @Test fun requirementsParity() {
        val parser = RequirementsParser(); val coverage = SemanticCoverageAnalyzer(); val failures = mutableListOf<String>(); var total = 0
        for (name in context.assets.list("golden/requirements")!!.filter { it.startsWith("SEM200-") && it.endsWith(".json") }.sorted()) {
            total++
            val result = runCatching {
                val record = context.assets.open("golden/requirements/$name").bufferedReader().use { json.parseToJsonElement(it.readText()).jsonObject }
                val query = record["query"]!!.jsonPrimitive.content; val parsed = parser.parse(query)
                val actual = json.encodeToJsonElement(TravelRequirements.serializer(), parsed)
                val r = GoldenComparator.difference(record["requirements"]!!, actual)
                val c = GoldenComparator.difference(record["coverage"]!!, json.encodeToJsonElement(CoverageResult.serializer(), coverage.analyze(query, parsed)))
                listOfNotNull(r, c).joinToString("; ").takeIf { it.isNotEmpty() }
            }
            if (result.isFailure) failures.add("$name: ${result.exceptionOrNull()?.javaClass?.name}: ${result.exceptionOrNull()?.message}")
            else result.getOrNull()?.let { failures.add("$name: $it") }
        }
        write("requirements-parity.txt", total, failures)
        assertTrue("requirements: ${total - failures.size}/$total passed; see requirements-parity.txt", total == 200 && failures.isEmpty())
    }
}
