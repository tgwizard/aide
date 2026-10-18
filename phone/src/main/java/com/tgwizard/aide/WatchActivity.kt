package com.tgwizard.aide

import android.content.Context
import androidx.core.content.edit

/** Recent requests from the watch, shown in the phone app. Persisted so it survives process death. */
object WatchActivity {

    data class Snapshot(val lastRequestPath: String?, val lastRequestAt: Long?, val lastLockAt: Long?)

    fun record(context: Context, path: String) {
        val now = System.currentTimeMillis()
        prefs(context).edit {
            putString(KEY_LAST_PATH, path)
            putLong(KEY_LAST_AT, now)
            if (path == Protocol.PATH_LOCK) putLong(KEY_LAST_LOCK_AT, now)
        }
    }

    fun read(context: Context): Snapshot = prefs(context).run {
        Snapshot(
            lastRequestPath = getString(KEY_LAST_PATH, null),
            lastRequestAt = getLong(KEY_LAST_AT, 0).takeIf { it > 0 },
            lastLockAt = getLong(KEY_LAST_LOCK_AT, 0).takeIf { it > 0 },
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences("watch_activity", Context.MODE_PRIVATE)

    private const val KEY_LAST_PATH = "last_path"
    private const val KEY_LAST_AT = "last_at"
    private const val KEY_LAST_LOCK_AT = "last_lock_at"
}
