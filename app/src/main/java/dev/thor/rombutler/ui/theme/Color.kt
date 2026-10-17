package dev.thor.rombutler.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Thor color palette — dark mode only.
 *
 * Concept: night-sky blue/black backgrounds, neon-blue "lightning" highlights,
 * gold accents (Mjölnir), subtle glow effects on interactive elements.
 */

// Backgrounds — deep blue-black, layered from darkest to lightest
val ThorBackground = Color(0xFF060A14)      // app background, almost black
val ThorSurface = Color(0xFF0C1322)         // cards
val ThorSurfaceHigh = Color(0xFF141E33)     // elevated cards, dialogs
val ThorSurfaceHighest = Color(0xFF1B2947)  // pressed / selected

// Neon blue — primary brand color ("lightning")
val ThorNeon = Color(0xFF38C8FF)
val ThorNeonBright = Color(0xFF7ADCFF)
val ThorNeonDim = Color(0xFF1E6E97)
val ThorOnNeon = Color(0xFF00293C)

// Gold — secondary accent (Mjölnir)
val ThorGold = Color(0xFFF5C542)
val ThorGoldBright = Color(0xFFFFDD7A)
val ThorGoldDim = Color(0xFF8A6D1D)
val ThorOnGold = Color(0xFF3A2E00)

// Text
val ThorTextPrimary = Color(0xFFE6EEF8)
val ThorTextSecondary = Color(0xFF93A5C0)
val ThorTextDisabled = Color(0xFF4E5D75)

// Semantic
val ThorError = Color(0xFFFF6B6B)
val ThorOnError = Color(0xFF3C0000)
val ThorSuccess = Color(0xFF4ADE80)
val ThorWarning = ThorGold

// Outlines / dividers
val ThorOutline = Color(0xFF3B5483)
val ThorOutlineVariant = Color(0xFF1A2740)

// Glow colors (used with alpha for shadow/glow modifiers)
val ThorGlowBlue = Color(0xFF38C8FF)
val ThorGlowGold = Color(0xFFF5C542)
