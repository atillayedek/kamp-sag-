# KampüsAğı — İlerleme Dosyası

> Bu dosya oturumlar arası hafızadır. Her oturum başında `KAMPUSAGI_CLAUDE_CODE_PROMPT.md` (özellikle §0) ve bu dosya okunur; kaldığın yerden devam edilir.
> Kararlar → `docs/DECISIONS.md` · Dış erişim gerektiren engeller → `docs/BLOCKERS.md`

## Şu an üzerinde çalışılan görev

**Görev 8: İhtiyaç Oluştur (`parse-need` analizi + `publish-need`) — sonra 9 (Eşleşmeler), 10 (Sohbet), 11 (Profil/Ayarlar), 12 (Premium), 13 (Etkinlikler).** Geçici sekme yer tutucuları (`MainScreen.kt` içinde `TabInProgress`, `ProfileTemporary`) görevler bittikçe kaldırılacak.

## Son başarılı build/test

- 2026-09-19 — `:app:testDebugUnitTest` ✓ (97 test), `:app:recordRoborazziDebug` ✓ (24 ekran görüntüsü, light+dark yan yana, gözle doğrulandı), `:app:compileDebugKotlin` ✓; canlı DB testleri (`supabase/tests/001,002`) ✓.

## Komutlar (Windows)

