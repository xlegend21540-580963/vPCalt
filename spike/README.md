# VortexSpike — Android W^X / SELinux Feasibility Spike (Experiments 1 & 2)

Standalone single-module `:app` project inside `/spike` with zero dependencies on the 7-module VortexPC project.
It tests whether an `arm64-v8a` APK can execute bundled ELF binaries and JIT/mmap memory across `targetSdk` **28, 29, 34, and 36**.

## Flavors & Package IDs (Install Side-by-Side)

| Flavor | `targetSdk` | `applicationId` | APK Filename |
|---|---|---|---|
| `t28` | 28 (Android 9 rules) | `com.aistudio.vortexpc.spike.t28` | `app-t28-debug.apk` |
| `t29` | 29 (Android 10 W^X cutoff) | `com.aistudio.vortexpc.spike.t29` | `app-t29-debug.apk` |
| `t34` | 34 (Android 14) | `com.aistudio.vortexpc.spike.t34` | `app-t34-debug.apk` |
| `t36` | 36 (Android 16) | `com.aistudio.vortexpc.spike.t36` | `app-t36-debug.apk` |

All flavors use `minSdk 26`, `abiFilters = ["arm64-v8a"]`, `extractNativeLibs="true"`, `useLegacyPackaging = true`, and C11 native builds linked with `-Wl,-z,max-page-size=16384`.

---

## 1. Download APKs from GitHub Actions

1. Push this repo to GitHub (or trigger **Actions -> VortexSpike Feasibility CI -> Run workflow**).
2. Open the completed workflow run and download the **`vortex-spike-debug-apks`** artifact zip.
3. Unzip it into a folder (e.g., `./spike-apks/`).

*(Or build locally if you have Android SDK + NDK `27.2.12479018` + CMake `3.22.1` + Gradle `9.3.1`:)*
```bash
gradle -p spike assembleT28Debug assembleT29Debug assembleT34Debug assembleT36Debug -PenableNativeBuild=true
```

---

## 2. Install All Four APKs via ADB

Make sure your arm64-v8a Android test device has USB debugging enabled (`adb devices`).
All four APKs have distinct `applicationIdSuffix` values, so they install side-by-side:

```bash
adb install -r app-t28-debug.apk
adb install -r app-t29-debug.apk
adb install -r app-t34-debug.apk
adb install -r app-t36-debug.apk
```

*(Note: on Android 14+, installing `t28`/`t29` low-targetSdk APKs may require `--bypass-low-target-sdk-block`:)*
```bash
adb install -r --bypass-low-target-sdk-block app-t28-debug.apk
adb install -r --bypass-low-target-sdk-block app-t29-debug.apk
```

---

## 3. Capture SELinux Denials & Run Experiments

Before launching each APK, clear `logcat` so denials are cleanly isolated per flavor:

```bash
adb logcat -c
```

Launch each app from the launcher (or via `adb shell monkey -p com.aistudio.vortexpc.spike.t28 1`, etc.), tap **Run all**, wait 1–2 seconds for completion, then capture denials and pull or share results:

```bash
# Capture SELinux AVC denials from logcat (works on non-rooted devices)
adb logcat -d -b all | grep -E "avc:|denied" > denials_t28.txt

# Kernel dmesg capture (note: Android 12+ blocks dmesg for non-root shell; run only if rooted/debuggable kernel)
adb shell dmesg | grep avc > dmesg_avc_t28.txt
```

Repeat (`adb logcat -c` -> tap **Run all** -> capture denials) for `.t29`, `.t34`, and `.t36`.

---

## 4. Export `results.txt`

Option A — In-app share sheet:
- Tap **Share results** inside each flavor app to export `results.txt` via email, Drive, or Quick Share.

Option B — Direct `adb` pull via `run-as` (works on all debug APKs without root):
```bash
adb exec-out run-as com.aistudio.vortexpc.spike.t28 cat files/results.txt > results_t28.txt
adb exec-out run-as com.aistudio.vortexpc.spike.t29 cat files/results.txt > results_t29.txt
adb exec-out run-as com.aistudio.vortexpc.spike.t34 cat files/results.txt > results_t34.txt
adb exec-out run-as com.aistudio.vortexpc.spike.t36 cat files/results.txt > results_t36.txt
```

Paste the raw `results_*.txt` and `denials_*.txt` outputs back before proceeding to Experiments 3–8.
