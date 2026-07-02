package dev.thor.rombutler.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.thor.rombutler.ui.theme.ThorGlowBlue
import dev.thor.rombutler.ui.theme.ThorGlowGold

/**
 * Subtle neon glow for cards and buttons — the Thor look. Implemented as a
 * colored shadow, so it stays cheap (no offscreen blur passes).
 *
 * @param color glow color (defaults to neon blue).
 * @param elevation glow spread; keep small for "dezent".
 */
@Composable
fun Modifier.neonGlow(
    color: Color = ThorGlowBlue,
    elevation: Dp = 8.dp,
    shape: Shape = MaterialTheme.shapes.medium,
): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = color,
    spotColor = color,
)

/** Gold variant for primary call-to-action elements. */
@Composable
fun Modifier.goldGlow(
    elevation: Dp = 10.dp,
    shape: Shape = RoundedCornerShape(26.dp),
): Modifier = neonGlow(color = ThorGlowGold, elevation = elevation, shape = shape)
