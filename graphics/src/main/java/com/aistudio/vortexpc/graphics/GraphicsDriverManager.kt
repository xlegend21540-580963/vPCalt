package com.aistudio.vortexpc.graphics

import android.content.Context
import android.net.Uri
import com.aistudio.vortexpc.core.data.db.VortexRepository
import com.aistudio.vortexpc.core.logging.DiagnosticLogManager
import com.aistudio.vortexpc.core.model.CustomDriverPackage
import com.aistudio.vortexpc.core.model.GraphicsDriverType
import com.aistudio.vortexpc.core.model.LogCategory
import com.aistudio.vortexpc.runtime.OfflineRuntimeStager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class AdrenoToolsMeta(
  val name: String = "Custom Turnip Driver",
  val driverVersion: String = "1.0",
  val author: String = "Community",
  val libraryName: String = "libvulkan_freedreno.so",
)

@Singleton
class GraphicsDriverManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val repository: VortexRepository,
  private val stager: OfflineRuntimeStager,
  private val json: Json,
  private val logger: DiagnosticLogManager,
) {
  val customDriversDir: File
    get() = File(context.filesDir, "vortex_drivers").apply { mkdirs() }

  suspend fun importDriverZip(uri: Uri): Result<CustomDriverPackage> = withContext(Dispatchers.IO) {
    runCatching {
      val id = "drv-${UUID.randomUUID().toString().take(8)}"
      val destDir = File(customDriversDir, id)
      context.contentResolver.openInputStream(uri)?.use { stream ->
        stager.extractZipSafely(stream, destDir)
      } ?: error("Unable to open driver archive")

      val metaFile = File(destDir, "meta.json")
      val meta = if (metaFile.exists()) {
        runCatching { json.decodeFromString<AdrenoToolsMeta>(metaFile.readText()) }
          .getOrDefault(AdrenoToolsMeta())
      } else {
        AdrenoToolsMeta(name = "Imported Vulkan Driver ($id)")
      }

      val icdFile = destDir.walkTopDown().firstOrNull { it.extension == "json" && it.name != "meta.json" }
      val soFile = destDir.walkTopDown().firstOrNull { it.extension == "so" }

      val pkg = CustomDriverPackage(
        id = id,
        name = meta.name,
        version = meta.driverVersion,
        author = meta.author,
        driverType = GraphicsDriverType.TURNIP,
        icdJsonPath = icdFile?.absolutePath ?: File(destDir, "freedreno_icd.json").absolutePath,
        libraryPath = soFile?.absolutePath ?: File(destDir, meta.libraryName).absolutePath,
      )
      repository.saveCustomDriver(pkg)
      logger.info(LogCategory.GRAPHICS, "DriverManager", "Imported custom driver '${pkg.name}' v${pkg.version}")
      pkg
    }
  }

  suspend fun removeCustomDriver(driverId: String) = withContext(Dispatchers.IO) {
    File(customDriversDir, driverId).deleteRecursively()
    repository.deleteCustomDriver(driverId)
    logger.info(LogCategory.GRAPHICS, "DriverManager", "Removed custom driver $driverId")
  }
}
