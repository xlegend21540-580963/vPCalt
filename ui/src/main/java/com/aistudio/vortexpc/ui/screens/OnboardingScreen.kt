package com.aistudio.vortexpc.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aistudio.vortexpc.core.model.AutoConfigTier
import com.aistudio.vortexpc.core.model.GpuVendor
import com.aistudio.vortexpc.core.model.GraphicsDriverType
import com.aistudio.vortexpc.core.model.HardwareProfile
import com.aistudio.vortexpc.core.model.ThemeMode
import com.aistudio.vortexpc.core.model.TranslatorEngine
import com.aistudio.vortexpc.runtime.StagingProgress
import com.aistudio.vortexpc.ui.R
import com.aistudio.vortexpc.ui.components.VortexConsoleCard
import com.aistudio.vortexpc.ui.components.VortexTagPill
import com.aistudio.vortexpc.ui.theme.LocalVortexAccents
import com.aistudio.vortexpc.ui.theme.VortexTheme

@Composable
fun OnboardingScreen(
  hardwareProfile: HardwareProfile?,
  stagingProgress: StagingProgress?,
  onStartSetup: (AutoConfigTier) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTier by remember { mutableStateOf(AutoConfigTier.BALANCED) }
  val accents = LocalVortexAccents.current
  val isStaging = stagingProgress != null && !stagingProgress.isComplete

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 720.dp)
        .fillMaxSize()
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Hero artwork banner
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(210.dp)
          .clip(RoundedCornerShape(22.dp))
          .background(Color.Black),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_onboarding_hero),
          contentDescription = "VortexPC handheld compatibility console",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0xCC000000)),
              )
            )
        )
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(18.dp),
        ) {
          VortexTagPill(
            text = "OFFLINE BUNDLED RUNTIME • GPLv3",
            accentColor = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
          )
          Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFBCC8DC),
          )
        }
      }

      // Detected Hardware Card
      VortexConsoleCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Memory,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = "Detected Device & Silicon",
            style = MaterialTheme.typography.titleMedium,
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (hardwareProfile != null) {
          Text(
            text = "${hardwareProfile.deviceModel} • ${hardwareProfile.socModel}",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
          )
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            VortexTagPill(hardwareProfile.gpuModel, accents.telemetryOk)
            VortexTagPill("Vulkan ${hardwareProfile.vulkanVersion}", MaterialTheme.colorScheme.primary)
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "RAM: ${hardwareProfile.ramTotalMb / 1024} GB • ${hardwareProfile.cpuCores} Cores • Page Size: ${hardwareProfile.pageSizeBytes / 1024}KB • Driver: ${hardwareProfile.recommendedDriver.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text("Probing device hardware...", style = MaterialTheme.typography.bodyMedium)
        }
      }

      Text(
        text = "Default Auto-Config Profile",
        style = MaterialTheme.typography.titleMedium,
      )

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TierOptionRow(
          title = "Battery Saver",
          subtitle = "960×540 • 30 FPS cap • Conservative Box64 dynarec",
          icon = Icons.Rounded.BatteryChargingFull,
          selected = selectedTier == AutoConfigTier.BATTERY_SAVER,
          onClick = { selectedTier = AutoConfigTier.BATTERY_SAVER },
          testTag = "tier_battery",
        )
        TierOptionRow(
          title = "Balanced (Recommended)",
          subtitle = "1280×720 • 60 FPS cap • Optimal thermal & Vulkan balance",
          icon = Icons.Rounded.AutoAwesome,
          selected = selectedTier == AutoConfigTier.BALANCED,
          onClick = { selectedTier = AutoConfigTier.BALANCED },
          testTag = "tier_balanced",
        )
        TierOptionRow(
          title = "Performance",
          subtitle = "Uncapped FPS • BigBlock 3 Dynarec • High-core CPU affinity",
          icon = Icons.Rounded.Speed,
          selected = selectedTier == AutoConfigTier.PERFORMANCE,
          onClick = { selectedTier = AutoConfigTier.PERFORMANCE },
          testTag = "tier_performance",
        )
      }

      AnimatedVisibility(visible = stagingProgress != null) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = stagingProgress?.currentStep.orEmpty(),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
              progress = { stagingProgress?.progressFraction ?: 0f },
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            )
          }
        }
      }

      Button(
        onClick = { onStartSetup(selectedTier) },
        enabled = !isStaging,
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("onboarding_initialize_button"),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
      ) {
        Icon(Icons.Rounded.RocketLaunch, contentDescription = null)
        Spacer(modifier = Modifier.size(10.dp))
        Text(
          text = if (isStaging) "Staging Offline Runtime..." else "Initialize Console & Create Container",
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
      }
    }
  }
}

@Composable
private fun TierOptionRow(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  VortexConsoleCard(
    onClick = onClick,
    selected = selected,
    modifier = Modifier
      .fillMaxWidth()
      .testTag(testTag),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (selected) {
        Icon(
          imageVector = Icons.Rounded.CheckCircle,
          contentDescription = "Selected",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun OnboardingPreview() {
  VortexTheme(ThemeMode.AMOLED) {
    OnboardingScreen(
      hardwareProfile = HardwareProfile(
        deviceModel = "Handheld Console",
        socModel = "SM8650",
        gpuVendor = GpuVendor.ADRENO,
        gpuModel = "Adreno 750",
        vulkanVersion = "1.3.275",
        supportsVulkan13 = true,
        ramTotalMb = 12288,
        cpuCores = 8,
        pageSizeBytes = 16384,
        supportedAbis = listOf("arm64-v8a"),
        hasGyroscope = true,
        recommendedDriver = GraphicsDriverType.TURNIP,
        recommendedTranslator = TranslatorEngine.BOX64,
        recommendedResolution = "1280x720",
      ),
      stagingProgress = null,
      onStartSetup = {},
    )
  }
}
