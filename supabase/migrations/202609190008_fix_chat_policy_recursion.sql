-- Sorun: conversation_members_select politikası kendi tablosunu (conversation_members) sorguluyordu ->
-- "infinite recursion detected in policy for relation conversation_members" (42P17). conversation_members'a ve
-- ona bağlı conversations/messages politikalarına HİÇBİR kullanıcı erişemezdi (sohbet baştan beri çalışmıyordu).
-- Çözüm: üyelik denetimini RLS'yi atlayan (security definer) tek bir yardımcı fonksiyona taşı.
create or replace function public.is_conversation_member(p_conversation_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.conversation_members m
    where m.conversation_id = p_conversation_id and m.user_id = auth.uid()
  );
$$;

revoke all on function public.is_conversation_member(uuid) from public, anon;
grant execute on function public.is_conversation_member(uuid) to authenticated;

drop policy "conversations_select_member" on public.conversations;
create policy "conversations_select_member" on public.conversations
for select to authenticated
using ((select public.is_conversation_member(id)));

drop policy "conversation_members_select" on public.conversation_members;
create policy "conversation_members_select" on public.conversation_members
for select to authenticated
using ((select public.is_conversation_member(conversation_id)));

drop policy "messages_select_member" on public.messages;
create policy "messages_select_member" on public.messages
for select to authenticated
using ((select public.is_conversation_member(conversation_id)));

drop policy "messages_insert_member" on public.messages;
create policy "messages_insert_member" on public.messages
for insert to authenticated
with check (
  sender_id = (select auth.uid())
  and (select public.is_account_active())
  and (select public.is_conversation_member(conversation_id))
);
