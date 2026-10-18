package com.tgwizard.aide

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.util.Date

private val Graphite = Color(0xFF111214)
private val Bronze = Color(0xFFB08D57)
private val Bone = Color(0xFFE6E1D6)
private val Muted = Color(0xFF8C8A85)

private val AideColors = darkColorScheme(
    primary = Bronze,
    onPrimary = Graphite,
    background = Graphite,
    onBackground = Bone,
    surface = Graphite,
    onSurface = Bone,
    onSurfaceVariant = Muted,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            MaterialTheme(colorScheme = AideColors) {
                Surface(modifier = Modifier.fillMaxSize()) { SetupScreen() }
            }
        }
    }
}

@Composable
private fun SetupScreen() {
    val context = LocalContext.current
    var accessibilityEnabled by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        accessibilityEnabled = AideAccessibilityService.instance != null
    }

    Column(
        modifier = Modifier.safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_rune),
                contentDescription = null,
                tint = Bronze,
                modifier = Modifier.size(28.dp),
            )
            Text(
                "AIDE",
                style = MaterialTheme.typography.titleLarge,
                letterSpacing = 0.25.em,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            if (accessibilityEnabled) "Accessibility service enabled. Your watch can lock this phone."
            else "Enable the Aide accessibility service so your watch can lock this phone.",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!accessibilityEnabled) {
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text("Open accessibility settings")
            }
        }
        OutlinedButton(
            onClick = { AideAccessibilityService.lockScreen() },
            enabled = accessibilityEnabled,
            border = BorderStroke(1.dp, Bronze),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Bronze),
        ) {
            Text("Lock now")
        }
        WatchSection()
    }
}

@Composable
private fun WatchSection() {
    val context = LocalContext.current
    var watchName by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Tick every second for the "ago" labels; check the connection every 5 s.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var tick = 0
            while (true) {
                now = System.currentTimeMillis()
                if (tick++ % 5 == 0) {
                    watchName = try {
                        Wearable.getNodeClient(context).connectedNodes.await().firstOrNull()?.displayName
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
                delay(1_000)
            }
        }
    }

    // Re-read on every tick (`now` changes each second); SharedPreferences reads are in-memory.
    val activity = remember(now) { WatchActivity.read(context) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
        Text("WATCH", color = Muted, fontSize = 12.sp, letterSpacing = 0.2.em)
        InfoRow("Connection", watchName?.let { "$it · connected" } ?: "Not connected")
        InfoRow(
            "Last request",
            activity.lastRequestAt?.let { at -> "${activity.lastRequestPath?.substringAfterLast('/')} · ${ago(now - at)}" } ?: "—",
        )
        InfoRow("Last lock", activity.lastLockAt?.let { DateFormat.getTimeFormat(context).format(Date(it)) } ?: "—")
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, color = Muted, modifier = Modifier.weight(1f))
        Text(value, color = Bone)
    }
}

private fun ago(millis: Long): String {
    val seconds = millis / 1_000
    return when {
        seconds < 60 -> "$seconds s ago"
        seconds < 3_600 -> "${seconds / 60} min ago"
        else -> "${seconds / 3_600} h ago"
    }
}
