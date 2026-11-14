package com.aistudio.vortexpc.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aistudio.vortexpc.core.model.ContainerConfig
import com.aistudio.vortexpc.ui.screens.ContainersScreen
import com.aistudio.vortexpc.ui.screens.ControlsEditorScreen
import com.aistudio.vortexpc.ui.screens.DriversAndRuntimeScreen
import com.aistudio.vortexpc.ui.screens.GameSessionScreen
import com.aistudio.vortexpc.ui.screens.LibraryScreen
import com.aistudio.vortexpc.ui.screens.OnboardingScreen
import com.aistudio.vortexpc.ui.screens.SettingsAndLogsScreen
import com.aistudio.vortexpc.ui.theme.VortexTheme
import com.aistudio.vortexpc.ui.viewmodel.VortexMainViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object RouteOnboarding
@Serializable data object RouteLibrary
@Serializable data object RouteContainers
@Serializable data object RouteDrivers
@Serializable data object RouteControls
@Serializable data object RouteSettings
@Serializable data object RouteSession

private data class NavTab(
  val route: Any,
  val routeQualifiedName: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun VortexAppShell(
  shortcutGameId: String? = null,
  onStartForegroundService: (title: String, containerName: String) -> Unit = { _, _ -> },
  onStopForegroundService: () -> Unit = {},
  viewModel: VortexMainViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val activity = context as? Activity
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val preferencesLoaded by viewModel.preferencesLoaded.collectAsStateWithLifecycle()
  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val hardwareProfile by viewModel.hardwareProfile.collectAsStateWithLifecycle()
  val stagingProgress by viewModel.stagingProgress.collectAsStateWithLifecycle()
  val games by viewModel.games.collectAsStateWithLifecycle()
  val containers by viewModel.containers.collectAsStateWithLifecycle()
  val bundledComponents by viewModel.bundledComponents.collectAsStateWithLifecycle()
  val bundledPresets by viewModel.bundledPresets.collectAsStateWithLifecycle()
  val customDrivers by viewModel.customDrivers.collectAsStateWithLifecycle()
  val inputProfiles by viewModel.inputProfiles.collectAsStateWithLifecycle()
  val sessionInfo by viewModel.sessionInfo.collectAsStateWithLifecycle()
  val x11State by viewModel.x11State.collectAsStateWithLifecycle()
  val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
  val logs by viewModel.logs.collectAsStateWithLifecycle()
  val recentSessions by viewModel.recentSessions.collectAsStateWithLifecycle()
  val inputDebug by viewModel.inputDebug.collectAsStateWithLifecycle()
  val banner by viewModel.statusBanner.collectAsStateWithLifecycle()

  var pendingExportContainer by remember { mutableStateOf<ContainerConfig?>(null) }

  val folderPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      }
      viewModel.scanSafFolder(uri)
    }
  }

  val exePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      }
      viewModel.importSingleExecutable(uri)
    }
  }

  val containerImportPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      context.contentResolver.openInputStream(uri)?.use { viewModel.importContainer(it) }
    }
  }

  val containerExportCreator = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/zip"),
  ) { uri ->
    val target = pendingExportContainer
    pendingExportContainer = null
    if (uri != null && target != null) {
      context.contentResolver.openOutputStream(uri)?.use { viewModel.exportContainer(target, it) }
    }
  }

  val driverZipPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      viewModel.importCustomDriver(uri)
    }
  }

  val exportDiagnosticLogs: () -> Unit = {
    scope.launch {
      val zipFile = viewModel.exportLogsZipFile()
      val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        zipFile,
      )
      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(Intent.createChooser(shareIntent, "Export VortexPC Diagnostics"))
    }
  }

  LaunchedEffect(banner) {
    val msg = banner ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearBanner()
  }

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route.orEmpty()

  var handledShortcut by remember(shortcutGameId) { mutableStateOf(false) }

  LaunchedEffect(preferencesLoaded, preferences.onboardingCompleted, currentRoute) {
    if (preferencesLoaded && preferences.onboardingCompleted && currentRoute == RouteOnboarding::class.qualifiedName) {
      navController.navigate(RouteLibrary) {
        popUpTo(RouteOnboarding) { inclusive = true }
      }
    }
  }

  LaunchedEffect(shortcutGameId, preferencesLoaded, preferences.onboardingCompleted) {
    if (!handledShortcut && preferencesLoaded && !shortcutGameId.isNullOrBlank() && preferences.onboardingCompleted) {
      handledShortcut = true
      viewModel.launchGameById(shortcutGameId) {
        navController.navigate(RouteSession)
      }
    }
  }

  val tabs = remember {
    listOf(
      NavTab(RouteLibrary, RouteLibrary::class.qualifiedName.orEmpty(), R.string.nav_library, Icons.Rounded.SportsEsports, Icons.Outlined.SportsEsports),
      NavTab(RouteContainers, RouteContainers::class.qualifiedName.orEmpty(), R.string.nav_containers, Icons.Rounded.Inventory2, Icons.Outlined.Inventory2),
      NavTab(RouteDrivers, RouteDrivers::class.qualifiedName.orEmpty(), R.string.nav_drivers, Icons.Rounded.Memory, Icons.Outlined.Memory),
      NavTab(RouteControls, RouteControls::class.qualifiedName.orEmpty(), R.string.nav_controls, Icons.Rounded.Gamepad, Icons.Outlined.Gamepad),
      NavTab(RouteSettings, RouteSettings::class.qualifiedName.orEmpty(), R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
    )
  }

  val isFullscreenRoute = !preferencesLoaded ||
    currentRoute == RouteOnboarding::class.qualifiedName ||
    currentRoute == RouteSession::class.qualifiedName

  val widthClass = activity?.let { calculateWindowSizeClass(it).widthSizeClass } ?: WindowWidthSizeClass.Compact
  val useNavRail = widthClass != WindowWidthSizeClass.Compact

  VortexTheme(themeMode = preferences.themeMode) {
    Scaffold(
      modifier = Modifier
        .fillMaxSize()
        .onPreviewKeyEvent { keyEvent ->
          if (!isFullscreenRoute && keyEvent.type == KeyEventType.KeyDown) {
            val activeIdx = tabs.indexOfFirst { it.routeQualifiedName == currentRoute }
            if (activeIdx >= 0) {
              when (keyEvent.key) {
                Key.ButtonL1 -> {
                  val prev = tabs[(activeIdx - 1 + tabs.size) % tabs.size]
                  navController.navigate(prev.route) { launchSingleTop = true }
                  true
                }
                Key.ButtonR1 -> {
                  val next = tabs[(activeIdx + 1) % tabs.size]
                  navController.navigate(next.route) { launchSingleTop = true }
                  true
                }
                else -> false
              }
            } else {
              false
            }
          } else {
            false
          }
        },
      containerColor = MaterialTheme.colorScheme.background,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = if (isFullscreenRoute) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
      bottomBar = {
        if (!isFullscreenRoute && !useNavRail) {
          NavigationBar(
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            tabs.forEach { tab ->
              val selected = currentRoute == tab.routeQualifiedName
              val label = stringResource(tab.labelRes)
              NavigationBarItem(
                selected = selected,
                onClick = {
                  navController.navigate(tab.route) {
                    popUpTo(RouteLibrary) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(label, maxLines = 1) },
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (!isFullscreenRoute && useNavRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          ) {
            tabs.forEach { tab ->
              val selected = currentRoute == tab.routeQualifiedName
              val label = stringResource(tab.labelRes)
              NavigationRailItem(
                selected = selected,
                onClick = {
                  navController.navigate(tab.route) {
                    popUpTo(RouteLibrary) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(label, maxLines = 1) },
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f)) {
          if (!preferencesLoaded) {
            Box(modifier = Modifier.fillMaxSize())
          } else {
            val initialStartDestination: Any = remember {
              if (preferences.onboardingCompleted) RouteLibrary else RouteOnboarding
            }

            NavHost(
              navController = navController,
              startDestination = initialStartDestination,
            ) {
            composable<RouteOnboarding> {
              OnboardingScreen(
                hardwareProfile = hardwareProfile,
                stagingProgress = stagingProgress,
                onStartSetup = { tier -> viewModel.completeOnboarding(tier) },
              )
            }
            composable<RouteLibrary> {
              LibraryScreen(
                games = games,
                containers = containers,
                inputProfiles = inputProfiles,
                hardwareProfile = hardwareProfile,
                onScanFolderClick = { folderPicker.launch(null) },
                onAddExeClick = { exePicker.launch(arrayOf("*/*")) },
                onLaunchGame = { game ->
                  viewModel.launchGameSession(game) {
                    onStartForegroundService(game.title, game.containerId)
                    navController.navigate(RouteSession)
                  }
                },
                onLaunchNotepadDemo = { ctr ->
                  viewModel.launchWineUtilitySession("notepad.exe", "Wine Notepad", ctr) {
                    onStartForegroundService("notepad.exe", ctr.name)
                    navController.navigate(RouteSession)
                  }
                },
                onAutoTuneGame = { game, tier -> viewModel.applyAutoConfigTier(game, tier) },
                onUpdateGame = { viewModel.updateGame(it) },
                onSaveContainer = { viewModel.saveContainer(it) },
                onDeleteGame = { viewModel.deleteGame(it) },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onPinShortcut = { viewModel.pinGameShortcut(it) },
              )
            }
            composable<RouteContainers> {
              ContainersScreen(
                containers = containers,
                presets = bundledPresets,
                winetricks = viewModel.availableWinetricks,
                onCreateContainer = { name, preset -> viewModel.createNewContainer(name, preset) },
                onSaveContainer = { viewModel.saveContainer(it) },
                onCloneContainer = { viewModel.cloneContainer(it) },
                onDeleteContainer = { viewModel.deleteContainer(it) },
                onExportContainer = { ctr ->
                  pendingExportContainer = ctr
                  containerExportCreator.launch("${ctr.name.replace(' ', '_')}.vtxc.zip")
                },
                onImportContainerClick = { containerImportPicker.launch(arrayOf("application/zip", "*/*")) },
                onInstallWinetricks = { cid, compId -> viewModel.installWinetricks(cid, compId) },
                onLaunchWineTool = { exe, title, ctr ->
                  viewModel.launchWineUtilitySession(exe, title, ctr) {
                    onStartForegroundService(title, ctr.name)
                    navController.navigate(RouteSession)
                  }
                },
              )
            }
            composable<RouteDrivers> {
              DriversAndRuntimeScreen(
                hardwareProfile = hardwareProfile,
                bundledComponents = bundledComponents,
                customDrivers = customDrivers,
                onImportDriverClick = { driverZipPicker.launch(arrayOf("application/zip", "*/*")) },
                onDeleteCustomDriver = { viewModel.deleteCustomDriver(it) },
              )
            }
            composable<RouteControls> {
              ControlsEditorScreen(
                profiles = inputProfiles,
                inputDebug = inputDebug,
                onSaveProfile = { viewModel.saveInputProfile(it) },
                onAddElement = { prof, type -> viewModel.addControlElementToProfile(prof, type) },
              )
            }
            composable<RouteSettings> {
              SettingsAndLogsScreen(
                preferences = preferences,
                logs = logs,
                recentSessions = recentSessions,
                onSetThemeMode = { viewModel.setThemeMode(it) },
                onSetShowHud = { viewModel.setShowHud(it) },
                onSetHaptics = { viewModel.setHapticsEnabled(it) },
                onExportLogsClick = exportDiagnosticLogs,
                onClearLogsClick = { viewModel.clearLogs() },
                onRerunOnboarding = {
                  viewModel.rerunOnboarding()
                  navController.navigate(RouteOnboarding) {
                    popUpTo(RouteLibrary) { inclusive = true }
                  }
                },
              )
            }
            composable<RouteSession> {
              val activeProfile = inputProfiles.firstOrNull() ?: com.aistudio.vortexpc.input.InputProfile.presets().first()
              GameSessionScreen(
                sessionInfo = sessionInfo,
                x11State = x11State,
                telemetry = telemetry,
                showHud = preferences.showHudOverlay,
                activeProfile = activeProfile,
                allProfiles = inputProfiles,
                onFramePresented = { viewModel.onFramePresented() },
                onTouchDrag = { dx, dy, ax, ay -> viewModel.inputDispatcher.onTouchSurfaceDrag(dx, dy, ax, ay) },
                onTouchTap = { ax, ay, right -> viewModel.inputDispatcher.onTouchSurfaceTap(ax, ay, right) },
                onVirtualButton = { key, down -> viewModel.inputDispatcher.onVirtualControlEvent(key, down) },
                onVirtualStick = { isRight, nx, ny -> viewModel.inputDispatcher.onVirtualStickMove(isRight, nx, ny) },
                onInjectKeyText = { viewModel.x11Server.injectKeyText(it) },
                onUpdateScaleAndFps = { scale, cap -> viewModel.x11Server.updateResolutionAndFps(scale = scale, fpsLimit = cap) },
                onToggleHud = { viewModel.setShowHud(it) },
                onSwitchMouseMode = { mode -> viewModel.switchSessionMouseMode(activeProfile, mode) },
                onSelectProfile = { prof -> viewModel.inputDispatcher.bindProfile(prof) },
                onExportLogs = exportDiagnosticLogs,
                onExitSession = {
                  viewModel.stopActiveSession()
                  onStopForegroundService()
                  navController.popBackStack()
                },
              )
            }
          }
          }
        }
      }
    }
  }
}
