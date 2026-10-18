package com.tgwizard.aide

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService

class WatchListenerService : WearableListenerService() {

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        Log.i(TAG, "Request $path from $nodeId")
        val result = when (path) {
            Protocol.PATH_LOCK -> AideAccessibilityService.lockScreen()
            else -> return null
        }
        Log.i(TAG, "Result for $path: $result")
        return Tasks.forResult(result.toByteArray())
    }

    private companion object {
        const val TAG = "AideWatchListener"
    }
}
