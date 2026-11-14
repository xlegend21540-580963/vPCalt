package com.aistudio.vortexpc.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.vortexpc.core.data.db.VortexRepository
import com.aistudio.vortexpc.core.data.prefs.SettingsDataStore
import com.aistudio.vortexpc.core.data.prefs.UserPreferences
import com.aistudio.vortexpc.core.hardware.HardwareDetector
import com.aistudio.vortexpc.core.logging.DiagnosticLogManager
import com.aistudio.vortexpc.core.model.AutoConfigTier
import com.aistudio.vortexpc.core.model.BundledComponent
import com.aistudio.vortexpc.core.model.CompatibilityPreset
import com.aistudio.vortexpc.core.model.ContainerConfig
import com.aistudio.vortexpc.core.model.CustomDriverPackage
import com.aistudio.vortexpc.core.model.DiagnosticLogEntry
import com.aistudio.vortexpc.core.model.GameEntry
import com.aistudio.vortexpc.core.model.HardwareProfile
import com.aistudio.vortexpc.core.model.LogCategory
import com.aistudio.vortexpc.core.model.MouseMode
import com.aistudio.vortexpc.core.model.PeArchitecture
import com.aistudio.vortexpc.core.model.PlaySessionRecord
import com.aistudio.vortexpc.core.model.TelemetrySnapshot
import com.aistudio.vortexpc.core.model.ThemeMode
import com.aistudio.vortexpc.core.scanner.SafGameScanner
import com.aistudio.vortexpc.graphics.GraphicsDriverManager
import com.aistudio.vortexpc.graphics.PerformanceTelemetryCollector
import com.aistudio.vortexpc.graphics.X11DisplayServer
import com.aistudio.vortexpc.graphics.X11DisplayState
import com.aistudio.vortexpc.input.ControlElement
import com.aistudio.vortexpc.input.ControlElementType
import com.aistudio.vortexpc.input.InputDispatcher
import com.aistudio.vortexpc.input.InputProfile
import com.aistudio.vortexpc.input.InputProfileRepository
import com.aistudio.vortexpc.input.LiveInputDebugState
import com.aistudio.vortexpc.runtime.ActiveSessionInfo
import com.aistudio.vortexpc.runtime.AutoConfigEngine
import com.aistudio.vortexpc.runtime.ContainerLauncher
import com.aistudio.vortexpc.runtime.ContainerManager
import com.aistudio.vortexpc.runtime.OfflineRuntimeStager
import com.aistudio.vortexpc.runtime.StagingProgress
import com.aistudio.vortexpc.runtime.WinetricksComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibrarySortMode {
  RECENT,
  PLAYTIME,
  TITLE,
}

