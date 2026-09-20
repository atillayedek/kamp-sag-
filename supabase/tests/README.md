# Sunucu kuralları (RLS / tetikleyici / RPC) testleri

Her dosya **tek transaction**'dır ve `ROLLBACK` ile biter: canlı veritabanında kalıcı veri bırakmaz. Bir doğrulama başarısız olursa
`FAIL: ...` istisnası fırlar ve çalıştırma hata ile durur; **hatasız bitmesi = geçti**.

## Nasıl çalıştırılır

- **Supabase SQL editörü / MCP `execute_sql`:** dosyanın tamamını yapıştırıp çalıştırın (sonuç boş liste = geçti).
- **`psql` ile (CI için):**
  ```bash
  SUPABASE_DB_URL="postgresql://postgres:<şifre>@db.<ref>.supabase.co:5432/postgres" ./scripts/run-rules-tests.sh
  ```
  Şifre ortam değişkeninden gelir, depoya yazılmaz.

## Testler

| Dosya | Neyi kanıtlar |
|---|---|
| `001_verification_rules.sql` | Belge onayı yalnızca moderatör + atomik RPC ile; doğrudan UPDATE etkisiz; onaylı hesap üniversiteyi/`account_status`'u/`karma_score`'u değiştiremez; hesap silme temizliği |
| `002_community_rules.sql` | Yalnızca kendi üniversitesine gönderi; sayaçlar herkese doğru ve sahtelenemez; başka üniversite/onaysız hesap beğeni-yorum-kaydetme yapamaz; kaldırılan gönderi geri yayınlanamaz |
| `003_chat_rules.sql` | Sohbet yalnızca RPC ile; üyelik izolasyonu; gönderen kimliği sahtelenemez; mesaj değiştirilemez/silinemez; bildirim, okundu bilgisi, realtime konu yetkisi |
| `004_notification_preferences.sql` | Bildirim tercihi yalnızca sahibine; yeni-mesaj bildirimi tercihe uyar; hesap silinince tercih gider |
| `005_viewer_must_be_approved.sql` | **Okuma** yetkisi doğrulanmış öğrenciye ait: PENDING/REJECTED hesap gönderi/yorum/ilan/etkinlik/topluluk/başkasının profilini göremez |
| `006_subscriptions.sql` | Kullanıcı kendine hak yazamaz; süresi dolan hak FREE; aynı jeton iki hesaba bağlanamaz; taslak plan görünmez |
| `007_campus_events.sql` | Etkinlik akışı yalnızca doğrulanmış kullanıcıya, genel + kendi üniversitesi, yaklaşan; sayı doğru kimlik sızmaz; katılım yalnızca görülebilen/bitmemiş etkinliğe |
| `008_comment_notification_protection.sql` | Yorumda yalnızca `body`, bildirimde yalnızca `is_read` değişebilir (sunucu tarafı etkilenmez) |

Edge Function saf mantığı: `node --test supabase/functions/verify-purchase/logic.test.ts` (Node 22.18+).

## Yeni kural eklerken

1. Önce **başarısız** olacak testi yaz (açığı gerçekten gösterdiğini doğrula — gerekirse korumayı transaction içinde geçici kaldır).
2. Migration'ı yaz ve uygula; testin geçtiğini gör.
3. Tetikleyici korumalarında `current_user = 'authenticated'` deseni kullanılır (sunucu/definer tarafını etkilemez; `auth.role()` KULLANMA — bkz. docs/DECISIONS.md D17).
4. Test kullanıcı kimliklerinin ilk 25 onaltılık karakteri birbirinden farklı olmalı (varsayılan kullanıcı adı buradan türer).
