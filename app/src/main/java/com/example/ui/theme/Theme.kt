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
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    surface = androidx.compose.ui.graphics.Color(0xFF1E1F26), // Elevated Surface Container
    background = androidx.compose.ui.graphics.Color(0xFF111319), // Professional Dark Background
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF191B22), // Lower elevation surface
    outline = androidx.compose.ui.graphics.Color(0xFF474555), // Outline border color
    onSurface = androidx.compose.ui.graphics.Color(0xFFE2E2EB),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE2E2EB),
    surfaceTint = androidx.compose.ui.graphics.Color.Transparent
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    surface = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFFF8F9FE),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1E293B),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1E293B)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to let our pure dark theme shine consistently
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
