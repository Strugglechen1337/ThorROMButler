package dev.thor.rombutler.ui.components

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Human-readable file size, e.g. "1,4 GB" (German decimal comma).
 */
fun formatFileSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.GERMANY, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.GERMANY, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.GERMANY, "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}

/**
 * Short localized date for archive cards, e.g. "02.07.2026".
 */
fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.GERMANY).format(Date(epochMillis))
