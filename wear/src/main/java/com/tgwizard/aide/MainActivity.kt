package com.tgwizard.aide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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

private const val STATUS_INTERVAL_MS = 2_000L
private const val SCREEN_INTERVAL_MS = 3_000L

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
    var busy by remember { mutableStateOf(false) }
    var lockResult by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<PhoneStatus?>(null) }
    var reachable by remember { mutableStateOf(true) }
    var screen by remember { mutableStateOf<ImageBitmap?>(null) }

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
        }
    }

    // Item 1 (the ring) starts centred, like on the tile; status scrolls in from below.
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Graphite),
        state = listState,
        autoCentering = AutoCenteringParams(itemIndex = 1),
    ) {
        item {
            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                Caps(lockResult.orEmpty(), Bone)
            }
        }
        item {
            LockRing(enabled = !busy) {
                busy = true
                lockResult = "Locking…"
                scope.launch {
                    lockResult = PhoneClient.lockPhone(context).message
                    busy = false
                }
            }
        }
        item {
            Box(Modifier.padding(top = 20.dp, bottom = 4.dp)) {
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

@Composable
private fun LockRing(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(Surface)
            .border(1.5.dp, Bronze, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Lock phone", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = "Lock phone",
            tint = Bronze,
            modifier = Modifier.size(40.dp),
        )
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
