# Yönetim Rehberi (moderatör / admin)

Yetki **yalnızca** Supabase Auth JWT'sindeki `app_metadata` alanlarındadır (`is_admin`, `is_moderator`); istemci yazamaz.
RLS'teki `is_admin()` / `is_moderator()` bunları okur. Uygulamada moderasyon arayüzü yoktur; işlemler Supabase Studio
(SQL editörü) veya Edge Function ile yapılır.

## 1. İlk admini atama (bir kereye mahsus)

`set-user-role` yalnızca admin çağırabildiği için ilk admin SQL ile atanır (Supabase Dashboard → SQL Editor):

```sql
update auth.users
set raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb) || '{"is_admin": true}'::jsonb
where email = 'YONETICI_E_POSTASI';
```

Kullanıcı çıkış-giriş yapınca (veya oturum yenilenince) JWT yeni yetkiyi taşır.

## 2. Moderatör atama / kaldırma (admin, uygulama JWT'siyle)

`POST {SUPABASE_URL}/functions/v1/set-user-role` — `Authorization: Bearer <admin kullanıcı JWT'si>`

```json
{ "userId": "<uuid>", "isModerator": true }
```

Adminin kendi `isAdmin` bayrağını düşürmesi engellenir. Her değişiklik fonksiyon loguna yazılır.

## 3. Öğrenci belgesi onayı / reddi (moderatör)

Bekleyen başvurular ve belgeler:

```sql
select v.id, v.user_id, p.full_name, p.department, v.document_path, v.created_at
from public.student_verifications v
join public.profiles p on p.id = v.user_id
where v.status = 'PENDING'
order by v.created_at;
```

Belgeyi Studio → Storage → `student-documents/{user_id}/document.pdf` üzerinden açın. Karar **tek atomik RPC** ile verilir
(moderatör JWT'siyle; doğrudan UPDATE kapalıdır):

```sql
select public.review_student_verification('<başvuru-id>', 'APPROVED');
select public.review_student_verification('<başvuru-id>', 'REJECTED', 'Belge okunamıyor.');
```

Bu tek işlemde: başvuru durumu + `reviewed_by/at` → profil `verification_status/account_status` → kullanıcıya bildirim.
Red gerekçesi zorunludur ve kullanıcıya "SEBEP" kartında gösterilir. Uygulama canlı (Realtime) dinlediği için ekran anında güncellenir.

## 4. Hesap silme

Kullanıcı Profil → "Hesabı Sil" ile `delete-account` fonksiyonunu çağırır: Storage'daki belgeler silinir, `auth.users` satırı
silinir; kullanıcının tüm verisi `ON DELETE CASCADE` ile gider.

## 5. Testler

`supabase/tests/*.sql` gerçek veritabanında tek transaction'da çalışır ve `ROLLBACK` ile biter (kalıcı veri bırakmaz):
`001_verification_rules.sql` (onay akışı, profil koruması, hesap silme zinciri), `002_community_rules.sql` (gönderi/beğeni/yorum kuralları, sayaçlar).
Çalıştırma: Supabase SQL Editor'a yapıştırın veya MCP `execute_sql`. Başarısızlıkta `FAIL: ...` istisnası fırlar; başarıda `TÜM DOĞRULAMALAR GEÇTİ` döner.
