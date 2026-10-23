package eu.kanade.tachiyomi.data.track.metadata

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Lenient readers for the JSON of trackers: anything missing or of another type is just null, empty or false. */
internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.array(): JsonArray = (this as? JsonArray) ?: JsonArray(emptyList())

internal fun JsonElement?.text(): String? =
    (this as? JsonPrimitive)
        ?.takeUnless {
            it is JsonNull
        }?.contentOrNull
        ?.takeIf { it.isNotBlank() }

internal fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull

internal fun JsonElement?.double(): Double? = (this as? JsonPrimitive)?.doubleOrNull

internal fun JsonElement?.bool(): Boolean = (this as? JsonPrimitive)?.booleanOrNull == true

internal fun JsonObject?.text(key: String): String? = this?.get(key).text()

internal fun JsonObject?.obj(key: String): JsonObject? = this?.get(key).obj()

internal fun JsonObject?.array(key: String): JsonArray = this?.get(key).array()
