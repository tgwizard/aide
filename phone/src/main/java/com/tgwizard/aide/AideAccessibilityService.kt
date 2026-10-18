package com.tgwizard.aide

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.io.ByteArrayOutputStream

/**
 * Must be enabled by the user under Settings > Accessibility (or via adb, see README).
 * While enabled, the system keeps it bound, so [instance] is available to the rest of the app.
 *
 * Besides locking, it tracks the foreground app (from window-state events) and can take
 * screenshots without a MediaProjection consent prompt.
 */
class AideAccessibilityService : AccessibilityService() {

    data class ForegroundApp(val packageName: String, val label: String, val since: Long)

    private val homePackage by lazy {
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == foreground?.packageName || !isUserFacingApp(pkg)) return
        foreground = ForegroundApp(pkg, labelFor(pkg), System.currentTimeMillis())
    }

    override fun onInterrupt() {}

    /** Skips system UI, keyboards and other overlays that also emit window-state events. */
    private fun isUserFacingApp(pkg: String): Boolean =
        pkg == homePackage || packageManager.getLaunchIntentForPackage(pkg) != null

    private fun labelFor(pkg: String): String {
        if (pkg == homePackage) return "Home"
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    /** Captures the screen as a small JPEG, or null if not possible (e.g. a FLAG_SECURE window). */
    fun captureScreen(onResult: (ByteArray?) -> Unit) {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bytes = result.hardwareBuffer.use { buffer ->
                        Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.let(::encodeThumbnail)
                    }
                    onResult(bytes)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Screenshot failed: $errorCode")
                    onResult(null)
                }
            },
        )
    }

    private fun encodeThumbnail(hardwareBitmap: Bitmap): ByteArray {
        // Hardware bitmaps can't be drawn in software, so copy before scaling.
        val full = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
        val height = full.height * THUMBNAIL_WIDTH / full.width
        val thumbnail = Bitmap.createScaledBitmap(full, THUMBNAIL_WIDTH, height, true)
        return ByteArrayOutputStream().use { out ->
            thumbnail.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
            out.toByteArray()
        }
    }

    companion object {
        private const val TAG = "AideAccessibility"
        private const val THUMBNAIL_WIDTH = 288
        private const val THUMBNAIL_QUALITY = 60

        @Volatile
        var instance: AideAccessibilityService? = null
            private set

        @Volatile
        var foreground: ForegroundApp? = null
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
