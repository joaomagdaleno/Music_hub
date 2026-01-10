package com.joaomagdaleno.music_hub.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.joaomagdaleno.music_hub.playback.listener.EffectsListener.Companion.CUSTOM_EFFECTS
import com.joaomagdaleno.music_hub.playback.listener.EffectsListener.Companion.GLOBAL_FX
import com.joaomagdaleno.music_hub.utils.ContextUtils.SETTINGS_NAME
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.BufferedReader
import java.io.InputStreamReader

private fun Map<String, Any?>.toJsonElementMap(): Map<String, JsonElement> {
    return this.mapValues { (_, value) ->
        when (value) {
            is Boolean -> JsonPrimitive(value)
            is Float -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Set<*> -> JsonArray(value.filterIsInstance<String>().map { JsonPrimitive(it) })
            null -> JsonNull
            else -> throw IllegalArgumentException("Unsupported type for serialization: ${value::class.java}")
        }
    }
}

fun Context.exportSettings(uri: Uri) {
    val settingsPrefs = getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)
    val globalFxPrefs = getSharedPreferences(GLOBAL_FX, Context.MODE_PRIVATE)

    val settingsJson = settingsPrefs.all.toJsonElementMap()
    val globalFxJson = globalFxPrefs.all.toJsonElementMap()
    val customFxJson = globalFxPrefs.getStringSet(CUSTOM_EFFECTS, emptySet())?.map { fxName ->
        fxName to getSharedPreferences("fx_$fxName", Context.MODE_PRIVATE).all.toJsonElementMap()
    }

    val allPrefsJson = mutableMapOf(SETTINGS_NAME to settingsJson, GLOBAL_FX to globalFxJson)
    customFxJson?.forEach { (name, map) -> allPrefsJson["fx_$name"] = map }

    contentResolver.openOutputStream(uri, "w")?.use { out ->
        out.write(Json.encodeToString(allPrefsJson).toByteArray())
    }
}

fun Context.importSettings(uri: Uri) {
    val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).readText()
    } ?: return

    val allPrefsJson = Json.decodeFromString<Map<String, JsonObject>>(jsonString)

    allPrefsJson.forEach { (prefName, prefMap) ->
        getSharedPreferences(prefName, Context.MODE_PRIVATE).edit {
            prefMap.forEach { (key, value) ->
                when {
                    value is JsonPrimitive && value.booleanOrNull != null -> putBoolean(
                        key,
                        value.booleanOrNull!!
                    )

                    value is JsonPrimitive && value.intOrNull != null -> putInt(key, value.intOrNull!!)
                    value is JsonPrimitive && value.longOrNull != null -> putLong(key, value.longOrNull!!)
                    value is JsonPrimitive && value.floatOrNull != null -> putFloat(
                        key,
                        value.floatOrNull!!
                    )

                    value is JsonPrimitive && value.isString -> putString(key, value.content)
                    value is JsonArray -> putStringSet(
                        key,
                        value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
                    )

                    value is JsonNull -> remove(key)
                    else -> throw IllegalArgumentException("Unsupported type for deserialization: ${value::class.java}")
                }
            }
        }
    }
}