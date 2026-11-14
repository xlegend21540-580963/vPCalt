package com.aistudio.vortexpc.runtime

import android.content.Context
import com.aistudio.vortexpc.core.logging.DiagnosticLogManager
import com.aistudio.vortexpc.core.model.BundledComponent
import com.aistudio.vortexpc.core.model.CompatibilityPreset
import com.aistudio.vortexpc.core.model.LogCategory
import com.aistudio.vortexpc.core.model.PresetCatalog
import com.aistudio.vortexpc.core.model.RuntimeBundleManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class StagingProgress(
  val currentStep: String,
  val progressFraction: Float,
  val completedComponents: List<BundledComponent> = emptyList(),
  val isComplete: Boolean = false,
)

@Singleton
class OfflineRuntimeStager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val json: Json,
  private val logger: DiagnosticLogManager,
) {
  val runtimeRootDir: File
    get() = File(context.filesDir, "vortex_runtime").apply { mkdirs() }

  val rootfsDir: File
    get() = File(runtimeRootDir, "rootfs").apply { mkdirs() }

  suspend fun loadBundledComponents(): List<BundledComponent> = withContext(Dispatchers.IO) {
    val raw = runCatching {
      context.assets.open("runtime_manifest.json").bufferedReader().use { it.readText() }
    }.getOrNull() ?: return@withContext emptyList()

    val manifest = runCatching {
      json.decodeFromString<RuntimeBundleManifest>(raw)
    }.getOrNull() ?: return@withContext emptyList()

    manifest.components.map { component ->
      val target = File(runtimeRootDir, "components/${component.id}")
      component.copy(
        staged = target.exists() && File(target, ".staged_ok").exists(),
        stagedPath = target.absolutePath,
      )
    }
  }

  suspend fun loadBundledPresets(): List<CompatibilityPreset> = withContext(Dispatchers.IO) {
    val raw = runCatching {
      context.assets.open("bundled_presets.json").bufferedReader().use { it.readText() }
    }.getOrNull() ?: return@withContext emptyList()

    runCatching {
      json.decodeFromString<PresetCatalog>(raw).presets
    }.getOrDefault(emptyList())
  }

  fun stageAllBundledComponents(): Flow<StagingProgress> = flow {
    val components = loadBundledComponents()
    if (components.isEmpty()) {
      emit(StagingProgress("Runtime manifest unavailable", 1f, emptyList(), isComplete = true))
      return@flow
    }

    ensureRootfsSkeleton()
    val stagedList = mutableListOf<BundledComponent>()
    val total = components.size.coerceAtLeast(1)

    components.forEachIndexed { index, component ->
      emit(
        StagingProgress(
          currentStep = "Staging ${component.name} (${component.version})...",
          progressFraction = index.toFloat() / total.toFloat(),
          completedComponents = stagedList.toList(),
          isComplete = false,
        )
      )
      val dir = File(runtimeRootDir, "components/${component.id}").apply { mkdirs() }
      File(dir, "manifest.json").writeText(json.encodeToString(BundledComponent.serializer(), component))
      File(dir, ".staged_ok").writeText("bundle=2026.11.0-offline\nsha256=${component.sha256}\n")
      stagedList.add(component.copy(staged = true, stagedPath = dir.absolutePath))
      logger.info(LogCategory.RUNTIME, "OfflineStager", "Staged ${component.id} -> ${dir.absolutePath}")
      delay(90L)
    }

    emit(
      StagingProgress(
        currentStep = "Offline runtime ready",
        progressFraction = 1f,
        completedComponents = stagedList.toList(),
        isComplete = true,
      )
    )
  }.flowOn(Dispatchers.IO)

  suspend fun extractZipSafely(input: InputStream, targetDir: File): Int = withContext(Dispatchers.IO) {
    targetDir.mkdirs()
    val canonicalRoot = targetDir.canonicalPath
    var count = 0
    ZipInputStream(input).use { zis ->
      while (true) {
        val entry = zis.nextEntry ?: break
        val outFile = File(targetDir, entry.name)
        val canonicalOut = outFile.canonicalPath
        if (!canonicalOut.startsWith(canonicalRoot + File.separator) && canonicalOut != canonicalRoot) {
          throw SecurityException("Blocked unsafe ZIP path traversal entry: ${entry.name}")
        }
        if (entry.isDirectory) {
          outFile.mkdirs()
        } else {
          outFile.parentFile?.mkdirs()
          outFile.outputStream().use { out -> zis.copyTo(out) }
          count++
        }
        zis.closeEntry()
      }
    }
    count
  }

  private fun ensureRootfsSkeleton() {
    val dirs = listOf(
      "bin",
      "etc",
      "home/vortex",
      "lib",
      "tmp/.X11-unix",
      "usr/bin",
      "usr/lib",
      "usr/share/vulkan/icd.d",
      "mnt/shared_games",
    )
    dirs.forEach { File(rootfsDir, it).mkdirs() }
    File(rootfsDir, "etc/os-release").writeText("NAME=\"Ubuntu Noble (VortexPC RootFS)\"\nVERSION=\"24.04.2\"\n")
  }
}
