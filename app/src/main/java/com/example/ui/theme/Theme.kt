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

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryRose,
    secondary = SecondaryPeach,
    tertiary = SoftSage,
    background = DarkBackground,
    surface = DarkCard,
    onPrimary = DarkTextLight,
    onSecondary = DarkTextLight,
    onBackground = DarkTextLight,
    onSurface = DarkTextLight
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryRose,
    secondary = SecondaryPeach,
    tertiary = SoftSage,
    background = WarmBackground,
    surface = WarmCard,
    onPrimary = WarmTextLight,
    onSecondary = WarmTextDark,
    onBackground = WarmTextDark,
    onSurface = WarmTextDark
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Set to false by default to showcase our gorgeous warm organic rose style
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
