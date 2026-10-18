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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

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
    val scope = rememberCoroutineScope()
    var onDemand by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onDemand = AccessibilityToggle.canToggle(context)
        accessibilityEnabled = AideAccessibilityService.instance != null
    }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).safeDrawingPadding().padding(24.dp),
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
            when {
                onDemand -> "Accessibility on demand: Aide switches its service on only while the watch locks the phone or shows its screen, so apps like BankID keep working."
                accessibilityEnabled -> "Accessibility service enabled. Your watch can lock this phone."
                else -> "Enable the Aide accessibility service so your watch can lock this phone."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!onDemand && !accessibilityEnabled) {
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text("Open accessibility settings")
            }
        }
        OutlinedButton(
            onClick = { scope.launch { AideAccessibilityService.lockScreen(context) } },
            enabled = onDemand || accessibilityEnabled,
            border = BorderStroke(1.dp, Bronze),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Bronze),
        ) {
            Text("Lock now")
        }
        WatchSection()
        VerisureSection()
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
        SectionHeader("Watch")
        InfoRow("Connection", watchName?.let { "$it · connected" } ?: "Not connected")
        InfoRow(
            "Last request",
            activity.lastRequestAt?.let { at -> "${activity.lastRequestPath?.substringAfterLast('/')} · ${ago(now - at)}" } ?: "—",
        )
        InfoRow("Last lock", activity.lastLockAt?.let { DateFormat.getTimeFormat(context).format(Date(it)) } ?: "—")
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
