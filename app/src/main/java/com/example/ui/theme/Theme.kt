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
    primary = DLSADarkGold,
    secondary = DLSASlateBlue,
    tertiary = DLSASteelGray,
    background = DLSADarkBg,
    surface = DLSADarkSurface,
    onPrimary = DLSADeepNavy,
    onSecondary = DLSADarkText,
    onTertiary = DLSADarkText,
    onBackground = DLSADarkText,
    onSurface = DLSADarkText
  )

private val LightColorScheme =
  lightColorScheme(
    primary = DLSADeepNavy,
    secondary = DLSASlateBlue,
    tertiary = DLSASteelGray,
    background = DLSALightBg,
    surface = DLSALightSurface,
    onPrimary = DLSALightSurface,
    onSecondary = DLSALightSurface,
    onTertiary = DLSALightSurface,
    onBackground = DLSALightText,
    onSurface = DLSALightText,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Override dynamicColor default to false so brand colors show up nicely
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
