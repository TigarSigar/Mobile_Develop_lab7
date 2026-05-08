package com.example.buhlograf.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BuhloBackground = Color(0xFF15120F)
val BuhloSurface = Color(0xFF231D18)
val BuhloSurfaceAlt = Color(0xFF302821)
val BuhloAmber = Color(0xFFFE9601)
val BuhloCream = Color(0xFFFFF1C7)
val BuhloMint = Color(0xFF6EE7B7)
val BuhloBlue = Color(0xFF7DD3FC)
val BuhloRed = Color(0xFFFF6B6B)
val BuhloText = Color(0xFFFDF7E7)
val BuhloMuted = Color(0xFFCDBFA9)

private val BuhlografColors = darkColorScheme(
    primary = BuhloAmber,
    secondary = BuhloMint,
    tertiary = BuhloBlue,
    background = BuhloBackground,
    surface = BuhloSurface,
    surfaceVariant = BuhloSurfaceAlt,
    onPrimary = Color(0xFF211100),
    onSecondary = Color(0xFF052115),
    onBackground = BuhloText,
    onSurface = BuhloText,
    onSurfaceVariant = BuhloMuted
)

@Composable
fun BuhlografTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BuhlografColors,
        content = content
    )
}
