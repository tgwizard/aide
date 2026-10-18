package com.tgwizard.aide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Talks to the phone app over the Wearable Data Layer. */
object PhoneClient {

    enum class LockResult(val message: String) {
        LOCKED("Locked"),
        NO_ACCESSIBILITY("Phone setup needed"),
        UNREACHABLE("Not reachable"),
        FAILED("Failed"),
    }

    suspend fun lockPhone(context: Context): LockResult {
        val reply = request(context, Protocol.PATH_LOCK) ?: return LockResult.UNREACHABLE
        return when (String(reply)) {
            Protocol.RESULT_OK -> LockResult.LOCKED
            Protocol.RESULT_NO_ACCESSIBILITY -> LockResult.NO_ACCESSIBILITY
            else -> LockResult.FAILED
        }
    }

    /** Null when the phone can't be reached. */
    suspend fun status(context: Context): PhoneStatus? =
        request(context, Protocol.PATH_STATUS)?.let { PhoneStatus.parse(String(it)) }

    /** Null when the phone can't be reached or couldn't take a screenshot. */
    suspend fun screen(context: Context): Bitmap? {
        val jpeg = request(context, Protocol.PATH_SCREEN)?.takeIf { it.isNotEmpty() } ?: return null
        return withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) }
    }

    /** Door lock status, or the result of a lock/unlock (which can take ~15 s to confirm). */
    suspend fun door(context: Context, path: String): DoorStatus? {
        val timeout = if (path == Protocol.PATH_DOOR_STATUS) 15_000L else 40_000L
        return request(context, path, timeout)?.let { DoorStatus.parse(String(it)) }
    }

    /** Sends an RPC to the phone app; returns its reply, or null if it's unreachable. */
    private suspend fun request(context: Context, path: String, timeoutMs: Long = 10_000): ByteArray? = try {
        withTimeout(timeoutMs) {
            val nodes = Wearable.getCapabilityClient(context)
                .getCapability(Protocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
            val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            node?.let {
                Wearable.getMessageClient(context).sendRequest(it.id, path, ByteArray(0)).await()
            }
        }
    } catch (e: TimeoutCancellationException) {
        Log.w(TAG, "Request $path timed out")
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Request $path failed", e)
        null
    }

    private const val TAG = "AidePhoneClient"
}
