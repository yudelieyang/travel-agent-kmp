package travel

import java.io.File
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals

class ErrorsParityTest {
    @TestFactory fun errors(): List<DynamicTest> {
        val cases = Json.parseToJsonElement(File("src/test/resources/parity/errors.json").readText()).jsonArray
        return cases.map { item ->
            val row = item.jsonObject
            DynamicTest.dynamicTest(row.getValue("id").jsonPrimitive.content) {
                val run = TravelEngine().run(row.getValue("query").jsonPrimitive.content)
                val expected = row.getValue("errors").jsonArray.map { it.jsonPrimitive.content }
                assertEquals(expected, run.errors)
                assertEquals(expected, run.result.errors)
            }
        }
    }
}
