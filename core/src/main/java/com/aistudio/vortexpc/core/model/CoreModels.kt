package com.aistudio.vortexpc.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode {
  AMOLED,
  DARK,
  LIGHT,
  DYNAMIC,
}

@Serializable
enum class AutoConfigTier {
  BATTERY_SAVER,
  BALANCED,
  PERFORMANCE,
}

@Serializable
enum class TranslatorEngine {
  BOX64,
  BOX86,
  FEX,
}

@Serializable
enum class Box64Profile {
  STABILITY,
  COMPATIBILITY,
  PERFORMANCE,
  CUSTOM,
}

@Serializable
enum class GraphicsDriverType {
  TURNIP,
  SYSTEM_VULKAN,
  ZINK,
  VIRGL,
}

@Serializable
enum class DxWrapperType {
  DXVK,
  VKD3D_PROTON,
  WINED3D,
  D8VK,
}

@Serializable
enum class AudioDriverType {
  AAUDIO,
  PULSEAUDIO,
  ALSA,
}

@Serializable
enum class MouseMode {
  TOUCHPAD,
  DIRECT_TOUCH,
}

@Serializable
enum class StartupSelection {
  ESSENTIAL_SERVICES,
  NORMAL_DESKTOP,
  AGGRESSIVE_MINIMAL,
}

@Serializable
enum class PeArchitecture {
  X86,
  X64,
  ARM64EC,
  UNKNOWN,
}

@Serializable
enum class GpuVendor {
  ADRENO,
  MALI,
  XCLIPSE,
  POWERVR,
  UNKNOWN,
}

@Serializable
enum class ComponentCategory {
  ROOTFS,
  WINE,
  TRANSLATOR,
  GRAPHICS_DRIVER,
  DX_WRAPPER,
}

@Serializable
data class DriveMapping(
  val letter: String,
  val targetPath: String,
  val safTreeUri: String? = null,
)

@Serializable
data class ContainerConfig(
  val id: String,
  val name: String,
  val wineVersion: String = "wine-9.22-arm64ec",
  val screenResolution: String = "1280x720",
  val graphicsDriver: GraphicsDriverType = GraphicsDriverType.TURNIP,
  val customDriverId: String? = null,
  val dxWrapper: DxWrapperType = DxWrapperType.DXVK,
  val dxWrapperVersion: String = "2.5.3",
  val audioDriver: AudioDriverType = AudioDriverType.AAUDIO,
  val audioLatencyMs: Int = 24,
  val translator: TranslatorEngine = TranslatorEngine.BOX64,
  val box64Profile: Box64Profile = Box64Profile.PERFORMANCE,
  val startupSelection: StartupSelection = StartupSelection.ESSENTIAL_SERVICES,
  val cpuAffinityMask: Int = 0b11111111,
  val envVars: Map<String, String> = defaultEnvVars(),
  val driveMappings: List<DriveMapping> = defaultDriveMappings(),
  val fullscreen: Boolean = true,
  val renderScale: Float = 1.0f,
  val fpsLimit: Int = 60,
  val showHudByDefault: Boolean = true,
  val installedComponents: List<String> = listOf("vcrun2022", "d3dx9", "corefonts"),
  val createdAtEpochMs: Long = System.currentTimeMillis(),
) {
  companion object {
    fun defaultEnvVars(): Map<String, String> = mapOf(
      "WINEESYNC" to "1",
      "WINEFSYNC" to "1",
      "DXVK_STATE_CACHE" to "1",
      "DXVK_HUD" to "compiler",
      "MESA_VK_WSI_PRESENT_MODE" to "mailbox",
      "BOX64_DYNAREC_BIGBLOCK" to "2",
      "BOX64_DYNAREC_STRONGMEM" to "1",
    )

    fun defaultDriveMappings(): List<DriveMapping> = listOf(
      DriveMapping("C:", "/home/vortex/.wine/drive_c"),
      DriveMapping("D:", "/mnt/shared_games"),
      DriveMapping("Z:", "/"),
    )
  }
}

@Serializable
data class GameEntry(
  val id: String,
  val title: String,
  val exeUri: String,
  val exeFileName: String,
  val peArchitecture: PeArchitecture = PeArchitecture.X64,
  val workingDir: String = "",
  val launchArgs: String = "",
  val coverArtUri: String? = null,
  val containerId: String,
  val autoConfigTier: AutoConfigTier = AutoConfigTier.BALANCED,
  val autoConfigReason: String = "",
  val useAdvancedOverride: Boolean = false,
  val inputProfileId: String = "profile-gamepad-pro",
  val playTimeSeconds: Long = 0L,
  val lastPlayedAtEpochMs: Long = 0L,
  val pinnedShortcut: Boolean = false,
  val favorite: Boolean = false,
  val tags: List<String> = emptyList(),
)

@Serializable
data class HardwareProfile(
  val deviceModel: String,
  val socModel: String,
  val gpuVendor: GpuVendor,
  val gpuModel: String,
  val vulkanVersion: String,
  val supportsVulkan13: Boolean,
  val ramTotalMb: Long,
  val cpuCores: Int,
  val pageSizeBytes: Int,
  val supportedAbis: List<String>,
  val hasGyroscope: Boolean,
  val recommendedDriver: GraphicsDriverType,
  val recommendedTranslator: TranslatorEngine,
  val recommendedResolution: String,
) {
  val is16KbPageDevice: Boolean get() = pageSizeBytes >= 16384
}

