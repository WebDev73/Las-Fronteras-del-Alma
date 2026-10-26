package app.fronteras.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val Rust = Color(0xFF9C4221)
private val Ink = Color(0xFF1C1B19)
private val Paper = Color(0xFFF7F2E7)
private val Sand = Color(0xFFEFE7D8)

private val Light = lightColorScheme(
    primary = Rust, onPrimary = Color.White, secondary = Color(0xFF5A5650), background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink, surfaceVariant = Sand, onSurfaceVariant = Color(0xFF4A463F),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFE5A287), onPrimary = Color(0xFF3B1304), background = Color(0xFF15140F), onBackground = Color(0xFFEDE6DA),
    surface = Color(0xFF15140F), onSurface = Color(0xFFEDE6DA), surfaceVariant = Color(0xFF2B2924), onSurfaceVariant = Color(0xFFCFC6B8),
)

private fun TextStyle.scaled(f: Float) = copy(
    fontSize = if (fontSize != TextUnit.Unspecified) (fontSize.value * f).sp else fontSize,
    lineHeight = if (lineHeight != TextUnit.Unspecified) (lineHeight.value * f).sp else lineHeight,
)

@Composable
fun FronterasTheme(largeText: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    val base = Typography()
    val f = if (largeText) 1.2f else 1f
    val typography = Typography(
        displaySmall = base.displaySmall.scaled(f), headlineMedium = base.headlineMedium.scaled(f), headlineSmall = base.headlineSmall.scaled(f),
        titleLarge = base.titleLarge.scaled(f), titleMedium = base.titleMedium.scaled(f), titleSmall = base.titleSmall.scaled(f),
        bodyLarge = base.bodyLarge.scaled(f), bodyMedium = base.bodyMedium.scaled(f), bodySmall = base.bodySmall.scaled(f),
        labelLarge = base.labelLarge.scaled(f), labelMedium = base.labelMedium.scaled(f), labelSmall = base.labelSmall.scaled(f),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
