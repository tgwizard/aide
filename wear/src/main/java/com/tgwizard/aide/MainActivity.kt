package com.tgwizard.aide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Graphite = Color(Palette.GRAPHITE)
private val Surface = Color(Palette.SURFACE)
private val Bronze = Color(Palette.BRONZE)
private val Bone = Color(Palette.BONE)
private val Muted = Color(Palette.MUTED)
private val Ember = Color(Palette.EMBER)

private const val STATUS_INTERVAL_MS = 2_000L
private const val SCREEN_INTERVAL_MS = 3_000L
private const val DOOR_INTERVAL_MS = 15_000L
private const val HOLD_TO_UNLOCK_MS = 1_500

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppScaffold { HomeScreen() }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phoneBusy by remember { mutableStateOf(false) }
    var phoneResult by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<PhoneStatus?>(null) }
    var reachable by remember { mutableStateOf(true) }
    var screen by remember { mutableStateOf<ImageBitmap?>(null) }
    val door by DoorRemote.status.collectAsState()
    val doorPending by DoorRemote.pending.collectAsState()
    var doorHint by remember { mutableStateOf<String?>(null) }

    // Poll the phone only while the app is in the foreground.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch {
                while (true) {
                    val latest = PhoneClient.status(context)
                    reachable = latest != null
                    if (latest != null) status = latest
                    delay(STATUS_INTERVAL_MS)
                }
            }
            launch {
                while (true) {
                    // A screenshot of a dark screen is just black; only fetch while it's on.
                    if (status?.screenOn == true) {
                        PhoneClient.screen(context)?.let { screen = it.asImageBitmap() }
                    } else {
                        screen = null
                    }
                    delay(SCREEN_INTERVAL_MS)
                }
            }
            launch {
                while (true) {
                    if (DoorRemote.pending.value == null) DoorRemote.refresh(context)
                    delay(DOOR_INTERVAL_MS)
                }
            }
        }
    }
    LaunchedEffect(doorHint) {
        if (doorHint != null) {
            delay(2_000)
            doorHint = null
        }
    }

    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Graphite),
        state = listState,
        autoCentering = AutoCenteringParams(itemIndex = 0),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Control(title = "Phone", label = phoneResult.orEmpty(), detail = null) {
                    Ring(icon = R.drawable.ic_lock, color = Bronze, description = "Lock phone", onTap = {
                        if (!phoneBusy) {
                            phoneBusy = true
                            phoneResult = "Locking…"
                            scope.launch {
                                phoneResult = PhoneClient.lockPhone(context).message
                                phoneBusy = false
                            }
                        }
                    })
                }
                val d = door
                val unlock: (() -> Unit)? =
                    if (d?.isLocked == true && doorPending == null) fun() { DoorRemote.unlock(context) } else null
                Control(title = "Door", label = doorHint ?: doorPending ?: d?.label ?: "…", detail = d?.detail) {
                    val locked = d?.isLocked == true
                    Ring(
                        icon = if (d?.isLocked == false) R.drawable.ic_lock_open else R.drawable.ic_lock,
                        color = when {
                            d?.lock == null -> Muted
                            locked -> Bronze
                            else -> Ember
                        },
                        description = if (locked) "Hold to unlock door" else "Lock door",
                        onTap = {
                            when {
                                doorPending != null || d?.lock == null -> Unit
                                locked -> doorHint = "Hold to unlock"
                                else -> DoorRemote.lock(context)
                            }
                        },
                        onHold = unlock,
                    )
                }
            }
        }
        item {
            Box(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                Caps(if (reachable) "Phone" else "Phone not reachable", Muted)
            }
        }
        status?.let { s ->
            if (!s.locked && s.app != null) {
                item { StatusRow("App", s.appSince?.let { "${s.app} · ${ago(it)}" } ?: s.app) }
            }
            item {
                StatusRow(
                    "Screen",
                    listOf(if (s.locked) "Locked" else "Unlocked", if (s.screenOn) "on" else "off").joinToString(" · "),
                )
            }
            item { StatusRow("Battery", if (s.charging) "${s.battery}% · charging" else "${s.battery}%") }
            item {
                StatusRow(
                    "Sound",
                    listOfNotNull(s.ringer.replaceFirstChar(Char::uppercase), "DND".takeIf { s.dnd }).joinToString(" · "),
                )
            }
        }
        screen?.takeIf { status?.screenOn == true }?.let { bitmap ->
            item {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Phone screen",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Surface, RoundedCornerShape(14.dp)),
                )
            }
        }
    }
}

/** A titled ring with a state label and an optional detail line underneath. */
@Composable
private fun Control(title: String, label: String, detail: String?, ring: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
        Caps(title, Muted, fontSize = 10.sp)
        Box(Modifier.height(6.dp))
        ring()
        Box(Modifier.height(6.dp))
        Caps(label, Bone, fontSize = 10.sp)
        Text(detail.orEmpty(), color = Muted, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/**
 * A round button. With [onHold], pressing and holding for [HOLD_TO_UNLOCK_MS] fills a progress arc
 * and then fires it; releasing early cancels.
 */
@Composable
private fun Ring(
    @DrawableRes icon: Int,
    color: Color,
    description: String,
    onTap: () -> Unit,
    onHold: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val progress = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Surface)
            .border(1.5.dp, color, CircleShape)
            .drawWithContent {
                drawContent()
                if (progress.value > 0f) {
                    val stroke = 4.dp.toPx()
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke),
                    )
                }
            }
            .semantics {
                role = Role.Button
                onClick(label = description) { onTap(); true }
                if (onHold != null) onLongClick(label = description) { onHold(); true }
            }
            .pointerInput(onTap, onHold) {
                detectTapGestures(
                    onTap = { onTap() },
                    onPress = {
                        if (onHold != null) {
                            val hold = scope.launch {
                                progress.animateTo(1f, tween(HOLD_TO_UNLOCK_MS, easing = LinearEasing))
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onHold()
                            }
                            tryAwaitRelease()
                            hold.cancel()
                            scope.launch { progress.animateTo(0f, tween(150)) }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 2.dp)) {
        Caps(label, Muted, fontSize = 10.sp)
        Text(value, color = Bone, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun Caps(text: String, color: Color, fontSize: TextUnit = 12.sp) {
    Text(text.uppercase(), color = color, fontSize = fontSize, letterSpacing = 0.15.em, maxLines = 1)
}

private fun ago(since: Long): String {
    val minutes = (System.currentTimeMillis() - since) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h"
    }
}
