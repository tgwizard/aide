package com.tgwizard.aide

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Locks the screen and takes screenshots (without a MediaProjection consent prompt).
 *
 * Normally switched on and off on demand by [AccessibilityToggle]; reach it through
 * [AccessibilityToggle.withService] rather than [instance] directly.
 */
class AideAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        AccessibilityToggle.onServiceConnected(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    /** Captures the screen as a small JPEG, or null if not possible (e.g. a FLAG_SECURE window). */
    suspend fun captureScreen(): ByteArray? = suspendCancellableCoroutine { continuation ->
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bytes = result.hardwareBuffer.use { buffer ->
                        Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.let(::encodeThumbnail)
                    }
                    continuation.resume(bytes)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Screenshot failed: $errorCode")
                    continuation.resume(null)
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
        private const val LOCK_IDLE_MS = 2_000L
        private const val SCREENSHOT_IDLE_MS = 15_000L

        @Volatile
        var instance: AideAccessibilityService? = null
            private set

        suspend fun lockScreen(context: Context): String =
            AccessibilityToggle.withService(context, LOCK_IDLE_MS) { service ->
                if (service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)) Protocol.RESULT_OK else Protocol.RESULT_FAILED
            } ?: Protocol.RESULT_NO_ACCESSIBILITY

        /** Kept on while the watch keeps asking, so a live thumbnail doesn't re-enable it every time. */
        suspend fun screenshot(context: Context): ByteArray? =
            AccessibilityToggle.withService(context, SCREENSHOT_IDLE_MS) { it.captureScreen() }
    }
}
