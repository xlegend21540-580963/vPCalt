# VortexPC — Phase 0: System Architecture, Security Model & Technical Specification

> **Status:** Architecture Baseline (Implemented across `:app`, `:ui`, `:core`, `:runtime`, `:graphics`, `:input`, `:native`)  
> **Application ID:** `com.aistudio.vortexpc.xkqmtr`  
> **SDK Baseline:** `minSdk = 26` • `targetSdk = 36` • `compileSdk = 36.1` • Primary ABI `arm64-v8a`  
> **License:** GNU General Public License v3.0 or later (`GPL-3.0-or-later`)

---

## 1. Architectural Vision & Upstream Winlator Strategy

**VortexPC** is an Android Windows x86/x64 compatibility launcher and container manager designed around a GameHub-style true-black `#000000` AMOLED handheld console UX while retaining full Winlator configuration parity.

### Fork vs. Clean Rewrite Decision
Rather than forking Winlator's monolithic Java `AppCompatActivity` / `Fragment` / XML codebase—which tightly couples Android lifecycle callbacks to native IPC and relies on legacy `targetSdk 28` execution assumptions—VortexPC implements a **clean 7-module Kotlin/Compose/Hilt UI and orchestration layer** (`:app`, `:ui`, `:core`, `:runtime`, `:graphics`, `:input`) paired with a **modernized NDK C11/C++17 native bridge layer** (`:native`) aligned with Winlator's GPLv3 native architecture (`XConnector`, `sysvshm`, `PRoot`, `VirGL`, and `AAudio`).

This separation provides:
- Unidirectional state flow (`ViewModel` -> `StateFlow` -> Jetpack Compose) with adaptive `WindowSizeClass` support (`NavigationBar` on phones, `NavigationRail` on tablets/foldables/handhelds) and controller `L1`/`R1`/D-pad focus navigation.
- Fast local JVM + Robolectric + Compose UI verification without requiring an emulator for business logic or startup tests.
- Dual-mode UX: **One-Tap Auto Config** (`AutoConfigEngine` driven by SoC/GPU/Vulkan hardware telemetry) alongside a full **Advanced Winlator** settings drawer.
- Strict compliance with Android 15/16 (`targetSdk 36`), 16KB ELF page-size enforcement, and Google Play Dynamic Code Loading (DCL) rules via **offline pre-bundled runtime packaging**.

---

## 2. Seven-Module Dependency Graph & Comprehensive Breakdown

```text
                    ┌────────────────────────────────────────┐
                    │                  :app                  │
                    │  Hilt Root • MainActivity • Service    │
                    │  Manifest • Offline Assets • Shortcuts │
                    └───────┬──────────┬──────────┬──────────┘
                            │          │          │
             ┌──────────────▼──┐  ┌────▼─────┐  ┌─▼──────────────────┐
             │       :ui       │  │  :input  │  │     :graphics      │
             │ Compose Screens │  │ Touch /  │  │ X11DisplayServer   │
             │ AMOLED M3 Theme │  │ Gamepad  │  │ GraphicsDriverMgr  │
             │ MainViewModel   │  │ Gyro Aim │  │ TelemetryCollector │
             └────────┬────────┘  └────┬─────┘  └─────────┬──────────┘
                      │                │                  │
                      └────────┬───────┴─────────┬────────┘
                               ▼                 ▼
                    ┌──────────────────┐ ┌──────────────────┐
                    │     :runtime     │ │     :native      │
                    │ ContainerManager │ │ CMake C11/C++17  │
                    │ AutoConfigEngine │ │ VortexNativeJni  │
                    │ OfflineStager    │ │ NativeBridgeMgr  │
                    │ EnvBuilder/Launch│ └────────┬─────────┘
                    └────────┬─────────┘          │
                             │                    │
                             ▼                    ▼
                    ┌────────────────────────────────────────┐
                    │                 :core                  │
                    │ Domain Models • Room DB • DataStore    │
                    │ HardwareDetector • SAF & PE Parser     │
                    │ DiagnosticLogManager (ZIP Exporter)    │
                    └────────────────────────────────────────┘
```