@HiltViewModel
class VortexMainViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val repository: VortexRepository,
  private val settingsDataStore: SettingsDataStore,
  private val hardwareDetector: HardwareDetector,
  private val autoConfigEngine: AutoConfigEngine,
  private val stager: OfflineRuntimeStager,
  private val containerManager: ContainerManager,
  private val safScanner: SafGameScanner,
  private val driverManager: GraphicsDriverManager,
  private val inputProfileRepo: InputProfileRepository,
  private val launcher: ContainerLauncher,
  val x11Server: X11DisplayServer,
  val inputDispatcher: InputDispatcher,
  private val telemetryCollector: PerformanceTelemetryCollector,
  private val logger: DiagnosticLogManager,
) : ViewModel() {

  private val _preferencesLoaded = MutableStateFlow(false)
  val preferencesLoaded: StateFlow<Boolean> = _preferencesLoaded.asStateFlow()

  val preferences: StateFlow<UserPreferences> = settingsDataStore.preferences
    .onEach { _preferencesLoaded.value = true }
    .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

  val containers: StateFlow<List<ContainerConfig>> = repository.containers
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val games: StateFlow<List<GameEntry>> = repository.games
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val customDrivers: StateFlow<List<CustomDriverPackage>> = repository.customDrivers
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val inputProfiles: StateFlow<List<InputProfile>> = inputProfileRepo.profiles
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InputProfile.presets())

  val recentSessions: StateFlow<List<PlaySessionRecord>> = repository.recentSessions
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val sessionInfo: StateFlow<ActiveSessionInfo> = launcher.session
  val x11State: StateFlow<X11DisplayState> = x11Server.state
  val telemetry: StateFlow<TelemetrySnapshot> = telemetryCollector.snapshot
  val logs: StateFlow<List<DiagnosticLogEntry>> = logger.logs
  val inputDebug: StateFlow<LiveInputDebugState> = inputDispatcher.debugState

  private val _hardwareProfile = MutableStateFlow<HardwareProfile?>(null)
  val hardwareProfile: StateFlow<HardwareProfile?> = _hardwareProfile.asStateFlow()

  private val _bundledComponents = MutableStateFlow<List<BundledComponent>>(emptyList())
  val bundledComponents: StateFlow<List<BundledComponent>> = _bundledComponents.asStateFlow()

  private val _bundledPresets = MutableStateFlow<List<CompatibilityPreset>>(emptyList())
  val bundledPresets: StateFlow<List<CompatibilityPreset>> = _bundledPresets.asStateFlow()

  private val _stagingProgress = MutableStateFlow<StagingProgress?>(null)
  val stagingProgress: StateFlow<StagingProgress?> = _stagingProgress.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _activeGameSession = MutableStateFlow<GameEntry?>(null)
  private var sessionStartedAtMs: Long = 0L

  val availableWinetricks: List<WinetricksComponent> = containerManager.availableWinetricks

  init {
    viewModelScope.launch(Dispatchers.IO) {
      val hw = hardwareDetector.detect()
      _hardwareProfile.value = hw
      _bundledComponents.value = stager.loadBundledComponents()
      _bundledPresets.value = stager.loadBundledPresets()
      inputProfileRepo.ensureDefaultsSeeded()
      logger.info(
        LogCategory.SYSTEM,
        "HardwareProbe",
        "Detected ${hw.deviceModel} | SoC=${hw.socModel} | GPU=${hw.gpuModel} | Vulkan=${hw.vulkanVersion} | Page=${hw.pageSizeBytes}B",
      )
    }
  }

  fun clearBanner() {
    _statusBanner.value = null
  }

  fun completeOnboarding(selectedTier: AutoConfigTier) {
    viewModelScope.launch {
      settingsDataStore.setDefaultAutoTier(selectedTier)
      stager.stageAllBundledComponents().collect { progress ->
        _stagingProgress.value = progress
        if (progress.isComplete) {
          _bundledComponents.value = progress.completedComponents
          val hw = _hardwareProfile.value ?: hardwareDetector.detect()
          val existing = containers.value
          if (existing.isEmpty()) {
            val rec = autoConfigEngine.buildConfig(
              hardware = hw,
              tier = selectedTier,
              peArch = PeArchitecture.X64,
              containerName = "Primary Console Container",
            )
            containerManager.createContainer(rec.containerConfig)
          }
          settingsDataStore.setOnboardingCompleted(true, "2026.11.0-offline")
        }
      }
    }
  }

  fun rerunOnboarding() {
    viewModelScope.launch {
      _stagingProgress.value = null
      settingsDataStore.setOnboardingCompleted(false)
    }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
  }

  fun setShowHud(enabled: Boolean) {
    viewModelScope.launch { settingsDataStore.setShowHudOverlay(enabled) }
  }

  fun setHapticsEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsDataStore.setHapticsEnabled(enabled) }
  }

  fun scanSafFolder(treeUri: Uri) {
    viewModelScope.launch {
      settingsDataStore.addSafTreeUri(treeUri.toString())
      val found = safScanner.scanTree(treeUri)
      val hw = _hardwareProfile.value ?: hardwareDetector.detect()
      val defaultContainer = ensureDefaultContainer(hw)
      var added = 0
      found.forEach { exe ->
        val rec = autoConfigEngine.buildConfig(
          hardware = hw,
          tier = preferences.value.defaultAutoTier,
          peArch = exe.peArchitecture,
          containerName = defaultContainer.name,
          existingId = defaultContainer.id,
        )
        val entry = GameEntry(
          id = "game-${UUID.nameUUIDFromBytes(exe.uriString.toByteArray()).toString().take(8)}",
          title = exe.title,
          exeUri = exe.uriString,
          exeFileName = exe.fileName,
          peArchitecture = exe.peArchitecture,
          workingDir = exe.parentFolderName,
          containerId = defaultContainer.id,
          autoConfigTier = preferences.value.defaultAutoTier,
          autoConfigReason = rec.summaryReason,
        )
        repository.saveGame(entry)
        added++
      }
      _statusBanner.value = if (added > 0) {
        "Imported $added Windows executable(s) with Auto Config"
      } else {
        "No .exe files discovered in selected folder"
      }
    }
  }

  fun importSingleExecutable(fileUri: Uri) {
    viewModelScope.launch {
      val exe = safScanner.inspectSingleFile(fileUri)
      if (exe == null) {
        _statusBanner.value = "Selected file is not a valid Windows .exe"
        return@launch
      }
      val hw = _hardwareProfile.value ?: hardwareDetector.detect()
      val container = ensureDefaultContainer(hw)
      val rec = autoConfigEngine.buildConfig(
        hardware = hw,
        tier = preferences.value.defaultAutoTier,
        peArch = exe.peArchitecture,
        containerName = container.name,
        existingId = container.id,
      )
      val entry = GameEntry(
        id = "game-${UUID.nameUUIDFromBytes(exe.uriString.toByteArray()).toString().take(8)}",
        title = exe.title,
        exeUri = exe.uriString,
        exeFileName = exe.fileName,
        peArchitecture = exe.peArchitecture,
        workingDir = exe.parentFolderName,
        containerId = container.id,
        autoConfigTier = preferences.value.defaultAutoTier,
        autoConfigReason = rec.summaryReason,
      )
      repository.saveGame(entry)
      _statusBanner.value = "Added ${entry.title} (${entry.peArchitecture.name})"
    }
  }

  fun applyAutoConfigTier(game: GameEntry, tier: AutoConfigTier) {
    viewModelScope.launch {
      val hw = _hardwareProfile.value ?: hardwareDetector.detect()
      val container = repository.getContainer(game.containerId) ?: ensureDefaultContainer(hw)
      val rec = autoConfigEngine.buildConfig(
        hardware = hw,
        tier = tier,
        peArch = game.peArchitecture,
        containerName = container.name,
        existingId = container.id,
      )
      containerManager.updateContainer(rec.containerConfig)
      repository.saveGame(
        game.copy(
          autoConfigTier = tier,
          autoConfigReason = rec.summaryReason,
          useAdvancedOverride = false,
        )
      )
      _statusBanner.value = "Auto-Tuned '${game.title}' for ${tier.name.replace('_', ' ')}"
    }
  }

  fun updateGame(game: GameEntry) {
    viewModelScope.launch { repository.saveGame(game) }
  }

  fun deleteGame(gameId: String) {
    viewModelScope.launch {
      repository.deleteGame(gameId)
      _statusBanner.value = "Removed title from library"
    }
  }

  fun toggleFavorite(game: GameEntry) {
    viewModelScope.launch { repository.saveGame(game.copy(favorite = !game.favorite)) }
  }

  fun pinGameShortcut(game: GameEntry) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
      _statusBanner.value = "Launcher does not support pinned shortcuts"
      return
    }
    val launchIntent = Intent("com.aistudio.vortexpc.ACTION_LAUNCH_GAME").apply {
      setPackage(context.packageName)
      putExtra("extra_game_id", game.id)
    }
    val shortcut = ShortcutInfoCompat.Builder(context, "vortex_game_${game.id}")
      .setShortLabel(game.title.take(18))
      .setLongLabel("Launch ${game.title}")
      .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_media_play))
      .setIntent(launchIntent)
      .build()
    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    viewModelScope.launch {
      repository.saveGame(game.copy(pinnedShortcut = true))
    }
    _statusBanner.value = "Shortcut requested for ${game.title}"
  }

  fun createNewContainer(name: String, preset: CompatibilityPreset? = null) {
    viewModelScope.launch {
      val hw = _hardwareProfile.value ?: hardwareDetector.detect()
      val base = autoConfigEngine.buildConfig(
        hardware = hw,
        tier = preferences.value.defaultAutoTier,
        containerName = name.ifBlank { "Container ${containers.value.size + 1}" },
      ).containerConfig
      val finalConfig = if (preset != null) {
        base.copy(
          translator = preset.translator,
          box64Profile = preset.box64Profile,
          dxWrapper = preset.dxWrapper,
          screenResolution = preset.resolution,
          fpsLimit = preset.fpsLimit,
          envVars = base.envVars + preset.envVars,
        )
      } else {
        base
      }
      containerManager.createContainer(finalConfig)
      _statusBanner.value = "Created '${finalConfig.name}'"
    }
  }

  fun saveContainer(config: ContainerConfig) {
    viewModelScope.launch {
      containerManager.updateContainer(config)
      _statusBanner.value = "Saved '${config.name}'"
    }
  }

  fun cloneContainer(config: ContainerConfig) {
    viewModelScope.launch {
      containerManager.cloneContainer(config)
      _statusBanner.value = "Cloned '${config.name}'"
    }
  }

  fun deleteContainer(id: String) {
    viewModelScope.launch {
      containerManager.deleteContainer(id)
      _statusBanner.value = "Deleted container"
    }
  }

  fun installWinetricks(containerId: String, componentId: String) {
    viewModelScope.launch {
      containerManager.installWinetricksComponent(containerId, componentId)
      _statusBanner.value = "Applied $componentId to container prefix"
    }
  }

  fun exportContainer(config: ContainerConfig, output: OutputStream) {
    viewModelScope.launch {
      containerManager.exportContainerArchive(config, output)
      _statusBanner.value = "Exported '${config.name}' archive"
    }
  }

  fun importContainer(input: InputStream) {
    viewModelScope.launch {
      val imported = containerManager.importContainerArchive(input)
      _statusBanner.value = if (imported != null) {
        "Imported container '${imported.name}'"
      } else {
        "Invalid .vtxc container archive"
      }
    }
  }

  fun importCustomDriver(uri: Uri) {
    viewModelScope.launch {
      driverManager.importDriverZip(uri)
        .onSuccess { pkg -> _statusBanner.value = "Imported driver '${pkg.name}'" }
        .onFailure { err -> _statusBanner.value = "Driver import failed: ${err.message}" }
    }
  }

  fun deleteCustomDriver(id: String) {
    viewModelScope.launch { driverManager.removeCustomDriver(id) }
  }

  fun saveInputProfile(profile: InputProfile) {
    viewModelScope.launch {
      inputProfileRepo.saveProfile(profile)
      _statusBanner.value = "Saved control profile '${profile.name}'"
    }
  }

  fun addControlElementToProfile(profile: InputProfile, type: ControlElementType) {
    val idx = profile.elements.size + 1
    val el = ControlElement(
      id = "el_${UUID.randomUUID().toString().take(6)}",
      label = when (type) {
        ControlElementType.BUTTON -> "K$idx"
        ControlElementType.DPAD -> "DPAD"
        ControlElementType.THUMBSTICK_LEFT -> "LS"
        ControlElementType.THUMBSTICK_RIGHT -> "RS"
        ControlElementType.TRACKPAD -> "PAD"
      },
      type = type,
      xFraction = 0.5f,
      yFraction = 0.5f,
      sizeDp = if (type == ControlElementType.BUTTON) 54f else 100f,
      bindingKey = "KEY_SPACE",
    )
    saveInputProfile(profile.copy(elements = profile.elements + el))
  }

  fun launchGameSession(game: GameEntry, onReadyToOpenSession: () -> Unit) {
    viewModelScope.launch {
      val profile = withContext(Dispatchers.IO) {
        val hw = _hardwareProfile.value ?: hardwareDetector.detect()
        val container = repository.getContainer(game.containerId) ?: ensureDefaultContainer(hw)
        val prof = inputProfiles.value.firstOrNull { it.id == game.inputProfileId }
          ?: inputProfiles.value.first()

        _activeGameSession.value = game
        sessionStartedAtMs = System.currentTimeMillis()
        x11Server.start(
          resolution = container.screenResolution,
          renderScale = container.renderScale,
          fpsLimit = container.fpsLimit,
          targetLabel = game.title,
        )
        telemetryCollector.startPolling()
        launcher.launchGame(game, container)
        prof
      }
      inputDispatcher.bindProfile(profile)
      onReadyToOpenSession()
    }
  }

  fun launchWineUtilitySession(
    toolExe: String,
    title: String,
    container: ContainerConfig,
    onReadyToOpenSession: () -> Unit,
  ) {
    viewModelScope.launch {
      val profile = withContext(Dispatchers.IO) {
        val prof = inputProfiles.value.first()
        _activeGameSession.value = null
        sessionStartedAtMs = System.currentTimeMillis()
        x11Server.start(
          resolution = container.screenResolution,
          renderScale = container.renderScale,
          fpsLimit = container.fpsLimit,
          targetLabel = title,
        )
        telemetryCollector.startPolling()
        launcher.launchWineTool(toolExe, title, container)
        prof
      }
      inputDispatcher.bindProfile(profile)
      onReadyToOpenSession()
    }
  }

  fun launchGameById(gameId: String, onReady: () -> Unit) {
    viewModelScope.launch {
      val game = repository.getGame(gameId) ?: return@launch
      launchGameSession(game, onReady)
    }
  }

  fun stopActiveSession() {
    val activeGame = _activeGameSession.value
    val startedAt = sessionStartedAtMs
    val snap = telemetryCollector.sampleNow()
    telemetryCollector.stopPolling()
    inputDispatcher.unbind()
    x11Server.stop()
    launcher.stopSession()

    if (activeGame != null && startedAt > 0L) {
      val durationSec = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(1L)
      viewModelScope.launch {
        repository.recordPlaySession(
          PlaySessionRecord(
            id = "sess-${UUID.randomUUID().toString().take(8)}",
            gameId = activeGame.id,
            gameTitle = activeGame.title,
            containerId = activeGame.containerId,
            startedAtEpochMs = startedAt,
            durationSeconds = durationSec,
            avgFps = snap.fps,
            peakTempC = snap.batteryTempC,
          )
        )
      }
    }
    _activeGameSession.value = null
    sessionStartedAtMs = 0L
  }

  fun switchSessionMouseMode(profile: InputProfile, mode: MouseMode) {
    val updated = profile.copy(mouseMode = mode)
    inputDispatcher.bindProfile(updated)
    saveInputProfile(updated)
  }

  fun onFramePresented() {
    telemetryCollector.recordPresentedFrame()
  }

  suspend fun exportLogsZipFile(): File {
    val activeCtr = sessionInfo.value.containerConfig
    val summary = if (activeCtr != null) {
      "${activeCtr.name} | wine=${activeCtr.wineVersion} | driver=${activeCtr.graphicsDriver} | dx=${activeCtr.dxWrapper}"
    } else {
      "Containers: ${containers.value.size}, Games: ${games.value.size}"
    }
    return logger.exportDiagnosticZip(_hardwareProfile.value, summary)
  }

  fun clearLogs() = logger.clear()

  private suspend fun ensureDefaultContainer(hw: HardwareProfile): ContainerConfig {
    val existing = containers.value.firstOrNull()
    if (existing != null) return existing
    val rec = autoConfigEngine.buildConfig(
      hardware = hw,
      tier = preferences.value.defaultAutoTier,
      containerName = "Primary Console Container",
    )
    return containerManager.createContainer(rec.containerConfig)
  }
}
