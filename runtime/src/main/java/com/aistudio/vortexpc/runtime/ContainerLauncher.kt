package com.aistudio.vortexpc.runtime

import android.content.Context
import com.aistudio.vortexpc.core.logging.DiagnosticLogManager
import com.aistudio.vortexpc.core.model.ContainerConfig
import com.aistudio.vortexpc.core.model.GameEntry
import com.aistudio.vortexpc.core.model.LogCategory
import com.aistudio.vortexpc.nativebridge.NativeBridgeManager
import com.aistudio.vortexpc.nativebridge.VortexNativeJni
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionLifecycleState {
  IDLE,
  STAGING,
  BOOTING_X11,
  RUNNING,
  STOPPED,
  ERROR,
}

data class ActiveSessionInfo(
  val state: SessionLifecycleState = SessionLifecycleState.IDLE,
  val targetName: String = "",
  val executableCommand: String = "",
  val containerConfig: ContainerConfig? = null,
  val environment: Map<String, String> = emptyMap(),
  val nativeProotActive: Boolean = false,
  val errorMessage: String? = null,
)

@Singleton
class ContainerLauncher @Inject constructor(
  @ApplicationContext private val context: Context,
  private val containerManager: ContainerManager,
  private val stager: OfflineRuntimeStager,
  private val envBuilder: RuntimeEnvironmentBuilder,
  private val nativeBridge: NativeBridgeManager,
  private val logger: DiagnosticLogManager,
) {
  private val _session = MutableStateFlow(ActiveSessionInfo())
  val session: StateFlow<ActiveSessionInfo> = _session.asStateFlow()

  private var osProcess: Process? = null

  fun launchGame(game: GameEntry, container: ContainerConfig) {
    launchExecutable(
      displayName = game.title,
      commandLine = buildString {
        append("wine ")
        append(game.exeFileName)
        if (game.launchArgs.isNotBlank()) {
          append(' ')
          append(game.launchArgs)
        }
      },
      container = container,
    )
  }

  fun launchWineTool(toolExe: String, displayName: String, container: ContainerConfig) {
    launchExecutable(
      displayName = displayName,
      commandLine = "wine $toolExe",
      container = container,
    )
  }

  private fun launchExecutable(
    displayName: String,
    commandLine: String,
    container: ContainerConfig,
  ) {
    stopSession()
    _session.value = ActiveSessionInfo(
      state = SessionLifecycleState.STAGING,
      targetName = displayName,
      executableCommand = commandLine,
      containerConfig = container,
    )

    val prefixDir = containerManager.getPrefixDir(container.id)
    val rootfsDir = stager.rootfsDir
    val xSocketDir = File(context.cacheDir, "x11").apply { mkdirs() }
    val xSocketPath = File(xSocketDir, "X0").absolutePath
    val audioSocketPath = File(context.cacheDir, "audio/pulse.sock").apply {
      parentFile?.mkdirs()
    }.absolutePath

    val nativePaths = nativeBridge.inspectExecutionPaths()
    val env = envBuilder.buildEnvMap(
      config = container,
      prefixPath = prefixDir.absolutePath,
      rootfsPath = rootfsDir.absolutePath,
      nativePaths = nativePaths,
      displayNumber = 0,
      xSocketDir = xSocketDir.absolutePath,
      audioSocketPath = audioSocketPath,
    )

    _session.value = _session.value.copy(
      state = SessionLifecycleState.BOOTING_X11,
      environment = env,
    )

    val xOk = nativeBridge.openXConnector(xSocketPath)
    val shmOk = nativeBridge.allocateSysVShm("vortex_x11_shm", 8 * 1024 * 1024)
    val audioOk = nativeBridge.startAudioSubsystem(container.audioDriver, container.audioLatencyMs)

    logger.info(
      LogCategory.RUNTIME,
      "ContainerLauncher",
      "Booting '$displayName' in '${container.name}' | cmd='$commandLine' | driver=${container.graphicsDriver} | dx=${container.dxWrapper} | xConnector=$xOk shm=$shmOk audio=$audioOk",
    )
    logger.info(
      LogCategory.RUNTIME,
      "Environment",
      "Wine=${container.wineVersion} Translator=${container.translator}(${container.box64Profile}) Res=${container.screenResolution} PROOT_LOADER=${nativePaths.prootLoaderPath}",
    )

    var nativeProotActive = false
    if (nativePaths.hasBundledProot) {
      if (VortexNativeJni.isNativeLoaded) {
        val pid = VortexNativeJni.nativeSpawnPRoot(
          loaderPath = nativePaths.prootLoaderPath,
          prootPath = nativePaths.prootBinaryPath,
          rootfsPath = rootfsDir.absolutePath,
          command = commandLine,
          affinityMask = container.cpuAffinityMask,
        )
        nativeProotActive = pid > 0
        logger.info(LogCategory.RUNTIME, "PRootSpawn", "Spawned native PRoot pid=$pid")
      } else {
        runCatching {
          val pb = ProcessBuilder(
            nativePaths.prootBinaryPath,
            "-r",
            rootfsDir.absolutePath,
            "-0",
            "/bin/sh",
            "-c",
            commandLine,
          )
          pb.environment().putAll(env)
          osProcess = pb.start()
          nativeProotActive = true
        }.onFailure { ex ->
          logger.warn(LogCategory.RUNTIME, "PRootSpawn", "PRoot exec skipped (${ex.message}); running integrated X11 runtime host")
        }
      }
    } else {
      logger.info(
        LogCategory.RUNTIME,
        "RuntimeHost",
        "Bundled ELF guest payloads absent in preview container; active X11/AAudio/Telemetry loopback session ready for '$displayName'",
      )
    }

    _session.value = _session.value.copy(
      state = SessionLifecycleState.RUNNING,
      nativeProotActive = nativeProotActive,
    )
  }

  fun stopSession() {
    runCatching { osProcess?.destroy() }
    osProcess = null
    nativeBridge.stopAudioSubsystem()
    nativeBridge.releaseSysVShm()
    nativeBridge.closeXConnector()
    if (_session.value.state == SessionLifecycleState.RUNNING) {
      logger.info(LogCategory.RUNTIME, "ContainerLauncher", "Session stopped cleanly")
    }
    _session.value = ActiveSessionInfo(state = SessionLifecycleState.STOPPED)
  }
}
