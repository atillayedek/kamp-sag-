-- Ortak yardımcı fonksiyonlar. Tüm RLS politikaları ve tetikleyiciler bunlara dayanır.
-- security definer + set search_path = public: Postgres güvenlik danışmanlığının
-- önerdiği standart, search_path injection'a karşı korumalı desendir.

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- Admin/moderatör yetkisi YALNIZCA JWT app_metadata üzerinden okunur.
-- app_metadata yalnızca service_role (Admin API / Edge Function) tarafından
-- set edilebilir; istemci asla değiştiremez (bkz. AI_Guidelines.md §3,
-- kullanıcının bu turdaki talimatı §13: "Admin yetkisi yalnızca Supabase Auth
-- JWT veya raw_app_meta_data üzerinden kontrol edilmeli").
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select coalesce((auth.jwt() -> 'app_metadata' ->> 'is_admin')::boolean, false);
$$;

create or replace function public.is_moderator()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select coalesce((auth.jwt() -> 'app_metadata' ->> 'is_moderator')::boolean, false)
    or public.is_admin();
$$;

-- NOT: current_university_id() ve is_account_active() burada DEĞİL,
-- 202609160001_create_profiles.sql'in sonunda tanımlanır — çünkü
-- public.profiles tablosuna bağımlılar ve bu tablo henüz bu migration'da
-- yok (canlı DB'ye uygulanırken de bu sırayla, iki ayrı adımda uygulandı;
-- bkz. memory-bank/Memory_Bank.md).
