package com.tgwizard.aide

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent

/**
 * Tracks the foreground app from usage events (needs the GET_USAGE_STATS app-op, granted via adb;
 * see README). Unlike an accessibility service, this doesn't trip BankID's checks.
 */
object ForegroundApps {

    data class App(val packageName: String, val label: String, val since: Long)

    private var current: App? = null
    private var queriedUntil = 0L

    @Synchronized
    fun current(context: Context): App? {
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        // Only read events since the last call; the first call looks back a day.
        val events = usage.queryEvents(if (queriedUntil == 0L) now - DAY_MS else queriedUntil, now)
        queriedUntil = now
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val pkg = event.packageName
            if (pkg == current?.packageName || !isUserFacing(context, pkg)) continue
            current = App(pkg, label(context, pkg), event.timeStamp)
        }
        return current
    }

    /** Skips system UI, keyboards and other overlays that also resume activities. */
    private fun isUserFacing(context: Context, pkg: String): Boolean =
        pkg == homePackage(context) || context.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun label(context: Context, pkg: String): String {
        if (pkg == homePackage(context)) return "Home"
        val pm = context.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    private fun homePackage(context: Context): String? =
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName

    private const val DAY_MS = 24 * 60 * 60_000L
}
