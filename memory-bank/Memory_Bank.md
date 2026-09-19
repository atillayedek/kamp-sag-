# KampüsAğı — Memory Bank

> Bu dosya, projenin canlı hafızasıdır: alınan kararlar, tamamlanan işler, mevcut durum, açık sorular ve kullanılan skill/agent kaynakları burada tutulur. Her önemli görev sonrası güncellenir.

---

## 0. Mevcut Durum

**Faz:** Backend (Supabase) canlıda, iOS iskeleti yazıldı ve Supabase'e bağlandı — henüz Xcode'da derlenmedi (bu ortamda Xcode/Swift toolchain yok).
**Son güncelleme:** 2026-09-16 — Firebase tamamen kaldırıldı, Supabase'e geçildi (proje: `KampusAgi`, ref `ggphcgapgwrcdumfldsc`). Detay: §9 (yeni bölüm, aşağıda).

## 1. Proje Özeti

KampüsAğı — doğrulanmış üniversite öğrencilerini kampüs içi ihtiyaçlar üzerinden eşleştiren, Claude destekli bir iOS sosyal platformu. Detay: [project-goals.md](../project-goals.md). Kurallar: [AI_Guidelines.md](../AI_Guidelines.md).

---

## 2. Skill Keşif Taraması (2026-09-14)

Proje dizini (`C:\Users\xeazr\Desktop\kampusagıios`) tam taranmıştır. Bulgular:

- **Proje-özel skill kaynağı bulunamadı.** `.skills/`, `skill/`, `skills/`, `.cursor/`, `.cursor/skills/`, `docs/`, `tools/`, `prompts/`, `agents/` (proje-özel), `workflows/`, `SKILL.md`, `AGENTS.md`, `CLAUDE.md`, `.cursorrules`, `rules/`, `guidelines/` — hiçbiri mevcut değil.
- Dizinde yalnızca `.claude/agents/` bulundu (273 dosya) — bu, projeye özel değil, Claude Code'un **standart önceden yüklenmiş** genel amaçlı alt-ajan (subagent) tanım kütüphanesidir. Proje-özel bir talimat kaynağı olarak sayılmamıştır.
- Bu nedenle envanter, Claude Code'un genel Skill aracı kataloğu ve Agent alt-ajan kataloğundan, KampüsAğı ile ilgisine göre süzülerek oluşturulmuştur.
- **Not:** Aşağıdaki tabloda "Skill" = `Skill` aracıyla çağrılan hazır iş akışı; "Agent" = `Agent` aracıyla spawn edilen uzman alt-ajan. İkisi farklı mekanizmalardır, tabloda ayrıştırılmıştır.

### 2.1 Active Skills / Agents Tablosu

