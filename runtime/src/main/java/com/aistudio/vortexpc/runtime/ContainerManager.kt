package com.aistudio.vortexpc.runtime

import android.content.Context
import com.aistudio.vortexpc.core.data.db.VortexRepository
import com.aistudio.vortexpc.core.logging.DiagnosticLogManager
import com.aistudio.vortexpc.core.model.ContainerConfig
import com.aistudio.vortexpc.core.model.LogCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class WinetricksComponent(
  val id: String,
  val displayName: String,
  val description: String,
  val dllOverrides: Map<String, String>,
)

@Singleton
class ContainerManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val repository: VortexRepository,
  private val json: Json,
  private val logger: DiagnosticLogManager,
) {
  val containersDir: File
    get() = File(context.filesDir, "vortex_containers").apply { mkdirs() }

  val availableWinetricks: List<WinetricksComponent> = listOf(
    WinetricksComponent(
      id = "vcrun2022",
      displayName = "Visual C++ 2015-2022 Redistributable",
      description = "Configures msvcp140, vcruntime140, ucrtbase native overrides",
      dllOverrides = mapOf("msvcp140" to "native,builtin", "vcruntime140" to "native,builtin", "vcruntime140_1" to "native,builtin"),
    ),
    WinetricksComponent(
      id = "d3dx9",
      displayName = "DirectX 9 Extensions (d3dx9_43)",
      description = "Registers legacy D3DX9 shader helper DLL overrides",
      dllOverrides = mapOf("d3dx9_43" to "native,builtin", "d3dcompiler_43" to "native,builtin"),
    ),
    WinetricksComponent(
      id = "d3dx11",
      displayName = "DirectX 11 Compiler (d3dcompiler_47)",
      description = "Registers D3DCompiler 47 for modern DX11 titles",
      dllOverrides = mapOf("d3dcompiler_47" to "native,builtin"),
    ),
    WinetricksComponent(
      id = "corefonts",
      displayName = "Core TrueType System Fonts",
      description = "Configures Wine font substitution mappings for UI clarity",
      dllOverrides = emptyMap(),
    ),
    WinetricksComponent(
      id = "xact",
      displayName = "XACT / XAudio2 Runtime",
      description = "Enables XAudio2_7 and X3DAudio1_7 spatial audio overrides",
      dllOverrides = mapOf("xaudio2_7" to "native,builtin", "xapofx1_5" to "native,builtin"),
    ),
    WinetricksComponent(
      id = "dotnet48",
      displayName = "Wine Mono / .NET 4.8 Profile",
      description = "Marks .NET 4.8 compatibility registry flags for managed launchers",
      dllOverrides = mapOf("mscoree" to "native,builtin"),
    ),
  )

  fun getPrefixDir(containerId: String): File =
    File(containersDir, "$containerId/.wine").apply { mkdirs() }

  suspend fun createContainer(config: ContainerConfig): ContainerConfig = withContext(Dispatchers.IO) {
    initializeWinePrefix(config)
    repository.saveContainer(config)
    logger.info(LogCategory.RUNTIME, "ContainerManager", "Created container '${config.name}' (${config.id})")
    config
  }

  suspend fun updateContainer(config: ContainerConfig): ContainerConfig = withContext(Dispatchers.IO) {
    initializeWinePrefix(config)
    repository.saveContainer(config)
    logger.info(LogCategory.RUNTIME, "ContainerManager", "Updated container '${config.name}'")
    config
  }

  suspend fun cloneContainer(source: ContainerConfig): ContainerConfig = withContext(Dispatchers.IO) {
    val clonedId = "ctr-${UUID.randomUUID().toString().take(8)}"
    val cloned = source.copy(
      id = clonedId,
      name = "${source.name} (Copy)",
      createdAtEpochMs = System.currentTimeMillis(),
    )
    val srcDir = File(containersDir, source.id)
    val dstDir = File(containersDir, clonedId)
    if (srcDir.exists()) {
      srcDir.copyRecursively(dstDir, overwrite = true)
    }
    initializeWinePrefix(cloned)
    repository.saveContainer(cloned)
    logger.info(LogCategory.RUNTIME, "ContainerManager", "Cloned '${source.name}' -> '${cloned.name}'")
    cloned
  }

  suspend fun deleteContainer(containerId: String) = withContext(Dispatchers.IO) {
    File(containersDir, containerId).deleteRecursively()
    repository.deleteContainer(containerId)
    logger.info(LogCategory.RUNTIME, "ContainerManager", "Deleted container $containerId")
  }

  suspend fun installWinetricksComponent(
    containerId: String,
    componentId: String,
  ): ContainerConfig? = withContext(Dispatchers.IO) {
    val existing = repository.getContainer(containerId) ?: return@withContext null
    val component = availableWinetricks.firstOrNull { it.id == componentId } ?: return@withContext existing
    val prefix = getPrefixDir(containerId)
    val markersDir = File(prefix, "vortex_components").apply { mkdirs() }
    File(markersDir, "$componentId.installed").writeText("installedAt=${System.currentTimeMillis()}\n")

    val updatedList = (existing.installedComponents + componentId).distinct()
    val updated = existing.copy(installedComponents = updatedList)
    writeDllOverridesToUserReg(prefix, updatedList)
    repository.saveContainer(updated)
    logger.info(LogCategory.RUNTIME, "Winetricks", "Applied $componentId (${component.displayName}) to ${existing.name}")
    updated
  }

  suspend fun exportContainerArchive(config: ContainerConfig, output: OutputStream) =
    withContext(Dispatchers.IO) {
      val prefix = getPrefixDir(config.id)
      ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("container.json"))
        zip.write(json.encodeToString(ContainerConfig.serializer(), config).toByteArray())
        zip.closeEntry()

        listOf("system.reg", "user.reg").forEach { regName ->
          val regFile = File(prefix, regName)
          if (regFile.exists()) {
            zip.putNextEntry(ZipEntry("wine/$regName"))
            regFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
          }
        }
      }
      logger.info(LogCategory.RUNTIME, "ContainerManager", "Exported container '${config.name}'")
    }

  suspend fun importContainerArchive(input: InputStream): ContainerConfig? =
    withContext(Dispatchers.IO) {
      var parsedConfig: ContainerConfig? = null
      val importedRegs = mutableMapOf<String, ByteArray>()

      ZipInputStream(input).use { zis ->
        while (true) {
          val entry = zis.nextEntry ?: break
          when (entry.name) {
            "container.json" -> {
              val raw = zis.readBytes().decodeToString()
              parsedConfig = json.decodeFromString(ContainerConfig.serializer(), raw)
            }
            "wine/system.reg", "wine/user.reg" -> {
              importedRegs[entry.name.substringAfterLast('/')] = zis.readBytes()
            }
          }
          zis.closeEntry()
        }
      }

      val base = parsedConfig ?: return@withContext null
      val newId = "ctr-${UUID.randomUUID().toString().take(8)}"
      val imported = base.copy(
        id = newId,
        name = "${base.name} (Imported)",
        createdAtEpochMs = System.currentTimeMillis(),
      )
      val prefix = getPrefixDir(newId)
      initializeWinePrefix(imported)
      importedRegs.forEach { (name, bytes) ->
        File(prefix, name).writeBytes(bytes)
      }
      repository.saveContainer(imported)
      logger.info(LogCategory.RUNTIME, "ContainerManager", "Imported container '${imported.name}'")
      imported
    }

  private fun initializeWinePrefix(config: ContainerConfig) {
    val prefix = getPrefixDir(config.id)
    val cDrive = File(prefix, "drive_c")
    listOf(
      "windows/system32",
      "windows/syswow64",
      "windows/Fonts",
      "Program Files",
      "Program Files (x86)",
      "users/vortex/Desktop",
      "users/vortex/Documents",
      "users/vortex/AppData/Roaming",
    ).forEach { File(cDrive, it).mkdirs() }

    val dosDevices = File(prefix, "dosdevices").apply { mkdirs() }
    File(dosDevices, "c_mapping.info").writeText("../drive_c\n")

    val systemReg = File(prefix, "system.reg")
    if (!systemReg.exists()) {
      systemReg.writeText(
        """
        WINE REGISTRY Version 2
        ;; All keys relative to \\Machine
        [Software\\Microsoft\\Windows NT\\CurrentVersion]
        "ProductName"="Microsoft Windows 10"
        "CurrentBuild"="19045"
        "CurrentVersion"="10.0"
        """.trimIndent() + "\n"
      )
    }

    writeDllOverridesToUserReg(prefix, config.installedComponents)
  }

  private fun writeDllOverridesToUserReg(prefix: File, installedIds: List<String>) {
    val overrides = mutableMapOf<String, String>()
    installedIds.forEach { id ->
      availableWinetricks.firstOrNull { it.id == id }?.dllOverrides?.let { overrides.putAll(it) }
    }
    val regLines = buildString {
      appendLine("WINE REGISTRY Version 2")
      appendLine(";; All keys relative to \\\\User\\\\vortex")
      appendLine("[Software\\\\Wine\\\\DllOverrides]")
      overrides.forEach { (dll, mode) ->
        appendLine("\"$dll\"=\"$mode\"")
      }
    }
    File(prefix, "user.reg").writeText(regLines)
  }
}
