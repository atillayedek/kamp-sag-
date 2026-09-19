-- SOHBET BACKEND'İ (Görev 10)
--
-- Sorun 1 (güvenlik): conversation_members_insert_self, herhangi bir kullanıcının BİLDİĞİ HERHANGİ BİR
-- sohbet UUID'sine kendini üye ekleyip messages_select_member ile tüm mesajları okumasına izin veriyordu;
-- conversations_insert_any_authenticated ise sahipsiz sohbet üretmeye. Ayrıca RLS altında karşı tarafı
-- üye olarak eklemek mümkün değildi (1:1 sohbet başlatılamıyordu). => istemci INSERT politikaları kaldırıldı;
-- sohbet yalnızca start_conversation RPC'siyle (security definer, denetimli) oluşur.
drop policy if exists "conversation_members_insert_self" on public.conversation_members;
drop policy if exists "conversations_insert_any_authenticated" on public.conversations;

create or replace function public.start_conversation(p_other_user uuid, p_requirement_id uuid default null)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_me uuid := auth.uid();
  v_id uuid;
begin
  if v_me is null or not public.is_account_active() then
    raise exception 'Sohbet başlatmak için onaylı bir hesap gerekir.' using errcode = '42501';
  end if;
  if p_other_user is null or p_other_user = v_me then
    raise exception 'Geçersiz kullanıcı.' using errcode = '22023';
  end if;
  if not exists (
    select 1 from public.profiles
    where id = p_other_user and account_status = 'ACTIVE' and verification_status = 'APPROVED'
  ) then
    raise exception 'Bu kullanıcıyla sohbet başlatılamaz.' using errcode = 'P0002';
  end if;

  -- Aynı çift için eşzamanlı çağrılar tek sohbet üretsin.
  perform pg_advisory_xact_lock(hashtextextended(least(v_me::text, p_other_user::text) || greatest(v_me::text, p_other_user::text), 0));

  select c.id into v_id
  from public.conversations c
  where exists (select 1 from public.conversation_members m where m.conversation_id = c.id and m.user_id = v_me)
    and exists (select 1 from public.conversation_members m where m.conversation_id = c.id and m.user_id = p_other_user)
    and (select count(*) from public.conversation_members m where m.conversation_id = c.id) = 2
  limit 1;

  if v_id is not null then
    return v_id;
  end if;

  insert into public.conversations (related_requirement_id)
  values (
    case when p_requirement_id is not null and exists (
      select 1 from public.requirements r where r.id = p_requirement_id and r.author_id in (v_me, p_other_user)
    ) then p_requirement_id end
  )
  returning id into v_id;

  insert into public.conversation_members (conversation_id, user_id, last_read_at)
  values (v_id, v_me, now()), (v_id, p_other_user, null);

  return v_id;
end;
$$;

revoke all on function public.start_conversation(uuid, uuid) from public, anon;
grant execute on function public.start_conversation(uuid, uuid) to authenticated;

-- Okundu bilgisi: yalnızca çağıranın KENDİ last_read_at'i (istemci UPDATE politikası yok).
create or replace function public.mark_conversation_read(p_conversation_id uuid)
returns void
language sql
security definer
set search_path = public
as $$
  update public.conversation_members
  set last_read_at = now()
  where conversation_id = p_conversation_id and user_id = auth.uid();
$$;

revoke all on function public.mark_conversation_read(uuid) from public, anon;
grant execute on function public.mark_conversation_read(uuid) to authenticated;

-- Sohbet listesi: karşı taraf, son mesaj, okunmamış sayısı. security INVOKER: RLS çağıran için geçerlidir.
create or replace function public.list_my_conversations()
returns table (
  conversation_id uuid,
  other_user_id uuid,
  other_full_name text,
  other_avatar_url text,
  last_message_body text,
  last_message_at timestamptz,
  last_message_sender_id uuid,
  unread_count bigint
)
language sql
stable
security invoker
set search_path = public
as $$
  select
    c.id,
    o.user_id,
    p.full_name,
    p.avatar_url,
    lm.body,
    lm.created_at,
    lm.sender_id,
    (select count(*) from public.messages m
      where m.conversation_id = c.id and m.sender_id <> auth.uid()
        and (me.last_read_at is null or m.created_at > me.last_read_at))
  from public.conversations c
  join public.conversation_members me on me.conversation_id = c.id and me.user_id = auth.uid()
  join public.conversation_members o on o.conversation_id = c.id and o.user_id <> auth.uid()
  join public.profiles p on p.id = o.user_id
  left join lateral (
    select m.body, m.created_at, m.sender_id from public.messages m
    where m.conversation_id = c.id order by m.created_at desc limit 1
  ) lm on true
  order by coalesce(lm.created_at, c.created_at) desc;
$$;

revoke all on function public.list_my_conversations() from public, anon;
grant execute on function public.list_my_conversations() to authenticated;

-- Okundu bilgisi canlı akabilsin (karşı tarafın last_read_at güncellemesi).
alter publication supabase_realtime add table public.conversation_members;

-- Yeni mesaj bildirimi (uygulama içi; FCM yok). Aynı sohbette okunmamış bildirim varsa yenisi eklenmez.
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
    and not exists (
      select 1 from public.notifications n
      where n.user_id = m.user_id and n.type = 'NEW_MESSAGE' and not n.is_read
        and n.related_entity_id = new.conversation_id
    );
  return null;
end;
$$;

revoke all on function public.notify_new_message() from public, anon, authenticated;

create trigger notify_new_message_trigger
after insert on public.messages
for each row execute function public.notify_new_message();

-- Realtime Authorization: yazıyor/çevrimiçi (broadcast + presence) kanalları yalnızca sohbet ÜYELERİNE açık.
-- Konu biçimi: "chat:<conversation_uuid>" (istemci kanalı private açar).
create or replace function public.is_chat_topic_member(p_topic text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select p_topic ~ '^chat:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    and exists (
      select 1 from public.conversation_members m
      where m.conversation_id = substr(p_topic, 6)::uuid and m.user_id = auth.uid()
    );
$$;

revoke all on function public.is_chat_topic_member(text) from public, anon;
grant execute on function public.is_chat_topic_member(text) to authenticated;

create policy "chat_members_receive" on realtime.messages
for select to authenticated
using (public.is_chat_topic_member((select realtime.topic())));

create policy "chat_members_send" on realtime.messages
for insert to authenticated
with check (public.is_chat_topic_member((select realtime.topic())));
