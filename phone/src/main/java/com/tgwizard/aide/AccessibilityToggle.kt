package com.tgwizard.aide

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps Aide's accessibility service switched off except while it's needed.
 *
 * Apps like BankID refuse to run while an unknown accessibility service is enabled, so instead of
 * leaving it on we enable it on demand and disable it after [idle] ms without use. That requires
 * WRITE_SECURE_SETTINGS, granted once via adb (see README); without it the user manages the
 * service in Settings as before and we never touch it.
 */
object AccessibilityToggle {

    private const val TAG = "AideA11yToggle"
    private const val BIND_TIMEOUT_MS = 3_000L
    private const val LEFTOVER_IDLE_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var disableJob: Job? = null

    @Volatile
    private var disableAt = 0L

    /** True between switching the service on in [withService] and it binding. */
    @Volatile
    private var enabling = false

    fun canToggle(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Runs [block] with the service, enabling it first if needed (when we can), and keeps it on for
     * [idle] ms afterwards. Returns null if the service isn't available.
     */
    suspend fun <T> withService(context: Context, idle: Long, block: suspend (AideAccessibilityService) -> T): T? {
        val service = AideAccessibilityService.instance ?: run {
            if (!canToggle(context)) return null
            enabling = true
            try {
                setEnabled(context, true)
                withTimeoutOrNull(BIND_TIMEOUT_MS) {
                    while (AideAccessibilityService.instance == null) delay(25)
                    AideAccessibilityService.instance
                }
            } finally {
                enabling = false
            }
        } ?: return null
        return try {
            block(service)
        } finally {
            scheduleDisable(context, idle)
        }
    }

    /**
     * Called when the service binds. If we didn't just switch it on ourselves, it was left enabled
     * (e.g. the process died before switching it off, or it was enabled in Settings), so switch it
     * off soon.
     */
    fun onServiceConnected(context: Context) {
        if (!enabling) scheduleDisable(context, LEFTOVER_IDLE_MS)
    }

    /** Switches the service off after [delayMs], unless it's used again before then. */
    fun scheduleDisable(context: Context, delayMs: Long) {
        if (!canToggle(context)) return
        val app = context.applicationContext
        synchronized(this) {
            disableAt = maxOf(disableAt, System.currentTimeMillis() + delayMs)
            if (disableJob?.isActive == true) return
            disableJob = scope.launch {
                while (true) {
                    val wait = disableAt - System.currentTimeMillis()
                    if (wait <= 0) break
                    delay(wait)
                }
                setEnabled(app, false)
            }
        }
    }

    private fun setEnabled(context: Context, enabled: Boolean) {
        val resolver = context.contentResolver
        val ours = ComponentName(context, AideAccessibilityService::class.java)
        val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        // Keep every other service (e.g. 1Password) exactly as it is; entries may use the short ".Class" form.
        val others = current.split(':').filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != ours }
        val updated = if (enabled) others + ours.flattenToString() else others
        if (updated.joinToString(":") == current) return
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated.joinToString(":"))
        if (enabled) Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Log.i(TAG, "Accessibility service ${if (enabled) "enabled" else "disabled"}")
    }
}
