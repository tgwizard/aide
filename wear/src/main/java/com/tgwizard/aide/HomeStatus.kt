package com.tgwizard.aide

import org.json.JSONArray
import org.json.JSONObject

/** Verisure door/window sensors and climate readings, as reported by phone/.../verisure/Verisure.kt. */
data class HomeStatus(val openings: List<Opening>, val climates: List<Climate>, val error: String?) {

    data class Opening(val name: String, val type: String, val open: Boolean, val at: Long?)

    data class Climate(val name: String, val temperature: Double, val humidity: Double?)

    val openCount get() = openings.count { it.open }

    val summary: String
        get() = when {
            openings.isEmpty() -> "No sensors"
            openCount == 0 -> "All closed"
            else -> "$openCount open"
        }

    companion object {
        fun parse(json: String): HomeStatus = JSONObject(json).run {
            HomeStatus(
                openings = optJSONArray("openings").objects().map {
                    Opening(it.optString("name"), it.optString("type"), it.optBoolean("open"), it.optLong("at", 0).takeIf { at -> at > 0 })
                },
                climates = optJSONArray("climates").objects().map {
                    Climate(it.optString("name"), it.getDouble("temp"), if (it.has("humidity")) it.getDouble("humidity") else null)
                },
                error = optString("error").ifEmpty { null },
            )
        }

        private fun JSONArray?.objects(): List<JSONObject> = this?.let { (0 until it.length()).map(it::getJSONObject) }.orEmpty()
    }
}
