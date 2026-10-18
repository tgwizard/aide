package com.tgwizard.aide

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Talks to the phone app over the Wearable Data Layer. */
object PhoneClient {

    enum class LockResult(val message: String) {
        LOCKED("Phone locked"),
        NO_ACCESSIBILITY("Enable Aide in phone's accessibility settings"),
        UNREACHABLE("Phone not reachable"),
        FAILED("Lock failed"),
    }

    suspend fun lockPhone(context: Context): LockResult = try {
        withTimeout(10_000) {
            val nodes = Wearable.getCapabilityClient(context)
                .getCapability(Protocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
            val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            if (node == null) {
                LockResult.UNREACHABLE
            } else {
                val reply = Wearable.getMessageClient(context)
                    .sendRequest(node.id, Protocol.PATH_LOCK, ByteArray(0))
                    .await()
                when (String(reply)) {
                    Protocol.RESULT_OK -> LockResult.LOCKED
                    Protocol.RESULT_NO_ACCESSIBILITY -> LockResult.NO_ACCESSIBILITY
                    else -> LockResult.FAILED
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        LockResult.UNREACHABLE
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Lock request failed", e)
        LockResult.UNREACHABLE
    }

    private const val TAG = "AidePhoneClient"
}
