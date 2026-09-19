create table public.student_verifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  document_path text not null,
  status text not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
  rejection_reason text,
  reviewed_by uuid references auth.users(id),
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on column public.student_verifications.status is 'İstemci yazamaz: INSERT yalnızca submit-student-document Edge Function (service_role) ile; UPDATE yalnızca moderatör/admin RLS politikasıyla (202609160010).';

create index student_verifications_user_id_idx on public.student_verifications (user_id);
create index student_verifications_status_idx on public.student_verifications (status);

-- Kullanıcı başına aynı anda yalnızca bir PENDING başvuru (bkz. §12 madde 8: duplicate PENDING engeli).
create unique index student_verifications_one_pending_per_user
  on public.student_verifications (user_id)
  where status = 'PENDING';

create trigger set_student_verifications_updated_at
before update on public.student_verifications
for each row execute function public.set_updated_at();

-- student_verifications.status değişince profiles.verification_status/account_status'u
-- OTOMATİK senkronize eder (security definer -> RLS'yi bypass eder, tablo sahibi olduğu için).
-- Bu, admin'in doğrudan `profiles` tablosuna yazmasına GEREK BIRAKMAZ — admin yalnızca
-- student_verifications.status'u günceller, gerisi burada otomatik olur.
create or replace function public.sync_profile_verification_status()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.status = 'APPROVED' then
    update public.profiles
    set verification_status = 'APPROVED', account_status = 'ACTIVE'
    where id = new.user_id;
  elsif new.status = 'REJECTED' then
    update public.profiles
    set verification_status = 'REJECTED'
    where id = new.user_id;
  elsif new.status = 'PENDING' then
    update public.profiles
    set verification_status = 'PENDING', account_status = 'PENDING'
    where id = new.user_id;
  end if;
  return new;
end;
$$;

create trigger sync_profile_verification_status_trigger
after insert or update on public.student_verifications
for each row execute function public.sync_profile_verification_status();

alter table public.student_verifications enable row level security;
