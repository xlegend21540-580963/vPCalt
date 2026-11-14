package com.aistudio.vortexpc.core.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.aistudio.vortexpc.core.model.PeArchitecture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DiscoveredExecutable(
  val title: String,
  val fileName: String,
  val uriString: String,
  val parentFolderName: String,
  val peArchitecture: PeArchitecture,
  val sizeBytes: Long,
)

@Singleton
class SafGameScanner @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val ignoredExecutables = setOf(
    "unins000.exe",
    "unins001.exe",
    "uninstall.exe",
    "crashreporter.exe",
    "dxsetup.exe",
    "vcredist_x64.exe",
    "vcredist_x86.exe",
    "vc_redist.x64.exe",
    "vc_redist.x86.exe",
    "UE4PrereqSetup_x64.exe".lowercase(),
  )

  suspend fun scanTree(treeUri: Uri, maxDepth: Int = 3): List<DiscoveredExecutable> =
    withContext(Dispatchers.IO) {
      val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
      val results = mutableListOf<DiscoveredExecutable>()
      walkDirectory(root, root.name ?: "Games", 0, maxDepth, results)
      results.sortedBy { it.title.lowercase() }
    }

  suspend fun inspectSingleFile(fileUri: Uri): DiscoveredExecutable? =
    withContext(Dispatchers.IO) {
      val doc = DocumentFile.fromSingleUri(context, fileUri) ?: return@withContext null
      val name = doc.name ?: return@withContext null
      if (!name.endsWith(".exe", ignoreCase = true)) return@withContext null
      val arch = detectPeArch(doc.uri)
      DiscoveredExecutable(
        title = cleanTitle(name, "Custom App"),
        fileName = name,
        uriString = doc.uri.toString(),
        parentFolderName = "Imported",
        peArchitecture = arch,
        sizeBytes = doc.length(),
      )
    }

  private fun walkDirectory(
    dir: DocumentFile,
    folderName: String,
    depth: Int,
    maxDepth: Int,
    out: MutableList<DiscoveredExecutable>,
  ) {
    if (depth > maxDepth || !dir.isDirectory) return
    val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
    for (child in children) {
      val name = child.name ?: continue
      if (child.isDirectory) {
        if (!name.startsWith(".") && name.lowercase() !in setOf("windows", "system32", "syswow64")) {
          walkDirectory(child, name, depth + 1, maxDepth, out)
        }
      } else if (child.isFile && name.endsWith(".exe", ignoreCase = true)) {
        if (name.lowercase() in ignoredExecutables) continue
        val arch = detectPeArch(child.uri)
        out.add(
          DiscoveredExecutable(
            title = cleanTitle(name, folderName),
            fileName = name,
            uriString = child.uri.toString(),
            parentFolderName = folderName,
            peArchitecture = arch,
            sizeBytes = child.length(),
          )
        )
      }
    }
  }

  private fun detectPeArch(uri: Uri): PeArchitecture {
    return runCatching {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        PeHeaderParser.detectArchitecture(stream)
      } ?: PeArchitecture.UNKNOWN
    }.getOrDefault(PeArchitecture.UNKNOWN)
  }

  private fun cleanTitle(fileName: String, parentFolder: String): String {
    val base = fileName.substringBeforeLast('.')
      .replace('_', ' ')
      .replace('-', ' ')
      .trim()
    val genericNames = setOf("game", "launcher", "start", "play", "client", "bin", "win64", "win32")
    return if (base.lowercase() in genericNames && parentFolder.isNotBlank()) {
      parentFolder.replace('_', ' ').replace('-', ' ').trim()
    } else {
      base.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
  }
}
