package com.tgwizard.aide

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

/**
 * One-tap "lock phone" tile, with a STATUS edge button that opens the app.
 *
 * Tapping the ring fires a LoadAction, which makes the system re-request the tile with
 * [CLICK_LOCK] as the last clicked id; we lock the phone then, render the result, and ask for a
 * refresh a few seconds later to return to the idle layout.
 */
class LockTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val clicked = requestParams.currentState.lastClickableId
            Log.i(TAG, "Tile request, lastClickableId='$clicked', revertPending=$revertPending")
            val status = when {
                revertPending -> {
                    revertPending = false
                    null
                }
                clicked == CLICK_LOCK -> PhoneClient.lockPhone(this@LockTileService).message.also { scheduleRevert() }
                else -> null
            }
            val layout = TileUi.screen(
                top = TileUi.caps(status.orEmpty(), Palette.BONE),
                ring = TileUi.ring(requestParams.scope, CLICK_LOCK, TileUi.reload(), R.drawable.ic_lock, Palette.BRONZE, "Lock phone"),
                bottom = TileUi.pill(CLICK_STATUS, TileUi.openApp(packageName), "Status", "Open status"),
            )
            TileBuilders.Tile.Builder()
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

    private fun scheduleRevert() {
        Handler(Looper.getMainLooper()).postDelayed({
            revertPending = true
            getUpdater(applicationContext).requestUpdate(LockTileService::class.java)
        }, REVERT_DELAY_MS)
    }

    companion object {
        private const val TAG = "AideLockTile"
        private const val CLICK_LOCK = "lock"
        private const val CLICK_STATUS = "status"
        private const val REVERT_DELAY_MS = 4_000L

        /** Set before the post-lock refresh so that request renders idle instead of locking again. */
        @Volatile
        private var revertPending = false
    }
}
