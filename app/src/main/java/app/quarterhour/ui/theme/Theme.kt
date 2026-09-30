package app.quarterhour.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val Brand = Color(0xFF1F4E5F)
val Amber = Color(0xFFE8A33D)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Color(0xFF2B1C00),
    background = Color(0xFFFBF8F3),
    surface = Color(0xFFFBF8F3),
    surfaceVariant = Color(0xFFEDE7DD),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8CC7DB),
    onPrimary = Color(0xFF00363F),
    secondary = Amber,
    background = Color(0xFF121416),
    surface = Color(0xFF121416),
    surfaceVariant = Color(0xFF2A2D30),
)

/** Serif body text for the reader: easier on long reads. */
val ReaderBody = TextStyle(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 28.sp)

@Composable
fun QuarterHourTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = base.copy(
            headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
            titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif),
        ),
        content = content,
    )
}
