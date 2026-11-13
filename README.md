# XHide

XHide is an Xposed module that hides launcher icons for Xposed modules and user-selected applications on Android 11 through Android 17 (API 30–37+).

By intercepting launcher queries at `LauncherAppsService`, XHide removes icons from home screens, app drawers, and launcher search results while keeping applications intact, fully functional, and accessible through framework managers and system settings.

---

## Features

- **Launcher-Level Icon Hiding:** Filters launcher activity queries (`LauncherAppsService`) without modifying low-level package manager states or breaking system integrations.
- **Automatic Module Detection:** Identifies installed Xposed modules across base and split APKs using standard manifest metadata (`xposedmodule`, `libxposed`) and package assets (`assets/xposed_init`, `META-INF/xposed/module.prop`).
- **Manual Control & Overrides:** Any installed application can be manually hidden or unhidden. User-defined rules always take precedence over automatic module detection.
- **Multi-Profile Support:** Manages visibility rules independently across user profiles, including Work Profiles, Clone/Dual Apps, Secure Folder, and Android 15+ Private Space.
- **Immediate Launcher Updates:** Notifies active launchers via system package callbacks when visibility toggles change, applying changes without requiring a device or launcher restart.
- **Companion Application:**
  - Material 3 interface with per-profile filtering tabs (`User 0`, `User 10`, etc.).
  - Smart package filtering: Headless hook modules without launcher icons are organized under system components to keep the main list clean.
  - Backup and restore configuration via Storage Access Framework (SAF).
  - Dynamic Material You colors, AMOLED dark theme, and fine-grained UI scaling (90%–150%).
  - Multi-language support: English, Turkish, German, Russian, and Simplified Chinese.

---

## Note on Module Detection

Automatic hiding detects modules based on conventional Xposed manifest metadata and archive markers. If an application or custom framework manager includes these metadata tags, it may be recognized as an Xposed module.

If you want any detected module or manager icon to remain visible on your launcher, simply toggle it off in the **Apps** tab. Explicit user preferences are whitelisted and always override automatic detection.

---

## Note on Icon Placement

When an app is hidden, XHide tells launchers that the package was removed so they purge it from their caches immediately. When the app is unhidden, launchers treat it like a newly installed app. As a result, the icon does not return to its previous home screen, folder, or dock position; it appears in the app drawer or in the first free home screen slot, depending on your launcher settings.

---

## Compatibility

- **Android Versions:** Android 11 (API 30) through Android 17 (API 37+).
- **Frameworks:** LSPosed, Vector, and compatible ART hook runtimes.
- **Scope:** Requires scoping to `System Framework` (`android`).

---

## Installation

1. Download the latest release APK from [Releases](https://github.com/everyoneexe/XHide/releases).
2. Install the APK on your device.
3. Open your Xposed manager (e.g. LSPosed or Vector), enable **XHide**, and ensure **System Framework (`android`)** is selected in the scope.
4. Reboot your device or restart `system_server`.
5. Open XHide to configure rules or toggle application visibility.

---

## Building from Source

Prerequisites: JDK 17+ and the Android SDK.

```bash
git clone https://github.com/everyoneexe/XHide.git
cd XHide
./gradlew assembleRelease assembleDebug
```

Output APKs:
- Release: `app/build/outputs/apk/release/app-release.apk`
- Debug: `app/build/outputs/apk/debug/app-debug.apk`

---

## Acknowledgements

- **[@MrRaita](https://github.com/MrRaita)** — For valuable static code review feedback, bug reports, and suggestions that helped improve concurrency safety and hook reliability.

---

## License

This project is licensed under the [MIT License](LICENSE).
