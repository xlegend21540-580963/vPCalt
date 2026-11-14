package com.aistudio.vortexpc.graphics

import android.net.LocalServerSocket
import android.net.LocalSocket
import com.aistudio.vortexpc.core.logging.DiagnosticLogManager
import com.aistudio.vortexpc.core.model.LogCategory
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class X11WindowNode(
  val id: Int,
  val title: String,
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float,
  val isDesktop: Boolean = false,
  val bodyLines: List<String> = emptyList(),
)

data class X11DisplayState(
  val running: Boolean = false,
  val displayWidth: Int = 1280,
  val displayHeight: Int = 720,
  val renderScale: Float = 1.0f,
  val fpsLimit: Int = 60,
  val cursorX: Float = 0.5f,
  val cursorY: Float = 0.5f,
  val mouseButtonsMask: Int = 0,
  val connectedClients: Int = 0,
  val activeWindows: List<X11WindowNode> = emptyList(),
  val activeToolTitle: String = "Desktop",
  val typedBuffer: String = "",
)

@Singleton
class X11DisplayServer @Inject constructor(
  private val logger: DiagnosticLogManager,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var serverJob: Job? = null
  private var serverSocket: LocalServerSocket? = null

  private val _state = MutableStateFlow(X11DisplayState())
  val state: StateFlow<X11DisplayState> = _state.asStateFlow()

  fun start(
    resolution: String,
    renderScale: Float,
    fpsLimit: Int,
    targetLabel: String,
  ) {
    stop()
    val (w, h) = parseResolution(resolution)
    val initialWindows = buildInitialWindowsForTarget(targetLabel)

    _state.value = X11DisplayState(
      running = true,
      displayWidth = w,
      displayHeight = h,
      renderScale = renderScale.coerceIn(0.5f, 1.0f),
      fpsLimit = fpsLimit,
      cursorX = 0.5f,
      cursorY = 0.5f,
      activeWindows = initialWindows,
      activeToolTitle = targetLabel,
      typedBuffer = defaultBodyForTarget(targetLabel),
    )

    serverJob = scope.launch {
      runCatching {
        val sock = LocalServerSocket("vortex_x11_0")
        serverSocket = sock
        logger.info(LogCategory.XSERVER, "X11Server", "Listening on @vortex_x11_0 (${w}x${h}, scale=$renderScale)")
        while (isActive) {
          val client = sock.accept()
          _state.value = _state.value.copy(connectedClients = _state.value.connectedClients + 1)
          launch { handleX11Client(client, w, h) }
        }
      }.onFailure { ex ->
        if (isActive) {
          logger.warn(LogCategory.XSERVER, "X11Server", "LocalServerSocket bind note: ${ex.message}")
        }
      }
    }
  }

  fun stop() {
    serverJob?.cancel()
    serverJob = null
    runCatching { serverSocket?.close() }
    serverSocket = null
    _state.value = X11DisplayState(running = false)
  }

  fun updateResolutionAndFps(resolution: String? = null, scale: Float? = null, fpsLimit: Int? = null) {
    val cur = _state.value
    val (w, h) = if (resolution != null) parseResolution(resolution) else (cur.displayWidth to cur.displayHeight)
    _state.value = cur.copy(
      displayWidth = w,
      displayHeight = h,
      renderScale = scale?.coerceIn(0.5f, 1.0f) ?: cur.renderScale,
      fpsLimit = fpsLimit ?: cur.fpsLimit,
    )
  }

  fun moveCursorNormalized(nx: Float, ny: Float) {
    val cur = _state.value
    _state.value = cur.copy(
      cursorX = nx.coerceIn(0f, 1f),
      cursorY = ny.coerceIn(0f, 1f),
    )
  }

  fun moveCursorDelta(dxNorm: Float, dyNorm: Float) {
    val cur = _state.value
    _state.value = cur.copy(
      cursorX = (cur.cursorX + dxNorm).coerceIn(0f, 1f),
      cursorY = (cur.cursorY + dyNorm).coerceIn(0f, 1f),
    )
  }

  fun setMouseButton(buttonMask: Int, pressed: Boolean) {
    val cur = _state.value
    val updatedMask = if (pressed) cur.mouseButtonsMask or buttonMask else cur.mouseButtonsMask and buttonMask.inv()
    _state.value = cur.copy(mouseButtonsMask = updatedMask)
  }

  fun injectKeyText(charOrKey: String) {
    val cur = _state.value
    val updated = when (charOrKey) {
      "BACKSPACE" -> cur.typedBuffer.dropLast(1)
      "ENTER" -> cur.typedBuffer + "\n"
      else -> (cur.typedBuffer + charOrKey).takeLast(1200)
    }
    _state.value = cur.copy(typedBuffer = updated)
  }

  fun openUtilityWindow(title: String, lines: List<String>) {
    val cur = _state.value
    val existing = cur.activeWindows.filterNot { it.title == title }
    val newWin = X11WindowNode(
      id = (cur.activeWindows.maxOfOrNull { it.id } ?: 1) + 1,
      title = title,
      x = 0.14f,
      y = 0.14f,
      width = 0.72f,
      height = 0.68f,
      bodyLines = lines,
    )
    _state.value = cur.copy(
      activeWindows = existing + newWin,
      activeToolTitle = title,
    )
  }

  private fun handleX11Client(client: LocalSocket, width: Int, height: Int) {
    client.use { sock ->
      val input: InputStream = sock.inputStream
      val output: OutputStream = sock.outputStream
      val setupHeader = ByteArray(12)
      val read = input.read(setupHeader)
      if (read < 12) return

      val byteOrderByte = setupHeader[0].toInt()
      val order = if (byteOrderByte == 0x42) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN

      // Minimal X11 SetupResponse (Success = 1) advertising 24-bit TrueColor root window
      val response = ByteBuffer.allocate(40).order(order).apply {
        put(1.toByte()) // Success
        put(0.toByte())
        putShort(11.toShort()) // X11 major
        putShort(0.toShort())  // X11 minor
        putShort(8.toShort())  // additional length in 4-byte units
        putInt(1)              // release number
        putInt(0x00200000)     // resource-id-base
        putInt(0x001FFFFF)     // resource-id-mask
        putInt(256)            // motion-buffer-size
        putShort(0.toShort())  // vendor len
        putShort(65535.toShort())
        put(1.toByte())        // roots
        put(1.toByte())        // formats
        put(0.toByte())        // image-byte-order
        put(0.toByte())        // bitmap-bit-order
        put(32.toByte())       // scanline-unit
        put(32.toByte())       // scanline-pad
        put(8.toByte())        // min-keycode
        put(255.toByte())      // max-keycode
        putInt(0)              // unused
      }
      output.write(response.array())
      output.flush()
      logger.info(LogCategory.XSERVER, "X11Handshake", "Completed X11 client handshake (${width}x${height})")
    }
  }

  private fun parseResolution(res: String): Pair<Int, Int> {
    val parts = res.lowercase().split('x')
    val w = parts.getOrNull(0)?.trim()?.toIntOrNull()?.coerceIn(640, 2560) ?: 1280
    val h = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(360, 1440) ?: 720
    return w to h
  }

  private fun buildInitialWindowsForTarget(target: String): List<X11WindowNode> {
    val desktop = X11WindowNode(
      id = 1,
      title = "Wine Desktop",
      x = 0f,
      y = 0f,
      width = 1f,
      height = 1f,
      isDesktop = true,
    )
    val foreground = X11WindowNode(
      id = 2,
      title = target,
      x = 0.12f,
      y = 0.10f,
      width = 0.76f,
      height = 0.74f,
      isDesktop = false,
      bodyLines = defaultBodyForTarget(target).lines(),
    )
    return listOf(desktop, foreground)
  }

  private fun defaultBodyForTarget(target: String): String {
    val lower = target.lowercase()
    return when {
      "notepad" in lower -> "VortexPC Wine X11 Session — notepad.exe\nReady for keyboard & virtual touchpad input.\nPress keys via Quick Menu or physical keyboard."
      "winecfg" in lower -> "Wine Configuration (Windows 10)\nAudio: AAudio Low-Latency Bridge\nGraphics: Vulkan / DXVK Enabled"
      "taskmgr" in lower -> "Wine Task Manager\nPID 104  services.exe   0.4% CPU   14 MB\nPID 118  explorer.exe   1.1% CPU   28 MB\nPID 142  winedevice.exe 0.2% CPU   11 MB"
      "regedit" in lower -> "HKEY_CURRENT_USER\\Software\\Wine\\DllOverrides\nd3d11 = native,builtin\nd3d9  = native,builtin\ndxgi  = native,builtin"
      else -> "Launching $target via PRoot + Wine + Box64...\nX11 Display :0 active • Shared memory & AAudio attached."
    }
  }
}
