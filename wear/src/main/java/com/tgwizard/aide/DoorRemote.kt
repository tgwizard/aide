package com.tgwizard.aide

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Watch-side door state. Lock/unlock run in a process-wide scope so they finish even if the UI
 * that started them goes away.
 */
object DoorRemote {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _status = MutableStateFlow<DoorStatus?>(null)
    val status: StateFlow<DoorStatus?> = _status

    /** "Locking…"/"Unlocking…" while a change is in flight. */
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    suspend fun refresh(context: Context) {
        val latest = PhoneClient.door(context, Protocol.PATH_DOOR_STATUS)
        _status.value = latest ?: _status.value?.copy(error = "unreachable") ?: DoorStatus.UNREACHABLE
    }

    fun lock(context: Context) = change(context, Protocol.PATH_DOOR_LOCK, "Locking…")

    fun unlock(context: Context) = change(context, Protocol.PATH_DOOR_UNLOCK, "Unlocking…")

    private fun change(context: Context, path: String, label: String) {
        if (_pending.value != null) return
        val app = context.applicationContext
        _pending.value = label
        scope.launch {
            val result = PhoneClient.door(app, path)
            _status.value = result ?: _status.value?.copy(error = "unreachable") ?: DoorStatus.UNREACHABLE
            _pending.value = null
        }
    }
}
