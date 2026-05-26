package com.example.ui.theme

import androidx.compose.ui.graphics.Color

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme

// Clean Minimalism Color Palette
val CleanBackground = Color(0xFFFDFBFF)
val CleanSurface = Color(0xFFF3F4F9)
val CleanWhite = Color(0xFFFFFFFF)

val CleanTextPrimary: Color
    @Composable
    get() = MaterialTheme.colorScheme.onBackground

val CleanTextSecondary: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

val CleanBlue = Color(0xFF0061A4)
val CleanBlueContainer = Color(0xFFD1E4FF)

val CleanGreen = Color(0xFF2E7D32)
val CleanGreenContainer = Color(0xFFE8F5E9)

val CleanRed = Color(0xFFBA1A1A)
val CleanRedContainer = Color(0xFFFFDAD7)

val CleanOrange = Color(0xFFEF6C00)
val CleanOrangeContainer = Color(0xFFFFE0B2)

val CleanOutline = Color(0xFF74777F)
val CleanDivider = Color(0xFFE1E2EC)
