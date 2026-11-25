# HFW v0.1.0 - Release Notes

Release of HFW, an LSPosed/Vector module for renaming apps, changing icons, and hiding apps per profile.

## Highlights
* Rename apps, change their icons (Android 14+) and hide them (Android 11+) from one module, for every user profile (main, work, clone, private).
* Offline-safe synchronization between the companion app and the system hook with a persistent pending queue and hash reconciliation.
* Companion app shows live module status, hook health and an activity log, and hides features the running Android version does not support.
* System apps are not listed by default; a switch in the sort menu enables them after a one-time risk warning.
* Changing the name or icon of system apps or of HFW itself is allowed after a warning; only `android` and `system` are locked.
* Material 3 Expressive UI with dynamic color, AMOLED theme, UI scale and English/Turkish localization.

## Requirements
* LSPosed or Vector with libxposed API 101 or newer, scope: System Framework.
* Icon changes additionally need the bundled SELinux module (saved from the app and installed with your root manager).
