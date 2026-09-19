# KampüsAğı — Proje Hedefleri

> Durum: v0.2 — Backend (Supabase) ve iOS iskeleti yazıldı (2026-09-16). Bu doküman, kullanıcı ile yapılan ilk proje görüşmesinden (2026-09-14) çıkarılmıştır.
>
> ⚠️ **2026-09-16 MİMARİ DEĞİŞİKLİĞİ:** Backend Firebase'den **Supabase**'e geçirildi (kullanıcının açık talimatıyla — "Supabase MCP + SwiftUI iOS Production Development Prompt"). Bu dokümanda ve `AI_Guidelines.md`'de Firebase'e atıfta bulunan bölümler **artık geçerli değildir**; güncel mimari için §10 ve `AI_Guidelines.md §45`'e bakın. Firebase'e özgü tüm dosyalar (`functions/`, `firestore.rules`, `storage.rules`, `firebase.json`) repodan silinmiştir.

## 1. Tek Cümlelik Vizyon

KampüsAğı, doğrulanmış üniversite öğrencilerinin kampüs içindeki ihtiyaçlarını doğal Türkçe ile paylaşmasını, Claude destekli yapılandırma ve backend tabanlı eşleştirme ile doğru öğrencilerle buluşmasını, ardından topluluk ve mesajlaşma üzerinden iletişim kurmasını sağlayan bir iOS sosyal platformudur.

## 2. Problem Tanımı

Üniversite öğrencileri kampüs içinde sürekli küçük, anlık ihtiyaçlar yaşar ("Cuma akşamı basketbol oynayacak 2 kişi arıyorum", "Python bilen proje arkadaşı arıyorum") ama bu ihtiyaçları karşılayacak kişilerle buluşacak bir kanal yok. Klasik sosyal medya paylaşım odaklıdır, ihtiyaç–kişi eşleştirmesi yapmaz. KampüsAğı bu boşluğu, doğal dilde yazılan bir ihtiyacı yapılandırılmış bir talebe çevirip uygun öğrencilerle eşleştirerek kapatır.

## 3. Konumlandırma

"Instagram klonu" değil; **kampüs ilan panosu + çay bahçesi** hissi veren, ihtiyaç-eşleştirme çekirdekli bir sosyal platform. İmza bileşen: `TearOffStrip` — ilan kâğıdının alttan koparılması hissi veren UI öğesi.

## 4. Hedef Kullanıcı Kitlesi

- **Birincil:** Türkiye'deki örgün üniversite öğrencileri (lisans/yüksek lisans/doktora — kesinleşmemiş, bkz. §8).
- Kullanıcılar e-Devlet öğrenci belgesiyle doğrulanmış olmalı; doğrulanmamış/reddedilmiş hesaplar içerik ve eşleşme erişiminde kısıtlıdır.
- Her kullanıcı bir `universityId` ile bir üniversiteye bağlıdır; üniversiteye özel içerik yalnızca o üniversitenin öğrencilerine görünür.

## 5. Temel Değer Önermesi

> "Gerçek üniversite öğrencileri, gerçek kampüsler."

Anonim/doğrulanmamış erişim yoktur. Güven, kimlik doğrulaması üzerine inşa edilir.

## 6. Uçtan Uca Temel Akış

```
Öğrenci
   ↓
İhtiyacını Türkçe yazar
   ↓
Firebase Cloud Function
   ↓
Claude API (metni yapılandırılmış ihtiyaca dönüştürür)
   ↓
Yapılandırılmış ihtiyaç → Firestore
   ↓
Eşleştirme motoru (backend, skor hesaplar)
   ↓
Uygun öğrenciler
   ↓
Eşleşme
   ↓
Mesajlaşma
```

**Kritik mimari ilke:** Claude bir karar mercii değildir. Claude yalnızca serbest metni yapılandırılmış veriye çevirir (NLU/parsing). Eşleşme skorunu Claude değil, backend (Cloud Functions) hesaplar. Claude asla doğrudan iOS istemcisinden çağrılmaz; her zaman Firebase Cloud Function aracılığıyla çağrılır. Detay için [AI_Guidelines.md](AI_Guidelines.md) §Claude API Kullanım Kuralları.

## 7. Temel Özellikler (Ana Navigasyon: 4 Sekme)

### 7.1 Keşfet
- İhtiyaç ilanı oluşturma (doğal Türkçe metin girişi)
- İhtiyaç ilanları akışı
- Eşleşmeler ve öneriler
- Eşleşme skoru + skorun **neden** verildiğinin açıklaması (ör. "Aynı kampüstesiniz", "Basketbol ilginiz ortak", "Spor alanında deneyimin var")