| Konu Alanı | Kaynak Tipi | Kaynak Adı | Kullanım Alanı | Durum |
|---|---|---|---|---|
| Genel çok-alanlı koordinasyon | Skill | `anthropic-skills:allinone` | Planlama/mimari/kodlama/QA gerektiren karma görevlerde ilgili yaklaşımları uygulama | Active (gerektiğinde) |
| Claude / Anthropic API | Skill | `claude-api` | Cloud Functions içinde Claude API entegrasyonu (model seçimi, pricing, tool use, streaming) — proje çekirdek bağımlılığı | **Active** |
| Mimari (genel) | Agent | Software Architect | Sistem tasarımı, domain modeli kararları | Active (gerektiğinde) |
| Backend / Cloud Functions tasarımı | Agent | Backend Architect | Ölçeklenebilir Cloud Functions, API tasarımı | **Active** |
| iOS/Native mobil geliştirme | Agent | Mobile App Builder | SwiftUI istemci geliştirme | **Active** |
| iOS QA / Fix / Design Review / Sync | Skill | `ios-qa`, `ios-fix`, `ios-design-review`, `ios-clean`, `ios-sync` (gstack) | iOS özellik QA, otomatik hata düzeltme, tasarım incelemesi | Active (gerektiğinde) |
| Güvenlik mimarisi | Agent | Security Architect | Tehdit modelleme, güven sınırı analizi (istemci/backend ayrımı) | **Active** |
| Uygulama güvenliği | Agent | Application Security Engineer | Kod düzeyi güvenlik incelemesi | Active (gerektiğinde) |
| Kod incelemesi | Skill | `code-review`, `security-review` | Her önemli PR/diff öncesi | **Active** |
| Kimlik doğrulama / yetkilendirme | Agent | Identity & Access Engineer | Firebase Auth, custom claims, oturum mimarisi | **Active** |
| Sır / kimlik bilgisi hijyeni | Agent | Secrets & Credential Hygiene Engineer | API anahtarı yönetimi, rotasyon | Active (gerektiğinde) |
| Gizlilik mühendisliği | Agent | Privacy Engineer | PII minimizasyonu, veri silme/anonimleştirme akışları | **Active** |
| Kurumsal gizlilik / KVKK | Agent | Data Privacy Officer | Veri işleme envanteri, KVKK-benzeri uyum çerçevesi (Türkiye'ye özgü nüans doğrulanmalı) | Active — **dikkat:** KVKK'ya özgü değil, genel GDPR/CCPA odaklı; hukuki teyit gerektirir |
| Veritabanı / veri modeli | Agent | Database Optimizer | Genel şema/indeks tavsiyesi — **Firestore'a özgü değil (SQL odaklı)**, kısmi uyum | Kısmi uyum — dikkatli kullan |
| Mobil sürüm/dağıtım | Agent | Mobile Release Engineer | Code signing, fastlane, App Store Connect süreçleri | İlerleyen fazda Active |
| Erişilebilirlik | Agent | Accessibility Auditor | WCAG odaklı, **web merkezli** — native iOS VoiceOver için kısmi uyum | Kısmi uyum |
| i18n / Türkçe | Agent | Internationalization Engineer | Genel i18n mühendisliği — Türkçe'ye özgü değil | Kısmi uyum |
| Tasarım sistemi / UI | Agent + Skill | UI Designer, UX Architect / `frontend-design`, `design-taste-frontend` | Renk/tipografi/kart tabanlı tasarım dili yönü — **çoğunlukla web/HTML odaklı**, SwiftUI'a birebir uygulanamaz | Kısmi uyum — ilke düzeyinde kullan, Apple HIG ile çapraz doğrula |
| Dokümantasyon | Skill + Agent | `doc-coauthoring` / Technical Writer | Teknik dokümantasyon üretimi/güncellemesi | Active (gerektiğinde) |
| Gerçek zamanlı mesajlaşma | Agent | Realtime Collaboration Engineer | WebSocket/CRDT odaklı — Firestore listener mimarisiyle **tam örtüşmüyor**, kısmi referans | Kısmi uyum |
| Git iş akışı | Agent | Git Workflow Master | Branch stratejisi, commit hijyeni | Active (gerektiğinde) |
| Performans | Agent | Performance Benchmarker | Genel performans ölçümü | Active (gerektiğinde) |
| Analytics | Agent | Analytics Reporter | Genel analitik — Firebase Analytics/Crashlytics'e özgü değil | Kısmi uyum |

### 2.2 Missing Skill (Eksik Skill Kayıtları)

Aşağıdaki alanlarda proje ihtiyacına **tam uyan** bir Skill veya Agent bulunamamıştır. Uydurulmamıştır; ilgili görevlerde genel prensipler + resmi Firebase/Apple dokümantasyonu ile ilerlenecektir:

- Firebase / Firestore'a özgü uzman skill/agent (yalnızca genel Backend Architect + Database Optimizer ile kısmen karşılanıyor)
- Firestore Security Rules'a özgü doğrulama/test skill'i
- Push bildirim (FCM/APNs) entegrasyonuna özgü skill/agent
- Swift Concurrency (async/await, actor modeli) odaklı skill/agent
- SwiftUI'a özgü MVVM/mimari desen skill'i
- Native iOS erişilebilirlik (VoiceOver, Dynamic Type) odaklı skill/agent (mevcut Accessibility Auditor web-merkezli)
- KVKK'ya (6698 sayılı Kanun) özgü, Türkiye hukukuna hakim bir uyum skill'i (mevcut Data Privacy Officer genel GDPR/CCPA odaklı)
- Firestore offline persistence / önbellekleme stratejisi skill'i
- Deep linking / Universal Links (iOS) skill'i

Bu liste yeni skill keşfedildikçe veya ihtiyaç ortaya çıktıkça güncellenecektir.

**Güncelleme (2026-09-14):** Kullanıcının verdiği detaylı spesifikasyon Firebase App Check ve Firebase Cloud Messaging'i teknoloji yığınına ekledi. Bu ikisi için de özel bir Skill/Agent bulunamamıştır — genel "Firebase'e özgü uzman skill/agent bulunamadı" kaydı kapsamında değerlendirilecektir.

### 2.3 Tasarım Mockup Artifact'i — Bulgular ve Çelişki (2026-09-14)

Kullanıcı, iddiaya göre `KampusAgi-iOS/KampusAgi/Features/…` altındaki gerçek SwiftUI kaynağından türetilmiş statik bir HTML mockup galerisi paylaştı (Welcome/Login/Register-belge adımı/Pending/Rejected — 5 ekran). Detay: `project-goals.md §9.5`. Özet:

| Konu | Durum |
|---|---|
| Marka rengi çelişkisi (koyu yeşil vs. indigo `#4F46E5`) | **Çözüldü (2026-09-14):** Kullanıcı "yeni bilgilere göre güncelle, eski zorunluluklara bağlı kalma" talimatı verdi → mockup rengi (indigo/mor) bağlayıcı kabul edildi, "koyu yeşil" tarifi supersede edildi. `project-goals.md §8` madde 19 "Netleşti" olarak güncellendi. |
| Klasör yapısı (`App/Core/Domain/Data/Features`) | `AI_Guidelines.md §2.3`'e işlendi, iskelet buna göre oluşturuldu |
| Apple/Google Sign-In | **Netleşti (2026-09-14):** Firebase Auth'a dahil edilecek. `project-goals.md §8` madde 21. |
| 6 adımlı kayıt sihirbazı (1-5. adım içeriği), red gerekçesi alanı | Hâlâ açık — hiçbir kaynakta verilmedi, uydurulmayacak. `project-goals.md §8` madde 20. |
| Mockup'ın dayandığı gerçek kodun konumu | Bu proje dizininde yok; belirsiz — `project-goals.md §8` madde 22 |

**Karar (2026-09-14):** Kullanıcı açıkça talimat verdi: mockup'tan çıkan yeni bilgiler, önceki metinsel spesifikasyonla çeliştiği durumlarda dahi önceliklidir; eski kurala "bağlı kalınmayacak". Bu, yalnızca §2.3'teki renk çelişkisine değil, ileride ortaya çıkabilecek benzer çelişkilere de uygulanacak genel bir önceliklendirme kararıdır — ancak bu, hiç veri olmayan noktalarda (ör. madde 20, 22) uydurma yapılacağı anlamına gelmez; yalnızca **elde somut kanıt olan** noktalarda eski tarif yerine yeni kanıt esas alınır.

### 2.4 Skill Çakışma Kararları

| Decision | Why | Alternative | Risk |
|---|---|---|---|
| Web-merkezli tasarım skill'leri (`frontend-design`, `ui-ux-pro-max` vb.) yalnızca **ilke düzeyinde** (renk teorisi, tipografi ritmi, hiyerarşi) kullanılacak; SwiftUI implementasyon detayları için referans alınmayacak | Bu skill'ler HTML/CSS/web bileşen varsayımıyla yazılmış; SwiftUI'a birebir uygulanması yanlış desenlere yol açabilir | Apple Human Interface Guidelines'ı birincil kaynak olarak kullanmak | Düşük — yanlış uygulanırsa native olmayan bir UI hissi ortaya çıkabilir, tasarım incelemesinde yakalanır |
| Data Privacy Officer agent'ının KVKK çıktıları "hukuki teyit gerekir" etiketiyle kullanılacak, doğrudan uyum kararı olarak kabul edilmeyecek | Agent genel GDPR/CCPA eğitimlidir, KVKK'nın Türkiye'ye özgü maddeleri (ör. Kurul kararları) garanti kapsanmıyor | Gerçek bir hukuk danışmanına/KVKK uzmanına danışmak | Orta — yanlış uyum varsayımı gerçek hukuki riske yol açabilir |

---

## 3. Alınan Kararlar (Decision Log)

| Tarih | Decision | Why | Alternative | Risk |
|---|---|---|---|---|
| 2026-09-14 | Teknoloji yığını: Swift + SwiftUI + Firebase (Firestore, Cloud Functions, Storage, Auth) + Claude API | Kullanıcı tarafından doğrudan belirtildi | — | — |
| 2026-09-14 | Claude API yalnızca Cloud Functions üzerinden çağrılacak, istemciden asla doğrudan çağrılmayacak | Kullanıcı açıkça belirtti; ayrıca API anahtarını istemci tarafında saklamamak güvenlik gereği | Client-side çağrı (reddedildi) | — |
| 2026-09-14 | Eşleşme skoru backend'de hesaplanacak, Claude karar mercii olmayacak | Kullanıcının temel mimari ilkesi: "Claude eşleşmeye karar veren sistem değil" | Skor hesaplamasını Claude'a bırakmak (reddedildi) | — |
| 2026-09-14 | Admin/moderatör yetkisi yalnızca Firebase Auth custom claims ile belirlenecek | Kullanıcı açıkça belirtti; istemci-taraflı `isAdmin` güvenilmez | İstemci taraflı flag (reddedildi — güvenlik açığı) | — |
| 2026-09-14 | Üniversite izolasyonu hem istemci filtrelemesiyle hem de Firestore Security Rules ile zorlanacak | Kullanıcı açıkça belirtti; sadece istemci filtrelemesi yetersiz | Sadece istemci filtrelemesi (reddedildi) | — |
| 2026-09-14 | project-goals.md §8'deki 17 madde "açık nokta" olarak işaretlendi, varsayılan bir teknik karara dönüştürülmedi | Kaynak proje fikninde bu noktalar belirtilmemişti; talimat gereği kafadan varsayım yapılmayacak | Makul varsayımlarla devam etmek (reddedildi — kullanıcı talimatına aykırı) | Netleşmeden geliştirmeye geçilirse yeniden işe yol açabilir |
| 2026-09-14 | iOS 17+, Swift Concurrency (async/await), MVVM + Repository Pattern mimari standardı olarak kabul edildi | Kullanıcı açıkça belirtti (detaylı spesifikasyon) | Daha eski iOS hedefi / farklı mimari desen (reddedildi) | — |
| 2026-09-14 | Firebase Cloud Messaging (push), Firebase App Check (istemci bütünlüğü), Firebase Crashlytics + Analytics teknoloji yığınına eklendi | Kullanıcı açıkça belirtti | — | App Check sağlayıcı detayı hâlâ açık (`project-goals.md §8.18`) |
| 2026-09-14 | Claude model adı koda sabitlenmeyecek; `ANTHROPIC_MODEL` secret/env değişkeni üzerinden yönetilecek | Model değişikliğinde iOS'un yeniden yayınlanmasını gerektirmemek için | Kod içine sabit model adı yazmak (reddedildi) | Production'da kullanılacak tam model adı hâlâ belirtilmedi |
| 2026-09-14 | Claude çıktısı strict structured JSON olacak; backend 8 adımlı doğrulama (parse → şema → whitelist → uzunluk limiti → beklenmeyen alan reddi → kontrol karakteri temizliği → fallback → Firestore) uygulayacak | Kullanıcı açıkça belirtti; Claude çıktısı güvenilmeyen veri kabul edilir | Claude çıktısını doğrudan Firestore'a yazmak (reddedildi — güvenlik açığı) | — |
| 2026-09-14 | `score, admin, isVerified, accountStatus, userId, ownerId, universityId, permissions` alanları Claude çıktısında asla güvenilmeyecek; whitelist tarafından reddedilecek | Kullanıcı açıkça belirtti; güvenlik-kritik alanlar yalnızca backend tarafından belirlenir | Bu alanların Claude çıktısından kabul edilmesi (reddedildi) | — |
| 2026-09-14 | Backend Claude servisi `functions/src/ai/{anthropic-client.ts, need-parser.ts, schemas/need-schema.ts}` olarak yapılandırılacak | Kullanıcı açıkça belirtti; tek sorumluluk ayrımı | Tek dosyada monolitik entegrasyon (reddedildi) | — |
| 2026-09-14 | iOS tarafında `AIParsingRepository` protokolü + `FirebaseFunctionsRepository` implementasyonu kullanılacak; `AnthropicClient`/`ClaudeClient`/`AnthropicAPIKey` gibi iOS-taraflı bir AI servisi olmayacak | Kullanıcı açıkça belirtti; Claude'a erişim tek noktadan (Cloud Functions) olmalı | iOS'ta doğrudan Anthropic SDK kullanımı (reddedildi — güvenlik ilkesine aykırı) | — |
| 2026-09-14 | `parseNeed` ve `recomputeMatches` kullanıcı bazlı rate limit ile korunacak (Firestore `rate_limits/{bucketId}`, istemci yazamaz); Claude her mesajda değil yalnızca ihtiyaç oluşturma/ayrıştırmada çağrılacak, değişmeyen taslak için tekrar çağrılmayacak | Claude API ücretli; kötüye kullanım ve maliyet kontrolü gerekli | Sınırsız çağrı (reddedildi) | — |
| 2026-09-14 | Claude'a e-posta, telefon, öğrenci belgesi, Firebase UID, Auth token, admin bilgisi, mesajlaşmanın tamamı gönderilmeyecek; yalnızca ihtiyaç metninin anlaşılması için gerekli minimum metin gönderilecek | KVKK veri minimizasyonu ilkesi, kullanıcı açıkça belirtti | Tüm kullanıcı bağlamını Claude'a göndermek (reddedildi) | — |
| 2026-09-14 | Claude request/response ham içeriği (`rawText`, `messages`, kişisel veri) production loglarına yazılmayacak; yalnızca `requestId`, `duration`, `model`, `success/failure`, `token usage` metadata'sı tutulacak | KVKK + güvenlik; loglar da bir veri sızıntı yüzeyidir | Ham içeriği debug amaçlı loglamak (reddedildi) | — |
| 2026-09-14 | Claude API kullanılamazsa basit, deterministic bir fallback parser (tahmin/yetki/sahte veri üretmeyen) devreye girecek; o da başarısızsa kullanıcıya düzenlenebilir taslak veya Türkçe hata mesajı gösterilecek | Sistem tek bir dış servise (Claude) tam bağımlı olmamalı | Claude başarısızsa özelliği tamamen kapatmak (reddedildi) | — |
| 2026-09-14 | AI sağlayıcısı kesin olarak Anthropic Claude; Gemini ve OpenAI **kullanılmayacak** | Kullanıcı açıkça ve tekrar tekrar vurguladı ("SON KURAL") | — | — |

---

## 4. Tamamlanan İşler

- [x] 2026-09-14 — Proje dizini ve skill kaynakları tarandı (bulgular: §2)
- [x] 2026-09-14 — `project-goals.md` oluşturuldu
- [x] 2026-09-14 — `AI_Guidelines.md` oluşturuldu
- [x] 2026-09-14 — `memory-bank/Memory_Bank.md` başlatıldı
- [x] 2026-09-14 — Kullanıcıdan alınan detaylı Claude API + SwiftUI mimari spesifikasyonu işlendi: `project-goals.md §8` ve §10 güncellendi (8 madde netleşti/kısmen netleşti), `AI_Guidelines.md` §2 (mimari akış + iOS katman mimarisi), §3.3 (App Check), §4 (Claude API kuralları 14 alt bölüme genişletildi), §6 ve §11 güncellendi; 13 yeni karar Decision Log'a işlendi

## 5. Açık Sorular

`project-goals.md §8`'deki güncel 18 maddenin tam listesi için o dokümana bakın (2026-09-14 itibarıyla 8 madde netleşti/kısmen netleşti). Özet — henüz **tamamen** açık kalan, geliştirmeyi doğrudan etkileyecek en kritik maddeler:

1. Eşleşme skoru ağırlıklarının gerçekten nihai olup olmadığı (hâlâ "örnek" ifadesiyle sunuluyor)
2. Production'da kullanılacak tam Claude model adı (mekanizma netleşti — `ANTHROPIC_MODEL` env; isim netleşmedi)
3. Firebase App Check sağlayıcı stratejisi (DeviceCheck/App Attest, debug token)
4. Üniversite listesi ve `universityId` doğrulama mekanizması
5. Offline/önbellekleme stratejisi, test stratejisi, moderasyon iş akışı detayı, mesajlaşma kapsamı (1:1/grup), KVKK veri saklama süreleri, App Store yayın planı, hedef öğrenci seviyesi, monetizasyon, lokalizasyon kapsamı

## 6. Sıradaki Adımlar

1. Kullanıcı ile §5'teki kalan açık soruları netleştirmek (öncelik: skor ağırlıklarının nihailiği, production Claude modeli, App Check sağlayıcısı)
2. Firestore veri modeli taslağının tamamlanması — `needs` koleksiyonu için ilk alan sahiplik tablosu hazır (bkz. `AI_Guidelines.md §4.14`); `users`, `matches`, `communities`, `events`, `messages`, `reports`, `rate_limits` koleksiyonları hâlâ taslak aşamasında
3. Firestore Security Rules taslağının yazılması (güvenlik-kritik alanlar için — bkz. `AI_Guidelines.md §3`, §4.6, §4.14)
4. Tasarım sistemi tanımı (renk paleti, tipografi, `TearOffStrip` bileşen spesifikasyonu)
5. Firebase projesinin kurulması (App Check, FCM, Crashlytics, Analytics dahil — henüz kurulmadı)
6. Xcode projesinin başlatılması, `.gitignore` yapılandırması (bkz. `AI_Guidelines.md §3.1, §9`)
7. `functions/src/ai/` klasör iskeletinin oluşturulması (`anthropic-client.ts`, `need-parser.ts`, `schemas/need-schema.ts`) — henüz kod yazılmadı, yalnızca yapı planlandı
8. İlk düşük-riskli özellik (muhtemelen: onboarding + öğrenci doğrulama akışı) için detaylı plan

## 7. Değişiklik Geçmişi

| Tarih | Değişiklik |
|---|---|
| 2026-09-14 | Memory Bank başlatıldı; ilk skill taraması, karar günlüğü ve açık sorular kaydedildi |
| 2026-09-14 | Detaylı Claude API + SwiftUI mimari spesifikasyonu işlendi; 13 yeni karar eklendi, 8 açık nokta netleşti/kısmen netleşti, sıradaki adımlar ve açık sorular güncellendi |
| 2026-09-16 | Backend (Cloud Functions) implementasyonu yazıldı ve `npx tsc --noEmit` ile derleme hatasız doğrulandı: `functions/src/ai/{anthropic-client,need-parser,fallback-parser,prompts,schemas/need-schema}.ts`, `functions/src/callable/{parseNeed,publishNeed}.ts`, `functions/src/lib/{rateLimit,logging,sanitize}.ts`, `functions/src/index.ts`, `firestore.rules`, `firestore.indexes.json`, `firebase.json`, `.gitignore`. Ardından tüm iOS tarafı Swift ile yazıldı (29 dosya, `KampusAgi-iOS/KampusAgi/` altında App/Core/Domain/Data/Features) — bkz. §8 aşağıda. |

## 8. 2026-09-16 İmplementasyon Turu (Firebase — SÜPERSEDE EDİLDİ)

> ⚠️ Bu bölüm TARİHSEL kayıttır. Aynı gün ilerleyen saatlerde backend Firebase'den Supabase'e taşındı (kullanıcı talimatıyla — bkz. §9). Firebase dosyaları (`functions/`, `firestore.rules`, `storage.rules`, `firebase.json`) silindi. Aşağıdaki içerik silinmedi çünkü alınan mimari kararların (ör. "Claude güvenilmez veri üretir" ilkesi, publishNeed'in neden ayrı bir adım olduğu) gerekçesi hâlâ geçerli — yalnızca implementasyon platformu değişti.

### Backend (doğrulandı: `npx tsc --noEmit` hatasız)
- `parseNeed`: rawText -> Claude -> ParsedNeedSchema (.strict()) doğrulaması -> fallback -> **yalnızca taslak döner, Firestore'a yazmaz**.
- `publishNeed`: **kullanıcının orijinal spesifikasyonunda birebir verilmedi** — "Taslak göster -> Kullanıcı düzenler -> Yayınla -> Firestore" akışını tamamlamak için tasarlandı (bkz. §3 Decision Log). Taslağı sunucu tarafında yeniden doğrular, universityId'yi `users/{uid}` dokümanından okur, authorId/status/timestamps'i kendisi set eder.
- Rate limit: Firestore `rate_limits/{uid_bucket}`, transaction tabanlı, istemci yazamaz.

### iOS (YAZILDI, DERLENEMEDİ — bu ortamda Xcode/Swift toolchain yok, Windows)
Klasör: `KampusAgi-iOS/KampusAgi/{App,Core,Domain,Data,Features}` (bkz. §9.5, AI_Guidelines §2.3). 29 dosya: Auth (Welcome/Login/Registration/DocumentStep — mockup'a birebir), StudentVerification (Pending/Rejected), NeedCreation (Claude akışı uçtan uca), MainTabView (4 sekme, Topluluklar/Mesajlar/Profil açıkça "netleşmedi" placeholder).

**Kod incelemesinde bulunup düzeltilen gerçek hata:** Swift'in sentezlediği varsayılan `Encodable`, `nil` Optional alanları (`participantCount`, `startsAt`) JSON'dan tamamen atlıyordu (`encodeIfPresent`); backend şeması bu alanları `.nullable()` (zorunlu ama null olabilir) tanımlıyor, `.optional()` değil — anahtar eksik olsaydı `publishNeed` her null durumda taslağı reddederdi. `ParsedNeed` için elle `encode(to:)` yazılarak düzeltildi (bkz. `Domain/NeedCreation/ParsedNeed.swift`).

**Yeni tespit edilen TODO'lar (henüz yazılmadı):**
1. `submitStudentDocument` Cloud Function — `FirebaseStudentVerificationRepository.swift` bunu çağırıyor ama backend'de bu fonksiyon **henüz yok**. PDF Storage'a yüklendikten sonra Firestore inceleme kaydını oluşturup `users/{uid}.accountStatus`'u PENDING yapması gerekiyor (istemci bunu doğrudan yapamaz, bkz. firestore.rules).
2. `storage.rules` yazıldı (`studentDocuments/{userId}/document.pdf`, sahibi + moderatör okuyabilir, 10MB + PDF content-type sınırı) ve `firebase.json`'a eklendi.
3. **Yeni bağımlılık (kullanıcının orijinal teknoloji listesinde YOKTU, mockup'tan çıkarıldı):** GoogleSignIn-iOS SPM paketi (>=7.x) — Apple Sign In için AuthenticationServices zaten sistem framework'ü, ek bağımlılık gerektirmiyor.
4. Firebase App Check `AppCheckProviderFactory` `KampusAgiApp.swift`'te set edilmedi (sağlayıcı netleşmedi, bkz. `project-goals.md §8` madde 18) — bu olmadan `enforceAppCheck: true` olan Callable Function'lar prod'da tüm istekleri reddeder.
5. Xcode projesi (.xcodeproj/.xcworkspace), Assets.xcassets, GoogleService-Info.plist bu ortamda ÜRETİLMEDİ — Windows'ta Xcode yok. Kullanıcı bu Swift dosyalarını bir Mac'te Xcode ile yeni/mevcut bir projeye eklemeli, SPM bağımlılıklarını (firebase-ios-sdk, GoogleSignIn-iOS) eklemeli ve derleme hatalarını (özellikle Apple/Google Sign-In API imzaları, SDK sürümüne göre değişebilir) gidermelidir.

---

## 9. 2026-09-16 (devam) — Supabase'e Geçiş: İşlem Sonu Raporu

Kullanıcı talimatı: "KampüsAğı — Supabase MCP + SwiftUI iOS Production Development Prompt" (44 bölümlü, Firebase'i tamamen Supabase ile değiştiren büyük kapsamlı talimat). Aşağıda kullanıcının kendi istediği rapor formatı (§44) izlenmiştir.

### 9.1 İncelenen Dosyalar
`project-goals.md`, `AI_Guidelines.md`, `memory-bank/Memory_Bank.md` (bu dosya), `KampusAgi-iOS/` (29 Swift dosyası), eski `functions/`, `firestore.rules`, `storage.rules`, `firebase.json`, `firestore.indexes.json` (incelendi, sonra silindi). Web prototipi (App.tsx, CommunityFeed.tsx vb.) bu proje dizininde **bulunamadı** — muhtemelen ayrı bir ortamda/oturumda; bu yüzden §16-21'deki (Topluluklar/PostCard/CreatePost ekranları) native dönüşüm bu turda yapılamadı.

### 9.2 Oluşturulan Supabase Migration'ları
22 migration dosyası, `supabase/migrations/` altında, sırayla uygulandı ve canlı projede doğrulandı (bkz. §9.4):
`202609160000_create_helpers` → `...0021_add_profiles_to_realtime`. Tam liste için dizine bakın. Kullanıcının önerdiği 12 dosyalık plandan fazlası gerekti çünkü (a) tablo listesi (§8) 12 dosyaya sığmıyordu (campus_events/leaderboard/badges/reports için ek dosyalar), (b) `get_advisors` sonrası düzeltme migration'ları eklendi (§9.4).

### 9.3 Oluşturulan Tablolar ve İlişkiler
20 tablo: `profiles, universities, student_verifications, communities, community_members, posts, comments, post_likes, saved_posts, requirements, matching_config, matches, conversations, conversation_members, messages, notifications, campus_events, campus_event_attendees, badges, user_badges, leaderboard_entries, reports, rate_limits` (23 — kullanıcının 20'lik listesine `matching_config` ve `campus_event_attendees` eklendi, gerekçesi migration yorumlarında). Tüm foreign key'ler, check constraint'ler, `updated_at` tetikleyicileri ve indeksler mevcut (bkz. §9.4 performans düzeltmesi).

### 9.4 RLS Politikaları
Her tabloda RLS aktif. İlk turda `get_advisors` çalıştırıldı ve **gerçek bir güvenlik açığı** bulundu: `match_requirements` fonksiyonu `target_university_id`'yi parametre olarak kabul ediyordu — bir istemci başka bir üniversitenin requirement ID'lerini/benzerlik skorlarını RPC ile sorgulayabilirdi. Düzeltildi: artık her zaman çağıranın kendi profilinden türetiliyor. Ayrıca: 4 trigger-only fonksiyonun gereksiz public RPC erişimi kapatıldı; 13 eksik FK indeksi eklendi; 31 RLS politikasında `auth.uid()`/özel fonksiyon çağrıları `(select ...)` ile sarmalanarak per-row yeniden değerlendirme performans sorunu giderildi; 3 tabloda çakışan permissive policy'ler ayrıştırıldı. **İkinci `get_advisors` çalıştırması: yalnızca beklenen/kasıtlı bulgular kaldı** (rate_limits'in policy'siz olması ve RLS'de kullanılan yardımcı fonksiyonların RPC olarak çağrılabilmesi — ikisi de tasarım gereği).

### 9.5 Storage Bucket ve Politikaları
`student-documents` bucket (private, 10MB limit, yalnızca `application/pdf`). Yol: `{user_id}/document.pdf`. Politikalar: sahibi + moderatör/admin okuyabilir; yalnızca sahibi yükleyip güncelleyebilir (upsert, red sonrası yeniden yükleme için).

### 9.6 Oluşturulan Edge Functions
4/4 deploy edildi (`ACTIVE`, `verify_jwt: true`): `parse-need`, `publish-need`, `submit-student-document`, `recompute-matches`. **`ANTHROPIC_API_KEY`/`ANTHROPIC_MODEL` secret'ları HENÜZ SET EDİLMEDİ** — Supabase MCP'de bunun için bir araç yok (beklenen: API anahtarı değerini zaten görmemeliyim). **Kullanıcı bunu Dashboard > Edge Functions > Secrets'tan veya `supabase secrets set` ile yapmalıdır**, aksi halde `parse-need` her zaman fallback parser'a düşer.

### 9.7 Claude Entegrasyonu ve Güvenlik Akışı
AI_Guidelines.md §45.4'te özetlendi — Firebase-dönemi ilkelerin (Claude karar vermez, çıktısı güvenilmez, prompt injection farkındalığı, PII minimizasyonu, ham içerik loglanmaz) TAMAMI korundu, yalnızca Cloud Functions→Edge Functions, Firestore→PostgreSQL, Firestore Rules→RLS okunmalı. **Açık kalan kritik sorun:** eşleştirme ağırlıklarının %60'ı (`semantic_weight`) pgvector embedding gerektiriyor; Claude'un embedding endpoint'i yok, OpenAI/Gemini yasak. `recompute-matches` şu an yalnızca kategori+etiket+bölüm yakınlığını hesaplıyor (gerçek ama KISMİ bir eşleşme motoru) — bkz. AI_Guidelines §45.5.

### 9.8 SwiftUI'ye Dönüştürülen/Güncellenen Ekranlar
Mevcut 5 ekran (Welcome/Login/Registration+DocumentStep/Pending/Rejected) kullanıcının 2026-09-16 tasarım spesifikasyonu mesajına göre yeniden düzenlendi: `Core/DesignSystem/{AppColors,AppTypography,AppSpacing,AppRadius,AppTheme}.swift` oluşturuldu (buton stilleri, `LabeledInputField`, kart modifier'ı — tüm sayısal değerler mesajdan birebir), her ekrana açık/koyu `#Preview` eklendi, `RootViewModel.Destination` enum'ı `unauthenticated/pendingReview/approved/rejected(reason:)` olarak güncellendi. **Community/Chat/Notifications/Leaderboard/CampusEvents ekranları HENÜZ native olarak yazılmadı** (yalnızca `MainTabView` içinde açıkça "netleşmedi" placeholder'lar var) — web prototipi bu ortamda bulunamadığı için (§9.1) ve kapsam devasa olduğu için bu turda yapılamadı.

### 9.9 Silinen Mock/Test Verileri
Bu proje hiçbir zaman mock veri İÇERMEDİ (baştan Supabase/Firebase gerçek backend'e yazılacak şekilde tasarlandı) — silinecek `mockData.ts` bulunamadı. `supabase/seed.sql` yalnızca GERÇEK referans verisi içerir (12 gerçek Türkiye üniversitesi, otomatik topluluklar, 3 rozet tanımı) — sahte kullanıcı/gönderi/mesaj YOKTUR, canlı DB'de doğrulandı (`universities: 12, communities: 13, badges: 3`).

### 9.10 Kategori Scrollbar Düzeltmesi
**Uygulanamadı** — kategori/feed ekranı (§16-19) bu turda native olarak yazılmadığı için scrollbar sorunu ortaya çıkmadı. `ScrollView(.horizontal, showsIndicators: false)` deseni ileride bu ekran yazıldığında uygulanmalıdır (bkz. §17).

### 9.11 Xcode'da Manuel Yapılması Gerekenler
1. Xcode'da yeni/mevcut bir iOS App projesi oluşturup 33 Swift dosyasını ekleyin.
2. SPM: `supabase-swift` (>=2.x), `GoogleSignIn-iOS` (>=7.x).
3. Info.plist: Google reversed client ID + `GIDClientID`; Sign in with Apple capability.
4. Supabase Dashboard > Authentication > Providers: Apple ve Google sağlayıcılarını etkinleştirin (Client ID/Secret girin).
5. Supabase Dashboard > Edge Functions > Secrets: `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` set edin.
6. Bundle Identifier + Signing Team ayarlayın.

### 9.12 Build Sonuçları
- **Supabase backend:** Canlı, `get_advisors` (security + performance) temiz. `execute_sql` ile seed verisi doğrulandı.
- **iOS/Swift:** Bu ortamda (Windows, Xcode/Swift toolchain yok) **DERLENEMEDİ**. Kod, iyi bilinen supabase-swift/GoogleSignIn-iOS API kalıplarına göre yazıldı; SDK sürümüne bağlı küçük imza farkları (özellikle Realtime V2 `postgresChange`/`decodeRecord` ve `functions.invoke` generic decode) Xcode'da ilk derlemede düzeltme gerektirebilir.

### 9.13 Unit Test Sonuçları
**Yazılmadı.** Kapsam devasa olduğu için bu turda test yazımına ayrılacak bütçe kalmadı — açıkça eksik olarak işaretleniyor, "tamamlandı" gibi gösterilmiyor.

### 9.14 UI Test Sonuçları
**Yazılmadı/çalıştırılamadı** (Xcode/Simulator yok).

### 9.15 Kalan Hatalar ve Açık Kararlar
1. **Kesinleşmemiş:** Semantik eşleşme embedding sağlayıcısı (§9.7) — kullanıcı onayı gerekiyor.
2. **Yapılmadı:** Community/Chat/Notifications/Leaderboard/CampusEvents için native SwiftUI ekranları, ilgili Repository'ler (`SupabaseCommunityRepository`, `SupabasePostRepository`, `SupabaseChatRepository`, `SupabaseNotificationRepository` — protokolleri de henüz yazılmadı).
3. **Yapılmadı:** `admin`/moderatör paneli (öğrenci belgesi inceleme arayüzü) — yalnızca backend (RLS `student_verifications_update_staff`) hazır, istemci arayüzü yok.
4. **Yapılmadı:** SwiftData local cache/taslak katmanı, StoreKit 2 (hiç istenmedi, kapsam dışı varsayıldı).
5. **Yapılmadı:** Tüm §38'deki test senaryoları (backend + unit + UI).
6. **Kullanıcı eylemi gerekli:** §9.6 ve §9.11'deki manuel adımlar olmadan uygulama gerçek Claude çağrısı yapamaz ve Xcode'da açılamaz.

### 9.16 Mac Erişimi (2026-09-16 — kullanıcının Mac'i yok, yalnızca iPhone 16 var)

Kullanıcı Mac sahibi değil. Önerilen yol: cloud Mac kiralama (MacInCloud/MacStadium) ile uzaktan Xcode kullanımı. Bunu kolaylaştırmak için `KampusAgi-iOS/project.yml` (xcodegen spesifikasyonu) + `Info.plist` + `KampusAgi.entitlements` oluşturuldu — hangi Mac'e erişilirse erişilsin, 33 dosyayı elle eklemek yerine `xcodegen generate` tek komutla tam yapılandırılmış `.xcodeproj` üretir (Supabase + GoogleSignIn SPM paketleri dahil). `.xcodeproj` bilerek git'e eklenmiyor (`project.yml` source of truth).

**Kullanıcının doldurması gereken tek placeholder:** `Info.plist`'teki Google Sign-In `CFBundleURLSchemes` → `com.googleusercontent.apps.REPLACE_ME` (Google Cloud Console'da iOS OAuth Client ID oluşturulmadan bilinemez, uydurulmadı).

**Not:** `SWIFT_VERSION: "6.0"` ayarlandı (kullanıcının orijinal "Swift 6" talebi) — Swift 6 dil modunun strict concurrency checking'i ilk derlemede ek uyarı/hata çıkarabilir; `project.yml` içinde geçici düşürme (Swift 5 dil modu) notu bırakıldı.

---

## 10. 2026-09-16 (devam) — Swift/iOS TAMAMEN TERK EDİLDİ, Android/Kotlin'e geçiş

Kullanıcı talimatı: *"Mantık aynı ama swiftten vazgeçtim tüm kodu kotlin diline döndür androide tasarım promptun da bu [...]"* + tam bir Android/Jetpack Compose tasarım spesifikasyonu (Material 3, Clean Architecture, Hilt, Navigation Compose, Credential Manager).

### 10.1 Yapılan İşlem
- `KampusAgi-iOS/` klasörü **tamamen silindi** (xcodegen dahil — artık gereksiz).
- `supabase/` klasörü **hiç dokunulmadan** korundu — backend platformdan bağımsız, hiçbir migration/Edge Function/RLS değişmedi.
- `KampusAgi-Android/` yeni Gradle projesi oluşturuldu: 28 Kotlin dosyası + Gradle Kotlin DSL yapılandırması (settings.gradle.kts, build.gradle.kts, libs.versions.toml) + strings.xml (tüm metinler, kullanıcının isteği gereği) + adaptive launcher icon (yer tutucu, bkz. §10.5).
- Ekranlar (Welcome/Login/Register 6 adım+DocumentStep/PendingReview/Rejected) kullanıcının Android tasarım mesajındaki ölçülerle (48dp buton, 12dp/16dp köşe, Material3 renk paleti) birebir yazıldı; her ekranda açık/koyu `@Preview` var — Hilt ViewModel'e bağımlı olmaması için her ekran "stateful" (hiltViewModel()) ve "stateless" (Content) composable'a ayrıldı, @Preview YALNIZCA stateless sürümü kullanıyor (aksi halde Hilt olmadan preview çöker).

### 10.2 Doğrulama Durumu (ÖNEMLİ — dürüstçe belirtiliyor)
Bu ortamda `java` var (OpenJDK 17) ama `kotlinc`, `gradle`, Android SDK **YOK**. Gradle wrapper JAR'ı (binary) da üretilemedi — yalnızca `gradle-wrapper.properties` (metin) var; `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` Android Studio ilk açılışta otomatik tamamlar. **Hiçbir Kotlin dosyası derlenip test edilememiştir.** Yazım sırasında en az 3 gerçek hata kendi kendine yakalanıp düzeltildi (örn. `12.dp()` gibi olmayan bir fonksiyon çağrısı, WelcomeScreen'de `Modifier.background` için gereksiz/yanlış bir extension tanımı, RejectedScreen'de gerçek iş yapmayan sahte bir `resubmitDocument` stub'ı) — bu, kod tabanının dikkatlice gözden geçirildiğinin ama YİNE DE Xcode/Android Studio'da gerçek bir derleme kontrolüne ihtiyaç duyduğunun kanıtıdır.

### 10.3 Kullanıcının Doldurması Gereken Placeholder'lar
1. `LoginScreen.kt` içinde `setServerClientId("REPLACE_WITH_GOOGLE_WEB_CLIENT_ID")` — Supabase Dashboard > Authentication > Providers > Google'da kayıtlı **Web** OAuth Client ID (Android Client ID DEĞİL).
2. `di/SupabaseModule.kt` içindeki `PUBLISHABLE_KEY`/`PROJECT_URL` zaten GERÇEK değerler (güvenli, public) — değişiklik gerekmiyor.
3. `ANTHROPIC_API_KEY`/`ANTHROPIC_MODEL` Supabase Edge Functions secret'ları — hâlâ set edilmedi (bkz. §9.6, platform değişikliğinden etkilenmez).

### 10.4 Kasıtlı Kapsam Dışı Bırakılanlar (uydurulmadı)
- Ana uygulama ekranları (Topluluk/İhtiyaç & AI/Sıralama/Mesajlar/Profil) — Android tasarım mesajı yalnızca auth+doğrulama akışını kapsıyordu, `ApprovedPlaceholderScreen` yalnızca asgari bir yer tutucu.
- Kayıt sihirbazının 2-5. adımları (PERSONAL_INFO/UNIVERSITY_SELECTION/DEPARTMENT_INFO/ADDITIONAL_INFO) — içerik hiçbir kaynakta verilmedi, "netleşmedi" ekranı gösteriliyor.
- Apple ile giriş — kullanıcının notuyla ("istemiyorsan kaldır") bilinçli olarak kaldırıldı.
- Gerçek FCM push entegrasyonu — bkz. §10.6 çelişki notu.
- SwiftData eşdeğeri (Room/DataStore local cache) — hiç talep edilmedi bu Android turunda, yazılmadı.

### 10.5 Launcher Icon
Gerçek bir marka logosu yoktu (uydurulmadı); AndroidManifest'in `@mipmap/ic_launcher` referansının derleme hatası vermemesi için minimal bir adaptive icon (brandPrimary arka plan + beyaz daire) XML olarak eklendi. Kullanıcı Android Studio > File > New > Image Asset ile gerçek logoyu eklemeden önce bu YAYINA ÇIKMAMALIDIR.

### 10.6 Push Bildirim Çelişkisi — ÇÖZÜLDÜ (2026-09-16, devam)
Kullanıcı kararı: **"Fcm yi iptal et"** — çelişki Firebase yasağı lehine çözüldü. `POST_NOTIFICATIONS` izin kodu (`MainActivity.kt`) ve manifest girişi (`AndroidManifest.xml`) kaldırıldı. Bu proje turunda push bildirim altyapısı hiç kurulmuyor; bildirimler yalnızca uygulama içi (`notifications` tablosu + Realtime) çalışacak. Bkz. `AI_Guidelines.md §45.7` (güncellendi).

## 11. 2026-09-16 (devam) — Aynı mesajdaki ikinci talep: "supabase yanlış yazılmış"

Kullanıcı FCM iptalinin yanında "supabase yanlış yazılmış" dedi ama nerede olduğunu belirtmedi. Kod tabanında (`*.kt`, `*.xml`, `*.toml`, `*.kts`, `*.sql`, `*.ts`, `*.md`) "Supabase" kelimesinin yazım hatalı bir varyantı (Supbase/Suprabase/Supabse vb.) için tam arama yapıldı — **bulunamadı**. `di/SupabaseModule.kt` içindeki proje URL'i (`https://ggphcgapgwrcdumfldsc.supabase.co`) ve publishable key, MCP `get_project_url`/`get_publishable_keys` çağrılarından alınan gerçek değerlerle birebir eşleşiyor. Kullanıcıdan hangi dosya/ekran olduğu netleşene kadar bu madde **AÇIK** — uydurup rastgele bir "düzeltme" yapılmadı.

### 10.7 Sıradaki Adımlar (güncel)
1. Android Studio'da projeyi açıp Gradle sync yapmak (wrapper otomatik tamamlanır).
2. §10.3'teki Google Web Client ID placeholder'ını doldurmak.
3. Supabase Edge Functions secret'larını set etmek (§9.6).
4. **Kullanıcıdan bekleniyor:** "supabase yanlış yazılmış" — nerede olduğunun netleşmesi (§11).
5. Ana uygulama ekranlarına (varsa) geçmeden önce kullanıcıdan yeni bir tasarım turu istemek.
