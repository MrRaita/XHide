# XHide v1.0.5 - Release Notes

This release represents a major milestone in XHide's development: a complete architectural migration from legacy Xposed API 82 to modern LibXposed (supporting API 102 in-memory hot reload with stable API 101 fallback), resilient offline synchronization with system-first rule adoption, a complete Material 3 Expressive motion overhaul, and critical reliability fixes implemented in close collaboration with @MrRaita (https://github.com/MrRaita).

---

## Key Highlights

### Core Engine and LibXposed Architecture
* **LibXposed Migration**: Completely decoupled the hooking engine from legacy `XposedBridge` and `XposedHelpers` using a runtime-neutral `HookBackend` interface, fully purging classic API 82 artifacts (`assets/xposed_init`, `MainHook`, `xposedminversion="82"`).
* **API 102 Hot Reload Support**: Introduced dynamic in-memory hot reload on supported LibXposed runtimes (e.g. Vector-SR) via `replaceHook`, allowing seamless module updates without rebooting or restarting `system_server`.
* **API 101 Fallback Compatibility**: Configured `minApiVersion=101` and `targetApiVersion=102` with runtime capability negotiation and separate verification test suites, ensuring stable operation on standard LibXposed runtimes.
* **Pure AOSP LauncherApps Architecture**: Completely purged legacy `PackageManagerService` hooks in favor of clean `LauncherAppsService` interception (`resolveLauncherActivityInternal`, `isActivityEnabled`, `getApplicationInfo`, `shouldHideFromSuggestions`), guaranteeing absolute system stability without deadlocks or bootloops.
* **One UI Home Instant Unhide**: Resolved propagation delays where unhidden apps took up to 10 minutes to appear on Samsung One UI Home by eliminating event race conditions and isolating profile broadcast pipelines.
* **Multi-User Profile Event Isolation**: Retained profile identifiers (`userId:packageName`) in IPC and ConfigStore, strictly filtering `broadcastOnPackageChangedBatch` so secondary profile listeners (e.g. Work Profile / Secure Folder) never receive primary profile events.
* **Signal Collision Elimination**: Removed redundant `onPackageChanged` broadcasts during unhide operations, dispatching only standard AOSP `onPackageAdded` and `onPackagesAvailable` to prevent OEM launcher internal data operator drops.
* **Launcher Startup Reconciliation Pulse**: Scheduled a delayed reconciliation pulse and follow-up broadcast (2.5s post-query) to eliminate race conditions where busy launchers drop package removal events during heavy cold-boot loading windows.
* **LibXposed System Framework Scope**: Included `android` alongside `system` in `META-INF/xposed/scope.list` for automatic system framework recognition across modern LibXposed managers.
* **Persistent Module Verdict Cache**: Cached APK module verification results across reboots with bounded LRU eviction and atomic file persistence, eliminating repetitive ZIP re-scans on Binder threads.

### Synchronization, Offline Safety, and Integrity
* **Offline Rule Queueing**: Toggles and configuration adjustments made while the system hook is updating, restarting, or inactive are safely queued in persistent storage and automatically replayed in order once the hook reconnects.
* **System-First Rule Adoption**: The companion application now reads and adopts the system's persisted rules upon startup before submitting local updates, ensuring a cleared local app state or fresh install never overwrites active system configurations.
* **Multi-User Stale Rule Protection**: Isolated package presence queries to prevent stale-rule cleanup from ever wiping rules belonging to unmounted secondary profiles or unseen users.
* **IPC Payload Bounds & Integrity**: Enforced strict size limits (`MAX_IPC_PAYLOAD_CHARS = 96KB`) with validation on IPC transfers to prevent Binder buffer overflows and eliminate silent data truncation.
* **Atomic Storage & Directory Fsync**: Enforced atomic replacement and parent directory fsync operations for all rule files and module caches to guard against sudden power loss corruption.

### UI, Expressive Motion, and User Experience
* **Material 3 Expressive Motion Overhaul**: Rebuilt interface transitions using official Material Expressive design principles, featuring fluid pager tab navigation, predictive back gesture support, and dynamic status badge morphing.
* **Prioritize Hidden Applications**: Hidden applications are automatically elevated to the top of the list with smooth spring animations, making it effortless to review and manage hidden apps. (Implemented following recommendations by @MrRaita)
* **Smooth Placement Animations**: Eliminated opacity flickering, tearing, and visual jitter during list item reordering by fine-tuning `animateItem` with non-bouncy spring physics and disabling redundant fade specs.
* **Isolated List View Scoping**: Scoped list item keys by filter mode, search state, and sorting criteria to prevent cross-filter animation glitches, with automated scroll-to-top anchoring upon filter changes.
* **Custom Application Sorting**: Added extensive sorting options accessible from the search filter sheet, allowing applications to be sorted by Name (A-Z / Z-A), Installation Date, and Last Update Date, with persistent preference storage.
* **Modernized Pull-to-Refresh**: Replaced the canvas arc with a crisp Material Vector refresh icon, ensuring full rotational cycles at threshold before smooth retraction without visual stutter.
* **AMOLED Pure Black by Default**: Enabled high-contrast pure black (#000000) surfaces by default for maximum OLED power efficiency and clarity.
* **Theme Contrast Optimization**: Corrected text and surface container contrast across dynamic Material You themes to guarantee legibility under all system accent colors.
* **API 30 Backup Compatibility**: Replaced Java 9 `readNBytes` with a bounded stream reading loop, ensuring seamless backup imports on Android 11 and 12 devices (`minSdk = 30`).

---

**Full Changelog**: https://github.com/everyoneexe/XHide/compare/v1.0.4...v1.0.5
