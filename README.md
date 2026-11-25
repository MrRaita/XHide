# Fadalias

An LSPosed/Vector module that renames apps, changes their icons and hides them, with a Material 3 Expressive companion app.
It merges the Rebrand and XHide modules. The name and package id are provisional and live in `Identity.kt`, `app/build.gradle.kts` and `sepolicy-module/`.

| Feature | Android |
|---|---|
| Rename | 8+ (8-10 path untested) |
| Hide | 11+ |
| Change icon | 14+ (needs the SELinux module) |

The companion app hides features the running Android version does not support.

## Architecture
- **Hook (system_server):** `LauncherAppsService`, `UserManagerService.getProfileIds` and `PackageInfoUtils`. A single `getLauncherActivities` hook handles IPC, filtering, names and icons.
- **IPC:** the command travels in the package-name argument of `getActivityList` (`__fadalias_ipc__:ping|status|log|reload|state.get|state.update:<json>|all_users`); the reply is JSON in `ApplicationInfo.metaData["dev.fadalias.REPLY"]`. An empty list means rejected or unreachable. The caller is verified by package name and signature (pin it with `FADALIAS_CERT_SHA256`).
- **State:** `/data/system/fadalias/state` (+ `.bak`), written atomically with fsync; icons in `icons/<pkg>.png`. The companion app keeps its own `rules.json` and `pending.json` (offline queue); the hook state is a replica. Sync: replay the queue, compare hashes, `Replace` on mismatch (adopt the hook state on a fresh empty store). A rejected change is reverted locally.
- **Icon layer:** FabricatedOverlay (Android 14+) fed from the manager's `RulesProvider` (uid 1000 only). The SELinux policy lives in `sepolicy-module/`; the app offers it as a zip ("Save module file").
- **Permissions:** `QUERY_ALL_PACKAGES`, `RECEIVE_BOOT_COMPLETED`. No `INTERNET`.
- **libxposed:** the core is compiled against API 101; the API 102 sources (hot reload) live in `src/api102` and are guarded at runtime. `./gradlew :app:verifyApi101Core` checks that the core compiles against 101 only.

## System apps and Fadalias itself
- System apps are hidden from the list unless the switch in the sort menu is on (first enable shows a risk warning).
- Name and icon of any system app, and of Fadalias itself, can be changed after a warning ("don't show again" is available). Only the `android` and `system` framework packages are locked.
- Fadalias always sees its own real name; custom names and icons only show up in the launcher and the system. Data is keyed by package name, so renaming never touches it.
- If you hide Fadalias, open it again from LSPosed -> Modules -> Fadalias.

## Build
Requires JDK 21 and the Android SDK (platform 37).
```
./gradlew assembleDebug assembleRelease
./gradlew test
```
Release builds are R8-minified and signed with the debug key unless `FADALIAS_KEYSTORE`, `FADALIAS_KEYSTORE_PASSWORD`, `FADALIAS_KEY_ALIAS` and `FADALIAS_KEY_PASSWORD` are set.
`scripts/pack-sepolicy.sh` rebuilds the bundled `fadalias_sepolicy.zip` after editing `sepolicy-module/`.

## Install
1. Install the APK, enable the module in LSPosed/Vector (scope: System Framework) and reboot.
2. For icons: save the SELinux module zip from the app, install it with your root manager and reboot.

## Recovery (boot loop)
| Root | Action |
|---|---|
| KernelSU | Hold Volume Down during boot (3+ presses) to disable modules |
| Magisk | Safe mode, or create `/data/adb/modules/<id>/disable` |
| APatch | Safe mode via the power key during boot |

If needed, delete `/data/system/fadalias/`. Uninstalling the SELinux module does not remove the state directory.

## Known limits
- The icon overlay is enabled for the requesting user and profiles where the package exists at that time; use "Refresh launchers now" after installing an app later.
- Name and icon are per package (not per profile); hiding is per profile.
- Icon previews are lost if the app data is cleared (the overlays keep working); reset and pick again.
- No backup/restore yet.

## License
MIT, see `LICENSE`.
