create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique,
  full_name text not null,
  avatar_url text,
  university_id uuid,
  department text,
  account_status text not null default 'PENDING'
    check (account_status in ('PENDING', 'ACTIVE', 'REJECTED', 'SUSPENDED')),
  verification_status text not null default 'PENDING'
    check (verification_status in ('PENDING', 'APPROVED', 'REJECTED')),
  karma_score integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint profiles_username_format check (username ~ '^[a-z0-9_]{3,30}$')
);

comment on table public.profiles is 'Uygulama profili. account_status/verification_status/karma_score istemciden ASLA yazılamaz (bkz. protect_profiles_privileged_fields tetikleyicisi + 202609160010 RLS).';
comment on column public.profiles.account_status is 'İstemci değiştiremez. submit-student-document / admin onayı bunu server-side (service_role veya moderatör RLS) günceller.';
comment on column public.profiles.verification_status is 'İstemci değiştiremez.';
comment on column public.profiles.karma_score is 'İstemci değiştiremez. Yalnızca backend hesaplar.';

create index profiles_university_id_idx on public.profiles (university_id);

create trigger set_profiles_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

-- account_status/verification_status/karma_score değişikliğini yalnızca
-- 'authenticated' (istemci) rolü için engeller; service_role (Edge Functions,
-- Admin API) bu kısıtlamadan muaftır — auth.role() 'service_role' döner.
create or replace function public.protect_profile_privileged_fields()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.role() = 'authenticated' then
    if new.account_status is distinct from old.account_status
       or new.verification_status is distinct from old.verification_status
       or new.karma_score is distinct from old.karma_score then
      raise exception 'account_status, verification_status ve karma_score istemci tarafından değiştirilemez.';
    end if;
  end if;
  return new;
end;
$$;

create trigger protect_profiles_privileged_fields
before update on public.profiles
for each row execute function public.protect_profile_privileged_fields();

-- Yeni auth.users kaydı oluşunca profili OTOMATİK oluşturur (security definer
-- ile RLS'yi bypass eder) — istemcinin profiles'a doğrudan INSERT yetkisi
-- hiç OLMAZ, bu yüzden sahte/rastgele bir profil satırı oluşturamaz.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, username, full_name)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'username', 'user_' || substr(new.id::text, 1, 8)),
    coalesce(new.raw_user_meta_data ->> 'full_name', '')
  );
  return new;
end;
$$;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

alter table public.profiles enable row level security;
-- Bu migration'da henüz policy YOK: RLS enable + policy yok = fail-closed
-- (tüm istemci erişimi reddedilir). Gerçek politikalar 202609160010'da.

-- profiles tablosuna bağımlı oldukları için burada tanımlanır (bkz. 202609160000).
create or replace function public.current_university_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select university_id from public.profiles where id = auth.uid();
$$;

create or replace function public.is_account_active()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(
    (select account_status = 'ACTIVE' and verification_status = 'APPROVED'
     from public.profiles where id = auth.uid()),
    false
  );
$$;
