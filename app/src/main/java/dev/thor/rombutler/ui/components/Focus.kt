package dev.thor.rombutler.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Gamepad/D-pad support for cards: makes the element focusable so
 * hardware navigation can walk the lists (which auto-scroll to the
 * focused item) and draws a visible focus frame in the theme's primary
 * color. Purely ADDITIVE — touch behavior is unchanged.
 */
@Composable
fun Modifier.thorFocusable(shape: Shape = MaterialTheme.shapes.medium): Modifier {
    var focused by remember { mutableStateOf(false) }
    val borderColor = MaterialTheme.colorScheme.primary
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) Modifier.border(width = 2.dp, color = borderColor, shape = shape)
            else Modifier,
        )
        .focusable()
}
