-- Bildirim tercihleri (Profil > Bildirim Ayarları). Yalnızca üretilen bildirim türleri için satır tutulur;
-- şu an tek üretici olan yeni mesaj bildirimi (`notify_new_message`) bu tercihe uyar.
-- Satır yoksa varsayılan AÇIK'tır (kullanıcı hiç ayar yapmadıysa davranış değişmez).
create table public.notification_preferences (
  user_id uuid primary key references auth.users(id) on delete cascade,
  new_message boolean not null default true,
  updated_at timestamptz not null default now()
);

alter table public.notification_preferences enable row level security;

create policy notification_preferences_select_own on public.notification_preferences
  for select to authenticated using (user_id = (select auth.uid()));

create policy notification_preferences_insert_own on public.notification_preferences
  for insert to authenticated with check (user_id = (select auth.uid()));

create policy notification_preferences_update_own on public.notification_preferences
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create or replace function public.notify_new_message()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_sender_name text;
begin
  select full_name into v_sender_name from public.profiles where id = new.sender_id;

  insert into public.notifications (user_id, type, title, body, related_entity_type, related_entity_id)
  select m.user_id, 'NEW_MESSAGE', coalesce(nullif(v_sender_name, ''), 'Yeni mesaj'), left(new.body, 120),
         'conversation', new.conversation_id
  from public.conversation_members m
  where m.conversation_id = new.conversation_id
    and m.user_id <> new.sender_id
    -- Alıcı yeni mesaj bildirimlerini kapattıysa satır oluşturulmaz (satır yoksa varsayılan: açık).
    and coalesce((select p.new_message from public.notification_preferences p where p.user_id = m.user_id), true)
    and not exists (
      select 1 from public.notifications n
      where n.user_id = m.user_id and n.type = 'NEW_MESSAGE' and not n.is_read
        and n.related_entity_id = new.conversation_id
    );
  return null;
end;
$$;

revoke all on function public.notify_new_message() from public, anon, authenticated;
