package com.footballstudio.livescore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF004D40),
    onPrimary = Color.White,
    secondary = Color(0xFF2E7D32),
    onSecondary = Color.White,
    background = Color(0xFFF6FBF7),
    onBackground = Color(0xFF15231E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15231E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6DD8C2),
    secondary = Color(0xFF81C784),
    background = Color(0xFF0F1A16),
    surface = Color(0xFF14231D)
)

@Composable
fun FootballLiveTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