Proje yolunda `ı` var → Gradle test işçisi çalışmıyor; **testler için ASCII sürücü şart**:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\ensure-k-drive.ps1   # K: -> proje kökü (oturum başına bir kez)
```
```bash
cd /k/KampusAgi-Android
./gradlew :app:assembleDebug                 # derleme
./gradlew :app:testDebugUnitTest             # birim testleri
./gradlew :app:verifyRoborazziDebug          # ekran görüntüsü doğrulama (baseline: app/src/test/snapshots)
./gradlew :app:recordRoborazziDebug          # UI bilerek değiştiyse baseline'ı yeniden üret, PNG'leri gözle kontrol et
./gradlew :app:assembleRelease               # Release
```
Google girişi için `KampusAgi-Android/local.properties` içine `google.webClientId=...` (gizli değil, git'e girmez). Supabase URL/publishable key `gradle.properties`'te.

## Mevcut Durum (Keşif — 2026-09-19)

### Depo gerçeği (prompt'taki varsayımlarla farklar)
| Prompt diyor | Depoda gerçek |
|---|---|
| `KampusAgi-iOS/` (SwiftUI) | **Yok.** 2026-09-16'da kullanıcı kararıyla Swift/iOS tamamen silindi; istemci artık yalnızca `KampusAgi-Android/` (Kotlin, Compose, M3, Hilt) |
| Backend Firebase-only | **Supabase** (Postgres+RLS, Storage, Edge Functions, Realtime) — canlı proje `ggphcgapgwrcdumfldsc`, 24 migration + 4 Edge Function uygulanmış. Firebase kodu yok |
| Gemini | Sunucu tarafı AI **Claude** (Edge Function `parse-need`); anahtar yalnızca Edge Function secret'ında |
| `docs/PHASE1_AUDIT.md` | Yok (`docs/` klasörü hiç yoktu; proje belgeleri kökte: `project-goals.md`, `AI_Guidelines.md`, `memory-bank/Memory_Bank.md`) |
| Git deposu | Yoktu → bu oturumda `git init` yapıldı |
| Etkinlikler `api.kampusagi.com` REST | Bu depoda entegrasyon yok. Eski (Firebase) proje `Desktop\dosyalar\kampüs ağı\` içinde `CampusEventApi.kt` var → sözleşme oradan alınır (Görev 13) |

Ayrıca makinede eski bir Firebase tabanlı KampüsAğı projesi (`Desktop\dosyalar\kampüs ağı\`, Android + Firestore + Gemini, `ed625a2`) bulundu. **Salt-okunur referans**; değiştirilmez. Bkz. DECISIONS D1.

### Ekranlar (Android)
| Ekran | Durum |
|---|---|
| Welcome | Var, gerçek (mockup token'larına göre yazılmış; yeni token'lara taşınacak) |
| Login (e-posta/şifre, Google, şifre sıfırlama) | Var, Supabase Auth'a bağlı. Google Web Client ID **placeholder** (BLOCKERS B2). Apple ile devam et: Android'de anlamsız → yok (D5) |
| Register (6 adım) | Adım 1 (hesap) ve 6 (belge) gerçek; **adım 2–5 içeriği hiçbir kaynakta verilmedi** → "netleşmedi" placeholder + `"İleri (geçici)"` hardcoded metin (**mock**) |
| PendingReview / Rejected | Var, gerçek (Supabase'e bağlı); canlı izleme yok (yalnızca manuel yenile) |
| Approved | **Placeholder ekran** (ana uygulama yok) |
| Topluluklar, İhtiyaç Oluştur, Eşleşmeler, Sohbet, Profil/Ayarlar, Premium, Etkinlikler, ana navigasyon | **Yok** |

### Backend (Supabase) durumu
- Var: profiles, universities, student_verifications, communities, posts/comments/post_likes/saved_posts, requirements, matches, chat, notifications, campus_events, rate_limits, leaderboard/badges, reports; `student-documents` bucket; RLS her tabloda; `post_feed_view` (sayaçlar `count(*)` ile türetilir → trigger sayaç yok, D8).
- Edge Function: `parse-need` (Claude), `publish-need`, `submit-student-document`, `recompute-matches`.
- **Eksik:** atomik belge onayı function'ı (`review-verification`), admin claim atama, hesap silme, satın alma doğrulama, presence/typing altyapısı, embedding sağlayıcısı (anlamsal eşleşme şu an işlevsel değil, BLOCKERS B3).

### Bilinen kod sorunları (önceki `/code-review`, 7 bulgu)
Eksik `dp` import (derlemeyi kırıyor), `signUp` e-posta doğrulama varsayımı, `observeAuthState` Initializing→Unauthenticated flaşı, Google giriş `GoogleIdTokenParsingException` yakalanmıyor, sınırsız `readBytes()` (OOM), hardcoded `"İleri (geçici)"`, tekrarlı dosya okuma → Görev 4'te düzeltilecek.

### Derleme ortamı
Android SDK (`%LOCALAPPDATA%\Android\Sdk`: platform 36.1/37.0, build-tools 36.0.0, lisanslar kabul edilmiş), Android Studio, JDK 17, Gradle 9.x dağıtımları ve önbelleği mevcut → **gerçek derleme yapılabilir**. Eski notlardaki "derleme ortamı yok" artık geçerli değil.

## Görevler (Bölüm 6)

- [x] 1. Keşif + PROGRESS/DECISIONS/BLOCKERS dosyaları (+ git init, gerçek derleme altyapısı)
- [x] 2. Design tokens (renk, tipografi, boşluk) + light/dark  → `core/designsystem/{AppColors,AppTypography,Dimens,Theme}.kt`
- [x] 3. DesignSystem bileşen kütüphanesi + Preview'lar  → `core/designsystem/component/*` (17 bileşen; Apple butonu Android'de yok, D5)
- [x] 4. Auth/doğrulama ekranları tokens'a taşındı, mock'lar temizlendi, gerçek Supabase'e bağlandı (Welcome, Login, 6 adımlı Register, Pending, Rejected)
- [x] 5. Sunucu: `review_student_verification` RPC (atomik onay), `set-user-role` (claim), `delete-account`, tetikleyicili sayaçlar, university_id kilidi — gerçek DB testleriyle doğrulandı (`supabase/tests/`)
- [x] 6. Navigasyon + accountStatus yönlendirmesi (kök durum makinesi + 5 sekmeli `MainScreen`; sekmeler görevler ilerledikçe gerçek ekranla dolar)
- [x] 7. Topluluklar (Genel/Üniversitem akışı, sayfalama, pull-to-refresh, iyimser beğeni, gönderi oluşturma, yorumlar)
- [ ] 8. İhtiyaç Oluştur + analiz Function'ı (`parse-need`)
- [ ] 9. Eşleşmeler + skor Function'ı
- [ ] 10. Sohbet + presence (+ bildirim: FCM yerine Realtime, bkz. BLOCKERS B1)
- [ ] 11. Profil / Ayarlar + tema tercihi
- [ ] 12. Premium + sunucu tarafı satın alma doğrulaması
- [ ] 13. Etkinlikler ekranını tasarıma uyarla
- [ ] 14. Rules (RLS) + rules testleri
- [ ] 15. Erişilebilirlik, yerelleştirme, gizlilik (Android karşılıkları)
- [ ] 16. Unit + UI + snapshot testleri
- [ ] 17. Son tarama: mock/placeholder/TODO/FIXME/hardcoded örnek veri yok
- [ ] 18. Temiz build (Release) + tüm testler yeşil + `docs/RELEASE_NOTES.md`
