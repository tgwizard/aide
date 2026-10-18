package com.tgwizard.aide

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService
import com.tgwizard.aide.verisure.Door
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
            Protocol.PATH_LOCK -> {
                val result = AideAccessibilityService.lockScreen()
                Log.i(TAG, "Lock result: $result")
                Tasks.forResult(result.toByteArray())
            }
            Protocol.PATH_STATUS -> Tasks.forResult(StatusSnapshot.json(this).toString().toByteArray())
            Protocol.PATH_SCREEN -> captureScreen()
            Protocol.PATH_DOOR_STATUS -> async { Door.statusJson(this) }
            Protocol.PATH_DOOR_LOCK -> async { Door.lockJson(this) }
            Protocol.PATH_DOOR_UNLOCK -> async { Door.unlockJson(this) }
            else -> null
        }
    }

    private fun async(block: suspend () -> JSONObject): Task<ByteArray> {
        val source = TaskCompletionSource<ByteArray>()
        scope.launch { source.setResult(block().toString().toByteArray()) }
        return source.task
    }

    /** Replies with JPEG bytes, or an empty array when no screenshot could be taken. */
    private fun captureScreen(): Task<ByteArray> {
        val service = AideAccessibilityService.instance ?: return Tasks.forResult(ByteArray(0))
        val source = TaskCompletionSource<ByteArray>()
        service.captureScreen { source.setResult(it ?: ByteArray(0)) }
        return source.task
    }

    private companion object {
        const val TAG = "AideWatchListener"

        /**
         * Process-wide, not tied to this service: the system may destroy the service as soon as
         * onRequest returns, while a door lock takes up to ~15 s to confirm.
         */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
