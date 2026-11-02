package com.odil9.orbitstrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json

class ConfigUtilsTest {
    @Test fun acceptsObjectAndCountsFlags() {
        assertEquals("Valid JSON object · 2 entries", ConfigUtils.validate("""{"a":1,"b":true}"""))
    }

    @Test fun rejectsArrayRoot() {
        assertThrows(IllegalArgumentException::class.java) { ConfigUtils.validate("[1,2]") }
    }

    @Test fun rejectsOversizedConfig() {
        assertThrows(IllegalArgumentException::class.java) { ConfigUtils.validate("{" + " ".repeat(1_000_001) + "}") }
    }

    @Test fun addsAndEditsPrimitiveFlags() {
        val first = ConfigUtils.upsertFlag("{}", "TargetFps", "120")
        val second = ConfigUtils.upsertFlag(first, "TargetFps", "true", "TargetFps")
        assertEquals("true", Json.parseToJsonElement(second).jsonObject.getValue("TargetFps").toString())
    }

    @Test fun rejectsObjectFlagValues() {
        assertThrows(IllegalArgumentException::class.java) {
            ConfigUtils.upsertFlag("{}", "Nested", "{\"x\":1}")
        }
    }

    @Test fun deletesFlag() {
        val result = ConfigUtils.deleteFlag("""{"a":1,"b":2}""", "a")
        assertEquals(setOf("b"), Json.parseToJsonElement(result).jsonObject.keys)
    }
}
