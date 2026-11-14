package com.aistudio.vortexpc.runtime

import com.aistudio.vortexpc.core.model.AudioDriverType
import com.aistudio.vortexpc.core.model.AutoConfigTier
import com.aistudio.vortexpc.core.model.Box64Profile
import com.aistudio.vortexpc.core.model.ContainerConfig
import com.aistudio.vortexpc.core.model.DxWrapperType
import com.aistudio.vortexpc.core.model.GpuVendor
import com.aistudio.vortexpc.core.model.GraphicsDriverType
import com.aistudio.vortexpc.core.model.HardwareProfile
import com.aistudio.vortexpc.core.model.PeArchitecture
import com.aistudio.vortexpc.core.model.StartupSelection
import com.aistudio.vortexpc.core.model.TranslatorEngine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class AutoConfigRecommendation(
  val containerConfig: ContainerConfig,
  val summaryReason: String,
  val highlights: List<String>,
)

@Singleton
class AutoConfigEngine @Inject constructor() {

  fun buildConfig(
    hardware: HardwareProfile,
    tier: AutoConfigTier,
    peArch: PeArchitecture = PeArchitecture.X64,
    containerName: String = "Auto-Tuned Container",
    existingId: String? = null,
  ): AutoConfigRecommendation {
    val driver = when (hardware.gpuVendor) {
      GpuVendor.ADRENO -> GraphicsDriverType.TURNIP
      GpuVendor.MALI, GpuVendor.XCLIPSE ->
        if (hardware.supportsVulkan13) GraphicsDriverType.SYSTEM_VULKAN else GraphicsDriverType.VIRGL
      GpuVendor.POWERVR, GpuVendor.UNKNOWN -> GraphicsDriverType.VIRGL
    }

    val dxWrapper = when {
      driver == GraphicsDriverType.VIRGL -> DxWrapperType.WINED3D
      tier == AutoConfigTier.BATTERY_SAVER -> DxWrapperType.DXVK
      hardware.supportsVulkan13 -> DxWrapperType.DXVK
      else -> DxWrapperType.WINED3D
    }

    val translator = when (peArch) {
      PeArchitecture.X86 -> TranslatorEngine.BOX86
      PeArchitecture.X64, PeArchitecture.UNKNOWN -> TranslatorEngine.BOX64
      PeArchitecture.ARM64EC -> TranslatorEngine.BOX64
    }

    val boxProfile = when (tier) {
      AutoConfigTier.BATTERY_SAVER -> Box64Profile.STABILITY
      AutoConfigTier.BALANCED -> Box64Profile.COMPATIBILITY
      AutoConfigTier.PERFORMANCE -> Box64Profile.PERFORMANCE
    }

    val resolution = when (tier) {
      AutoConfigTier.BATTERY_SAVER -> "960x540"
      AutoConfigTier.BALANCED -> if (hardware.ramTotalMb >= 8192L) "1280x720" else "960x540"
      AutoConfigTier.PERFORMANCE -> if (hardware.gpuVendor == GpuVendor.ADRENO) "1280x720" else "1024x576"
    }

    val fpsLimit = when (tier) {
      AutoConfigTier.BATTERY_SAVER -> 30
      AutoConfigTier.BALANCED -> 60
      AutoConfigTier.PERFORMANCE -> 0 // Uncapped
    }

    val affinityMask = computeAffinityMask(hardware.cpuCores, tier)
    val env = ContainerConfig.defaultEnvVars().toMutableMap().apply {
      when (boxProfile) {
        Box64Profile.STABILITY -> {
          put("BOX64_DYNAREC_BIGBLOCK", "1")
          put("BOX64_DYNAREC_STRONGMEM", "2")
          put("BOX64_DYNAREC_FASTNAN", "0")
          put("BOX64_DYNAREC_FASTROUND", "0")
        }
        Box64Profile.COMPATIBILITY -> {
          put("BOX64_DYNAREC_BIGBLOCK", "1")
          put("BOX64_DYNAREC_STRONGMEM", "1")
          put("BOX64_DYNAREC_FASTNAN", "1")
          put("BOX64_DYNAREC_FASTROUND", "1")
        }
        Box64Profile.PERFORMANCE, Box64Profile.CUSTOM -> {
          put("BOX64_DYNAREC_BIGBLOCK", "3")
          put("BOX64_DYNAREC_STRONGMEM", "0")
          put("BOX64_DYNAREC_FASTNAN", "1")
          put("BOX64_DYNAREC_FASTROUND", "1")
          put("BOX64_DYNAREC_SAFEFLAGS", "0")
        }
      }
      if (dxWrapper == DxWrapperType.DXVK) {
        put("DXVK_ASYNC", "1")
        put("DXVK_STATE_CACHE", "1")
      }
      if (hardware.is16KbPageDevice) {
        put("VORTEX_PAGE_SIZE", "16384")
      }
    }

    val wineBuild = if (peArch == PeArchitecture.ARM64EC || hardware.ramTotalMb >= 8192L) {
      "wine-9.22-arm64ec"
    } else {
      "wine-9.22-x86_64"
    }

    val config = ContainerConfig(
      id = existingId ?: "ctr-${UUID.randomUUID().toString().take(8)}",
      name = containerName,
      wineVersion = wineBuild,
      screenResolution = resolution,
      graphicsDriver = driver,
      dxWrapper = dxWrapper,
      dxWrapperVersion = if (dxWrapper == DxWrapperType.VKD3D_PROTON) "2.14.0" else "2.5.3",
      audioDriver = AudioDriverType.AAUDIO,
      audioLatencyMs = if (tier == AutoConfigTier.PERFORMANCE) 16 else 24,
      translator = translator,
      box64Profile = boxProfile,
      startupSelection = StartupSelection.ESSENTIAL_SERVICES,
      cpuAffinityMask = affinityMask,
      envVars = env,
      fullscreen = true,
      renderScale = if (tier == AutoConfigTier.BATTERY_SAVER) 0.85f else 1.0f,
      fpsLimit = fpsLimit,
      showHudByDefault = true,
    )

    val driverLabel = when (driver) {
      GraphicsDriverType.TURNIP -> "Mesa Turnip KGSL"
      GraphicsDriverType.SYSTEM_VULKAN -> "System Vulkan"
      GraphicsDriverType.ZINK -> "Mesa Zink"
      GraphicsDriverType.VIRGL -> "VirGL GLES"
    }

    val summary = "${hardware.gpuModel} (${hardware.vulkanVersion}) -> $driverLabel + ${dxWrapper.name} + ${translator.name} (${boxProfile.name})"
    val highlights = listOf(
      "GPU Path: $driverLabel (${dxWrapper.name})",
      "Translator: ${translator.name} • ${boxProfile.name} Dynarec",
      "Display: $resolution @ ${if (fpsLimit == 0) "Uncapped" else "${fpsLimit} FPS"}",
      "Page Size: ${hardware.pageSizeBytes / 1024}KB • ${hardware.cpuCores} Cores",
    )

    return AutoConfigRecommendation(
      containerConfig = config,
      summaryReason = summary,
      highlights = highlights,
    )
  }

  private fun computeAffinityMask(cores: Int, tier: AutoConfigTier): Int {
    val clamped = cores.coerceIn(1, 16)
    val allMask = (1 shl clamped) - 1
    return when (tier) {
      AutoConfigTier.BATTERY_SAVER -> if (clamped >= 4) 0b00001111 else allMask
      AutoConfigTier.BALANCED -> allMask
      AutoConfigTier.PERFORMANCE -> if (clamped >= 8) 0b11110000 else allMask
    }
  }
}
