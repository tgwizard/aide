package com.tgwizard.aide

import org.json.JSONObject

/** Phone state as reported by phone/.../StatusSnapshot.kt. */
data class PhoneStatus(
    val locked: Boolean,
    val screenOn: Boolean,
    val battery: Int,
    val charging: Boolean,
    val ringer: String,
    val dnd: Boolean,
    val app: String?,
    val appSince: Long?,
) {
    companion object {
        fun parse(json: String): PhoneStatus = JSONObject(json).run {
            PhoneStatus(
                locked = optBoolean("locked"),
                screenOn = optBoolean("screenOn"),
                battery = optInt("battery", -1),
                charging = optBoolean("charging"),
                ringer = optString("ringer", "ring"),
                dnd = optBoolean("dnd"),
                app = optString("app").ifEmpty { null },
                appSince = if (has("appSince")) getLong("appSince") else null,
            )
        }
    }
}
