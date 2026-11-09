# XHide UI Kaynak Kodu İncelemesi ve Düzeltme Takip Listesi (v1.0.3 -> v1.0.4)

> **Kaynak:** [@MrRaita](https://github.com/MrRaita) tarafından hazırlanan UI ve Mimari İnceleme Raporu.  
> **Kural:** Her madde ayrı bir commit olarak uygulanır, Galaxy S24 üzerinde canlı test edilir ve commit mesajlarında `@MrRaita`'ya atıf yapılır.

---

## 1. Hatalar (Bugs)

- [x] **1.1 Senkronizasyon Sonucu Kontrolü ve Geri Alma (Revert):** `syncWithSystem` fire-and-forget çalışıyordu. `saveUiPrefsAndSync` başarısız olduğunda kullanıcıya hata bildirim tostu (`syncFailedToast`) gösterilmesi ve UI üzerindeki toggle durumunun önceki haline geri alınması sağlandı.
- [x] **1.2 Senkronizasyon Sıralama & Yarış Durumu (Mutex/Queue):** Hızlı switch geçişlerinde eşzamanlı coroutine'lerin sırasının bozulmasını önlemek için coroutine senkronizasyon kuyruğu (`Mutex.withLock`) uygulandı.
- [x] **1.3 Dışa Aktarma / Kopyalama Tost Mesajı Düzeltmesi:** Dışa aktarma hatasında `importFailedToast` gösterilme hatası düzeltildi; `exportSuccessToast` ve `exportFailedToast` doğru bağlamlarla yerelleştirildi.
- [x] **1.4 Sıfırlama ve İçe Aktarma Onay Diyalogları:** `onResetAll` ve yedekten içe aktarma işlemleri kullanıcıdan teyit alan onay diyaloglarına (`AlertDialog`) bağlandı; içe aktarmada geçerli ve atlanan geçersiz kural sayıları önizlenerek onay alındı.
- [x] **1.5 Başlangıçta Kırmızı "INACTIVE" Yanıp Sönmesi:** `isHookActive` başlangıç değeri `Boolean?` (null) yapılarak ilk ping tamamlanana kadar nötr/denetleniyor durumu (`badgeChecking`, `hookCheckingTitle`) gösterildi; kırmızı rozetin açılışta anlık yanıp sönmesi engellendi ve rozet metinleri yerelleştirildi.
- [x] **1.6 Dinamik Sürüm Bilgisi:** Ayarlar ekranındaki sabit `"v1.0.2"` / `"v1.0.3"` metni yerine `BuildConfig.VERSION_NAME` dinamik sürüm bilgisi bağlandı.
- [x] **1.7 Ana Sayfa Modül Kartı Metni:** "APK / Manifest / API 100" yerine modern modül tespit mimarisine uygun güncel "LibXposed & Manifest" açıklaması yerelleştirildi.
- [x] **1.8 Korunan Yöneticiler Listesi Senkronizasyonu:** Ayarlardaki liste `KNOWN_MANAGERS` içeriğindeki tüm yöneticilerle senkronize edildi (Vector, LSPosed, KernelSU, KSU Next, Magisk, APatch, LSPatch, EdXposed).
- [x] **1.9 Sistem Çubuğu Renkleri ve Dinamik Edge-to-Edge:** Tema modu değişimlerini izleyen `DisposableEffect` ile `enableEdgeToEdge()` dinamik `SystemBarStyle` çağrıları entegre edildi; Açık/Koyu tema geçişlerinde sistem çubuğu simgelerinin okunabilirliği sağlandı.
- [x] **1.10 Başlangıç Penceresi (Window Preview Theme):** Manifestteki sabit koyu tema yerine `res/values/themes.xml` ve `res/values-night/themes.xml` ile DayNight uyumlu `Theme.XHide` tanımlandı; açık tema kullanıcılarının başlangıçta siyah pencere görmesi engellendi.
- [x] **1.11 Profil Listesi Hata Bildirimi:** Sistemdeki kullanıcı profilleri (`UserManager` / `LauncherApps.profiles`) doğrudan taranarak boş/kilitli profillerin sessizce kaybolması önlendi ve profil listesi sistem seviyesinde senkronize edildi.
- [x] **1.12 Uygulama İkonu Önbellek İptali:** `AppEntry` modeline `lastUpdateTime` eklendi; `appIconLruCache` anahtarı `$userId:$packageName:$lastUpdateTime` yapılarak uygulama güncellendiğinde ikonların otomatik olarak yenilenmesi sağlandı.
- [x] **1.13 Sistem Dili Değişikliği Takibi:** `MainScreen` içerisindeki dil çözümlemesi `LocalConfiguration.current` ile reaktif hale getirilerek sistem dili değiştirildiğinde UI metinlerinin dinamik yenilenmesi sağlandı.

---

## 2. Performans ve Bellek (Performance & Memory)

- [x] **2.1 Liste Yükleme Sırasında İkonların Ayrılması:** Liste önce metin ve durum bilgileriyle anında render edilmeli, ikonlar satırlar ekrana girdikçe asenkron yüklenmeli (Tamamlandı).
- [x] **2.2 Dinamik İkon Boyutlandırma:** İkon çizim boyutu cihaz ekran yoğunluğuna (`displayMetrics.density`) göre dinamik hesaplanacak şekilde optimize edildi.
- [x] **2.3 Yapılandırma Değişikliklerinde Durum Koruma (rememberSaveable):** `currentTab` ve `searchQuery` `rememberSaveable` ile sarılarak tema/DPI değişikliklerinde durum kaybı önlendi.
- [x] **2.4 Yenile Düğmesi Aşırı İş Yükü ve Çift Tıklama Koruması:** `refreshState` fonksiyonuna `isLoading || isRefreshing` yeniden giriş kilidi eklendi; gereksiz toplu IPC re-sync döngüsü kaldırıldı.
- [x] **2.5 Paket Kurulum/Kaldırma Dinleyicisi:** Gerçek zamanlı `ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REMOVED` dinleyicisi ve `onResume` tazelemesi (Tamamlandı).
- [x] **2.6 Sekme Geçişlerinde Kaydırma Konumunu Koruma:** `LazyListState` ve `ScrollState` nesneleri `MainScreen` seviyesine taşınarak sekme geçişlerinde kaydırma konumunun korunması sağlandı.
- [x] **2.7 Lambda ve Kararsız Nesne Optimizasyonu:** `isEntryHidden` lambda çağrısı `remember` ile önbelleğe alınarak gereksiz Compose recomposition tetiklemeleri önlendi.
- [x] **2.8 Çökme Dosyası Okumasını IO İş Parçacığına Taşıma:** `pendingCrashReport` dosya okuması ana thread'den `Dispatchers.IO` üzerine taşındı.
- [x] **2.9 Arama Alanına Debounce Ekleme:** Arama sorgusuna 200ms `delay` debounce eklenerek hızlı yazımda gereksiz filtreleme yükü engellendi.

---

## 3. Erişilebilirlik ve Kullanılabilirlik (Accessibility & Usability)

- [x] **3.1 Tipografi ve Yazı Boyutları:** Metinler ve rozetler sistem yazı tipi ölçeğiyle uyumlu hale getirildi; taşmalar önlendi.
- [x] **3.2 Dinamik Yükseklik Sınırları:** Sabit yükseklikler (`height(30.dp)`) yerine `heightIn(min = ...)` kullanılarak büyük yazı tiplerinde metinlerin kırpılması engellendi.
- [x] **3.3 Navigasyon Çubuğu Boşluğu:** `NavigationBar` üzerindeki mükerrer `.navigationBarsPadding()` kaldırılarak çift boşluk riski giderildi.
- [x] **3.4 Switch Semantikleri ve TalkBack Desteği:** `Modifier.toggleable(role = Role.Switch)` uygulanarak TalkBack için çift odak ve çift okuma engellendi.
- [x] **3.5 Dekoratif İkonlar ve Erişilebilirlik Açıklamaları:** Uygulama etiketinin yanındaki ikonların `contentDescription` değeri `null` yapıldı; arama kapatma butonuna `IconButton` dokunma alanı eklendi.
- [x] **3.6 Satır Tıklama Alanı:** Kaydırma sırasında yanlışlıkla toggle tetiklenmesini önlemek için dokunma semantiği `toggleable` ile tekil hale getirildi.
- [x] **3.7 Geri Tuşu Yönetimi (`BackHandler`):** Uygulamalar veya Ayarlar sekmesindeyken geri basıldığında uygulamadan çıkmak yerine Ana Sayfa sekmesine dönülmesi sağlandı.
- [x] **3.8 Arama Alanı Klavye Eylemi:** `KeyboardOptions(imeAction = ImeAction.Search)` ve `KeyboardActions(onSearch = { focusManager.clearFocus() })` klavye kapatma desteği eklendi.
- [x] **3.9 Anlaşılır Profil Adları:** Yalnızca "User 10" yerine profil türünü (Ana Profil, İkincil / Klon Profil) belirten açıklamalar eklendi.
- [x] **3.10 Dile Duyarlı Sıralama (`Collator`):** Etiket sıralamasında `lowercase()` yerine `Collator.getInstance(Locale.getDefault())` kullanılarak Türkçe karakterlerin (Ç, Ğ, İ, Ö, Ş, Ü) doğru sıralanması sağlandı.
- [x] **3.11 Pano Kopyalama Onayı:** "Sorun bildir" çalıştırılmadan önce panonun üzerine yazılacağına dair kullanıcıya onay diyaloğu eklendi.

---

## 4. Yerelleştirme ve Tema (Localization & Theme)

- [x] **4.1 Sabit İngilizce Metinlerin Taşınması:** "ACTIVE", "INACTIVE", "System Launcher", "User N", "GitHub" gibi sabit metinler `Strings.kt` içerisine taşındı.
- [x] **4.2 Teşhis Tostunun Çoklu Dil Desteği:** `AppDiagnostics.openGitHubIssue` ve teşhis bildirimleri yerelleştirildi.
- [x] **4.3 AMOLED Modu Yüzey Renkleri:** `surfaceContainerHigh`, `surfaceContainerHighest`, `surfaceVariant`, `surfaceBright` renkleri AMOLED modunda saf siyah (`#000000`) paletine uyarlandı.
- [x] **4.4 Tip Güvenli Enum Yapıları:** `ThemeMode` ve `AppLanguage` için tip güvenli `enum class` tanımlandı.
