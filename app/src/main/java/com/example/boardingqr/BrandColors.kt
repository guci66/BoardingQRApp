package com.example.boardingqr

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette
val BrandPrimary = Color(0xFF0057D9)
val BrandOnPrimary = Color(0xFFFFFFFF)
val BrandSecondary = Color(0xFF00C2A8)
val BrandBackground = Color(0xFFF7F9FC)
val BrandSurface = Color(0xFFFFFFFF)
val BrandOnSurface = Color(0xFF1A2130)

fun brandLightColors(): ColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    secondary = BrandSecondary,
    background = BrandBackground,
    surface = BrandSurface,
    onSurface = BrandOnSurface
)