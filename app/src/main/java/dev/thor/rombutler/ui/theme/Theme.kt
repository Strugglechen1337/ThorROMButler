package dev.thor.rombutler.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Thor color scheme — the app is dark mode ONLY by design decision,
 * so there is no light scheme and no dynamic color.
 */
private val ThorColorScheme = darkColorScheme(
    primary = ThorNeon,
    onPrimary = ThorOnNeon,
    primaryContainer = ThorNeonDim,
    onPrimaryContainer = ThorNeonBright,

    secondary = ThorGold,
    onSecondary = ThorOnGold,
    secondaryContainer = ThorGoldDim,
    onSecondaryContainer = ThorGoldBright,

    tertiary = ThorGoldBright,
    onTertiary = ThorOnGold,

    background = ThorBackground,
    onBackground = ThorTextPrimary,

    surface = ThorSurface,
    onSurface = ThorTextPrimary,
    surfaceVariant = ThorSurfaceHigh,
    onSurfaceVariant = ThorTextSecondary,
    surfaceContainer = ThorSurface,
    surfaceContainerHigh = ThorSurfaceHigh,
    surfaceContainerHighest = ThorSurfaceHighest,
    surfaceContainerLow = ThorSurface,
    surfaceContainerLowest = ThorBackground,

    error = ThorError,
    onError = ThorOnError,

    outline = ThorOutline,
    outlineVariant = ThorOutlineVariant,
)

/**
 * Expressive-inspired shape scale: deliberately varied, generous radii —
 * small chips stay crisp while cards and dialogs get soft, large corners.
 */
private val ThorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * Odin variant: violet lightning, silver accents — same night sky.
 */
private val OdinColorScheme = ThorColorScheme.copy(
    primary = Color(0xFFB794F6),
    onPrimary = Color(0xFF2A1052),
    primaryContainer = Color(0xFF5B3A8E),
    onPrimaryContainer = Color(0xFFDDCEFF),
    secondary = Color(0xFFCBD5E1),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF64748B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFFE2E8F0),
)

/**
 * CRT variant: phosphor green and amber, like an old tube monitor.
 */
private val CrtColorScheme = ThorColorScheme.copy(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF166534),
    onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF422006),
    secondaryContainer = Color(0xFF92600D),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = Color(0xFFFDE68A),
)

/** Valid theme ids (see AppSettings.themeId). */
object AppThemes {
    const val THOR = "thor"
    const val ODIN = "odin"
    const val CRT = "crt"
    val ALL = listOf(THOR, ODIN, CRT)
}

/**
 * App theme wrapper. Always dark — ignores the system setting on purpose.
 *
 * @param themeId one of [AppThemes]; unknown values fall back to Thor.
 */
@Composable
fun ThorRomButlerTheme(themeId: String = AppThemes.THOR, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = when (themeId) {
            AppThemes.ODIN -> OdinColorScheme
            AppThemes.CRT -> CrtColorScheme
            else -> ThorColorScheme
        },
        typography = ThorTypography,
        shapes = ThorShapes,
        content = content,
    )
}
