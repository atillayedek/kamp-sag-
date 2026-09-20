# KampüsAğı — İlerleme Dosyası

> Bu dosya oturumlar arası hafızadır. Her oturum başında `KAMPUSAGI_CLAUDE_CODE_PROMPT.md` (özellikle §0) ve bu dosya okunur; kaldığın yerden devam edilir.
> Kararlar → `docs/DECISIONS.md` · Dış erişim gerektiren engeller → `docs/BLOCKERS.md`

## Şu an üzerinde çalışılan görev

**Görev 14: Rules (RLS) testlerinin konsolidasyonu — sonra 15 (erişilebilirlik/yerelleştirme/gizlilik), 16, 17, 18.**

## Son başarılı build/test

- 2026-09-20 — `:app:testDebugUnitTest` ✓ (275 test), `:app:verifyRoborazziDebug` ✓ (50 ekran görüntüsü, light+dark yan yana, gözle doğrulandı), `:app:compileDebugKotlin` ✓; `node --test supabase/functions/verify-purchase/logic.test.ts` ✓ (12); canlı DB testleri `supabase/tests/001,004,005,006,007` ✓ (002/003 Görev 14'te yeniden koşulacak).

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
| Welcome, Login, Register (6 adım), PendingReview, Rejected | Gerçek, Supabase'e bağlı, yeni tasarım sisteminde. Google Web Client ID `local.properties`'ten (BLOCKERS B2). Apple girişi Android'de yok (D5) |
| Topluluklar (+ gönderi oluştur, gönderi detayı/yorumlar) | Gerçek (Görev 7) |
| İhtiyaç Oluştur (AI analiz kartı) | Gerçek (Görev 8): `parse-need` + `publish-need`; yayından sonra `recompute-matches` tetiklenir |
| Eşleşmeler (kart yığını, kaydırarak geç, Profili Gör, Mesaj At) + kullanıcı profili | Gerçek (Görev 9) |
| Sohbet listesi + sohbet (gönderiliyor/gönderilemedi/iletildi, yazıyor, çevrimiçi, eski mesajlar, yeniden bağlanma) + okunmamış rozeti | Gerçek (Görev 10); Realtime parçaları canlıda elle doğrulanmalı (RELEASE_NOTES) |
| Profil/Ayarlar (Hesap Bilgileri, Bildirim Ayarları, Gizlilik ve Konum, Koyu Görünüm, Hesabı Sil, Çıkış) | Gerçek (Görev 11) |
| Premium (Free / Premium / Community Pro) | Gerçek entegrasyon (Görev 12); ücretli planlar B5/B9 çözülene kadar görünmez |
| Etkinlikler (Topluluklar üst çubuğundan) | Gerçek (Görev 13): Supabase `campus_events`, katıl/ayrıl iyimser; REST API yok (B6, D34) |

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
- [x] 8. İhtiyaç Oluştur + analiz Function'ı (`parse-need`) — MockEngine ile HTTP sözleşmesi testli
- [x] 9. Eşleşmeler + skor Function'ı (`recompute-matches` normalize skor; "Anlamsal" yalnızca gerçek embedding skorunda)
- [x] 10. Sohbet + presence (RPC ile sohbet başlatma, özel Realtime kanalı, okundu/yazıyor/çevrimiçi; bildirim: FCM yerine Realtime, BLOCKERS B1)
- [x] 11. Profil / Ayarlar + tema tercihi (DataStore), hesap silme, bildirim tercihi (+ okuma yetkisi açığı düzeltildi)
- [x] 12. Premium + sunucu tarafı satın alma doğrulaması (`verify-purchase`; B5/B9/B10)
- [x] 13. Etkinlikler ekranı (Supabase kaynaklı — REST API park edilmiş alan adı, B6/D34)
- [ ] 14. Rules (RLS) + rules testleri
- [ ] 15. Erişilebilirlik, yerelleştirme, gizlilik (Android karşılıkları)
- [ ] 16. Unit + UI + snapshot testleri
- [ ] 17. Son tarama: mock/placeholder/TODO/FIXME/hardcoded örnek veri yok
- [ ] 18. Temiz build (Release) + tüm testler yeşil + `docs/RELEASE_NOTES.md`
