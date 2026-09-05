package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

val SophisticatedDarkColorScheme =
  darkColorScheme(
    primary = SophisticatedPrimary,
    onPrimary = SophisticatedOnPrimary,
    primaryContainer = SophisticatedPrimaryContainer,
    onPrimaryContainer = SophisticatedOnPrimaryContainer,
    secondary = SophisticatedSecondary,
    onSecondary = SophisticatedOnSecondary,
    secondaryContainer = SophisticatedSecondaryContainer,
    onSecondaryContainer = SophisticatedOnSecondaryContainer,
    tertiary = SophisticatedAccentCyan,
    onTertiary = SophisticatedOnAccentCyan,
    tertiaryContainer = SophisticatedAccentCyanContainer,
    onTertiaryContainer = SophisticatedOnAccentCyanContainer,
    background = SophisticatedDarkBg,
    onBackground = SophisticatedTextPrimary,
    surface = SophisticatedSurface,
    onSurface = SophisticatedTextPrimary,
    surfaceVariant = SophisticatedSurfaceVariant,
    onSurfaceVariant = SophisticatedTextSecondary,
    surfaceContainerLowest = SophisticatedSurfaceContainerLowest,
    surfaceContainerLow = SophisticatedSurfaceContainerLow,
    surfaceContainer = SophisticatedSurfaceContainer,
    surfaceContainerHigh = SophisticatedSurfaceContainerHigh,
    surfaceContainerHighest = SophisticatedSurfaceContainerHighest,
    outline = SophisticatedOutline,
    outlineVariant = SophisticatedOutlineVariant
  )

private val DarkColorScheme = SophisticatedDarkColorScheme
private val LightColorScheme = SophisticatedDarkColorScheme

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
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
