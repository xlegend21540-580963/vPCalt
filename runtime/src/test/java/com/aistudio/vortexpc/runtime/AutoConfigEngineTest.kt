package com.aistudio.vortexpc.runtime

import com.aistudio.vortexpc.core.model.AutoConfigTier
import com.aistudio.vortexpc.core.model.DxWrapperType
import com.aistudio.vortexpc.core.model.GpuVendor
import com.aistudio.vortexpc.core.model.GraphicsDriverType
import com.aistudio.vortexpc.core.model.HardwareProfile
import com.aistudio.vortexpc.core.model.PeArchitecture
import com.aistudio.vortexpc.core.model.TranslatorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConfigEngineTest {

  private val engine = AutoConfigEngine()

  private fun sampleHardware(vendor: GpuVendor, vulkan13: Boolean = true): HardwareProfile =
    HardwareProfile(
      deviceModel = "Test Device",
      socModel = "TestSoC",
      gpuVendor = vendor,
      gpuModel = if (vendor == GpuVendor.ADRENO) "Adreno 750" else "Mali-G720",
      vulkanVersion = if (vulkan13) "1.3.275" else "1.1.0",
      supportsVulkan13 = vulkan13,
      ramTotalMb = 12288,
      cpuCores = 8,
      pageSizeBytes = 16384,
      supportedAbis = listOf("arm64-v8a"),
      hasGyroscope = true,
      recommendedDriver = GraphicsDriverType.TURNIP,
      recommendedTranslator = TranslatorEngine.BOX64,
      recommendedResolution = "1280x720",
    )

  @Test
  fun `selects Turnip and DXVK on Adreno with Vulkan 1_3`() {
    val rec = engine.buildConfig(
      hardware = sampleHardware(GpuVendor.ADRENO, vulkan13 = true),
      tier = AutoConfigTier.BALANCED,
      peArch = PeArchitecture.X64,
    )
    assertEquals(GraphicsDriverType.TURNIP, rec.containerConfig.graphicsDriver)
    assertEquals(DxWrapperType.DXVK, rec.containerConfig.dxWrapper)
    assertEquals(TranslatorEngine.BOX64, rec.containerConfig.translator)
    assertEquals("16384", rec.containerConfig.envVars["VORTEX_PAGE_SIZE"])
  }

  @Test
  fun `selects Box86 for 32-bit x86 PE binaries`() {
    val rec = engine.buildConfig(
      hardware = sampleHardware(GpuVendor.MALI, vulkan13 = false),
      tier = AutoConfigTier.BATTERY_SAVER,
      peArch = PeArchitecture.X86,
    )
    assertEquals(TranslatorEngine.BOX86, rec.containerConfig.translator)
    assertEquals(GraphicsDriverType.VIRGL, rec.containerConfig.graphicsDriver)
    assertEquals(30, rec.containerConfig.fpsLimit)
    assertTrue(rec.highlights.isNotEmpty())
  }
}
