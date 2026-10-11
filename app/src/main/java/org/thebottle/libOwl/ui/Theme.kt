package org.thebottle.libOwl.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.thebottle.libOwl.data.AccentColor

/** Material's standard scheme with the chosen accent applied to the primary and container colours. */
fun appColorScheme(accent: AccentColor, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return if (dark) base.copy(
        primary = Color(accent.darkPrimary),
        onPrimary = Color(0xFF1A1A1A),
        primaryContainer = Color(accent.darkContainer),
        onPrimaryContainer = Color(accent.onDarkContainer),
        secondaryContainer = Color(accent.darkContainer),
        onSecondaryContainer = Color(accent.onDarkContainer)
    ) else base.copy(
        primary = Color(accent.lightPrimary),
        onPrimary = Color.White,
        primaryContainer = Color(accent.lightContainer),
        onPrimaryContainer = Color(accent.onLightContainer),
        secondaryContainer = Color(accent.lightContainer),
        onSecondaryContainer = Color(accent.onLightContainer)
    )
}
