package com.tgwizard.aide

import android.content.Context
import android.util.Log
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Door lock tile. When unlocked (or the door is open), tapping the ring locks. When locked, it
 * opens the app instead, where unlocking needs a press-and-hold (tiles can't detect holds).
 * The state is refreshed from the phone whenever the tile comes into view and is stale.
 */
class DoorTileService : TileService() {

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        if (DoorRemote.isStale && DoorRemote.pending.value == null) requestUpdate(this)
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val clicked = requestParams.currentState.lastClickableId
            Log.i(TAG, "Tile request, lastClickableId='$clicked', ignoreClick=$ignoreClick")
            if (ignoreClick) {
                ignoreClick = false
            } else if (clicked == CLICK_LOCK) {
                DoorRemote.lock(this@DoorTileService)
            }
            if (DoorRemote.pending.value == null && DoorRemote.isStale) {
                withTimeoutOrNull(REFRESH_TIMEOUT_MS) { DoorRemote.refresh(this@DoorTileService) }
            }

            val status = DoorRemote.status.value
            val pending = DoorRemote.pending.value
            val canLock = status != null && status.lock != null && !status.isLocked
            val color = if (status?.isLocked == true) Palette.BRONZE else Palette.EMBER
            val layout = TileUi.screen(
                top = TileUi.caps(pending ?: status?.label ?: "…", Palette.BONE),
                ring = TileUi.ring(
                    scope = requestParams.scope,
                    clickableId = if (canLock) CLICK_LOCK else CLICK_OPEN,
                    action = if (canLock && pending == null) TileUi.reload() else TileUi.openApp(packageName),
                    icon = if (status?.isLocked == false) R.drawable.ic_lock_open else R.drawable.ic_lock,
                    color = if (status?.lock == null) Palette.MUTED else color,
                    description = if (canLock) "Lock door" else "Open Aide",
                ),
                bottom = TileUi.caps(status?.detail.orEmpty(), Palette.MUTED),
            )
            TileBuilders.Tile.Builder()
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        }

    companion object {
        private const val TAG = "AideDoorTile"
        private const val CLICK_LOCK = "lock"
        private const val CLICK_OPEN = "open"
        private const val REFRESH_TIMEOUT_MS = 8_000L

        /** Our own update requests must not be mistaken for a repeated tap on the ring. */
        @Volatile
        private var ignoreClick = false

        fun requestUpdate(context: Context) {
            ignoreClick = true
            getUpdater(context).requestUpdate(DoorTileService::class.java)
        }
    }
}
