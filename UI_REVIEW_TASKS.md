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
- [ ] **1.6 Dinamik Sürüm Bilgisi:** Ayarlar ekranındaki sabit `"v1.0.2"` metni yerine `BuildConfig.VERSION_NAME` kullanılmalı.
- [ ] **1.7 Ana Sayfa Modül Kartı Metni:** "APK / Manifest / API 100" yerine modern modül tespit mimarisine ("Modern LibXposed & Manifest Meta-Data") uygun güncel açıklama yazılmalı.
- [ ] **1.8 Korunan Yöneticiler Listesi Senkronizasyonu:** Ayarlardaki sabit metin `KNOWN_MANAGERS` içeriğindeki tüm yöneticileri yansıtmalı (Vector, LSPosed, KernelSU, Magisk, EdXposed, LSPatch, KSU Next, APatch).
- [ ] **1.9 Sistem Çubuğu Renkleri ve Dinamik Edge-to-Edge:** Uygulama içi tema değiştirildiğinde (Açık/Koyu/AMOLED) `enableEdgeToEdge()` dinamik `SystemBarStyle` ile çağrılarak durum çubuğu ve navigasyon çubuğu ikonlarının okunabilirliği garanti edilmeli.
- [ ] **1.10 Başlangıç Penceresi (Window Preview Theme):** Manifestteki sabit koyu pencere teması yerine sistem temasına uyumlu veya adaptif tema tanımlanmalı.
- [ ] **1.11 Profil Listesi Hata Bildirimi:** Kilitli Private Space veya duraklatılmış profiller sorgulanamadığında hata sessizce yutulmamalı, kullanıcıya bilgi verilmeli.
- [ ] **1.12 Uygulama İkonu Önbellek İptali:** `appIconLruCache` anahtarına `lastUpdateTime` veya `versionCode` eklenerek uygulama güncellendiğinde eski ikonun kalması önlenmeli.
- [ ] **1.13 Sistem Dili Değişikliği Takibi:** Sistem dili uygulama açıkken değiştiğinde "SYSTEM" modunun dinamik olarak yenilenmesi sağlanmalı.

---

## 2. Performans ve Bellek (Performance & Memory)

- [ ] **2.1 Liste Yükleme Sırasında İkonların Ayrılması:** Liste önce metin ve durum bilgileriyle anında render edilmeli, ikonlar satırlar ekrana girdikçe asenkron yüklenmeli (Tamamlandı & denetlenecek).
- [ ] **2.2 Dinamik İkon Boyutlandırma:** İkonlar sabit 96x96 yerine cihaz ekran yoğunluğuna (`density`) göre ölçeklenmeli ve `LruCache` sınırı bellek boyutuna göre optimize edilmeli.
- [ ] **2.3 Yapılandırma Değişikliklerinde Durum Koruma (rememberSaveable / State Hoisting):** Ekran yönü, yazı tipi ölçeği veya tema değiştiğinde `currentTab`, `searchQuery` ve `filterMode` sıfırlanmamalı.
- [ ] **2.4 Yenile Düğmesi Aşırı İş Yükü ve Çift Tıklama Koruması:** `refreshState` gereksiz tüm modülleri baştan göndermek yerine yalnızca durumu yeniden okumalı; art arda hızlı tıklamalara karşı debounced / mutex koruması eklenmeli.
- [ ] **2.5 Paket Kurulum/Kaldırma Dinleyicisi:** Gerçek zamanlı `ACTION_PACKAGE_ADDED` / `ACTION_PACKAGE_REMOVED` dinleyicisi ve `onResume` tazelemesi (Tamamlandı & denetlenecek).
- [ ] **2.6 Sekme Geçişlerinde Kaydırma Konumunu Koruma:** `LazyListState` sekme `when` bloğunun dışına taşınarak sekmeler arası geçişte listenin en başa dönmesi engellenmeli.
- [ ] **2.7 Lambda ve Kararsız Nesne Optimizasyonu:** `isEntryHidden` gibi lambda ve parametreler `remember` ile önbelleğe alınarak gereksiz Compose yeniden çizimleri önlenmeli.
- [ ] **2.8 Çökme Dosyası Okumasını IO İş Parçacığına Taşıma:** `pendingCrashReport` dosya okuması ana UI thread'inden `Dispatchers.IO`'ya taşınmalı.
- [ ] **2.9 Arama Alanına Debounce Ekleme:** Arama alanına 200ms debounce eklenerek her harf yazımında tüm listenin anında filtrelenmesi hafifletilmeli.

