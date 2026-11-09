# Launcher Synchronization and Android 16 Internals

This document details the internal architecture of Android's `LauncherAppsService`, the exact method and field signatures verified against Android 16 (API 36, Baklava), and the synchronization dynamics of OEM launchers (including Samsung One UI Home, Xiaomi HyperOS, and ColorOS).

---

## 1. Android 16 `LauncherAppsService` Architecture

In Android 16 (SDK 36), launcher management is coordinated by `com.android.server.pm.LauncherAppsService` and its inner implementation `LauncherAppsImpl`.

Launchers register client listeners via `LauncherApps.registerCallback()`, which communicates over Binder with `LauncherAppsImpl`.

### Verified System Fields (`LauncherAppsImpl`)

Extracted directly from Android 16 (`SDK 36`) `services.jar`:

| Field Name | Type | Access | Description |
| :--- | :--- | :--- | :--- |
| `mPackageMonitor` | `LauncherAppsImpl.MyPackageMonitor` | `public final` | Subclass of `com.android.internal.content.PackageMonitor`. Listens to system package broadcast intents. |
| `mListeners` | `LauncherAppsImpl.PackageCallbackList` | `public final` | Subclass of `android.os.RemoteCallbackList<IOnAppsChangedListener>`. Manages active launcher Binder callbacks and cookies. |

### Verified Method Signatures

#### `MyPackageMonitor.onPackagesUnavailable`
```java
public final void onPackagesUnavailable(String[] strArr)
```
- **Parameter:** `String[]` containing the affected package names.
- **User Resolution:** Resolves the active user internally via `new UserHandle(getChangingUserId())`.
- **Broadcast Delegation:** Iterates over `mListeners.beginBroadcast()`, validates profile accessibility via `isEnabledProfileOf()`, filters visible packages per listener via `mgetFilteredPackageNames()`, and invokes `IOnAppsChangedListener.onPackagesUnavailable()`.

#### `IOnAppsChangedListener` Callback Interface
The underlying AIDL interface implemented by launcher processes:
```java
void onPackageRemoved(UserHandle user, String packageName);
void onPackageAdded(UserHandle user, String packageName);
void onPackageChanged(UserHandle user, String packageName);
void onPackagesAvailable(UserHandle user, String[] packageNames, boolean replacing);
void onPackagesUnavailable(UserHandle user, String[] packageNames, boolean replacing);
void onPackagesSuspended(UserHandle user, String[] packageNames, Bundle launcherExtras);
void onPackagesUnsuspended(UserHandle user, String[] packageNames);
void onShortcutChanged(UserHandle user, String packageName, ParceledListSlice shortcuts);
```

---

## 2. OEM Launcher Caching and Database Persistence

Standard AOSP launchers query `LauncherApps.getActivityList()` frequently. However, major OEM launchers utilize local SQLite persistence for workspace and drawer layouts.

### Samsung One UI Home (`com.sec.android.app.launcher`)
- **Database:** `launcher.db`
- **Key Tables:**
  - `favorites`: Home screen workspace grid items and folders.
  - `appsTray`: App drawer grid order and custom page arrangements.
- **Callback Handling (`LauncherApps.Callback`):**
  - **`onPackageChanged(String, UserHandle)`:** Updates localized labels and icons in-memory. **Does not delete** entries from `appsTray` or `favorites`.
  - **`onPackageRemoved(String, UserHandle)`:** Executes immediate removal from in-memory collections and deletes records from `appsTray` via `removeIf`.
  - **`onPackagesUnavailable(String[], UserHandle, boolean)`:** Marks packages as unavailable or removes workspace representations depending on mode.

### Xiaomi HyperOS / MIUI Home & ColorOS
Similar to One UI Home, app drawer state and workspace placement are indexed in internal databases. Firing generic `onPackageChanged` events does not purge items from the persistent database index.

---

## 3. Recommended Multi-OEM Synchronization Strategy

Hooking directly into launcher client processes (`com.sec.android.app.launcher`, `com.miui.home`, etc.) is inherently fragile due to:
1. Package name fragmentation across vendors and regions.
2. In-process database and viewmodel schema changes between minor OEM updates (e.g., One UI 8.0 to 8.5).
3. Requiring manual scope configuration by users in Xposed managers.

### The Robust System-Level Approach:
By intercepting and broadcasting from `system_server` via `LauncherAppsImpl.mListeners`:
1. When an application is **hidden**: Broadcast `onPackageRemoved(userHandle, packageName)` or `onPackagesUnavailable(userHandle, new String[]{packageName}, false)` directly through `mListeners`. OEM launchers immediately evict the icon from their SQLite caches and workspace views.
2. When an application is **unhidden**: Broadcast `onPackageAdded(userHandle, packageName)` or `onPackagesAvailable(userHandle, new String[]{packageName}, false)` through `mListeners`. Launchers re-query `LauncherApps.getActivityList()` and insert the application.
3. Keep `getLauncherActivities` and `resolveLauncherActivityInternal` filtered so that even if the launcher performs a full database resync or device restart, the application remains completely hidden.
