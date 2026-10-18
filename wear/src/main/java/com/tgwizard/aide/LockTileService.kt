package com.tgwizard.aide

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.em
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.protolayout.ResourceBuilders
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
            TileBuilders.Tile.Builder()
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(requestParams.scope, status)))
                .build()
        }

    private fun scheduleRevert() {
        Handler(Looper.getMainLooper()).postDelayed({
            revertPending = true
            getUpdater(applicationContext).requestUpdate(LockTileService::class.java)
        }, REVERT_DELAY_MS)
    }

    /**
     * The top and bottom rows have the same fixed height, so the ring sits exactly in the middle
     * of the screen.
     */
    private fun layout(scope: ProtoLayoutScope, status: String?): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(row(caps(status.orEmpty(), Palette.BONE)))
                    .addContent(spacer())
                    .addContent(lockRing(scope))
                    .addContent(spacer())
                    .addContent(row(statusButton()))
                    .build(),
            )
            .build()

    private fun lockRing(scope: ProtoLayoutScope): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(RING_SIZE))
            .setHeight(dp(RING_SIZE))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(CLICK_LOCK)
                            .setOnClick(ActionBuilders.LoadAction.Builder().build())
                            .build(),
                    )
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(Palette.SURFACE))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(RING_SIZE / 2)).build())
                            .build(),
                    )
                    .setBorder(ModifiersBuilders.Border.Builder().setWidth(dp(1.5f)).setColor(argb(Palette.BRONZE)).build())
                    .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription("Lock phone").build())
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Image.Builder(scope)
                    .setImageResource(
                        ResourceBuilders.ImageResource.Builder()
                            .setAndroidResourceByResId(
                                ResourceBuilders.AndroidImageResourceByResId.Builder()
                                    .setResourceId(R.drawable.ic_lock)
                                    .build(),
                            )
                            .build(),
                    )
                    .setWidth(dp(40f))
                    .setHeight(dp(40f))
                    .setColorFilter(LayoutElementBuilders.ColorFilter.Builder().setTint(argb(Palette.BRONZE)).build())
                    .build(),
            )
            .build()

    private fun statusButton(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(96f))
            .setHeight(dp(32f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(CLICK_STATUS)
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName(MainActivity::class.java.name)
                                            .build(),
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(Palette.SURFACE))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(16f)).build())
                            .build(),
                    )
                    .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription("Open status").build())
                    .build(),
            )
            .addContent(caps("Status", Palette.BONE))
            .build()

    private fun row(content: LayoutElementBuilders.LayoutElement): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setHeight(dp(ROW_HEIGHT))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(content)
            .build()

    private fun caps(text: String, color: Int): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(text.uppercase())
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(12f))
                    .setLetterSpacing(em(0.15f))
                    .setColor(argb(color))
                    .build(),
            )
            .build()

    private fun spacer(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build()

    companion object {
        private const val TAG = "AideLockTile"
        private const val CLICK_LOCK = "lock"
        private const val CLICK_STATUS = "status"
        private const val RING_SIZE = 96f
        private const val ROW_HEIGHT = 32f
        private const val REVERT_DELAY_MS = 4_000L

        /** Set before the post-lock refresh so that request renders idle instead of locking again. */
        @Volatile
        private var revertPending = false
    }
}
