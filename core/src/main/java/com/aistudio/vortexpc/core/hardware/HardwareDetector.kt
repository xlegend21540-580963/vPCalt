package com.aistudio.vortexpc.core.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.system.Os
import android.system.OsConstants
import com.aistudio.vortexpc.core.model.GpuVendor
import com.aistudio.vortexpc.core.model.GraphicsDriverType
import com.aistudio.vortexpc.core.model.HardwareProfile
import com.aistudio.vortexpc.core.model.TranslatorEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class HardwareDetector @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  suspend fun detect(): HardwareProfile = withContext(Dispatchers.IO) {
    val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: readCpuInfoHardware()
    } else {
      readCpuInfoHardware()
    }

    val memInfo = ActivityManager.MemoryInfo()
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    am?.getMemoryInfo(memInfo)
    val ramTotalMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1024L)

    val (gpuVendor, gpuModel) = detectGpuSafely(soc)
    val vulkanVersionInt = detectVulkanVersion()
    val vulkanStr = formatVulkanVersion(vulkanVersionInt)
    val supports13 = vulkanVersionInt >= 0x00403000

    val pageSize = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE).toInt() }.getOrDefault(4096)
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    val hasGyro = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

    val recommendedDriver = when (gpuVendor) {
      GpuVendor.ADRENO -> GraphicsDriverType.TURNIP
      GpuVendor.MALI, GpuVendor.XCLIPSE ->
        if (vulkanVersionInt > 0) GraphicsDriverType.SYSTEM_VULKAN else GraphicsDriverType.VIRGL
      GpuVendor.POWERVR, GpuVendor.UNKNOWN -> GraphicsDriverType.VIRGL
    }

    val recommendedRes = if (ramTotalMb >= 8192L && gpuVendor == GpuVendor.ADRENO) {
      "1280x720"
    } else {
      "960x540"
    }

    HardwareProfile(
      deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
      socModel = soc,
      gpuVendor = gpuVendor,
      gpuModel = gpuModel,
      vulkanVersion = vulkanStr,
      supportsVulkan13 = supports13,
      ramTotalMb = ramTotalMb,
      cpuCores = Runtime.getRuntime().availableProcessors(),
      pageSizeBytes = pageSize,
      supportedAbis = Build.SUPPORTED_ABIS.toList(),
      hasGyroscope = hasGyro,
      recommendedDriver = recommendedDriver,
      recommendedTranslator = TranslatorEngine.BOX64,
      recommendedResolution = recommendedRes,
    )
  }

  private fun readCpuInfoHardware(): String {
    return runCatching {
      val file = File("/proc/cpuinfo")
      if (!file.exists()) return@runCatching Build.HARDWARE
      val line = file.useLines { lines ->
        lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
      }
      line?.substringAfter(':')?.trim()?.ifBlank { Build.HARDWARE } ?: Build.HARDWARE
    }.getOrDefault(Build.HARDWARE)
  }

  private fun detectGpuSafely(soc: String): Pair<GpuVendor, String> {
    val kgslModel = runCatching {
      val f = File("/sys/class/kgsl/kgsl-3d0/gpu_model")
      if (f.exists() && f.canRead()) f.readText().trim() else ""
    }.getOrDefault("")

    if (kgslModel.isNotBlank()) {
      return GpuVendor.ADRENO to kgslModel
    }

    val combined = "$soc ${Build.HARDWARE} ${Build.BOARD} ${Build.MANUFACTURER}".lowercase()
    return when {
      "qcom" in combined || "qualcomm" in combined || "snapdragon" in combined || "sm" in combined ->
        GpuVendor.ADRENO to "Qualcomm Adreno"
      "mali" in combined || "mediatek" in combined || "mt" in combined || "tensor" in combined ->
        GpuVendor.MALI to "ARM Mali / Immortalis"
      "exynos" in combined || "xclipse" in combined ->
        GpuVendor.XCLIPSE to "Samsung Xclipse"
      "goldfish" in combined || "ranchu" in combined || "cuttlefish" in combined ->
        GpuVendor.UNKNOWN to "Android Emulator GPU (${Build.HARDWARE})"
      else -> GpuVendor.UNKNOWN to "System GPU (${Build.HARDWARE})"
    }
  }

  private fun detectVulkanVersion(): Int {
    return runCatching {
      val pm = context.packageManager
      val feature = pm.systemAvailableFeatures?.firstOrNull {
        it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
      }
      feature?.version ?: 0
    }.getOrDefault(0)
  }

  private fun formatVulkanVersion(version: Int): String {
    if (version <= 0) return "Unavailable (GLES Fallback)"
    val major = (version ushr 22) and 0x7F
    val minor = (version ushr 12) and 0x3FF
    val patch = version and 0xFFF
    return "$major.$minor.$patch"
  }
}
