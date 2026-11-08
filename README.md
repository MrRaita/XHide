# XHide

**XHide** is a lightweight, zero-lock, system-level **Xposed / LSPosed / Vector** module that cleanly hides Xposed module icons and user-selected applications from all Android launchers (home screen, app drawer, and search) across all user profiles while keeping them **100% accessible** inside Xposed and Root managers.

---

## Key Features

- **Zero-Lock System Architecture:**
  Hooks `com.android.server.pm.LauncherAppsService$LauncherAppsImpl` and `com.android.server.pm.UserManagerService` at the binder boundary instead of `PackageManagerService`. This completely eliminates `PackageManagerService` lock contention, boot deadlocks, and `ActivityNotFoundException` crashes when launching module settings.
- **Launcher-Focused Scope:**
  Specifically targets launcher visibility (`LauncherAppsService`). Filters app drawer activities, pinned launcher shortcuts, and launcher queries across all profiles without modifying low-level `PackageManager.getInstalledPackages` system queries.
- **Lock-Free Atomic Concurrency:**
  Config rules are swapped atomically using `@Volatile` immutable sets, guaranteeing zero race conditions between concurrent Binder threads and companion UI sync.
- **Complete Launcher Filtering Coverage:**
  Intercepts `getLauncherActivities`, `resolveLauncherActivityInternal` (Android 12–17), `resolveActivity` (Android 11), and `isActivityEnabled` (Android 11–17) so both app drawer entries and workspace items are cleanly filtered.
- **True Per-User & Multi-Profile Isolation:**
  Visibility rules are keyed by `userId:packageName`. Hiding or unhiding an application in `User 0` (Owner) never affects `User 10` (Work Profile / Clone Profile), MIUI `999` (Dual Apps), Samsung `150` (Secure Folder), Android 15–17 Private Space, or Secondary Users, and vice versa.
- **Universal 3-Tier Xposed Module Detector (O(1) Cache):**
  Automatically detects modules across base and split APKs with negative cache poisoning protection:
  - **Tier 1 (Manifest Meta-Data):** `xposedmodule`, `xposedminversion`, `xposeddescription`, `xposedscope`, `libxposed`, `libxposed_minversion`, `libxposed_api`, `libxposed_scope`
  - **Tier 2 (Classic Xposed API):** Fast APK zip central directory inspection for `assets/xposed_init`
  - **Tier 3 (Modern LibXposed API 100):** Fast APK zip central directory inspection for `META-INF/xposed/module.prop`
- **Caller-Aware Manager Bypass & Cached AppId:**
  Queries originating from `android`, `XHide`, or protected managers (`LSPosed`, `Vector`, `LSPatch`, `EdXposed`, `KernelSU`, `KSU Next`, `Magisk`, `APatch`) are never filtered. Caller AppId is cached in memory to ensure instantaneous O(1) integer verification without repeated PackageManager IPC.
- **Batch Live Launcher Synchronization:**
  Toggling any rule triggers `MyPackageMonitor.onPackageChanged` with batched `IOnAppsChangedListener` callbacks across all user profiles, eliminating broadcast storms.
- **Fail-Safe Crash Protection & Secure Diagnostics:**
  Includes a global uncaught exception handler with infinite restart loop protection and a 1-tap GitHub Issue diagnostics reporter with Android 13+ sensitive clipboard protection (`ClipDescription.EXTRA_IS_SENSITIVE`).
- **Material 3 Companion UI:**
  - Clean 3-tab portrait interface (`Home`, `Apps`, `Settings`) with per-user filter chips (`User 0`, `User 10`, etc.).
  - Multi-language support: System Default, **English**, **Türkçe**, **Deutsch**, **Русский**, and **简体中文**.
  - Dynamic Material You colors and AMOLED Pure Black (`#000000`) theme mode.
  - Fine-grained UI / DPI scaling slider (90%..115%) with 1% steps.
  - JSON configuration backup export and import via Android Storage Access Framework (SAF).

---

## Android 11 – 17 Compatibility

Audited against AOSP source branches (`android11-release` through `main`) and HMA multi-user architecture:

| Android Version | SDK | Hook & Profile Coverage |
| :--- | :---: | :--- |
| **Android 11 (R)** | 30 | `ParceledListSlice<ResolveInfo>`, `resolveActivity`, `ensureInUserProfiles`, `canAccessProfile`, `UserManagerService.getProfileIds`, `mListeners` broadcast fallback |
| **Android 12 / 12L (S)** | 31–32 | `ParceledListSlice<LauncherActivityInfoInternal>`, `resolveLauncherActivityInternal`, `ensureInUserProfiles`, `canAccessProfile`, `UserManagerService.getProfileIds` |
| **Android 13 (T)** | 33 | `LauncherActivityInfoInternal`, `resolveLauncherActivityInternal`, `canAccessProfile`, `MyPackageMonitor.onPackageChanged`, `UserManagerService.getProfileIds` |
| **Android 14 (U)** | 34 | `LauncherActivityInfoInternal`, `resolveLauncherActivityInternal`, `canAccessProfile`, `MyPackageMonitor.onPackageChanged`, `UserManagerService.getProfileIds` |
| **Android 15 (V)** | 35 | `canAccessProfile` (2-arg & 5-arg), `canAccessHiddenProfile` (Private Space), `getUserProfiles`, `UserManagerService.getProfileIds` |
| **Android 16 (W)** | 36 | `canAccessProfile` (2-arg & 5-arg), `canAccessHiddenProfile`, `isActivityEnabled`, Clone / Work / Private Space profiles |
| **Android 17** | 37+ | Full parity with AOSP `main` `LauncherAppsService$LauncherAppsImpl` and `UserManagerService` signatures |

---

## Installation

1. Download **`XHide-v1.0.2-release.apk`** from the [Releases](https://github.com/everyoneexe/XHide/releases/latest) page and install it.
2. Open **LSPosed** or **Vector Manager**, enable **XHide**, and ensure **`System Framework` (`android`)** is selected in the module scope.
3. Reboot your device (or soft-restart `system_server`) once to activate the system hook.
4. Open **XHide** to configure automatic module hiding or toggle visibility for individual applications across any user profile in real time.

---

## Building from Source

```bash
git clone https://github.com/everyoneexe/XHide.git
cd XHide
./gradlew test assembleRelease assembleDebug
```

- **Release APK:** `app/build/outputs/apk/release/app-release.apk`
- **Debug APK:** `app/build/outputs/apk/debug/app-debug.apk`

---

## Acknowledgements & Special Thanks

- **[@MrRaita](https://github.com/MrRaita)** — For conducting a comprehensive static code review on v1.0.3, identifying concurrency race conditions in `ConfigStore`, Binder payload safety thresholds, hook isolation opportunities, and hardening security vectors across the codebase.

---

## License

Released under the [MIT License](LICENSE).
