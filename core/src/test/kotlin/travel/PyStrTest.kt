package travel
import kotlin.test.Test
import kotlin.test.assertEquals
class PyStrTest {
    @Test fun pythonMappings() {
        assertEquals("Boston", SnapshotLoader().resolveCity("Boſton"))
        assertEquals("i\u0307stanbul", PyStr.lower("İstanbul"))
        assertEquals("strasse", PyStr.casefold("straße"))
        assertEquals(PyStr.casefold("STRASSE"), PyStr.casefold("straße"))
    }
}
