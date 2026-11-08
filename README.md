# XHide

A lightweight, zero-deadlock, system-level Xposed / LSPosed / Vector module that cleanly hides Xposed module icons and any user-selected app icons from all Android launchers (home screen, app drawer, and search) while keeping them 100% accessible inside Xposed/LSPosed/Vector managers.

## Features

- **System-Level Launcher Filtering:** Hooks `LauncherAppsService$LauncherAppsImpl` at the IPC boundary—zero `PackageManagerService` lock contention, zero watchdog bootloop risks.
- **Universal Multi-User & Profile Support:** Full independent per-user hiding across User 0 (Owner), User 10+ (Work Profile, Clone Profile, Dual Apps, Private Space, Secondary Users).
- **Multi-Generation Module Detection (O(1) Cache):**
  - **Tier 1 (Manifest):** `xposedmodule`, `xposedminversion`, `xposeddescription`, `xposedscope`
  - **Tier 2 (Classic API):** `assets/xposed_init` inside APK
  - **Tier 3 (Modern LibXposed API 100):** `META-INF/xposed/module.prop` and `META-INF/xposed/java_init.list`
- **Caller-Aware Manager Bypass:** Queries from Xposed managers (`LSPosed`, `Vector`, `KernelSU`, `Magisk`) are never filtered, so modules remain launchable inside your manager.
- **Instant Launcher Synchronization:** Toggling an app triggers `MyPackageMonitor.onPackageChanged` directly in `system_server`, updating launchers (One UI Home, Niagara, Nova, Lawnchair, Pixel Launcher) in real time without restarting.
- **Modern Material 3 Companion UI:**
  - 3-tab layout (`Home`, `Apps`, `Settings`) locked to portrait orientation.
  - Multi-language support (System Default, English, Türkçe, Deutsch, Русский, 简体中文).
  - Dynamic Material You theming + AMOLED Pure Black mode.
  - JSON file backup export and import via Android Storage Access Framework (SAF).

## Requirements

- Android 11 – 17 (SDK 30 – 37+) (AOSP, One UI, Pixel UI, HyperOS, ColorOS, OxygenOS, etc.)
- LSPosed, LSPosed_mod, or Vector Framework
- **Scope:** `System Framework` (`android`)

## License

MIT License
