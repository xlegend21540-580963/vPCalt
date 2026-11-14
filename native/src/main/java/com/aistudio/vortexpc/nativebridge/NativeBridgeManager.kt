package com.aistudio.vortexpc.nativebridge

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SharedMemory
import com.aistudio.vortexpc.core.logging.DiagnosticLogManager
import com.aistudio.vortexpc.core.model.AudioDriverType
import com.aistudio.vortexpc.core.model.LogCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class NativeExecutionPaths(
  val nativeLibDir: String,
  val prootBinaryPath: String,
  val prootLoaderPath: String,
  val hasBundledProot: Boolean,
  val jniBridgeActive: Boolean,
)

@Singleton
class NativeBridgeManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val logger: DiagnosticLogManager,
) {
  private var fallbackAudioTrack: AudioTrack? = null
  private var xConnectorFd: Int = -1
  private var shmFd: Int = -1
  private var androidSharedMemory: SharedMemory? = null

  fun inspectExecutionPaths(): NativeExecutionPaths {
    val libDir = context.applicationInfo.nativeLibraryDir ?: ""
    val prootFile = File(libDir, "libproot.so")
    val loaderFile = File(libDir, "libproot_loader.so")
    return NativeExecutionPaths(
      nativeLibDir = libDir,
      prootBinaryPath = prootFile.absolutePath,
      prootLoaderPath = loaderFile.absolutePath,
      hasBundledProot = prootFile.exists() && loaderFile.exists(),
      jniBridgeActive = VortexNativeJni.isNativeLoaded,
    )
  }

  fun startAudioSubsystem(driver: AudioDriverType, latencyMs: Int): Boolean {
    stopAudioSubsystem()
    val sampleRate = 48000
    val burstFrames = ((sampleRate * latencyMs.coerceIn(8, 80)) / 1000).coerceAtLeast(128)

    if (VortexNativeJni.isNativeLoaded && driver == AudioDriverType.AAUDIO) {
      val rc = VortexNativeJni.nativeStartAudioBridge(sampleRate, 2, burstFrames)
      if (rc == 0) {
        logger.info(LogCategory.AUDIO, "AAudioBridge", "Started native AAudio stream ($sampleRate Hz, burst=$burstFrames)")
        return true
      }
      logger.warn(LogCategory.AUDIO, "AAudioBridge", "Native AAudio returned $rc, falling back to AudioTrack")
    }

    return runCatching {
      val minBuf = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT,
      ).coerceAtLeast(burstFrames * 4)

      val track = AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        )
        .setBufferSizeInBytes(minBuf)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        .build()

      track.play()
      fallbackAudioTrack = track
      logger.info(LogCategory.AUDIO, "AudioBridge", "Started ${driver.name} low-latency AudioTrack (${minBuf}B)")
      true
    }.getOrElse { ex ->
      logger.error(LogCategory.AUDIO, "AudioBridge", "Audio init failed: ${ex.message}")
      false
    }
  }

  fun stopAudioSubsystem() {
    if (VortexNativeJni.isNativeLoaded) {
      VortexNativeJni.nativeStopAudioBridge()
    }
    runCatching {
      fallbackAudioTrack?.stop()
      fallbackAudioTrack?.release()
    }
    fallbackAudioTrack = null
  }

  fun openXConnector(socketPath: String): Boolean {
    closeXConnector()
    return if (VortexNativeJni.isNativeLoaded) {
      xConnectorFd = VortexNativeJni.nativeOpenXConnector(socketPath)
      val ok = xConnectorFd >= 0
      logger.info(LogCategory.XSERVER, "XConnector", "Native UNIX socket $socketPath fd=$xConnectorFd")
      ok
    } else {
      logger.info(LogCategory.XSERVER, "XConnector", "Using Kotlin LocalServerSocket X11 transport for $socketPath")
      true
    }
  }

  fun closeXConnector() {
    if (VortexNativeJni.isNativeLoaded && xConnectorFd >= 0) {
      VortexNativeJni.nativeCloseXConnector(xConnectorFd)
      xConnectorFd = -1
    }
  }

  fun allocateSysVShm(name: String, sizeBytes: Int): Boolean {
    releaseSysVShm()
    return if (VortexNativeJni.isNativeLoaded) {
      shmFd = VortexNativeJni.nativeCreateSysVShm(name, sizeBytes)
      shmFd >= 0
    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
      runCatching {
        androidSharedMemory = SharedMemory.create(name, sizeBytes)
        true
      }.getOrDefault(false)
    } else {
      false
    }
  }

  fun releaseSysVShm() {
    if (VortexNativeJni.isNativeLoaded && shmFd >= 0) {
      VortexNativeJni.nativeCloseSysVShm(shmFd)
      shmFd = -1
    }
    runCatching { androidSharedMemory?.close() }
    androidSharedMemory = null
  }
}
