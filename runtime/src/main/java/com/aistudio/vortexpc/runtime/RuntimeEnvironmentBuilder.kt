package com.aistudio.vortexpc.runtime

import com.aistudio.vortexpc.core.model.AudioDriverType
import com.aistudio.vortexpc.core.model.Box64Profile
import com.aistudio.vortexpc.core.model.ContainerConfig
import com.aistudio.vortexpc.core.model.DxWrapperType
import com.aistudio.vortexpc.core.model.GraphicsDriverType
import com.aistudio.vortexpc.core.model.TranslatorEngine
import com.aistudio.vortexpc.nativebridge.NativeExecutionPaths
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeEnvironmentBuilder @Inject constructor() {

  fun buildEnvMap(
    config: ContainerConfig,
    prefixPath: String,
    rootfsPath: String,
    nativePaths: NativeExecutionPaths,
    displayNumber: Int = 0,
    xSocketDir: String,
    audioSocketPath: String,
  ): Map<String, String> {
    val env = linkedMapOf<String, String>()

    // PRoot & W^X targetSdk 36 loader environment
    env["PROOT_TMP_DIR"] = "$rootfsPath/tmp"
    if (nativePaths.prootLoaderPath.isNotBlank()) {
      env["PROOT_LOADER"] = nativePaths.prootLoaderPath
    }
    env["HOME"] = "/home/vortex"
    env["USER"] = "vortex"
    env["DISPLAY"] = ":$displayNumber"
    env["VORTEX_XSOCKET_DIR"] = xSocketDir
    env["WINEPREFIX"] = prefixPath
    env["WINEARCH"] = if (config.wineVersion.contains("arm64ec")) "win64" else "win64"

    // Translator flags
    when (config.translator) {
      TranslatorEngine.BOX64, TranslatorEngine.BOX86 -> {
        env["BOX64_LOG"] = "1"
        env["BOX64_NOBANNER"] = "0"
        when (config.box64Profile) {
          Box64Profile.STABILITY -> {
            env["BOX64_DYNAREC_BIGBLOCK"] = "1"
            env["BOX64_DYNAREC_STRONGMEM"] = "2"
          }
          Box64Profile.COMPATIBILITY -> {
            env["BOX64_DYNAREC_BIGBLOCK"] = "1"
            env["BOX64_DYNAREC_STRONGMEM"] = "1"
            env["BOX64_DYNAREC_FASTNAN"] = "1"
          }
          Box64Profile.PERFORMANCE, Box64Profile.CUSTOM -> {
            env["BOX64_DYNAREC_BIGBLOCK"] = "3"
            env["BOX64_DYNAREC_STRONGMEM"] = "0"
            env["BOX64_DYNAREC_FASTNAN"] = "1"
            env["BOX64_DYNAREC_FASTROUND"] = "1"
          }
        }
      }
      TranslatorEngine.FEX -> {
        env["FEX_APP_CONFIG_LOCATION"] = "$rootfsPath/etc/fex"
        env["FEX_TSOENABLED"] = "1"
      }
    }

    // Graphics driver & ICD configuration
    when (config.graphicsDriver) {
      GraphicsDriverType.TURNIP -> {
        env["VK_ICD_FILENAMES"] = "$rootfsPath/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json"
        env["TU_DEBUG"] = "noconform"
      }
      GraphicsDriverType.SYSTEM_VULKAN -> {
        env["VK_ICD_FILENAMES"] = "/vendor/etc/vulkan/icd.d/vulkan.json"
      }
      GraphicsDriverType.ZINK -> {
        env["MESA_LOADER_DRIVER_OVERRIDE"] = "zink"
        env["GALLIUM_DRIVER"] = "zink"
      }
      GraphicsDriverType.VIRGL -> {
        env["GALLIUM_DRIVER"] = "virpipe"
        env["VTEST_SOCK"] = "$rootfsPath/tmp/.virgl_test"
      }
    }

    // DirectX translation wrapper
    when (config.dxWrapper) {
      DxWrapperType.DXVK, DxWrapperType.D8VK -> {
        env["WINEDLLOVERRIDES"] = "d3d9,d3d10core,d3d11,dxgi=n,b"
        env["DXVK_STATE_CACHE"] = "1"
        env["DXVK_STATE_CACHE_PATH"] = "$prefixPath/dxvk_cache"
        if (config.fpsLimit > 0) {
          env["DXVK_FRAME_RATE"] = config.fpsLimit.toString()
        }
      }
      DxWrapperType.VKD3D_PROTON -> {
        env["WINEDLLOVERRIDES"] = "d3d12,d3d12core=n,b"
        env["VKD3D_CONFIG"] = "dxr"
      }
      DxWrapperType.WINED3D -> {
        env["WINEDLLOVERRIDES"] = "d3d9,d3d10core,d3d11,d3d12=b"
      }
    }

    // Audio bridge
    when (config.audioDriver) {
      AudioDriverType.AAUDIO -> {
        env["VORTEX_AUDIO_BACKEND"] = "aaudio"
        env["PULSE_SERVER"] = "unix:$audioSocketPath"
      }
      AudioDriverType.PULSEAUDIO -> {
        env["PULSE_SERVER"] = "unix:$audioSocketPath"
      }
      AudioDriverType.ALSA -> {
        env["ALSA_BRIDGE_SOCK"] = audioSocketPath
      }
    }

    // User / container overrides always win
    config.envVars.forEach { (k, v) -> env[k] = v }
    return env
  }
}
