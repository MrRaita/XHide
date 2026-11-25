# Android 8–13 Notları (İleride Değerlendirmek İçin)

## 1. Android 8–13 İkon Değiştirme Alternatifi (Launcher Scope Yaklaşımı)

### Mevcut Durumun Sınırı (`System Framework` Scope'u)
- `system_server` launcher'lara ikon pikseli (`Bitmap`/`Drawable`) değil, yalnızca `int icon` kaynak ID'si ve hedef APK'nın `sourceDir` yolunu gönderir.
- `FabricatedOverlay` ile PNG (`ParcelFileDescriptor`) desteği **Android 14 (API 34)** ile geldiği için Android 8–13'te yalnızca `System Framework` scope'unda kalarak dinamik ikon değiştirme yapılamaz.

### Daha Performanslı ve Az Hook'lu Alternatif
Eğer Android 8–13 desteği veya SELinux modülsüz ikon değiştirme istenirse:
- **Scope:** Kullanıcının kendi **Launcher** uygulamasını (isteğe bağlı olarak Ayarlar'ı) LSPosed/Vector kapsamına (`scope`) eklemek.
- **Gereken Hook'lar (1–2 adet):**
  1. `android.content.pm.LauncherActivityInfo.getIcon(int density)` *(ve/veya `getBadgedIcon`)*
  2. `android.app.ApplicationPackageManager.getDrawable(String packageName, int resId, ApplicationInfo appInfo)`
- **Artıları:**
  - Android 8'den 17'ye kadar tüm sürümlerde çalışır.
  - `idmap2d`, `FabricatedOverlay`, `ParcelFileDescriptor` ve **SELinux modülü (`fadalias_sepolicy`) gerekmez**.
  - `<overlayable>` engeline takılmaz; değişmeyen uygulamalarda `O(1)` erken çıkışla çok yüksek performans verir.
- **Eksisi:** Tek başına `System Framework` scope'u yetmez; kullanıcının launcher uygulamasını da scope'a eklemesi gerekir.

---

## 2. Android 8–10 Uygulama Gizleme (Rafa Kaldırıldı)
- Şu an `SDK < 30` için gizleme hook'ları (`shortcuts`, `resolve`, `shouldHide` filtresi) kapalıdır; yalnızca **isim değiştirme** (`PackageParser` + `applyLooks`) aktiftir.
- İleride Android 8–10 test cihazı/emülatörü ile gizleme açılmak istenirse:
  - `Model.kt` içindeki `Caps.hide` ve `LauncherHook`/`Coordinator`/`Notifier` içindeki `SDK >= 30` sınırı `>= 26` yapılmalı,
  - `LauncherAppsImpl` için `getShortcuts(legacy)`, `resolveActivity`, `isPackageEnabled` (2 ve 3 arg), `isActivityEnabled` (2 ve 3 arg), `getApplicationInfo`, `getActivityLaunchIntent` ve Android 8 `canAccessProfile(UserHandle, String)` `optional` hook'ları doğrulanmalıdır.
  - Android 11–17 için eklenen 7 gizleme hook'u da 8–10 için uyarlanmalıdır:
    - `PackageManagerService.queryIntentActivitiesInternal`: Android 8–10'da 5'ten az parametreli (`4` argümanlı vb.) imzalar da bulunduğundan `t.size >= 5` filtresi genişletilmeli ve `userId` indeksi buna göre çözülmelidir.
    - `LauncherAppsImpl` (`getShortcutConfigActivities`, `getShortcutConfigActivityIntent`, `getShortcutIntent`, `startShortcut`): Android 8'de `callingPackage` parametresi olmayan eski imzalar kontrol edilmelidir.
    - `ShortcutService.getShareTargets`: Android 10 (API 29) ile geldiği için Android 8–9'da `MISSING` kalması normaldir; `ShortcutService.getShortcuts` imzası ise 8–10 sürümlerinde doğrulanmalıdır.
