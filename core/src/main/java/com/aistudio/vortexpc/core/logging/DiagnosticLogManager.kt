package com.aistudio.vortexpc.core.logging

import android.content.Context
import com.aistudio.vortexpc.core.model.DiagnosticLogEntry
import com.aistudio.vortexpc.core.model.HardwareProfile
import com.aistudio.vortexpc.core.model.LogCategory
import com.aistudio.vortexpc.core.model.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class DiagnosticLogManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val maxEntries = 500
  private val lock = Any()
  private val buffer = ArrayDeque<DiagnosticLogEntry>(maxEntries)
  private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
  val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

  private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

  fun log(
    level: LogLevel,
    category: LogCategory,
    tag: String,
    message: String,
  ) {
    val entry = DiagnosticLogEntry(
      timestampMs = System.currentTimeMillis(),
      level = level,
      category = category,
      tag = tag,
      message = message,
    )
    synchronized(lock) {
      if (buffer.size >= maxEntries) {
        buffer.removeFirst()
      }
      buffer.addLast(entry)
      _logs.value = buffer.toList()
    }
  }

  fun info(category: LogCategory, tag: String, message: String) =
    log(LogLevel.INFO, category, tag, message)

  fun warn(category: LogCategory, tag: String, message: String) =
    log(LogLevel.WARN, category, tag, message)

  fun error(category: LogCategory, tag: String, message: String) =
    log(LogLevel.ERROR, category, tag, message)

  fun clear() {
    synchronized(lock) {
      buffer.clear()
      _logs.value = emptyList()
    }
  }

  fun formatAllAsText(): String {
    val snapshot = synchronized(lock) { buffer.toList() }
    return snapshot.joinToString("\n") { entry ->
      val ts = timeFormat.format(Date(entry.timestampMs))
      "[$ts] [${entry.level.name}] [${entry.category.name}/${entry.tag}] ${entry.message}"
    }
  }

  suspend fun exportDiagnosticZip(
    hardwareProfile: HardwareProfile?,
    activeContainerSummary: String = "No active container",
  ): File = withContext(Dispatchers.IO) {
    val logDir = File(context.cacheDir, "logs").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val outZip = File(logDir, "vortexpc_diagnostics_$stamp.zip")

    ZipOutputStream(FileOutputStream(outZip)).use { zip ->
      zip.putNextEntry(ZipEntry("session_logs.txt"))
      zip.write(formatAllAsText().ifBlank { "No log entries recorded yet.\n" }.toByteArray())
      zip.closeEntry()

      zip.putNextEntry(ZipEntry("hardware_profile.txt"))
      val hwText = buildString {
        appendLine("VortexPC Diagnostic Snapshot")
        appendLine("Timestamp: ${Date()}")
        if (hardwareProfile != null) {
          appendLine("Device: ${hardwareProfile.deviceModel}")
          appendLine("SoC: ${hardwareProfile.socModel}")
          appendLine("GPU: ${hardwareProfile.gpuVendor} - ${hardwareProfile.gpuModel}")
          appendLine("Vulkan: ${hardwareProfile.vulkanVersion}")
          appendLine("RAM: ${hardwareProfile.ramTotalMb} MB")
          appendLine("CPU Cores: ${hardwareProfile.cpuCores}")
          appendLine("OS Page Size: ${hardwareProfile.pageSizeBytes} bytes")
          appendLine("ABIs: ${hardwareProfile.supportedAbis.joinToString()}")
        }
        appendLine()
        appendLine("Active Container:")
        appendLine(activeContainerSummary)
      }
      zip.write(hwText.toByteArray())
      zip.closeEntry()
    }
    outZip
  }
}
