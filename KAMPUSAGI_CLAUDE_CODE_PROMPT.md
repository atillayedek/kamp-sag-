# KampüsAğı — Production-Ready Tamamlama Görevi (Otonom, Kesintisiz)

## 0. Çalışma kuralları (EN ÖNEMLİ BÖLÜM — her oturum başında tekrar oku)

Sen bu projede tam yetkili, otonom bir kıdemli mobil mühendissin. Görevin, **mevcut KampüsAğı projesini** aşağıdaki tasarım sistemine birebir uyacak şekilde **production-ready** hale getirmek.

1. **Durma, soru sorma, onay bekleme.** Bir karar gerektiğinde en makul seçeneği seç, `docs/DECISIONS.md` dosyasına tek satırla gerekçesini yaz ve devam et. Yalnızca gerçekten senin elinde olmayan şeyler (API anahtarı, App Store Connect ürünü, Apple Developer ayarı) engelleyiciyse bunu `docs/BLOCKERS.md`'ye yaz ve **bir sonraki göreve geç** — asla boşta bekleme.
2. **İlerleme dosyası zorunlu.** İlk iş olarak `docs/PROGRESS.md` oluştur (varsa oku ve kaldığın yerden devam et). İçinde:
   - Bölüm 6'daki tüm görevlerin checkbox listesi (`- [ ]` / `- [x]`)
   - "Şu an üzerinde çalışılan görev" satırı
   - Son başarılı build/test zamanı
   Her görev bittiğinde güncelle. Oturum kesilirse (limit, çökme) bir sonraki oturum bu dosyadan devam edecek.
3. **Küçük adımlarla commit at.** Her tamamlanan görevden sonra build + test çalıştır, geçiyorsa `git commit` yap (anlamlı mesajla). Kırık kodu commit etme.
4. **Asla sahte veri yok.** Mock, placeholder, hardcoded örnek kullanıcı/gönderi/mesaj YASAK. Maketteki "Elif Yıldız", "Ahmet Yılmaz", "Ada Lovelace", "%92", örnek e-posta vb. yalnızca tasarım örneğidir — koda girmez. Her ekran gerçek Firebase/REST verisiyle çalışacak; veri yoksa **boş durum (empty state)**, yüklenirken **skeleton/loading**, hata olursa **hata durumu + tekrar dene** gösterecek. Mock'lar yalnızca `#if DEBUG` altındaki Preview'larda ve test hedeflerinde kalabilir.
5. **Hile yok.** Testi geçirmek için test silme/devre dışı bırakma, Firestore rules'ları gevşetme, hata yutma (`try?` ile sessizce geçme) yapma.
6. **Gizli bilgi yok.** Anahtarları koda yazma. Mevcut env / `GoogleService-Info.plist` / `google-services.json` dosyalarındaki yapılandırmayı kullan. Gemini gibi servisler **yalnızca Cloud Functions üzerinden** çağrılır, istemcide anahtar tutulmaz.
7. **Bitiş tanımı:** Bölüm 7'deki "Bitti Tanımı" maddelerinin hepsi sağlanınca `docs/PROGRESS.md`'nin en üstüne tam olarak şu satırı yaz: `DURUM: TAMAMLANDI`. Bu satırı başka hiçbir koşulda yazma.

## 1. Bağlam — önce keşif yap

- Depoyu tara: `KampusAgi-iOS/` (SwiftUI, Swift 6, iOS 17+, MVVM, Clean Architecture: App/Core/Domain/Data/Features). Android deposu da varsa (Kotlin + Jetpack Compose + Material 3) aynı tasarım token'larını orada da uygula.
- `docs/PHASE1_AUDIT.md` ve mevcut tüm `docs/` dosyalarını oku.
- Backend: Firebase-only (Auth, Firestore, Storage, Cloud Functions, FCM). PostgreSQL kaldırıldı / kaldırılıyor. Sunucu tarafında kalması gerekenler Cloud Functions'ta:
  - admin custom claim ataması,
  - öğrenci belgesi onayı (onay → `accountStatus` değişimi → claim ataması tek atomik işlem, `reviewVerification` istemcide DEĞİL),
  - sayaç güncellemeleri (Firestore trigger).