---

## 3. Erişilebilirlik ve Kullanılabilirlik (Accessibility & Usability)

- [ ] **3.1 Tipografi ve Yazı Boyutları:** Sabit `sp` değerleri yerine Material 3 tipografi ölçeği kullanılmalı; sistem yazı tipi büyütüldüğünde taşmalar önlenmeli.
- [ ] **3.2 Dinamik Yükseklik Sınırları:** Sabit yükseklikler (`height(30.dp)`) yerine `heightIn(min = ...)` kullanılarak büyük yazı tiplerinde metinlerin kırpılması engellenmeli.
- [ ] **3.3 Navigasyon Çubuğu Boşluğu:** `NavigationBar` üzerindeki mükerrer `.navigationBarsPadding()` kaldırılarak çift boşluk riski giderilmeli.
- [ ] **3.4 Switch Semantikleri ve TalkBack Desteği:** `Modifier.toggleable(role = Role.Switch)` uygulanarak TalkBack için çift odak ve çift okuma engellenmeli.
- [ ] **3.5 Dekoratif İkonlar ve Erişilebilirlik Açıklamaları:** Uygulama etiketinin yanındaki ikonların `contentDescription` değeri `null` yapılmalı; arama kapatma butonuna açıklama eklenmeli.
- [ ] **3.6 Satır Tıklama Alanı:** Kaydırma sırasında yanlışlıkla toggle tetiklenmesini önlemek için dokunma hedefi anahtara izole edilmeli veya onaylanmalı.
- [ ] **3.7 Geri Tuşu Yönetimi (`BackHandler`):** Uygulamalar veya Ayarlar sekmesindeyken geri basıldığında uygulamadan çıkmak yerine Ana Sayfa sekmesine dönülmeli.
- [ ] **3.8 Arama Alanı Klavye Eylemi:** `KeyboardOptions(imeAction = ImeAction.Search)` ve klavye kapatma desteği eklenmeli.
- [ ] **3.9 Anlaşılır Profil Adları:** Yalnızca "User 10" yerine profil türünü (Klon, İş Profili, Özel Alan) belirten açıklamalar eklenmeli.
- [ ] **3.10 Dile Duyarlı Sıralama (`Collator`):** Etiket sıralamasında `lowercase()` yerine `Collator.getInstance(Locale.getDefault())` kullanılarak Türkçe karakterlerin (Ç, Ğ, İ, Ö, Ş, Ü) doğru sıralanması sağlanmalı.
- [ ] **3.11 Pano Kopyalama Onayı:** "Sorun bildir" çalıştırılmadan önce panonun üzerine yazılacağına dair kullanıcıya bilgi verilmeli.

---

## 4. Yerelleştirme ve Tema (Localization & Theme)

- [ ] **4.1 Sabit İngilizce Metinlerin Taşınması:** "ACTIVE", "INACTIVE", "System Launcher", "User N", "Xposed", "GitHub" gibi sabit metinler `Strings.kt` içerisine taşınmalı.
- [ ] **4.2 Teşhis Tostunun Çoklu Dil Desteği:** `AppDiagnostics.openGitHubIssue` içindeki sabit Türkçe toast metni yerelleştirilmeli.
- [ ] **4.3 AMOLED Modu Yüzey Renkleri:** `surfaceContainerHigh`, `surfaceContainerHighest`, `surfaceVariant`, `surfaceBright` renkleri AMOLED modunda saf siyah (`#000000`) paletine uyarlanmalı.
- [ ] **4.4 Tip Güvenli Enum Yapıları:** `themeMode` ve `appLanguage` için `"SYSTEM"`, `"DARK"` gibi ham metinler yerine `enum class ThemeMode` ve `enum class AppLanguage` kullanılmalı.
