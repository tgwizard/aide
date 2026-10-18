package com.tgwizard.aide

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Must be enabled by the user under Settings > Accessibility (or via adb, see README).
 * While enabled, the system keeps it bound, so [instance] is available to the rest of the app.
 */
class AideAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        @Volatile
        var instance: AideAccessibilityService? = null
            private set

        fun lockScreen(): String {
            val service = instance ?: return Protocol.RESULT_NO_ACCESSIBILITY
            return if (service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)) {
                Protocol.RESULT_OK
            } else {
                Protocol.RESULT_FAILED
            }
        }
    }
}
