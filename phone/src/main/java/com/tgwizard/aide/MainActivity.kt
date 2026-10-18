package com.tgwizard.aide

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

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
    }
}
