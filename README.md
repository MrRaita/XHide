# HFW

An LSPosed/Vector module (`dev.hfw`) that renames apps, changes their icons, and hides them across profiles, with a Material 3 Expressive companion app.

Built with major contributions from [@MrRaita](https://github.com/MrRaita).

| Feature | Android | Scope |
|---|---|---|
| Rename | 8+ (8-10 path untested) | Global & per-profile |
| Hide | 11+ | Per-profile (with toggleable intent chooser, shortcuts, shortcut configs & share targets) |
| Change icon | 14+ (needs the SELinux module) | Global & per-profile (including system apps) |

The companion app hides features the running Android version does not support.

## Architecture
- **Hook (`system_server`):** `LauncherAppsService$LauncherAppsImpl`, `ShortcutService`, `ComputerEngine` / `PackageManagerService` (`queryIntentActivitiesInternal`), `UserManagerService.getProfileIds`, `IdmapDaemon` (Android 14+), and `PackageInfoUtils`.
- **IPC:** commands travel in the package-name argument of `getActivityList` (`__hfw_ipc__:ping|status|log|reload|state.get|state.update:<json>|all_users`); the reply is JSON in `ApplicationInfo.metaData["dev.hfw.REPLY"]`. An empty list means rejected or unreachable. The caller is verified by package name and signature (pin it with `HFW_CERT_SHA256`).
- **State:** `/data/system/hfw/state` (+ `.bak`), written atomically with fsync; icons in `icons/<pkg>.png` (and per-user subdirectories). The companion app keeps its own `rules.json` and `pending.json` (offline queue); the hook state is a replica. Sync replays the queue, compares canonical hashes, and reconciles mismatches.
- **Backup & Restore:** Full `.zip` export/import of rules, labels, hide choices, and custom icons via `BackupManager`.
- **Icon layer:** `FabricatedOverlay` (Android 14+) fed from the manager's `RulesProvider` (uid 1000 only), with `IdmapDaemon` `<overlayable>` bypass for `hfw_*` overlays so pre-installed/system apps work too. The SELinux policy lives in `sepolicy-module/`; the app offers it as a zip ("Save module file").
- **Permissions:** `QUERY_ALL_PACKAGES`, `RECEIVE_BOOT_COMPLETED`. No `INTERNET`.
- **libxposed:** the core is compiled against API 101; the API 102 sources (hot reload) live in `src/api102` and are guarded at runtime. `./gradlew :app:verifyApi101Core` checks that the core compiles against 101 only.

## System apps and HFW itself
- System apps are hidden from the list unless the switch in the sort menu is on (first enable shows a risk warning).
- Name and icon of any system app, and of HFW itself, can be changed after a warning ("don't show again" is available). Only the `android` and `system` framework packages are locked.
- HFW always sees its own real name; custom names and icons only show up in the launcher and the system. Data is keyed by package name, so renaming never touches it.
- If you hide HFW, open it again from LSPosed/Vector -> Modules -> HFW.

## Build
Requires JDK 21 and the Android SDK (platform 37).
```bash
./gradlew :app:verifyApi101Core :app:testDebugUnitTest
./gradlew assembleDebug assembleRelease
```
Release builds are R8-minified and signed with the debug key unless `HFW_KEYSTORE`, `HFW_KEYSTORE_PASSWORD`, `HFW_KEY_ALIAS` and `HFW_KEY_PASSWORD` are set.
`scripts/pack-sepolicy.sh` rebuilds the bundled SELinux zip (`app/src/main/assets/hfw_sepolicy.zip`) after editing `sepolicy-module/`.

## Install
1. Install the APK, enable the module in LSPosed/Vector (scope: `System Framework`) and reboot (or hot-reload on API 102).
2. For icons (Android 14+): save the SELinux module zip (`hfw_sepolicy`) from the app, install it with your root manager, and reboot.

## Recovery (boot loop)
| Root | Action |
|---|---|
| KernelSU | Hold Volume Down during boot (3+ presses) to disable modules |
| Magisk | Safe mode, or create `/data/adb/modules/<id>/disable` |
| APatch | Safe mode via the power key during boot |

If needed, delete `/data/system/hfw/`. Uninstalling the SELinux module does not remove the state directory.

## Known limits
- Icon previews are lost if the app data is cleared (the overlays keep working); export a `.zip` backup beforehand or reset and pick again.
- Launcher-owned local SQLite search caches (e.g., OEM drawer search history/indexes) clear when the launcher processes package removal events or refreshes its cache.

## Disclaimer
Provided for personal device customization and educational purposes; use at your own risk. Modifying system apps or framework behavior is the user's responsibility.

## License
MIT, see `LICENSE`.
