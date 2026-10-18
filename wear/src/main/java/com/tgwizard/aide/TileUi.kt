package com.tgwizard.aide

import androidx.annotation.DrawableRes
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.em
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.protolayout.ResourceBuilders

/** Shared building blocks so all Aide tiles have the same geometry and look. */
internal object TileUi {

    private const val RING_SIZE = 96f
    private const val ROW_HEIGHT = 32f

    /** Top row, ring, bottom row; the rows have equal height so the ring is exactly centred. */
    fun screen(top: LayoutElement, ring: LayoutElement, bottom: LayoutElement): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(row(top))
                    .addContent(spacer())
                    .addContent(ring)
                    .addContent(spacer())
                    .addContent(row(bottom))
                    .build(),
            )
            .build()

    fun ring(
        scope: ProtoLayoutScope,
        clickableId: String,
        action: ActionBuilders.Action,
        @DrawableRes icon: Int,
        color: Int,
        description: String,
    ): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(RING_SIZE))
            .setHeight(dp(RING_SIZE))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(ModifiersBuilders.Clickable.Builder().setId(clickableId).setOnClick(action).build())
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(Palette.SURFACE))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(RING_SIZE / 2)).build())
                            .build(),
                    )
                    .setBorder(ModifiersBuilders.Border.Builder().setWidth(dp(1.5f)).setColor(argb(color)).build())
                    .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(description).build())
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Image.Builder(scope)
                    .setImageResource(
                        ResourceBuilders.ImageResource.Builder()
                            .setAndroidResourceByResId(
                                ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(icon).build(),
                            )
                            .build(),
                    )
                    .setWidth(dp(40f))
                    .setHeight(dp(40f))
                    .setColorFilter(LayoutElementBuilders.ColorFilter.Builder().setTint(argb(color)).build())
                    .build(),
            )
            .build()

    /** A small pill button, e.g. the STATUS edge button. */
    fun pill(clickableId: String, action: ActionBuilders.Action, text: String, description: String): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(dp(96f))
            .setHeight(dp(32f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(ModifiersBuilders.Clickable.Builder().setId(clickableId).setOnClick(action).build())
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(Palette.SURFACE))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(16f)).build())
                            .build(),
                    )
                    .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(description).build())
                    .build(),
            )
            .addContent(caps(text, Palette.BONE))
            .build()

    fun caps(text: String, color: Int): LayoutElement =
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

    fun openApp(packageName: String): ActionBuilders.Action =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build(),
            )
            .build()

    fun reload(): ActionBuilders.Action = ActionBuilders.LoadAction.Builder().build()

    private fun row(content: LayoutElement): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setHeight(dp(ROW_HEIGHT))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(content)
            .build()

    private fun spacer(): LayoutElement = LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build()
}
