package travel

import java.io.File
import java.math.BigDecimal
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.*

@Serializable private data class MoneyBox(val value: Money)

class TravelEngineTest {
    private val json = Json { explicitNulls = true; encodeDefaults = false }
    private val root = File("src/test/resources/golden")
    private val engine by lazy { TravelEngine() }
    @TestFactory fun e2e(): List<DynamicTest> = File(root, "e2e").listFiles()!!.filter { it.extension == "json" }.sortedBy { it.name }.map { file ->
        DynamicTest.dynamicTest(file.name) {
            val expected = json.parseToJsonElement(file.readText(Charsets.UTF_8))
            val fixture = json.decodeFromJsonElement<PlanResult>(expected)
            val actual = engine.runWithRequirements(fixture.requirements, fixture.query)
            val actualJson = json.encodeToJsonElement(PlanResult.serializer(), actual.result)
            assertNull(GoldenComparator.difference(expected, actualJson), file.name)
            val stages = actual.stages.map { it.name }
            if (fixture.status == "needs_clarification") assertEquals(listOf("preflight", "clarification"), stages)
            else if (fixture.repair != null) assertEquals(listOf("preflight", "planner", "tools", "validation", "replan", "planner", "tools", "validation", "finalization"), stages)
            else assertEquals(listOf("preflight", "planner", "tools", "validation", "finalization"), stages)
            assertEquals((1..stages.size).toList(), actual.stages.map { it.sequence })
        }
    }
    private fun assertFloatEquals(expected: Double?, actual: Double?, message: String) {
        if (expected == null || actual == null) assertEquals(expected, actual, message)
        else assertTrue(pyFloatIeeeEqual(expected, actual), "$message: expected $expected, actual $actual")
    }
    @TestFactory fun extendedFloatValues(): List<DynamicTest> = File(root, "e2e-ext").listFiles()!!.filter { it.extension == "json" }.sortedBy { it.name }.map { file ->
        DynamicTest.dynamicTest(file.name) {
            val record = json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
            val req = json.decodeFromJsonElement<TravelRequirements>(record.getValue("requirements"))
            val run = engine.runWithRequirements(req, record.getValue("query").jsonPrimitive.content)
            val hotel = record.getValue("tools").jsonArray.firstOrNull { it.jsonObject["tool_name"]!!.jsonPrimitive.content == "search_hotels" }
            val requested = hotel?.jsonObject?.get("requested_arguments")?.jsonObject?.get("max_price")
            val expectedMax = if (requested == null || requested is JsonNull) null else requested.jsonPrimitive.content.toDouble()
            val actualHotel = run.tools.firstOrNull { it.toolName == "search_hotels" }
            assertFloatEquals(expectedMax, actualHotel?.requestedArguments?.maxPrice, "${file.name} requested max_price")
            val runtime = hotel?.jsonObject?.get("runtime_arguments")
            if (runtime != null && runtime !is JsonNull) {
                val max = runtime.jsonObject["max_price"]
                val expectedRuntime = if (max == null || max is JsonNull) null else max.jsonPrimitive.content.toDouble()
                assertFloatEquals(expectedRuntime, actualHotel?.runtimeArguments?.maxPrice, "${file.name} runtime max_price")
            }
            val repair = record.getValue("repair")
            val savings = if (repair is JsonNull) null else repair.jsonObject["savings"]
            val expectedSavings = if (savings == null || savings is JsonNull) null else savings.jsonPrimitive.content.toDouble()
            assertFloatEquals(expectedSavings, run.savings, "${file.name} savings")
        }
    }
    @TestFactory fun supplementalPythonCases(): List<DynamicTest> {
        val resource = requireNotNull(javaClass.classLoader.getResourceAsStream("p1b-supplemental.json"))
        val records = json.parseToJsonElement(resource.bufferedReader().use { it.readText() }).jsonArray
        return records.map { entry ->
            val record = entry.jsonObject
            DynamicTest.dynamicTest(record.getValue("id").jsonPrimitive.content) {
                val expected = record.getValue("fixture")
                val fixture = json.decodeFromJsonElement<PlanResult>(expected)
                val run = engine.runWithRequirements(fixture.requirements, fixture.query)
                assertNull(GoldenComparator.difference(expected, json.encodeToJsonElement(PlanResult.serializer(), run.result)))
            }
        }
    }
    @Test fun hotelCeilingAndSavings() {
        assertEquals("253.33333333333334".toDouble().toRawBits(), pyFloatHotelCeiling(BigDecimal("760"), 1, 3).toRawBits())
        assertEquals(2.67.toRawBits(), pyFloatSavings(BigDecimal("2.675"), BigDecimal.ZERO).toRawBits())
        assertEquals(0.0.toRawBits(), pyFloatSavings(BigDecimal("1.00"), BigDecimal("2.00")).toRawBits())
    }
    @Test fun signedZeroBudgetAndDivisionByZero() {
        val file = File(root, "e2e-ext/hotel-ceiling-760.json")
        val fixture = json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
        val req = json.decodeFromJsonElement<TravelRequirements>(fixture.getValue("requirements"))
        val zeroConstraint = req.requirements_v2.constraints.single().copy(value = BigDecimal("-0.0"))
        val result = engine.runWithRequirements(req.copy(requirements_v2 = req.requirements_v2.copy(constraints = listOf(zeroConstraint))))
        val actual = result.tools.first { it.toolName == "search_hotels" }.requestedArguments!!.maxPrice!!
        val pythonExpected = -0.0
        assertTrue(pyFloatIeeeEqual(pythonExpected, actual))
        assertNotEquals(pythonExpected.toRawBits(), actual.toRawBits())
        assertFalse(pyFloatIeeeEqual(Double.NaN, Double.NaN))
        assertFailsWith<ArithmeticException> { pyFloatHotelCeiling(BigDecimal.ONE, 0, 3) }
        assertFailsWith<ArithmeticException> { pyFloatHotelCeiling(BigDecimal.ONE, 1, 0) }
    }
    @Test fun declaredDecimalExceptionsRemainExact() {
        for (text in listOf("1.2345678901234568", "4e-324", "1e309")) {
            val box = json.decodeFromString<MoneyBox>("{\"value\":\"$text\"}")
            assertEquals(0, box.value.compareTo(BigDecimal(text)), text)
        }
    }
    @Test fun halfEvenAndTies() {
        assertEquals(BigDecimal("0.12"), quantizeMoney(BigDecimal("0.125")))
        assertEquals(BigDecimal("0.14"), quantizeMoney(BigDecimal("0.135")))
        assertEquals(BigDecimal("2.68"), quantizeMoney(BigDecimal("2.675")))
        assertFailsWith<ArithmeticException> { quantizeMoney(BigDecimal("1e27")) }
        val rows = engine.candidatesFor("Austin", "transport", null, emptyList())!!
        val first = rows.first(); val tie = first.copy(id = "aaa")
        assertEquals("aaa", engine.selectOption(listOf(first, tie), 1, true).id)
    }
}