### 2.1 `:app` — Application Host, Lifecycle, Packaging & Entry Points
- **Namespace:** `com.example` (`applicationId = "com.aistudio.vortexpc.xkqmtr"`)
- **Gradle & Packaging (`app/build.gradle.kts`):**
  - Enforces `minSdk = 26`, `targetSdk = 36`, `ndk.abiFilters += listOf("arm64-v8a")`, and `packaging.jniLibs.useLegacyPackaging = true` so ELF binaries packaged as `lib*.so` are extracted to `applicationInfo.nativeLibraryDir` with executable (`r-x`) SELinux context.
  - Excludes `dependenciesInfo` from APK while retaining it for App Bundles.
- **Components:**
  - `VortexApplication`: `@HiltAndroidApp` root injector.
  - `MainActivity`: `@AndroidEntryPoint` `ComponentActivity` enabling edge-to-edge rendering (`enableEdgeToEdge()`), handling `ACTION_MAIN` and pinned game shortcut `com.aistudio.vortexpc.ACTION_LAUNCH_GAME` intents (`onCreate` / `onNewIntent`), requesting runtime `POST_NOTIFICATIONS` on Android 13+, and intercepting hardware gamepad/joystick `dispatchKeyEvent` / `dispatchGenericMotionEvent` directly into `InputDispatcher`.
  - `ContainerSessionService`: `specialUse` foreground service (`FOREGROUND_SERVICE_SPECIAL_USE`, `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`) keeping active Wine/Box64/X11/AAudio sessions alive with a persistent low-importance notification.
  - `AndroidManifest.xml` & `file_paths.xml`: Minimal permissions (`VIBRATE`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`), `android:extractNativeLibs="true"`, optional hardware feature declarations (`touchscreen`, `gamepad`, `gyroscope`, `vulkan.level` all `required="false"`), and `FileProvider` for exporting diagnostic ZIPs and container archives.
  - **Bundled Assets (`app/src/main/assets/`):** `runtime_manifest.json` (offline runtime inventory & SHA/metadata) and `bundled_presets.json` (`Indie & 2D`, `3D DX11 Action`, `DX12 Heavy`).
  - **Tests:** `ExampleRobolectricTest` and `MainActivityLaunchTest` (Robolectric + Compose UI CUJ test validating ANR-free cold launch and onboarding completion).

### 2.2 `:core` — Domain Models, Persistence, Hardware Probe & SAF/PE Inspection
- **Namespace:** `com.aistudio.vortexpc.core`
- **Gradle:** Kotlin Serialization, Room + KSP (`roomRuntime`, `roomKtx`, `roomCompiler`), DataStore Preferences, `androidx.documentfile`, Hilt.
- **Key Packages & Classes:**
  - `model/CoreModels.kt`: Serializable domain contracts including `ContainerConfig`, `GameEntry`, `DriveMapping`, `HardwareProfile`, `BundledComponent`, `RuntimeBundleManifest`, `CompatibilityPreset`, `CustomDriverPackage`, `PlaySessionRecord`, `TelemetrySnapshot`, `DiagnosticLogEntry`, `UpstreamAttributions`, and enums (`ThemeMode`, `AutoConfigTier`, `TranslatorEngine`, `Box64Profile`, `GraphicsDriverType`, `DxWrapperType`, `AudioDriverType`, `MouseMode`, `PeArchitecture`, `GpuVendor`).
  - `data/db/` (`VortexDatabase`, `Entities`, `Daos`, `VortexRepository`): Room database (`vortexpc.db`) persisting `containers`, `games`, `input_profiles`, `play_sessions`, and `custom_drivers`, reacting via Kotlin `Flow` and updating aggregated playtime/last-played timestamps upon session completion.
  - `data/prefs/SettingsDataStore`: Preferences DataStore (`vortex_settings`) storing `themeMode`, `onboardingCompleted`, `runtimeStagedVersion`, `defaultAutoTier`, `showHudOverlay`, `hapticsEnabled`, `controllerFocusSounds`, and persisted SAF tree URIs.
  - `hardware/HardwareDetector`: Runs exclusively on `Dispatchers.IO` to inspect `Build.SOC_MODEL` / `/proc/cpuinfo`, `ActivityManager.MemoryInfo` RAM capacity, `/sys/class/kgsl/kgsl-3d0/gpu_model` + SoC strings (Adreno vs. Mali vs. Xclipse vs. emulator/system GPU), `PackageManager.FEATURE_VULKAN_HARDWARE_VERSION`, `Os.sysconf(OsConstants._SC_PAGESIZE)` (4KB vs. 16KB detection), CPU core count, ABIs, and gyroscope availability—without synchronous `EGL14` main-thread probing.
  - `scanner/PeHeaderParser` & `scanner/SafGameScanner`: Zero-permission `DocumentFile` directory traversal (filtering installers/uninstallers like `unins000.exe`, `dxsetup.exe`, `vcredist_*.exe`) combined with binary DOS/PE header parsing (`MZ` -> `e_lfanew` -> `PE\0\0` Machine field: `0x014C` x86, `0x8664` x64, `0xA641` ARM64EC, `0xAA64` ARM64).
  - `logging/DiagnosticLogManager`: Thread-safe 500-entry ring buffer segmented by `LogCategory` (`RUNTIME`, `XSERVER`, `GRAPHICS`, `AUDIO`, `INPUT`, `SYSTEM`) with one-tap `exportDiagnosticZip()` bundling `session_logs.txt` and `hardware_profile.txt`.

### 2.3 `:native` — NDK C11/C++17 Bridges & Execution Path Discovery
- **Namespace:** `com.aistudio.vortexpc.nativebridge`
- **Build System (`native/build.gradle.kts` & `src/main/cpp/CMakeLists.txt`):**
  - Conditional CMake activation (`hasAndroidNdkAndCmake()` or `-PenableNativeBuild=true`), compiling `libvortex_native.so` for `arm64-v8a` with `-std=c++17`, C11, `-DANDROID_STL=c++_shared`, `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`, and linker flag `-Wl,-z,max-page-size=16384`.
  - Links `log`, `android`, and `aaudio`.
- **Native Sources (`src/main/cpp/`):**
  - `vortex_native.h` & `vortex_jni.cpp`: JNI surface for `VortexNativeJni` (`nativeGetAbiInfo`, XConnector, SysVShm, AAudioBridge, PRoot spawn, VirGL bridge).
  - `xconnector.c`: Non-blocking `AF_UNIX` `SOCK_STREAM` server (`SOCK_NONBLOCK | SOCK_CLOEXEC`), `poll`-driven `accept4`, and `recvmsg(..., SCM_RIGHTS)` file-descriptor passing for X11 client shared-memory buffers.
  - `sysvshm.c`: `ASharedMemory_create` (`ashmem`/memfd-backed) file descriptor allocation replacing restricted Android SysV IPC (`shmget`/`shmat`).
  - `audiobridge.c`: Lock-free atomic SPSC ring buffer (`48000 * 2` stereo PCM16 samples) feeding `AAudioStreamBuilder` in `AAUDIO_PERFORMANCE_MODE_LOW_LATENCY` with real-time `aaudio_data_callback_result_t` callback.
  - `proot_bridge.c`: `fork()` + `prctl(PR_SET_PDEATHSIG, SIGTERM)` + `sched_setaffinity()` CPU core pinning + `PROOT_LOADER` environment export + `execvp()` for `libproot.so`.
  - `virgl_bridge.c`: UNIX socket lifecycle management (`vortex_virgl_init` / `vortex_virgl_shutdown`) for VirGL VTEST 3D rendering fallback.
- **Kotlin Facade:**
  - `VortexNativeJni`: Safe `System.loadLibrary("vortex_native")` wrapper with graceful boolean status (`isNativeLoaded`).
  - `NativeBridgeManager`: Inspects `nativeLibraryDir` for `libproot.so` / `libproot_loader.so`, bridges to JNI when loaded, and falls back to Android SDK `AudioTrack` (`PERFORMANCE_MODE_LOW_LATENCY`), `LocalServerSocket`, and `android.os.SharedMemory` when running on non-NDK preview/emulator builds.

### 2.4 `:runtime` — Containers, Auto Config, Offline Stager & Process Orchestration
- **Namespace:** `com.aistudio.vortexpc.runtime`
- **Key Classes:**
  - `AutoConfigEngine`: Deterministic device-aware recommender mapping `HardwareProfile` + `AutoConfigTier` (`BATTERY_SAVER`, `BALANCED`, `PERFORMANCE`) + `PeArchitecture` to optimal graphics driver (`TURNIP` on Adreno, `SYSTEM_VULKAN` on Vulkan 1.3+ Mali/Xclipse, `VIRGL` fallback), DX wrapper (`DXVK` vs `WINED3D`), Box64/Box86 dynarec flags, resolution, FPS cap, AAudio latency, CPU big-core affinity mask (`0b11110000` on 8-core Performance), and `wine-9.22-arm64ec` vs `wine-9.22-x86_64`.
  - `OfflineRuntimeStager`: Reads `runtime_manifest.json` and `bundled_presets.json` from APK assets, emits `Flow<StagingProgress>` while staging component metadata and the Ubuntu 24.04.2 rootfs skeleton (`bin`, `etc/os-release`, `home/vortex`, `tmp/.X11-unix`, `usr/share/vulkan/icd.d`, `mnt/shared_games`) under `filesDir/vortex_runtime/`, and provides `extractZipSafely()` with canonical path-traversal (`../`) rejection.
  - `ContainerManager`: Full container lifecycle (create, update, clone with directory copy, delete, `.vtxc.zip` export/import), `.wine/drive_c` and `dosdevices` initialization, `system.reg` generation, and Winetricks-style DLL override management (`vcrun2022`, `d3dx9`, `d3dx11`, `corefonts`, `xact`, `dotnet48` written to `user.reg`).
  - `RuntimeEnvironmentBuilder`: Assembles the complete POSIX environment (`PROOT_TMP_DIR`, `PROOT_LOADER`, `DISPLAY`, `VORTEX_XSOCKET_DIR`, `WINEPREFIX`, `BOX64_DYNAREC_*`, `FEX_*`, `VK_ICD_FILENAMES`, `WINEDLLOVERRIDES`, `DXVK_STATE_CACHE*`, `PULSE_SERVER`, `ALSA_BRIDGE_SOCK`, plus user overrides).
  - `ContainerLauncher`: State machine (`IDLE` -> `STAGING` -> `BOOTING_X11` -> `RUNNING` -> `STOPPED` / `ERROR`) wiring `NativeBridgeManager` XConnector/SysVShm/AAudio and spawning `libproot.so` (via JNI or `ProcessBuilder`), or keeping an integrated X11/AAudio loopback host when guest ELFs are absent in preview builds.

### 2.5 `:graphics` — X11 Display Server, Custom Vulkan Drivers & Performance HUD Telemetry
- **Namespace:** `com.aistudio.vortexpc.graphics`
- **Key Classes:**
  - `X11DisplayServer`: Built-in X11 protocol server listening on abstract `LocalServerSocket("vortex_x11_0")` (completing X11 SetupResponse handshakes advertising 24-bit TrueColor root visuals), managing logical resolution, dynamic `renderScale` (`0.5f..1.0f`), FPS limit, cursor coordinates/mouse button masks, window stack state (`Wine Desktop` + foreground app/utility windows), and keyboard buffer injection for Wine tools (`notepad.exe`, `winecfg`, `taskmgr`, `regedit`).
  - `GraphicsDriverManager`: Imports local AdrenoTools-compatible Turnip driver `.zip` archives via SAF using `OfflineRuntimeStager.extractZipSafely()`, parses `meta.json`, locates ICD `.json` + `.so` paths, and persists `CustomDriverPackage` entries in Room.
  - `PerformanceTelemetryCollector`: Background `Dispatchers.IO` sampler (`500ms` cadence) calculating real-time FPS and frametime from presented frame timestamps (maintaining a 30-sample sparkline history), reading `/proc/stat` CPU deltas, `ActivityManager.MemoryInfo` RAM usage, `ACTION_BATTERY_CHANGED` charge/temperature, and `/sys/class/thermal/thermal_zone0/temp` fallback.

### 2.6 `:input` — Visual Touch Controls, Mouse Emulation, Gamepad Remapping & Gyro Aim
- **Namespace:** `com.aistudio.vortexpc.input`
- **Key Classes:**
  - `InputModels.kt`: Serializable `InputProfile`, `ControlElement` (`BUTTON`, `DPAD`, `THUMBSTICK_LEFT`, `THUMBSTICK_RIGHT`, `TRACKPAD` with normalized `(xFraction, yFraction)`, `sizeDp`, `opacity`, `bindingKey`, `isToggle`), `GamepadBinding`, `GyroConfig`, plus 3 built-in console presets (`Handheld Console (XInput)`, `FPS Touch + Gyro Aim`, `Strategy & Direct Mouse`).
  - `InputProfileRepository`: Observes and seeds default input profiles in Room (`ensureDefaultsSeeded()` checking `firstOrNull().isNullOrEmpty()`) and handles JSON import/export.
  - `GyroAimController`: `SensorEventListener` bound to `Sensor.TYPE_GYROSCOPE` (`SENSOR_DELAY_GAME`), computing frame-time-clamped (`<= 0.05s`) angular velocity deltas with configurable deadzone (`deadzoneRadPerSec`) and X/Y axis inversion.
  - `InputDispatcher`: Routes touch surface drags/taps (`MouseMode.TOUCHPAD` vs `MouseMode.DIRECT_TOUCH`), virtual button/stick events (with symmetric radial deadzone normalization), hardware `SOURCE_GAMEPAD` / `SOURCE_JOYSTICK` key/axis events, and gyro deltas to `X11DisplayServer`, exposing `LiveInputDebugState` for the controls monitor.

### 2.7 `:ui` — AMOLED Handheld Console UI, Navigation & ViewModel
- **Namespace:** `com.aistudio.vortexpc.ui`
- **Theme & Typography (`theme/`):**
  - `Theme.kt` / `Color.kt`: Pure `#000000` AMOLED color scheme with `#0B0D12`/`#141821` carbon containers, `#242B38` hairline borders, `#00E5FF` electric cyan focus/primary, `#39FF88` telemetry OK, `#FFB020` warning, `#FF4D5A` alert; also supports Dark Slate, Light, and Android 12+ Dynamic Color.
  - `Type.kt`: Bundled local `.ttf` fonts (`space_grotesk.ttf`, `inter.ttf`, `jetbrains_mono.ttf`).
- **Navigation & Shell (`VortexAppShell.kt`, `VortexMainViewModel.kt`):**
  - Type-safe `@Serializable` routes (`RouteOnboarding`, `RouteLibrary`, `RouteContainers`, `RouteDrivers`, `RouteControls`, `RouteSettings`, `RouteSession`) mounted only after `preferencesLoaded` emits from DataStore (`SharingStarted.Eagerly`), preventing startup graph mutations.
  - Hardware `ButtonL1` / `ButtonR1` preview key interception for instant shoulder-button tab cycling and adaptive `WindowWidthSizeClass` switching between `NavigationBar` and `NavigationRail`.
  - Session orchestration offloaded onto `Dispatchers.IO` while keeping `SensorManager` binding on the main thread; pinned desktop shortcut creation via `ShortcutManagerCompat`.
- **Screens & Components:**
  - `OnboardingScreen`: Hardware/Silicon diagnostic summary, 3-tier Auto-Config selector, live offline staging progress bar, and edge-to-edge `safeDrawingPadding()`.
  - `LibraryScreen` & `GameSettingsSheet`: Search/filter/sort console grid, Hero "Continue Playing" banner, Quick Launch carousel, SAF `.exe`/folder import, one-tap Auto-Config vs. Advanced Winlator tabs, shortcut pinning, and `notepad.exe` X11 smoke launch.
  - `ContainersScreen`: Create/clone/delete/import/export (`.vtxc.zip`) containers, preset templates, Winetricks sheet, CPU core affinity bitmask toggles, env var editor, and Wine utilities (`notepad`, `explorer`, `winecfg`, `taskmgr`, `regedit`).
  - `DriversAndRuntimeScreen`: Hardware/kernel page-size diagnostics, offline runtime stack status, and SAF AdrenoTools `.zip` driver importer.
  - `ControlsEditorScreen`: Interactive drag-and-drop touch overlay canvas, size/opacity/binding editor, mouse/gyro sliders, and live controller telemetry.
  - `SettingsAndLogsScreen`: Theme switcher, HUD/haptics toggles, recent play session history, live color-coded terminal logs, diagnostic ZIP exporter, and GPLv3/upstream attribution modal.
  - `GameSessionScreen`: Fullscreen `X11ViewportCanvas`, `VirtualControlsOverlay`, `PerformanceHudOverlay` (FPS + sparkline + CPU/GPU/RAM/battery/thermal), mini keyboard bar, slide-out Quick Menu, and `BackHandler`.

---

## 3. Runtime Stack Integration Strategy (Wine / Box64 & Box86 / FEX / PRoot)

### 3.1 Execution Pipeline
```text
SAF .exe URI ──► PeHeaderParser (x86 / x64 / ARM64EC) ──► AutoConfigEngine / User Override
                                                                     │
  ┌──────────────────────────────────────────────────────────────────┘
  ▼
ContainerManager (.wine prefix, dosdevices, system.reg + Winetricks user.reg)
  │
  ├──► RuntimeEnvironmentBuilder (PROOT_LOADER, BOX64_DYNAREC_*, FEX_*, VK_ICD_FILENAMES, WINEDLLOVERRIDES)
  ├──► GraphicsDriverManager / X11DisplayServer (:0 @vortex_x11_0, DXVK/VKD3D/VirGL)
  ├──► NativeBridgeManager (libsysvshm / SharedMemory + AAudio low-latency stream)
  └──► vortex_proot_spawn() ──► libproot.so (-r rootfs -0) ──► Wine + Box64/Box86/FEX ──► Windows PE
```

### 3.2 Wine Dual-Architecture Strategy
- **Wine 9.22 ARM64EC (`wine-9.22-arm64ec`):** Default for `ARM64EC` PE binaries and `>= 8 GB` RAM devices. Windows system DLLs (`ntdll`, `kernel32`, `user32`, graphics thunks) execute as native ARM64EC code, minimizing translation overhead for OS calls and Vulkan/DirectX thunks while only JIT-translating guest x64 application code.
- **Wine 9.22 x86_64 (`wine-9.22-x86_64`) + Box86/Box64:** Full x86/x64 Linux Wine userland executed under Box64 (`x86_64`) and Box86 (`i386`, `MACHINE_I386`) for maximum compatibility with legacy 32-bit installers, anti-tamper stubs, and games incompatible with WoW64/ARM64EC.

### 3.3 Box64 / Box86 & FEX Integration
- **Box64 / Box86 (`box64-0.37`):** Configured dynamically per container via environment presets:
  - *Stability*: `BOX64_DYNAREC_BIGBLOCK=1`, `BOX64_DYNAREC_STRONGMEM=2` (strict ARM memory ordering for race-sensitive titles).
  - *Compatibility*: `BOX64_DYNAREC_BIGBLOCK=1`, `BOX64_DYNAREC_STRONGMEM=1`, `BOX64_DYNAREC_FASTNAN=1`.
  - *Performance*: `BOX64_DYNAREC_BIGBLOCK=3`, `BOX64_DYNAREC_STRONGMEM=0`, `BOX64_DYNAREC_FASTNAN=1`, `BOX64_DYNAREC_FASTROUND=1`, `BOX64_DYNAREC_SAFEFLAGS=0`.
- **FEX-Emu (`fex-2503`):** Optional alternative user-mode x86/x64 JIT (`TranslatorEngine.FEX`), configured with `FEX_APP_CONFIG_LOCATION=$rootfsPath/etc/fex` and `FEX_TSOENABLED=1` (e.g., for heavy DX12/VKD3D-Proton workloads).

### 3.4 PRoot Rootfs & Graphics/Audio Bridges
- **PRoot (`libproot.so` + `libproot_loader.so`):** Provides rootless chroot (`-r $rootfsDir`), simulated UID 0 (`-0`), and path translation (`/home/vortex/.wine`, `/mnt/shared_games`) without root access. `proot_bridge.c` binds `PR_SET_PDEATHSIG = SIGTERM` so orphaned Wine/Box64 processes terminate cleanly if the host process exits, and applies `sched_setaffinity()` bitmasks for big-core pinning.
- **Graphics Stack:**
  - *Adreno GPUs*: Bundled **Mesa Turnip KGSL** (`VK_ICD_FILENAMES=.../freedreno_icd.aarch64.json`, `TU_DEBUG=noconform`) or user-imported AdrenoTools Turnip `.zip` packages, paired with **DXVK 2.5.3 + D8VK** (`d3d9,d3d10core,d3d11,dxgi=n,b`) or **VKD3D-Proton 2.14** (`d3d12,d3d12core=n,b`).
  - *Mali / Xclipse / Universal GPUs*: `SYSTEM_VULKAN` (`/vendor/etc/vulkan/icd.d/vulkan.json`) when Vulkan 1.3+ is present, or **Mesa Zink / VirGL** (`virgl_bridge.c` VTEST socket) + **WineD3D** fallback.
- **Audio Stack:** `audiobridge.c` feeds a lock-free stereo PCM16 ring buffer directly into Android **AAudio** (`AAUDIO_PERFORMANCE_MODE_LOW_LATENCY`), bridged to PulseAudio/ALSA UNIX domain sockets (`PULSE_SERVER`, `ALSA_BRIDGE_SOCK`).

---

## 4. Handling Android Security & Platform Constraints

| Constraint | Kernel / OS Enforcement | VortexPC Mitigation Strategy |
| :--- | :--- | :--- |
| **`targetSdk 36` `W^X` & SELinux `app_data_file`** | Android 10+ (`targetSdk >= 29`) forbids `execve()` on files written to `filesDir`/`cacheDir` (`app_data_file` lacks `execute` permission). | Package host ELFs (`libproot.so`, `libproot_loader.so`, `libvortex_native.so`) inside `jniLibs/arm64-v8a/` with `extractNativeLibs="true"` and `useLegacyPackaging = true`, extracting them into `nativeLibraryDir` (`apk_data_file` with `r-x`). `PROOT_LOADER` maps guest Wine/Box64 ELF segments directly into anonymous memory pages with `mmap` + `mprotect(PROT_READ \| PROT_EXEC)` rather than kernel `execve()`. |
| **Box64 / FEX JIT `W^X` Page Enforcement** | Writable and simultaneously executable (`PROT_READ \| PROT_WRITE \| PROT_EXEC`) anonymous pages are restricted or trigger faults on hardened kernels/SELinux. | Dynarec engines allocate JIT code caches with alternating `RW` -> write -> `RX` `mprotect` transitions (or dual-mapped `memfd`/`ashmem` aliases), never holding `RWX` permissions during execution. |
| **16KB Page Size Alignment (Android 15/16+)** | Devices configured with 16KB OS pages fail to load 4KB-aligned shared libraries (`PT_LOAD` segment misalignment). | `:native` CMake enforces `-Wl,-z,max-page-size=16384` and `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`. `HardwareDetector` detects `Os.sysconf(_SC_PAGESIZE)` at runtime and passes `VORTEX_PAGE_SIZE=16384` to the runtime environment. |
| **SysV Shared Memory (`shmget`) Restrictions** | Android Bionic & SELinux block standard Linux System V IPC shared memory used by X11 MIT-SHM and Wine IPC. | `sysvshm.c` creates `ASharedMemory_create` (`ashmem`/memfd) file descriptors and passes them over UNIX sockets via `xconnector.c` (`SCM_RIGHTS` ancillary data), with Kotlin `android.os.SharedMemory` fallback. |
| **Google Play DCL & System Integrity** | Downloading `.so` libraries or executable binaries from remote servers at runtime violates Play System Integrity (DCL). | **Offline pre-bundled APK architecture:** runtime manifests, presets, rootfs metadata, and native bridges ship inside the signed APK; optional community Turnip drivers are imported strictly as user-selected local `.zip` files via SAF with canonical path-traversal checks. |
| **Scoped Storage & Minimal Permissions** | Broad storage (`READ_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`) and accessibility/install permissions are restricted. | Zero storage permissions requested. Game folders and `.exe` files are accessed strictly through Android SAF (`OpenDocumentTree` / `OpenDocument` with persistable URI grants). |

---

## 5. Key Performance & Licensing Risks

### 5.1 Performance Risks & Mitigations
1. **x86-to-ARM64 Memory Ordering & Dynarec Stutter (`STRONGMEM`):**
   - *Risk:* Emulating x86 Total Store Order (TSO) on ARM64's weak memory model requires `DMB` barriers (`STRONGMEM=2`), costing 15–30% CPU throughput; disabling barriers (`STRONGMEM=0`) risks multi-threaded race crashes.
   - *Mitigation:* Per-tier Box64 presets in `AutoConfigEngine` + `Wine 9.22 ARM64EC` default on 8GB+ devices (eliminating JIT overhead for Windows system DLLs).
2. **Vulkan Shader Compilation Stutter (DXVK / VKD3D):**
   - *Risk:* First-time Direct3D-to-SPIR-V shader compilation pauses frame presentation on mobile GPUs.
   - *Mitigation:* Enable `DXVK_ASYNC=1`, persistent per-prefix `DXVK_STATE_CACHE_PATH`, and `VKD3D_SHADER_CACHE_PATH`.
3. **Thermal Throttling & Hybrid big.LITTLE Scheduling:**
   - *Risk:* Sustaining prime/big cores across 8 threads triggers rapid SoC thermal throttling and frame pacing drops.
   - *Mitigation:* `AutoConfigEngine` configures core affinity masks (`0b00001111` for Battery Saver vs `0b11110000` big-core pinning on 8-core Performance), paired with `PerformanceTelemetryCollector` (`/sys/class/thermal` + battery temp + FPS sparkline) and live FPS limiters (`30` / `40` / `60` / uncapped).
4. **16KB Kernel Page Mismatch on Guest Wine/x86 Allocators:**
   - *Risk:* Guest Win32/x86 software expects 4KB (`0x1000`) virtual memory granularity (`VirtualAlloc`), which can misalign or waste memory on 16KB Android kernels.
   - *Mitigation:* `HardwareDetector` flags `is16KbPageDevice` so the runtime layer can apply 4KB emulation alignment shims and expose `VORTEX_PAGE_SIZE=16384`.
5. **UI Main-Thread ANRs & Compose Startup Stalls:**
   - *Risk:* Synchronous hardware probing (`EGL14` pbuffer creation), DataStore reads, or Room seeding on the UI thread causes emulator/device startup ANRs (`"Close app or wait"`).
   - *Mitigation:* `HardwareDetector.detect()`, `OfflineRuntimeStager`, `ContainerLauncher`, and `InputProfileRepository` run on `Dispatchers.IO`; `VortexAppShell` waits for `SharingStarted.Eagerly` DataStore readiness with an immutable `NavHost` `startDestination`.

### 5.2 Licensing & Legal Risks
1. **GPLv3 Copyleft Propagation:**
   - *Risk:* Winlator (`brunodev85/winlator`) and key native execution bridges (`PRoot` is GPL-2.0+, Winlator architecture is GPL-3.0) require VortexPC to be distributed under **GPL-3.0-or-later** with complete corresponding source code availability, clear copyright attribution (`NOTICE` + in-app license dialog via `UpstreamAttributions`), and preservation of LGPL-2.1+ relinking/attribution rules for `Wine` and `VKD3D-Proton`.
   - *Mitigation:* Root `/NOTICE` file, manifest-level license metadata in `runtime_manifest.json`, and an in-app **GPLv3 & Licenses** viewer in `SettingsAndLogsScreen`.
2. **Proprietary GPU Firmware & Adreno KGSL Access:**
   - *Risk:* Proprietary vendor Vulkan drivers lack `/dev/kgsl-3d0` extensions or ship broken Vulkan 1.1 subsets; conversely, bundling proprietary vendor blobs violates redistribution licenses.
   - *Mitigation:* Bundle only open-source **Mesa Turnip / Zink / VirGL** (MIT/Expat) and support user-imported AdrenoTools `.zip` packages via SAF.
3. **Copyrighted Windows Software & Game Files:**
   - *Risk:* Distributing proprietary Windows DLLs, DirectX/Visual C++ binary redistributables, BIOS images, or copyrighted games creates severe copyright liability.
   - *Mitigation:* **Zero copyrighted Windows binaries or sample games are bundled.** VortexPC ships only open-source compatibility layers (Wine/Box64/FEX/DXVK/VKD3D/Mesa), creates synthetic Wine `WINEDLLOVERRIDES` stubs via Winetricks, and requires users to supply their own legally obtained Windows executables through SAF.
