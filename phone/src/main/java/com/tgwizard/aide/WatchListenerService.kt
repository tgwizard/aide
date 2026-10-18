package com.tgwizard.aide

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService
import com.tgwizard.aide.verisure.Verisure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class WatchListenerService : WearableListenerService() {

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        Log.d(TAG, "Request $path from $nodeId")
        WatchActivity.record(this, path)
        return when (path) {
            Protocol.PATH_LOCK -> async {
                AideAccessibilityService.lockScreen(this).also { Log.i(TAG, "Lock result: $it") }.toByteArray()
            }
            Protocol.PATH_STATUS -> Tasks.forResult(StatusSnapshot.json(this).toString().toByteArray())
            // Empty reply when no screenshot could be taken.
            Protocol.PATH_SCREEN -> async { AideAccessibilityService.screenshot(this) ?: ByteArray(0) }
            Protocol.PATH_DOOR_STATUS -> async { Verisure.statusJson(this).toBytes() }
            Protocol.PATH_DOOR_LOCK -> async { Verisure.lockJson(this).toBytes() }
            Protocol.PATH_DOOR_UNLOCK -> async { Verisure.unlockJson(this).toBytes() }
            Protocol.PATH_HOME -> async { Verisure.homeJson(this).toBytes() }
            else -> null
        }
    }

    private fun async(block: suspend () -> ByteArray): Task<ByteArray> {
        val source = TaskCompletionSource<ByteArray>()
        scope.launch { source.setResult(block()) }
        return source.task
    }

    private fun JSONObject.toBytes() = toString().toByteArray()

    private companion object {
        const val TAG = "AideWatchListener"

        /**
         * Process-wide, not tied to this service: the system may destroy the service as soon as
         * onRequest returns, while a door lock takes up to ~15 s to confirm.
         */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
