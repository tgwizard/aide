package com.tgwizard.aide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch

private val Graphite = Color(Palette.GRAPHITE)
private val Surface = Color(Palette.SURFACE)
private val Bronze = Color(Palette.BRONZE)
private val Bone = Color(Palette.BONE)

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
    var status by remember { mutableStateOf<String?>(null) }

    // Same geometry as the tile: equal-height rows above and below keep the ring centred.
    Box(
        modifier = Modifier.fillMaxSize().background(Graphite),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                Caps(status.orEmpty(), Bone)
            }
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .border(1.5.dp, Bronze, CircleShape)
                    .clickable(enabled = !busy, role = Role.Button, onClickLabel = "Lock phone") {
                        busy = true
                        status = "Locking…"
                        scope.launch {
                            status = PhoneClient.lockPhone(context).message
                            busy = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = "Lock phone",
                    tint = Bronze,
                    modifier = Modifier.size(40.dp),
                )
            }
            Box(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Caps(text: String, color: Color) {
    Text(text.uppercase(), color = color, fontSize = 12.sp, letterSpacing = 0.15.em, maxLines = 1)
}
