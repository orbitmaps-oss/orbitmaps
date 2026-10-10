// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colours from the approved previews (plan step 12.2): emerald actions, coloured status tags. */
object OrbitColors {
    val Emerald = Color(0xFF059669)
    val EmeraldLight = Color(0xFF34D399)
    val DarkGreen = Color(0xFF065F46)
    val Amber = Color(0xFFF59E0B)
    val Sky = Color(0xFF0EA5E9)
    val Confirmed = Color(0xFF16A34A)
    val SpeedRed = Color(0xFFDC2626)
    val RouteGreen = Color(0xFF22C55E)
    val MemberBlue = Color(0xFF2563EB)
    val PlaceholderLand = Color(0xFFF5F1E8)
    val PlaceholderLandNight = Color(0xFF1E293B)
}

private val LightColors = lightColorScheme(
    primary = OrbitColors.Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = OrbitColors.DarkGreen,
    secondary = OrbitColors.Sky,
    tertiary = OrbitColors.Amber
)

private val DarkColors = darkColorScheme(
    primary = OrbitColors.EmeraldLight,
    onPrimary = Color(0xFF00382A),
    primaryContainer = OrbitColors.DarkGreen,
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = OrbitColors.Sky,
    tertiary = OrbitColors.Amber
)

@Composable
fun OrbitTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
