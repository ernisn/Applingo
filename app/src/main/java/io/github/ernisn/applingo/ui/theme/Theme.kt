package io.github.ernisn.applingo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import io.github.ernisn.applingo.data.AppSettings
import io.github.ernisn.applingo.data.ThemeMode

@Composable
fun isAppInDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * Wallpaper colors (Monet, always available since minSdk 33) or a tonal-spot palette generated from
 * a seed color, optionally with pure-black surfaces for OLED screens.
 */
@Composable
fun ApplingoTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isAppInDarkTheme(settings)
    val base = if (settings.dynamicColor) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        remember(settings.seedColor, dark) { seedColorScheme(settings.seedColor, dark) }
    }
    val scheme = if (dark && settings.pureBlack) base.pureBlack() else base
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun seedColorScheme(seed: Int, dark: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0)
    val roles = MaterialDynamicColors()
    fun color(role: MaterialDynamicColors.() -> com.materialkolor.dynamiccolor.DynamicColor) =
        Color(roles.role().getArgb(scheme))
    val template = if (dark) darkColorScheme() else lightColorScheme()
    return template.copy(
        primary = color { primary() },
        onPrimary = color { onPrimary() },
        primaryContainer = color { primaryContainer() },
        onPrimaryContainer = color { onPrimaryContainer() },
        inversePrimary = color { inversePrimary() },
        secondary = color { secondary() },
        onSecondary = color { onSecondary() },
        secondaryContainer = color { secondaryContainer() },
        onSecondaryContainer = color { onSecondaryContainer() },
        tertiary = color { tertiary() },
        onTertiary = color { onTertiary() },
        tertiaryContainer = color { tertiaryContainer() },
        onTertiaryContainer = color { onTertiaryContainer() },
        background = color { background() },
        onBackground = color { onBackground() },
        surface = color { surface() },
        onSurface = color { onSurface() },
        surfaceVariant = color { surfaceVariant() },
        onSurfaceVariant = color { onSurfaceVariant() },
        surfaceTint = color { surfaceTint() },
        inverseSurface = color { inverseSurface() },
        inverseOnSurface = color { inverseOnSurface() },
        error = color { error() },
        onError = color { onError() },
        errorContainer = color { errorContainer() },
        onErrorContainer = color { onErrorContainer() },
        outline = color { outline() },
        outlineVariant = color { outlineVariant() },
        scrim = color { scrim() },
        surfaceBright = color { surfaceBright() },
        surfaceDim = color { surfaceDim() },
        surfaceContainer = color { surfaceContainer() },
        surfaceContainerHigh = color { surfaceContainerHigh() },
        surfaceContainerHighest = color { surfaceContainerHighest() },
        surfaceContainerLow = color { surfaceContainerLow() },
        surfaceContainerLowest = color { surfaceContainerLowest() },
    )
}

/** Black background and surfaces; containers stay slightly lifted so cards remain distinguishable. */
private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)
