package com.tgwizard.aide

import android.app.KeyguardManager
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import org.json.JSONObject

/** The phone state shown on the watch, serialized as JSON (parsed by wear/.../PhoneStatus.kt). */
object StatusSnapshot {

    fun json(context: Context): JSONObject {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val battery = context.getSystemService(BatteryManager::class.java)
        val foreground = ForegroundApps.current(context)

        return JSONObject()
            .put("locked", keyguard.isKeyguardLocked)
            .put("screenOn", power.isInteractive)
            .put("battery", battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            .put("charging", battery.isCharging)
            .put("app", foreground?.label)
            .put("appSince", foreground?.since)
    }
}
