-- Temel ürün analitiği (birinci taraf, üçüncü taraf SDK YOK): yalnızca olay ADI kaydedilir — içerik, metin, konum, cihaz
-- kimliği ve serbest özellik alanı yoktur. Kullanıcı bağlantısı hesap silinince olaylarıyla birlikte silinir (CASCADE).
-- İstemci tabloya DOĞRUDAN erişemez (politika yok); tek yol `track_event` RPC'sidir: izin verilen olay adları, kullanıcı başına
-- dakikada en fazla 60 kayıt (fazlası sessizce atılır — analitik hiçbir zaman kullanıcı akışını bozmamalı).
-- Kullanıcı Profil > Gizlilik ve Konum'dan istatistik paylaşımını kapatabilir (istemci olay göndermez).

create table public.analytics_events (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (name in (
    'SIGN_UP_COMPLETED', 'DOCUMENT_UPLOADED', 'POST_CREATED', 'REQUIREMENT_PUBLISHED',
    'CHAT_STARTED', 'MESSAGE_SENT', 'PURCHASE_COMPLETED'
  )),
  created_at timestamptz not null default now()
);

create index analytics_events_name_created_at_idx on public.analytics_events (name, created_at);
create index analytics_events_user_id_created_at_idx on public.analytics_events (user_id, created_at);

alter table public.analytics_events enable row level security;

create or replace function public.track_event(p_name text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := (select auth.uid());
begin
  if v_user is null then
    raise exception 'Oturum gerekli' using errcode = '28000';
  end if;
  if p_name is null or p_name not in (
    'SIGN_UP_COMPLETED', 'DOCUMENT_UPLOADED', 'POST_CREATED', 'REQUIREMENT_PUBLISHED',
    'CHAT_STARTED', 'MESSAGE_SENT', 'PURCHASE_COMPLETED'
  ) then
    raise exception 'Geçersiz olay adı' using errcode = '22023';
  end if;
  if (select count(*) from public.analytics_events where user_id = v_user and created_at > now() - interval '1 minute') >= 60 then
    return;
  end if;
  insert into public.analytics_events (user_id, name) values (v_user, p_name);
end;
$$;

revoke all on function public.track_event(text) from public, anon;
grant execute on function public.track_event(text) to authenticated;
