package com.tgwizard.aide

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Door lock state as reported by phone/.../verisure/Door.kt. */
data class DoorStatus(
    val configured: Boolean,
    val name: String?,
    val lock: String?,
    val door: String?,
    val changedBy: String?,
    val changedAt: Long?,
    val method: String?,
    val error: String?,
) {
    val isLocked get() = lock == "LOCKED"

    /** Short state for the ring's label. */
    val label: String
        get() = when {
            error == "not_configured" || (!configured && error == null) -> "Set up on phone"
            error == "login_needed" -> "Login needed"
            door == "OPEN" -> "Door open"
            lock == "LOCKED" -> "Locked"
            lock == "UNLOCKED" -> "Unlocked"
            error == "unreachable" -> "No phone"
            error != null -> "Offline"
            else -> "Unknown"
        }

    /** "Adam · 14:32" (or "Thumbturn · 14:32" when operated by hand), or why the state may be stale. */
    val detail: String?
        get() = when {
            error == "rate_limited" -> "Rate limited"
            error != null && lock != null -> "Last known"
            // First name only; it has to fit under an 80dp ring.
            else -> listOfNotNull(changedBy?.substringBefore(' ') ?: method?.let(::readable), changedAt?.let { TIME.format(Date(it)) })
                .joinToString(" · ").ifEmpty { null }
        }

    companion object {
        private val TIME = SimpleDateFormat("HH:mm", Locale.getDefault())

        val UNREACHABLE = DoorStatus(
            configured = true, name = null, lock = null, door = null, changedBy = null, changedAt = null, method = null, error = "unreachable",
        )

        /** "THUMB_TURN" → "Thumb turn". */
        private fun readable(method: String) = method.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)

        fun parse(json: String): DoorStatus = JSONObject(json).run {
            DoorStatus(
                configured = optBoolean("configured"),
                name = optString("name").ifEmpty { null },
                lock = optString("lock").ifEmpty { null },
                door = optString("door").ifEmpty { null },
                changedBy = optString("by").ifEmpty { null },
                changedAt = optLong("at", 0).takeIf { it > 0 },
                method = optString("method").ifEmpty { null },
                error = optString("error").ifEmpty { null },
            )
        }
    }
}
