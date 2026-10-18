package com.tgwizard.aide

import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
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
 * A one-tap "lock phone" tile. Tapping the button fires a LoadAction, which makes the system
 * re-request the tile with [CLICK_LOCK] as the last clicked id; we lock the phone then and
 * render the result.
 */
class LockTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val status = if (requestParams.currentState.lastClickableId == CLICK_LOCK) {
                PhoneClient.lockPhone(this@LockTileService).message
            } else {
                null
            }
            TileBuilders.Tile.Builder()
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(requestParams.scope, status)))
                .build()
        }

    private fun layout(scope: ProtoLayoutScope, status: String?): LayoutElementBuilders.LayoutElement {
        val button = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(BUTTON_SIZE))
            .setHeight(dp(BUTTON_SIZE))
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
                            .setColor(argb(BUTTON_COLOR))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(BUTTON_SIZE / 2)).build())
                            .build(),
                    )
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
                    .setColorFilter(LayoutElementBuilders.ColorFilter.Builder().setTint(argb(TEXT_COLOR)).build())
                    .build(),
            )
            .build()

        val label = LayoutElementBuilders.Text.Builder()
            .setText(status ?: "Lock phone")
            .setMaxLines(2)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(14f))
                    .setColor(argb(TEXT_COLOR))
                    .build(),
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(button)
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(10f)).build())
                    .addContent(label)
                    .build(),
            )
            .build()
    }

    private companion object {
        const val CLICK_LOCK = "lock"
        const val BUTTON_SIZE = 96f
        const val BUTTON_COLOR = 0xFF3949AB.toInt()
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    }
}
