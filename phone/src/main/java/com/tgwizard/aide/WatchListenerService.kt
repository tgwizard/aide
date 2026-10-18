package com.tgwizard.aide

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService

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
            else -> null
        }
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
    }
}
