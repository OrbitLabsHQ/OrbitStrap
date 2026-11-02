package com.odil9.orbitstrap

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Small, pure helpers kept separate so the JSON rules can be unit-tested. */
object ConfigUtils {
    private val prettyJson = Json { prettyPrint = true }

    fun parseObject(source: String): JsonObject {
        require(source.length <= 1_000_000) { "Configuration exceeds 1 MB" }
        val parsed = Json.parseToJsonElement(source)
        return parsed as? JsonObject ?: throw IllegalArgumentException("The root value must be a JSON object")
    }

    fun validate(source: String): String {
        val objectValue = parseObject(source)
        require(objectValue.keys.none { it.isBlank() }) { "Flag names cannot be blank" }
        return "Valid JSON object · ${objectValue.size} entries"
    }

    fun format(source: String): String = prettyJson.encodeToString(parseObject(source))

    /** rawValue must be one JSON primitive, e.g. 120, true, null, or \"text\". */
    fun upsertFlag(source: String, key: String, rawValue: String, oldKey: String? = null): String {
        val cleanKey = key.trim()
        require(cleanKey.isNotEmpty()) { "Flag name cannot be blank" }
        require(cleanKey.length <= 200) { "Flag name is too long" }
        require(rawValue.length <= 10_000) { "Flag value is too long" }
        val value = Json.parseToJsonElement(rawValue)
        require(value is JsonPrimitive) { "Flag values must be JSON primitives (string, number, boolean, or null)" }
        val values = parseObject(source).toMutableMap()
        if (oldKey != null && oldKey != cleanKey) values.remove(oldKey)
        require(cleanKey !in values || cleanKey == oldKey) { "A flag with that name already exists" }
        values[cleanKey] = value
        return prettyJson.encodeToString(JsonObject(values))
    }

    fun deleteFlag(source: String, key: String): String {
        val values = parseObject(source).toMutableMap()
        values.remove(key)
        return prettyJson.encodeToString(JsonObject(values))
    }
}