### 7.2 Topluluklar
- Üniversite/kampüs akışı
- Kulüpler (oluşturma/katılma)
- Etkinlikler (keşfetme/katılma)
- Kampüs gönderileri: paylaşım, beğeni, yorum

### 7.3 Mesajlar
- Eşleşilen veya iletişime geçilen öğrencilerle sohbet
- Bildirim merkezi ile entegre

### 7.4 Profil
- Kullanıcı bilgileri
- Öğrenci doğrulama durumu ve süreci
- Gizlilik ayarları (bkz. §7.5)
- Hesap yönetimi

### 7.5 Gizlilik / KVKK (Profil → Gizlilik altında)
- Verilerimi indir
- Hesabımı sil (silme sonrası: öğrenci belgesi gibi kişisel belgeler kaldırılır; ilişkili içerikler uygun şekilde anonimleştirilir — kesin anonimleştirme kapsamı §8'de açık nokta)
- Görünürlük ayarları
- Mesajlaşma ayarları
- Engellenen kullanıcılar listesi

### 7.6 Öğrenci Doğrulama (Onboarding'in parçası, ayrı özellik değil kritik bağımlılık)
- Kayıt sırasında e-Devlet öğrenci belgesi PDF yükleme → Firebase Storage
- Moderatör incelemesi (manuel onay/red)
- Onaylanana kadar/onaylanmazsa içerik ve eşleşme erişimi kısıtlı

### 7.7 Eşleşme Sistemi
Backend tarafında hesaplanan, istemci tarafından **değiştirilemeyen** bir skor. Kullanıcıya sadece yüzde değil, nedenleri de gösterilir. Aşağıdaki ağırlıklandırma kullanıcı tarafından **örnek** olarak verilmiştir, nihai/kesin değildir (bkz. §8):

```
Aynı kampüs       30
Kategori / ilgi   20
Etiketler         20
Beceri            25
Bölüm              5
Güvenilirlik       5
---------------------
Toplam            100
```

## 8. Belirsiz / Netleştirilmesi Gereken Noktalar

Aşağıdaki noktalar proje fikrinde açıkça belirtilmemiştir. Varsayım yapılmamıştır; geliştirmeye başlamadan önce netleştirilmelidir. Bu liste [memory-bank/Memory_Bank.md](memory-bank/Memory_Bank.md) içinde "Açık Sorular" olarak da takip edilecektir.

| # | Konu | Durum |
|---|---|---|
| 1 | Eşleşme skoru ağırlıkları | **Kısmen netleşti (2026-09-14):** Aynı örnek ağırlıklar (kampüs 30 / kategori-ilgi 20 / etiket 20 / beceri 25 / bölüm 5 / güvenilirlik 5 = 100) ikinci kez verildi ve "deterministic backend kodu" olacağı teyit edildi. Yine de "örnek" ifadesiyle sunuluyor — nihai/sabit olduğu henüz açıkça onaylanmadı. |
| 2 | Minimum iOS sürümü / Swift & Xcode sürümü | **Netleşti (2026-09-14):** iOS 17+, Swift Concurrency (async/await). Xcode sürümü hâlâ belirtilmedi. |
| 3 | Mimari desen (MVVM, Repository vb.) | **Netleşti (2026-09-14):** MVVM + Repository Pattern. |
| 4 | Offline / önbellekleme stratejisi | Açık — Firestore'un varsayılan offline persistence'ı mı kullanılacak, yoksa ek bir katman mı olacak, belirtilmedi. |
| 5 | Push bildirim servisi | **Netleşti (2026-09-14):** Firebase Cloud Messaging (FCM). |
| 6 | Claude API model seçimi | **Kısmen netleşti (2026-09-14):** Model adı Swift/TS koduna sabitlenmeyecek; `ANTHROPIC_MODEL` secret/env değişkeni üzerinden yönetilecek (model değişse bile iOS'un yeniden yayınlanması gerekmeyecek). Ancak production'da kullanılacak **tam model adı** hâlâ belirtilmedi. |
| 7 | Claude API hata/timeout durumunda davranış | **Netleşti (2026-09-14):** Kullanıcıya genel Türkçe hata mesajı gösterilir, raw API hatası (401/429/invalid_request_error vb.) asla UI'da gösterilmez. Detay: [AI_Guidelines.md §4.7](AI_Guidelines.md). |
| 8 | Test stratejisi | Açık — XCTest/XCUITest, Firebase Emulator Suite kullanımı hâlâ ayrıntılı belirtilmedi. |
| 9 | Analytics / Crash reporting | **Netleşti (2026-09-14):** Firebase Analytics + Firebase Crashlytics. |
| 10 | Lokalizasyon kapsamı | **Kısmen netleşti:** Claude'a gönderilen sistem talimatı İngilizce, kullanıcıya gösterilen tüm metinler Türkçe olacak. Uygulamanın genel çok dilli olup olmayacağı (ileride İngilizce arayüz vb.) hâlâ açık. |
| 11 | Monetizasyon | Açık — hiç bahsedilmedi; MVP kapsamı dışı varsayımı olarak işaretlenmiştir, onaylanmalı. |
| 12 | Üniversite listesi / `universityId` doğrulama | Açık — hangi üniversiteler ilk aşamada destekleniyor, `universityId` nasıl atanıyor/doğrulanıyor belirtilmedi. |
| 13 | Moderasyon iş akışı detayı | Açık — öğrenci belgesi onayı ve rate-limit/kötüye kullanım koruması dışında, gönderi/yorum/rapor moderasyonu ayrıntısı belirtilmedi. |
| 14 | Mesajlaşma kapsamı | **Kısmen netleşti:** Mesajlaşma içeriğinin tamamı Claude'a gönderilmeyecek (KVKK gereği). Sadece 1:1 mi, grup sohbeti de var mı sorusu hâlâ açık. |
| 15 | KVKK veri saklama süreleri | Açık — Claude'a hangi verinin **gönderilmeyeceği** netleşti (bkz. AI_Guidelines §4.10), ama silinen/anonimleştirilen verinin saklama süresi hâlâ açık. |
| 16 | App Store yayın planı | Açık — TestFlight/production takvimi belirtilmedi. |
| 17 | Hedef öğrenci seviyesi | Açık — sadece lisans mı, yüksek lisans/doktora da dahil mi belirtilmedi. |
| 18 | Firebase App Check sağlayıcı stratejisi | Açık — App Check kullanılacağı netleşti (bkz. §10) ama sağlayıcı (DeviceCheck/App Attest) ve debug token yönetimi ayrıntısı belirtilmedi. |
| 19 | Marka rengi: koyu yeşil mi, indigo/mor mu? | **Netleşti (2026-09-14):** Kullanıcı mockup'taki rengin esas alınmasını istedi. İndigo/mor (`#4F46E5` açık / `#818CF8` koyu) bağlayıcı marka rengidir; "koyu yeşil" tarifi supersede edildi. Bkz. §9.5. |
| 20 | Kayıt sihirbazının 1-5. adımlarının içeriği | Açık — mockup'ta yalnızca 6/6 (öğrenci belgesi) adımı görüldü, diğer adımlar hiçbir kaynakta verilmedi (uydurulmayacak). |
| 21 | Sosyal giriş (Apple/Google Sign-In) kapsamı | **Netleşti (2026-09-14):** Mockup'ta somut olarak gösterildi, önceki metinle çelişmiyor (sadece eksikti) — Firebase Auth'a Apple/Google sağlayıcıları dahil edilecek. |
| 22 | Mockup'ın dayandığı iddia edilen gerçek SwiftUI kodunun ("KampusAgi-iOS", PR #1) konumu | **Tarihsel/geçersiz (2026-09-16):** Swift/iOS tamamen terk edildi, proje Kotlin/Android'e taşındı. Bu madde artık konu dışı. |
| 23 | Yerel önbellekleme/taslak katmanı (SwiftData eşdeğeri) | Açık — Android tarafında (Room/DataStore) henüz tanımlanmadı, uydurulmadı. |
| 24 | Semantik eşleşme embedding sağlayıcısı | **Çözülmemiş kritik açık nokta:** Anthropic Claude'un embedding endpoint'i yok; OpenAI/Gemini yasak. Bir sağlayıcı (ör. Voyage AI) kullanıcı onayı bekliyor — bkz. AI_Guidelines.md §45.5. |
| 25 | Android'de Apple ile Devam Et | **Netleşti (2026-09-16):** Kullanıcının Android tasarım mesajı bunu "İstemiyorsan kaldır" notuyla isteğe bağlı bıraktı. Karar: KALDIRILDI — Android'de Apple Sign-In, web tabanlı OAuth gerektirdiği ve Android kullanıcıları için nadiren anlamlı olduğu için eklenmedi. Yalnızca e-posta/şifre + Google (Credential Manager) var. |
| 26 | Push bildirim altyapısı (FCM) | **Netleşti (2026-09-16):** Kullanıcı "FCM'i iptal et" dedi — çelişki Firebase yasağı lehine çözüldü. Push bildirim altyapısı bu turda hiç kurulmuyor; bildirimler yalnızca uygulama içi (Postgres `notifications` tablosu + Realtime) çalışıyor. `POST_NOTIFICATIONS` izin kodu kaldırıldı — bkz. AI_Guidelines.md §45.7. |

**Kural:** Bu listedeki maddeler, kullanıcı ile netleştirilmeden varsayılan bir teknik karara dönüştürülmeyecektir. Her netleşen madde, karar gerekçesiyle birlikte Memory Bank'e işlenmiştir (bkz. [memory-bank/Memory_Bank.md §3](memory-bank/Memory_Bank.md)).

## 9. Kapsam Dışı (Şimdilik)

- Web/Android istemcisi (proje iOS + SwiftUI olarak tanımlandı)
- Ödeme/işlem akışları
- Claude'un doğrudan istemciden çağrılması

## 9.5 Tasarım Referansı ve Genişleyen Onboarding Akışı (2026-09-14 — kullanıcının paylaştığı statik mockup artifact'inden)

Kullanıcı, `KampusAgi-iOS/KampusAgi/Features/…` yolundaki (iddiaya göre gerçek) SwiftUI kaynak koduna bakılarak çizildiği belirtilen bir statik HTML mockup galerisi paylaştı (5 ekran: Welcome, Login, Register — belge adımı, Pending Review, Rejected). Bu, dokümana iki şekilde yansıtılmıştır:

**A) Yeni/genişleyen bilgi (önceki metinle çelişmiyor, ekliyor):**
- Kayıt akışı **6 adımlı bir sihirbaz**; 6. adım öğrenci belgesi (PDF) yükleme. İlk 5 adımın içeriği mockup'ta görünmüyor — **netleşmedi**.
- Giriş/kayıt ekranlarında **Apple ile Devam Et** ve **Google ile devam et** sosyal giriş seçenekleri var (klasik e-posta/şifre'ye ek). Bu daha önce hiç belirtilmemişti.
- Doğrulama reddedilirse kullanıcıya bir **"Sebep"** (ör. "Belge okunamıyor, geçersiz veya güncel değil") gösteriliyor — moderatörün red gerekçesi girebildiği anlamına gelir.
- Bekleme ekranında kullanıcı "Başvuru Durumunu Yenile" ile durumu manuel kontrol edebiliyor.
- Klasör/mimari adlandırması: `KampusAgi-iOS/` (repo kökü) → `KampusAgi/` (uygulama hedefi) → **Clean Architecture: `App/Core/Domain/Data/Features`**. Bu, `AI_Guidelines.md §2.3`'teki MVVM + Repository açıklamasıyla çelişmiyor (Repository protokolleri Domain'de, implementasyonlar Data'da yaşar) ama daha ayrıntılı bir katman isimlendirmesi getiriyor — bu doküman ve `AI_Guidelines.md` bu adlandırmaya göre güncellenmiştir.

**B) Çözülen çelişki (2026-09-14):** Bu mockup'taki marka rengi **indigo/mor (`#4F46E5` açık tema / `#818CF8` koyu tema)** — kullanıcının ilk proje anlatımındaki *"koyu yeşil ana renk, az miktarda kehribar"* tarifiyle çelişiyordu. Kullanıcı açıkça talimat verdi: yeni bilgilere göre güncellensin, eski tarife bağlı kalınmasın. **Karar: mockup'taki indigo/mor palet esas alınır; "koyu yeşil ana renk" tarifi bununla supersede edilmiştir.** Bkz. `memory-bank/Memory_Bank.md` Decision Log.

### Onaylanmış Renk Tokenları (2026-09-14 — bağlayıcı)

| Token | Açık Tema | Koyu Tema |
|---|---|---|
| Brand/Primary | `#4F46E5` | `#818CF8` |
| Background | `#FFFFFF` | `#000000` |
| Background (ikincil) | `#F2F2F7` | `#1C1C1E` |
| Kart yüzeyi | `#FFFFFF` | `#2C2C2E` |
| Kart kenarlığı | `#E5E5EA` | `#3A3A3C` |
| Birincil metin | `#1C1C1E` | `#FFFFFF` |
| İkincil metin | `#6C6C70` | `#98989F` |
| Durum: Bekliyor | `#F59E0B` | `#FBBF24` |
| Durum: Onaylandı | `#16A34A` | `#4ADE80` |
| Durum: Reddedildi | `#DC2626` | `#F87171` |

Tipografi: uygulama ekranları sistem fontu (SF Pro / `-apple-system`) kullanıyor — "sade tipografi" ifadesiyle tutarlı, çelişki yok. Renk adlandırması `Assets.xcassets` içinde `BrandPrimary`, `StatusPending`, `StatusRejected` (ve muhtemelen `StatusApproved`) named color olarak tutulacak şekilde tasarlanmış görünüyor.

**Not — kaynak belirsizliği:** Bu mockup'ın dayandığı iddia edilen gerçek SwiftUI kodu (`KampusAgi-iOS/KampusAgi/Features/…`, "PR #1") bu proje dizininde (`C:\Users\xeazr\Desktop\kampusagıios`) bulunmuyor — dizin bu oturum başında boştu. Kod başka bir yerde/oturumda mı var yoksa mockup tamamen varsayımsal bir taslak mı, netleşmedi. Bu belirsizlik nedeniyle mockup **bağlayıcı gerçek kod olarak değil, tasarım referansı** olarak ele alınmıştır.

## 10. Teknoloji Yığını (2026-09-16 (devam) itibarıyla güncel — Android/Kotlin mimarisi)

> ⚠️ **İKİNCİ PLATFORM DEĞİŞİKLİĞİ (2026-09-16, aynı gün):** Kullanıcı Swift/iOS'tan **tamamen vazgeçti**, Kotlin/Jetpack Compose (Android) ile devam kararı aldı ("Mantık aynı ama swiftten vazgeçtim tüm kodu kotlin diline döndür android"). `KampusAgi-iOS/` klasörü SİLİNDİ. Supabase backend'i (migrations, Edge Functions, Storage, RLS) **olduğu gibi korunmuştur** — platformdan bağımsızdır, hiçbir değişiklik gerekmedi.

| Katman | Teknoloji |
|---|---|
| İstemci platformu | Android — minSdk 26, targetSdk 35, compileSdk 35 |
| Dil / UI | Kotlin, Jetpack Compose (Material 3, dynamic color KAPALI) |
| Eşzamanlılık | Kotlin Coroutines, `StateFlow<UiState>` |
| İstemci mimarisi | MVVM + Clean Architecture (app/core/domain/data/feature) + Repository Pattern |
| DI | Hilt |
| Navigasyon | Navigation Compose (tip-güvenli rotalar, `kotlinx.serialization`) |
| Google girişi | Credential Manager (`androidx.credentials`) |
| Backend | **Supabase** (proje: `KampusAgi`, ref `ggphcgapgwrcdumfldsc`, `eu-central-1`) — DEĞİŞMEDİ |
| Veritabanı | Supabase PostgreSQL 17 (23 tablo/view, RLS aktif — bkz. `supabase/migrations/`) — DEĞİŞMEDİ |
| Sunucu mantığı | Supabase Edge Functions (Deno) — `parse-need`, `publish-need`, `submit-student-document`, `recompute-matches` — DEĞİŞMEDİ |
| Dosya depolama | Supabase Storage (`student-documents` bucket) — DEĞİŞMEDİ |
| Kimlik doğrulama | Supabase Auth (e-posta/şifre, Google `signInWithIdToken`) — Apple girişi Android'de KALDIRILDI (bkz. §8 madde 25) |
| Yetki (admin/moderatör) | Supabase Auth JWT `app_metadata` — DEĞİŞMEDİ |
| Yetkilendirme / veri izolasyonu | PostgreSQL Row Level Security — DEĞİŞMEDİ |
| Semantik arama altyapısı | pgvector (**şema hazır, embedding sağlayıcısı ONAYLANMADI** — bkz. §8 madde 24, kritik açık nokta, DEĞİŞMEDİ) |
| Push bildirim | **Yok (kasıtlı) —** kullanıcı FCM'i iptal etti; bildirimler yalnızca uygulama içi (bkz. §8 madde 26) |
| AI / NLU sağlayıcısı | **Anthropic Claude API** — yalnızca Edge Functions üzerinden çağrılır; Android'den doğrudan çağrılmaz |

**Firebase bu projeden TAMAMEN kaldırılmıştır** (kullanıcının açık talimatıyla). Yalnızca `supabase/` (backend) ve `KampusAgi-Android/` (istemci) dizinleri mevcuttur.

**Açıkça dışlanan sağlayıcılar:** Gemini, OpenAI ve Firebase backend bileşenleri (Auth/Firestore/Storage/Functions) bu projede **kullanılmayacaktır**. Tek AI sağlayıcısı Anthropic Claude'dur.

Bu tablo dışında bir teknoloji bu doküman tarafından varsayılmamıştır. Claude/Supabase entegrasyonunun tam kuralları için bkz. [AI_Guidelines.md §45](AI_Guidelines.md).
