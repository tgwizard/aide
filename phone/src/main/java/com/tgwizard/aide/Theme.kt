package com.tgwizard.aide

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

internal val Graphite = Color(0xFF111214)
internal val Bronze = Color(0xFFB08D57)
internal val Bone = Color(0xFFE6E1D6)
internal val Muted = Color(0xFF8C8A85)
internal val Ember = Color(0xFFD9826A)

internal val AideColors = darkColorScheme(
    primary = Bronze,
    onPrimary = Graphite,
    background = Graphite,
    onBackground = Bone,
    surface = Graphite,
    onSurface = Bone,
    onSurfaceVariant = Muted,
    error = Ember,
)

@Composable
internal fun SectionHeader(text: String) {
    Text(text.uppercase(), color = Muted, fontSize = 12.sp, letterSpacing = 0.2.em)
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row {
        Text(label, color = Muted, modifier = Modifier.weight(1f))
        Text(value, color = Bone)
    }
}
