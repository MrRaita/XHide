# XHide

A lightweight, zero-deadlock, system-level Xposed/LSPosed/Vector module that cleanly hides Xposed module icons from all Android launchers (home screen, app drawer, and launcher search) while keeping them 100% accessible inside Xposed/LSPosed/Vector managers.

## Why XHide?

Traditional icon-hiding modules hook `PackageManagerService.queryIntentActivities` or disable package components (`pm disable`), which causes severe issues on modern Android (Android 12–15 / One UI 6+ / HyperOS):
- **PMS Lock Contention & Watchdog Reboots:** Calling `PackageManager` inside a `PackageManagerService` hook triggers recursive lock deadlocks, causing Android Watchdog to kill `system_server`.
- **Broken Boot Services:** Indiscriminate `queryIntentActivities` filtering breaks `android.ext.services` and `AppSearchManager` during boot.
- **Broken Manager Launch:** Disabling a module's main activity prevents LSPosed/Vector Manager from opening its settings screen.

### How XHide Solves This
Instead of touching `PackageManagerService`, **XHide** hooks the AOSP CTS-standardized launcher IPC boundary in `system_server`:
```java
com.android.server.pm.LauncherAppsService$LauncherAppsImpl#getLauncherActivities(
    String callingPackage,
    String packageName,
    UserHandle user
)
```

1. **Zero Boot Impact:** Core OS services (`android.ext.services`, `AppSearch`, Knox, etc.) never call `LauncherAppsService`. It is only invoked by launchers after boot.
2. **Caller-Aware Filtering:** Queries from Xposed managers (`org.lsposed.manager`, `io.github.vector`, etc.) are never filtered, so you can always open module UIs from your manager.
3. **3-Tier Universal Module Detection (O(1) Cache):**
   - **Tier 1 (Manifest Meta-Data):** Detects `xposedmodule`, `xposedminversion`, `xposeddescription`, `xposedscope` (classic & YukiHookAPI / ServiceLoader modules).
   - **Tier 2 (Classic Asset):** Detects `assets/xposed_init` inside the APK.
   - **Tier 3 (Modern LibXposed API 100):** Detects `META-INF/xposed/module.prop` and `META-INF/xposed/java_init.list`.
4. **Real-Time Launcher Refresh:** Toggling an app in the Material 3 companion UI dispatches `MyPackageMonitor.onPackageChanged` directly inside `LauncherAppsService`, refreshing Niagara, One UI Home, Pixel Launcher, Nova, and Lawnchair in real time without restarting the launcher.

## Requirements
- Android 10 – 15 (AOSP, One UI, Pixel UI, HyperOS, ColorOS, etc.)
- LSPosed / Vector Framework
- **Scope:** Only `System Framework` (`android`)

## License
MIT License
