package com.tgwizard.aide

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import org.json.JSONObject

/** The phone state shown on the watch, serialized as JSON (parsed by wear/.../PhoneStatus.kt). */
object StatusSnapshot {

    fun json(context: Context): JSONObject {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val battery = context.getSystemService(BatteryManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val foreground = AideAccessibilityService.foreground

        return JSONObject()
            .put("locked", keyguard.isKeyguardLocked)
            .put("screenOn", power.isInteractive)
            .put("battery", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            .put("charging", battery.isCharging)
            .put(
                "ringer",
                when (audio.ringerMode) {
                    AudioManager.RINGER_MODE_SILENT -> "silent"
                    AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                    else -> "ring"
                },
            )
            .put(
                "dnd",
                notifications.currentInterruptionFilter.let {
                    it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                },
            )
            .put("app", foreground?.label)
            .put("appSince", foreground?.since)
    }
}
