package app.roadready.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF1E5B4F)
val Amber = Color(0xFFF2B33D)
val Good = Color(0xFF2E7D32)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8DF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Amber,
    onSecondary = Color(0xFF2B1C00),
    secondaryContainer = Color(0xFFFFE3B0),
    onSecondaryContainer = Color(0xFF2B1C00),
    background = Color(0xFFF7F9F7),
    surface = Color(0xFFF7F9F7),
    surfaceVariant = Color(0xFFE2E8E4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DD5C2),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF125144),
    onPrimaryContainer = Color(0xFFCDE8DF),
    secondary = Amber,
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFE3B0),
    background = Color(0xFF111413),
    surface = Color(0xFF111413),
    surfaceVariant = Color(0xFF2A302D),
)

@Composable
fun RoadReadyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
