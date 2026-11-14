package com.aistudio.vortexpc.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aistudio.vortexpc.core.model.ThemeMode

@Immutable
data class VortexAccentPalette(
  val hairline: Color = HairlineSlate,
  val telemetryOk: Color = NeonEmerald,
  val telemetryWarn: Color = AmberTelemetry,
  val telemetryHot: Color = CrimsonAlert,
  val focusGlow: Color = ElectricCyan,
  val isTrueBlack: Boolean = true,
)

val LocalVortexAccents = staticCompositionLocalOf { VortexAccentPalette() }

private val AmoledColorScheme = darkColorScheme(
  primary = ElectricCyan,
  onPrimary = OnCyanDark,
  primaryContainer = CyanContainerDark,
  onPrimaryContainer = ElectricCyan,
  secondary = CobaltSecondary,
  onSecondary = AmoledBlack,
  tertiary = NeonEmerald,
  onTertiary = AmoledBlack,
  background = AmoledBlack,
  onBackground = TextPrimaryDark,
  surface = AmoledBlack,
  onSurface = TextPrimaryDark,
  surfaceVariant = CarbonSurface,
  onSurfaceVariant = TextSecondaryDark,
  surfaceContainerLowest = AmoledBlack,
  surfaceContainerLow = ObsidianBase,
  surfaceContainer = CarbonSurface,
  surfaceContainerHigh = CarbonElevated,
  surfaceContainerHighest = CarbonHighest,
  outline = HairlineSlate,
  error = CrimsonAlert,
)

private val DarkSlateColorScheme = darkColorScheme(
  primary = ElectricCyan,
  onPrimary = OnCyanDark,
  primaryContainer = CyanContainerDark,
  onPrimaryContainer = ElectricCyan,
  secondary = CobaltSecondary,
  tertiary = NeonEmerald,
  background = ObsidianBase,
  onBackground = TextPrimaryDark,
  surface = CarbonSurface,
  onSurface = TextPrimaryDark,
  surfaceVariant = CarbonElevated,
  onSurfaceVariant = TextSecondaryDark,
  surfaceContainerLow = ObsidianBase,
  surfaceContainer = CarbonSurface,
  surfaceContainerHigh = CarbonElevated,
  surfaceContainerHighest = CarbonHighest,
  outline = HairlineSlate,
  error = CrimsonAlert,
)

private val VortexLightColorScheme = lightColorScheme(
  primary = LightPrimary,
  onPrimary = Color.White,
  secondary = CobaltSecondary,
  tertiary = Color(0xFF007A43),
  background = LightCanvas,
  onBackground = LightTextPrimary,
  surface = LightSurface,
  onSurface = LightTextPrimary,
  surfaceVariant = LightElevated,
  onSurfaceVariant = LightTextSecondary,
  outline = Color(0xFFCBD5E1),
  error = CrimsonAlert,
)

private val VortexShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun VortexTheme(
  themeMode: ThemeMode = ThemeMode.AMOLED,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val colorScheme = when (themeMode) {
    ThemeMode.AMOLED -> AmoledColorScheme
    ThemeMode.DARK -> DarkSlateColorScheme
    ThemeMode.LIGHT -> VortexLightColorScheme
    ThemeMode.DYNAMIC -> {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
      } else {
        AmoledColorScheme
      }
    }
  }

  val accents = VortexAccentPalette(
    hairline = if (themeMode == ThemeMode.LIGHT) Color(0xFFD5DEEA) else HairlineSlate,
    telemetryOk = if (themeMode == ThemeMode.LIGHT) Color(0xFF00874B) else NeonEmerald,
    telemetryWarn = AmberTelemetry,
    telemetryHot = CrimsonAlert,
    focusGlow = if (themeMode == ThemeMode.LIGHT) LightPrimary else ElectricCyan,
    isTrueBlack = themeMode == ThemeMode.AMOLED,
  )

  CompositionLocalProvider(LocalVortexAccents provides accents) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = VortexTypography,
      shapes = VortexShapes,
      content = content,
    )
  }
}
