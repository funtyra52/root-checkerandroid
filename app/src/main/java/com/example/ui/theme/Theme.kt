package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CleanBlue,
    onPrimary = Color.White,
    secondary = CleanBlueContainer,
    background = Color(0xFF12131A),
    surface = Color(0xFF1E1F28),
    onBackground = Color(0xFFE3E2E6),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF2C2D35),
    onSurfaceVariant = Color(0xFFC4C6D0)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CleanBlue,
    onPrimary = Color.White,
    secondary = CleanBlueContainer,
    onSecondary = CleanBlue,
    background = CleanBackground,
    surface = CleanWhite,
    onBackground = Color(0xFF1B1B1F),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = CleanSurface,
    onSurfaceVariant = Color(0xFF44474F),
    outline = CleanOutline,
    outlineVariant = CleanDivider
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
