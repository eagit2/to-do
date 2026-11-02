package app.todo.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.todo.data.ThemeChoice

/** Teal Material 3 scheme from the approved preview. */
private val Light = lightColorScheme(
    primary = Color(0xFF006A64), onPrimary = Color.White,
    primaryContainer = Color(0xFF9DF2E9), onPrimaryContainer = Color(0xFF00504B),
    secondaryContainer = Color(0xFFCCE8E4), onSecondaryContainer = Color(0xFF324B49),
    tertiaryContainer = Color(0xFFCFE5FF), onTertiaryContainer = Color(0xFF304962),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF4FBF9), onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF4FBF9), onSurface = Color(0xFF161D1C), onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C6),
    inverseSurface = Color(0xFF2B3231), inverseOnSurface = Color(0xFFECF2F0), inversePrimary = Color(0xFF81D5CD),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5F3), surfaceContainer = Color(0xFFE9EFED),
    surfaceContainerHigh = Color(0xFFE3E9E8), surfaceContainerHighest = Color(0xFFDDE4E2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF81D5CD), onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504B), onPrimaryContainer = Color(0xFF9DF2E9),
    secondaryContainer = Color(0xFF324B49), onSecondaryContainer = Color(0xFFCCE8E4),
    tertiaryContainer = Color(0xFF304962), onTertiaryContainer = Color(0xFFCFE5FF),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514), onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF0E1514), onSurface = Color(0xFFDDE4E2), onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF899391), outlineVariant = Color(0xFF3F4947),
    inverseSurface = Color(0xFFDDE4E2), inverseOnSurface = Color(0xFF2B3231), inversePrimary = Color(0xFF006A64),
    surfaceContainerLowest = Color(0xFF090F0F), surfaceContainerLow = Color(0xFF161D1C), surfaceContainer = Color(0xFF1A2120),
    surfaceContainerHigh = Color(0xFF252B2A), surfaceContainerHighest = Color(0xFF2F3635),
)

/** The warm highlight for "due soon" and "behind pace". Never red: nothing on this list is ever overdue. */
data class Warm(val bg: Color, val fg: Color)

val LocalWarm = staticCompositionLocalOf { Warm(Color(0xFFFFDF9D), Color(0xFF5B4300)) }

@Composable
fun TodoTheme(choice: ThemeChoice, wallpaper: Boolean, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    val warm = if (dark) Warm(Color(0xFF5B4300), Color(0xFFFFDF9D)) else Warm(Color(0xFFFFDF9D), Color(0xFF5B4300))
    CompositionLocalProvider(LocalWarm provides warm) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
