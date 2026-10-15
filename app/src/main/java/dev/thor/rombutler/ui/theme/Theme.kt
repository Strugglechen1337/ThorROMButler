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

/**
 * Ragnarök variant: glowing ember orange on a deep crimson-black sky.
 */
private val RagnarokColorScheme = ThorColorScheme.copy(
    primary = Color(0xFFFF7A3D),
    onPrimary = Color(0xFF3A1200),
    primaryContainer = Color(0xFF8A3A12),
    onPrimaryContainer = Color(0xFFFFD6BF),
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFF8A6A1D),
    onSecondaryContainer = Color(0xFFFFE9B0),
    tertiary = Color(0xFFFFE9B0),
    background = Color(0xFF0E0707),
    surface = Color(0xFF1A0E0C),
    surfaceVariant = Color(0xFF281613),
    surfaceContainer = Color(0xFF1A0E0C),
    surfaceContainerLow = Color(0xFF1A0E0C),
    surfaceContainerLowest = Color(0xFF0E0707),
    surfaceContainerHigh = Color(0xFF281613),
    surfaceContainerHighest = Color(0xFF3A1F19),
    outline = Color(0xFF5C3328),
    outlineVariant = Color(0xFF3A1F19),
)

/**
 * Aurora variant: teal and magenta northern lights over a deep green-black sky.
 */
private val AuroraColorScheme = ThorColorScheme.copy(
    primary = Color(0xFF2DE2C4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0F6B5C),
    onPrimaryContainer = Color(0xFFB4F5EA),
    secondary = Color(0xFFFF6FD0),
    onSecondary = Color(0xFF47002F),
    secondaryContainer = Color(0xFF8A2D6C),
    onSecondaryContainer = Color(0xFFFFD3F0),
    tertiary = Color(0xFFFFD3F0),
    background = Color(0xFF040B0E),
    surface = Color(0xFF0A1619),
    surfaceVariant = Color(0xFF112326),
    surfaceContainer = Color(0xFF0A1619),
    surfaceContainerLow = Color(0xFF0A1619),
    surfaceContainerLowest = Color(0xFF040B0E),
    surfaceContainerHigh = Color(0xFF112326),
    surfaceContainerHighest = Color(0xFF1A3438),
    outline = Color(0xFF244A50),
    outlineVariant = Color(0xFF16302F),
)

/** Valid theme ids (see AppSettings.themeId). */
object AppThemes {
    const val THOR = "thor"
    const val ODIN = "odin"
    const val CRT = "crt"
    const val RAGNAROK = "ragnarok"
    const val AURORA = "aurora"
    val ALL = listOf(THOR, ODIN, CRT, RAGNAROK, AURORA)
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
            AppThemes.RAGNAROK -> RagnarokColorScheme
            AppThemes.AURORA -> AuroraColorScheme
            else -> ThorColorScheme
        },
        typography = ThorTypography,
        shapes = ThorShapes,
        content = content,
    )
}