- **Etkinlikler** Firebase'e taşınmaz: `https://api.kampusagi.com` REST backend'inden (Retrofit / URLSession) gelmeye devam eder.
- Keşif sonucunu `docs/PROGRESS.md`'nin "Mevcut Durum" bölümüne yaz: hangi ekran var, hangisi eksik, hangisinde mock var.

## 2. Tasarım sistemi — BİREBİR uygulanacak

Kaynak: `KampüsAğı Ekranları` maketi. Mevcut Swift dosyalarında zaten kodlanmış ekranlar (Welcome, Login, Register, PendingReview, Rejected) **referans gerçektir**; yeni ekranlar onlarla piksel tutarlılığında olmalı. Maket küçültülmüş bir cihaz çerçevesinde çizildiği için boyutları mevcut Swift kodundan al; aşağıdaki pt değerleri normalize edilmiş karşılıklardır.

### 2.1 Renk token'ları (hex değerleri AYNEN kullanılacak)

Tek bir `DesignTokens` / `AppColors` kaynağında (iOS: Asset Catalog color set'leri light+dark; Android: `Color.kt` + `MaterialTheme` colorScheme) tanımla. Ekranlarda hiçbir yerde ham hex yazma.

| Token | Light | Dark |
|---|---|---|
| `appBg` | `#FFFFFF` | `#000000` |
| `appBg2` (input, segmented, balon-gelen) | `#F2F2F7` | `#1C1C1E` |
| `card` | `#FFFFFF` | `#2C2C2E` |
| `cardBorder` / divider | `#E5E5EA` | `#3A3A3C` |
| `label` | `#1C1C1E` | `#FFFFFF` |
| `label2` (ikincil metin) | `#6C6C70` | `#98989F` |
| `primary` (BrandPrimary) | `#4F46E5` | `#818CF8` |
| `primaryContrast` | `#FFFFFF` | `#0B0B10` |
| `secondaryBtnBg` | `#F2F2F7` | `#1C1C1E` |
| `pending` (StatusPending) | `#F59E0B` | `#FBBF24` |
| `approved` | `#16A34A` | `#4ADE80` |
| `rejected` (StatusRejected) | `#DC2626` | `#F87171` |
| `gold` (premium) | `#B7862B` | `#E3B457` |

Türetilmiş renkler (opaklık karışımları):
- `secondaryBtnBorder` = primary %35
- `iconBadgeBg` = primary %14
- `aiCardBg` = card üzerine primary %7; `aiCardBorder` = primary %45, **kesik çizgi (dashed)**
- `featuredPlanBg` = card üzerine primary %6
- `matchScoreBg` = approved %16
- `aiChipNeed` border = primary %40

### 2.2 Tipografi (sistem fontu: SF Pro / Android'de varsayılan sans)

| Stil | Boyut | Ağırlık | Not |
|---|---|---|---|
| `titleLarge` | 28pt | heavy/800 | tracking -1%, ortalı |
| `titleMedium` | 20pt | heavy/800 | tracking -1% |
| `navTitle` | 17pt | bold | inline, ortalı |
| `button` | 17pt | bold | |
| `body` | 15pt | regular | satır aralığı 1.45–1.55 |
| `bodyCenter` | 15pt | regular | label2, ortalı |
| `fieldLabel` | 12pt | semibold | label2, BÜYÜK HARF metin ("E-POSTA", "ŞİFRE") |
| `caption` | 13pt | regular | label2, satır aralığı 1.5 |
| `reasonLabel` | 12pt | bold | BÜYÜK HARF, tracking +4% |
| `chip` | 12pt | semibold | |
| `receipt` | 11pt | regular | label2 |

Dynamic Type'ı destekle (sabit boyut yerine ölçeklenen font kullan).

### 2.3 Şekil ve boşluk

- Ekran yatay padding: 20pt; dikey bölüm arası boşluk: 16pt
- Buton: min yükseklik 50pt, köşe 12pt, ikon-metin arası 8pt
- Input kutusu: arka plan `appBg2`, köşe 10pt, padding 12×14pt
- Kart: `card` + 1pt `cardBorder`, köşe 14pt, padding 14×16pt
- Segmented control: arka plan `appBg2`, köşe 10pt, 2pt iç padding; aktif segment `card` + hafif gölge
- Chip / pill: tam yuvarlak (capsule), padding 5×10pt, 1pt `cardBorder`
- Avatar: 40pt (liste), 72pt (büyük); arka plan primary, beyaz baş harfler (kullanıcının fotoğrafı yoksa)
- Adım göstergesi: 6 segment, 4pt yükseklik, 4pt aralık, capsule; tamamlananlar primary, kalanlar `cardBorder`; altında "N/6 — Adım adı" (12pt semibold label2)
- FAB: 52pt daire, primary, sağ alt, gölge
- Status ikonları: ⏳ pending rengi, ❌ rejected rengi (mevcut Swift'teki gibi büyük sembol)

### 2.4 Bileşen kütüphanesi

`Core/DesignSystem/` altında yeniden kullanılabilir bileşenler oluştur (varsa genişlet): `PrimaryButton`, `SecondaryButton`, `TextDangerButton`, `AppleSignInButton` (siyah zemin beyaz yazı), `GoogleSignInButton` (secondary stil + renkli G logosu), `LabeledField`, `SecureField` (göz ikonu ile göster/gizle), `AppCard`, `StepIndicator`, `SegmentedPicker`, `Avatar`, `Chip`, `AIAnalysisCard`, `MatchScoreBadge`, `ChatBubble` (gelen/giden, köşe 18pt, kuyruk tarafı 6pt), `TypingIndicator`, `ChatInputBar` (capsule, sağda 32pt gönder butonu), `SettingsRow` (normal/premium/danger), `PlanCard` (featured + "Popüler" rozeti), `EmptyStateView`, `ErrorStateView`, `SkeletonView`. Her bileşen için light+dark Preview yaz (Preview'larda örnek veri serbest, sadece `#if DEBUG`).

## 3. Ekranlar ve metinler (Türkçe metinler AYNEN)

### 3.1 Mevcut akış (varsa koru, eksikse tamamla, gerçek backend'e bağla)
- **Welcome:** primary %14 daire içinde uygulama ikonu, "KampüsAğı", "Kampüsündeki öğrencilerle ihtiyaçlarını eşleştir.", altta "Giriş Yap" (primary) ve "Kayıt Ol" (secondary).
- **Login:** nav başlığı "Giriş Yap"; "E-POSTA", "ŞİFRE" alanları; sağa yaslı "Şifremi unuttum" (primary, semibold → Firebase şifre sıfırlama e-postası); "Giriş Yap"; ayraç; "Apple ile Devam Et"; "Google ile devam et". Gerçek Firebase Auth, doğrulama hataları Türkçe.
- **Register (6 adım):** mevcut adımları koru; son adım "6/6 — Öğrenci Belgesi", başlık "Öğrenci belgenizi yükleyin" + yardım ikonu, "PDF Öğrenci Belgesi Seç", açıklama "Yalnızca resmi PDF öğrenci belgesi kabul edilir. Ekran görüntüsü veya fotoğraf yüklemeyin.", altta "Geri" (secondary) ve "Başvuruyu Gönder" (primary, daha geniş). Yalnızca PDF MIME tipi, boyut limiti, yükleme ilerlemesi, Storage'a yükleme + `student_verifications` kaydı.
- **Pending Review:** ⏳, "Başvurunuz İnceleniyor", "Öğrenci belgeniz admin ekibimiz tarafından kontrol ediliyor. Bu işlem tamamlandığında bildirim alacaksınız.", "Başvuru Durumunu Yenile" (ID token'ı zorla yenileyip claim/durumu tekrar okur), "Çıkış Yap" (danger text). Durum değişimini snapshot listener ile canlı izle.
- **Rejected:** ❌, "Belgeniz Doğrulanamadı", "SEBEP" kartı (sebep metni Firestore'dan gelir, sabit yazma), "Lütfen yeni bir PDF yükleyin.", "PDF Öğrenci Belgesi Seç", "Çıkış Yap".

### 3.2 Eksik bölümler (maketteki konsept tasarımla, sıfırdan tam işlevsel)
- **Topluluklar (Features/Communities):** nav "Topluluklar"; segmented "Genel" / "Üniversitem"; seçili üniversite adı (kullanıcının profilinden); gönderi kartları (avatar, ad, göreli zaman "2 saat önce" formatı, metin, ♥ beğeni, 💬 yorum sayısı); sağ altta + FAB → gönderi oluştur. Sayfalama (limit + cursor), pull-to-refresh, beğeni optimistic update, sayaçlar Cloud Function trigger ile.
- **İhtiyaç Oluştur (Features/Requirements):** nav "İhtiyaç Oluştur"; "İHTİYACINI ANLAT" çok satırlı alan; "GEMINI ANALİZİ" başlıklı kesik çizgili AI kartı içinde çipler (tür çipi "İhtiyacım Var" primary vurgulu + kategori/zaman çipleri); açıklama "Kategori ve zaman bilgisi, backend üzerinden çalışan Gemini analiziyle otomatik çıkarılır — API anahtarı hiçbir zaman uygulamada tutulmaz."; "Paylaş". Analiz: callable Cloud Function (`analyzeRequirement`), debounce, rate-limit, yapılandırılmış JSON çıktı, sunucu tarafı doğrulama.
- **Eşleşmeler (Features/Matchmaking):** nav "Eşleşmeler"; üst üste kart yığını (arkadaki kartın kenarı görünür); büyük avatar, ad, bölüm, yeşil rozet "%N Anlamsal Eşleşme", etiket çipleri, "Profili Gör" (secondary) + "Mesaj At" (primary). Eşleşme skoru Cloud Function'da (embedding benzerliği) hesaplanır, istemci yalnızca okur.
- **Sohbet (Features/Chat):** nav'da ad + yeşil nokta "Çevrimiçi"; gelen/giden balonlar; "İletildi ✓✓" okundu bilgisi; yazıyor göstergesi (3 nokta); "Mesaj yaz…" capsule input + gönder. Firestore gerçek zamanlı, çevrimdışı kuyruk, okundu/iletildi durumları, FCM bildirim, presence.
- **Profil / Ayarlar (Features/Settings):** büyük avatar, ad, "Bölüm · Üniversite · Onaylı ✓"; kart içinde satırlar: "Hesap Bilgileri ›", "Bildirim Ayarları ›", "Gizlilik ve Konum ›", "Koyu Görünüm" (switch — sistem/açık/koyu tercihi kalıcı saklanır), "Premium'a Yükselt ›" (gold, bold), "Hesabı Sil" (danger — App Store kuralı gereği gerçek hesap + veri silme Cloud Function'ı), "Çıkış Yap" (danger).
- **Premium (Features/Subscription):** nav "Premium"; üç PlanCard: "Free" ("Mevcut plan"), "Premium" (featured, "Popüler" rozeti), "Community Pro"; ✓ yeşil tikli özellik listeleri; "Premium'a Geç". Fiyatlar ve dönem **StoreKit 2 / Google Play Billing ürünlerinden** yerelleştirilmiş olarak okunur, koda yazılmaz. Satın alma doğrulaması sunucuda (Cloud Function), entitlement Firestore'a/claim'e yazılır. Ürünler henüz tanımlı değilse BLOCKERS.md'ye yaz, ekranı yine de gerçek entegrasyonla bitir.
- **Etkinlikler:** mevcut REST entegrasyonunu aynı tasarım sistemine uyarla.

### 3.3 Navigasyon
Onaylı kullanıcı için TabView/NavigationBar: Topluluklar · Eşleşmeler · (ortada) İhtiyaç Oluştur · Sohbet · Profil. `accountStatus` = pending → PendingReview, rejected → Rejected, approved → ana uygulama. Deep link / bildirimden sohbete geçiş.

## 4. Production gereksinimleri

- Hata yönetimi: tipli hatalar, kullanıcıya Türkçe anlaşılır mesaj, loglama (Crashlytics).
- Erişilebilirlik: VoiceOver/TalkBack etiketleri, Dynamic Type, 44pt min dokunma alanı, kontrast.
- Yerelleştirme: tüm metinler `Localizable.xcstrings` / `strings.xml` içinde (tr varsayılan).
- Güvenlik: Firestore + Storage rules her yeni koleksiyon için; Emulator Suite ile rules unit testleri.
- Performans: sayfalama, sorgu limitleri, gereksiz geniş snapshot listener yok, görsel önbellekleme.
- Analitik: temel olaylar (kayıt, belge yükleme, gönderi, eşleşme, mesaj, satın alma).
- Gizlilik: iOS Privacy Manifest, izin açıklama metinleri (Türkçe), hesap silme akışı.

## 5. Test

- Domain/UseCase ve ViewModel unit testleri.
- Cloud Functions testleri (emulator).
- Firestore/Storage rules testleri.
- Kritik akışlar için UI testleri: giriş, kayıt + belge yükleme, pending → approved geçişi, gönderi paylaşma, mesaj gönderme.
- Tasarım doğrulama: her ekran için light ve dark snapshot testi (swift-snapshot-testing / Paparazzi).

## 6. Görev sırası (PROGRESS.md'ye bu listeyi kopyala)

1. Keşif + PROGRESS/DECISIONS/BLOCKERS dosyaları
2. Design tokens (renk, tipografi, boşluk) + light/dark
3. DesignSystem bileşen kütüphanesi + Preview'lar
4. Mevcut auth/doğrulama ekranlarını tokens'a taşı, mock'ları temizle, gerçek backend'e bağla
5. Cloud Functions: claim ataması, atomik belge onayı, sayaç trigger'ları, hesap silme
6. Navigasyon + accountStatus yönlendirmesi
7. Topluluklar
8. İhtiyaç Oluştur + `analyzeRequirement` Function
9. Eşleşmeler + skor Function'ı
10. Sohbet + FCM + presence
11. Profil / Ayarlar + tema tercihi
12. Premium + sunucu tarafı satın alma doğrulaması
13. Etkinlikler ekranını tasarıma uyarla
14. Rules + rules testleri
15. Erişilebilirlik, yerelleştirme, Privacy Manifest
16. Unit + UI + snapshot testleri
17. Son tarama: `grep` ile mock/placeholder/TODO/FIXME/hardcoded örnek veri kalmadığını doğrula
18. Temiz build (Release) + tüm testler yeşil + `docs/RELEASE_NOTES.md`

## 7. Bitti Tanımı

- [ ] Tüm ekranlar maketteki düzen, renk, metin ve bileşenlerle birebir; light + dark
- [ ] Üretim kodunda sıfır mock/örnek veri (Preview ve test hedefleri hariç)
- [ ] Tüm veri gerçek Firebase / REST kaynağından; boş, yükleniyor ve hata durumları var
- [ ] Release build hatasız, uyarılar temizlenmiş
- [ ] Tüm testler geçiyor
- [ ] Rules testleri geçiyor
- [ ] BLOCKERS.md'de yalnızca gerçekten dış erişim gerektiren maddeler kalmış

Hepsi tamamsa `DURUM: TAMAMLANDI` yaz. Değilse bir sonraki açık göreve geç ve çalışmaya devam et.