@Serializable
data class BundledComponent(
  val id: String,
  val name: String,
  val category: ComponentCategory,
  val version: String,
  val license: String,
  val sizeBytes: Long,
  val sha256: String,
  val bundled: Boolean = true,
  val description: String,
  val staged: Boolean = false,
  val stagedPath: String? = null,
)

@Serializable
data class RuntimeBundleManifest(
  val bundleVersion: String,
  val license: String,
  val components: List<BundledComponent>,
)

@Serializable
data class CompatibilityPreset(
  val id: String,
  val name: String,
  val description: String,
  val translator: TranslatorEngine,
  val box64Profile: Box64Profile,
  val dxWrapper: DxWrapperType,
  val resolution: String,
  val fpsLimit: Int,
  val envVars: Map<String, String>,
)

@Serializable
data class PresetCatalog(
  val presets: List<CompatibilityPreset>,
)

@Serializable
data class CustomDriverPackage(
  val id: String,
  val name: String,
  val version: String,
  val author: String,
  val driverType: GraphicsDriverType,
  val icdJsonPath: String,
  val libraryPath: String,
  val importedAtEpochMs: Long = System.currentTimeMillis(),
)

@Serializable
data class PlaySessionRecord(
  val id: String,
  val gameId: String,
  val gameTitle: String,
  val containerId: String,
  val startedAtEpochMs: Long,
  val durationSeconds: Long,
  val avgFps: Float,
  val peakTempC: Float,
)

@Serializable
data class TelemetrySnapshot(
  val fps: Float = 0f,
  val frameTimeMs: Float = 0f,
  val cpuUsagePercent: Int = 0,
  val gpuLoadEstimatePercent: Int = 0,
  val ramUsedMb: Long = 0L,
  val ramTotalMb: Long = 0L,
  val batteryPercent: Int = 100,
  val batteryTempC: Float = 32.0f,
  val isCharging: Boolean = false,
  val fpsHistory: List<Float> = emptyList(),
)

enum class LogLevel {
  DEBUG,
  INFO,
  WARN,
  ERROR,
}

enum class LogCategory {
  RUNTIME,
  XSERVER,
  GRAPHICS,
  AUDIO,
  INPUT,
  SYSTEM,
}

data class DiagnosticLogEntry(
  val timestampMs: Long,
  val level: LogLevel,
  val category: LogCategory,
  val tag: String,
  val message: String,
)

data class UpstreamAttribution(
  val project: String,
  val license: String,
  val role: String,
  val upstreamUrl: String,
)

object UpstreamAttributions {
  val all: List<UpstreamAttribution> = listOf(
    UpstreamAttribution(
      project = "Winlator (brunodev85/winlator)",
      license = "GPL-3.0",
      role = "Architecture reference, XConnector IPC, sysvshm, PRoot Android integration",
      upstreamUrl = "https://github.com/brunodev85/winlator",
    ),
    UpstreamAttribution(
      project = "Wine & Wine-ARM64EC",
      license = "LGPL-2.1+",
      role = "Win32/Win64 compatibility layer and PE loader",
      upstreamUrl = "https://www.winehq.org/",
    ),
    UpstreamAttribution(
      project = "Box64 & Box86 (ptitSeb)",
      license = "MIT",
      role = "x86_64 and x86 dynamic binary recompilation on ARM64",
      upstreamUrl = "https://github.com/ptitSeb/box64",
    ),
    UpstreamAttribution(
      project = "FEX-Emu",
      license = "MIT",
      role = "Optional user-mode x86/x64 JIT translator",
      upstreamUrl = "https://fex-emu.com/",
    ),
    UpstreamAttribution(
      project = "PRoot",
      license = "GPL-2.0+",
      role = "User-space chroot, mount --bind, and PROOT_LOADER execution bridge",
      upstreamUrl = "https://proot-me.github.io/",
    ),
    UpstreamAttribution(
      project = "DXVK & D8VK",
      license = "zlib/libpng",
      role = "Direct3D 8/9/10/11 to Vulkan translation",
      upstreamUrl = "https://github.com/doitsujin/dxvk",
    ),
    UpstreamAttribution(
      project = "VKD3D-Proton",
      license = "LGPL-2.1+",
      role = "Direct3D 12 to Vulkan translation",
      upstreamUrl = "https://github.com/HansKristian-Work/vkd3d-proton",
    ),
    UpstreamAttribution(
      project = "Mesa Turnip / Zink / Freedreno",
      license = "MIT / Expat",
      role = "Open-source Adreno KGSL Vulkan & OpenGL-over-Vulkan drivers",
      upstreamUrl = "https://www.mesa3d.org/",
    ),
    UpstreamAttribution(
      project = "virglrenderer",
      license = "MIT",
      role = "Virtual 3D GLES acceleration bridge",
      upstreamUrl = "https://gitlab.freedesktop.org/virgl/virglrenderer",
    ),
  )
}
