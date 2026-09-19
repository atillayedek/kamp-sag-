-- 202609160001-202609160009'da oluşturulan tüm tabloların RLS politikaları.
-- İlke: istemciden gelen hiçbir veri yetki/güvenlik kararı için güvenilmez
-- (bkz. AI_Guidelines.md §3). Bir tablo için burada policy yoksa o işlem
-- (ör. requirements INSERT) yalnızca service_role (Edge Function) ile yapılabilir.

-- ===== profiles =====
create policy "profiles_select" on public.profiles
for select to authenticated
using (id = auth.uid() or (account_status = 'ACTIVE' and verification_status = 'APPROVED'));

create policy "profiles_update_own" on public.profiles
for update to authenticated
using (id = auth.uid())
with check (id = auth.uid());
-- account_status/verification_status/karma_score koruması RLS'de değil,
-- protect_profiles_privileged_fields tetikleyicisinde (202609160001) — RLS
-- WITH CHECK'te aynı tabloya self-subquery yapmanın MVCC belirsizliği
-- taşıması nedeniyle bilinçli olarak tetikleyici tercih edildi.

-- ===== universities =====
create policy "universities_select_active" on public.universities
for select to anon, authenticated
using (is_active or public.is_admin());

create policy "universities_write_admin" on public.universities
for all to authenticated
using (public.is_admin())
with check (public.is_admin());

-- ===== student_verifications =====
create policy "student_verifications_select" on public.student_verifications
for select to authenticated
using (user_id = auth.uid() or public.is_moderator());

create policy "student_verifications_update_staff" on public.student_verifications
for update to authenticated
using (public.is_moderator())
with check (public.is_moderator());
-- INSERT policy YOK: yalnızca submit-student-document Edge Function (service_role).

-- ===== communities / community_members =====
create policy "communities_select" on public.communities
for select to authenticated using (true);

create policy "communities_write_admin" on public.communities
for all to authenticated
using (public.is_admin())
with check (public.is_admin());

create policy "community_members_select" on public.community_members
for select to authenticated
using (user_id = auth.uid() or public.is_moderator());

create policy "community_members_insert_own" on public.community_members
for insert to authenticated
with check (
  user_id = auth.uid()
  and exists (
    select 1 from public.communities c
    where c.id = community_id
      and (c.type = 'GENERAL' or c.university_id = public.current_university_id())
  )
);

create policy "community_members_delete_own" on public.community_members
for delete to authenticated
using (user_id = auth.uid());

-- ===== posts =====
create policy "posts_select" on public.posts
for select to authenticated
using (
  status = 'PUBLISHED'
  and exists (
    select 1 from public.communities c
    where c.id = community_id
      and (c.type = 'GENERAL' or c.university_id = public.current_university_id())
  )
);

create policy "posts_insert_own" on public.posts
for insert to authenticated
with check (
  author_id = auth.uid()
  and public.is_account_active()
  and exists (
    select 1 from public.communities c
    where c.id = community_id
      and (c.type = 'GENERAL' or c.university_id = public.current_university_id())
  )
);

create policy "posts_update_own" on public.posts
for update to authenticated
using (author_id = auth.uid())
with check (author_id = auth.uid());

create policy "posts_delete_own_or_staff" on public.posts
for delete to authenticated
using (author_id = auth.uid() or public.is_moderator());

-- ===== comments =====
create policy "comments_select" on public.comments
for select to authenticated
using (
  exists (
    select 1 from public.posts p
    join public.communities c on c.id = p.community_id
    where p.id = post_id
      and p.status = 'PUBLISHED'
      and (c.type = 'GENERAL' or c.university_id = public.current_university_id())
  )
);

create policy "comments_insert_own" on public.comments
for insert to authenticated
with check (
  author_id = auth.uid()
  and public.is_account_active()
  and exists (select 1 from public.posts p where p.id = post_id and p.status = 'PUBLISHED')
);

create policy "comments_update_own" on public.comments
for update to authenticated
using (author_id = auth.uid())
with check (author_id = auth.uid());

create policy "comments_delete_own_or_staff" on public.comments
for delete to authenticated
using (author_id = auth.uid() or public.is_moderator());

-- ===== post_likes / saved_posts =====
create policy "post_likes_select_own" on public.post_likes
for select to authenticated using (user_id = auth.uid());
create policy "post_likes_insert_own" on public.post_likes
for insert to authenticated with check (user_id = auth.uid());
create policy "post_likes_delete_own" on public.post_likes
for delete to authenticated using (user_id = auth.uid());

create policy "saved_posts_select_own" on public.saved_posts
for select to authenticated using (user_id = auth.uid());
create policy "saved_posts_insert_own" on public.saved_posts
for insert to authenticated with check (user_id = auth.uid());
create policy "saved_posts_delete_own" on public.saved_posts
for delete to authenticated using (user_id = auth.uid());

-- ===== requirements =====
create policy "requirements_select_own_university" on public.requirements
for select to authenticated
using (university_id = public.current_university_id());
-- INSERT/UPDATE policy YOK: yalnızca publish-need Edge Function (service_role).

-- ===== matching_config / matches =====
create policy "matching_config_select" on public.matching_config
for select to authenticated using (true);
create policy "matching_config_update_admin" on public.matching_config
for update to authenticated using (public.is_admin()) with check (public.is_admin());

create policy "matches_select" on public.matches
for select to authenticated
using (
  matched_user_id = auth.uid()
  or exists (select 1 from public.requirements r where r.id = requirement_id and r.author_id = auth.uid())
);
-- INSERT/UPDATE policy YOK: yalnızca recompute-matches Edge Function (service_role).

-- ===== conversations / conversation_members / messages =====
create policy "conversations_select_member" on public.conversations
for select to authenticated
using (exists (select 1 from public.conversation_members m where m.conversation_id = id and m.user_id = auth.uid()));

create policy "conversations_insert_any_authenticated" on public.conversations
for insert to authenticated with check (true);

create policy "conversation_members_select" on public.conversation_members
for select to authenticated
using (exists (
  select 1 from public.conversation_members m2
  where m2.conversation_id = conversation_members.conversation_id and m2.user_id = auth.uid()
));

create policy "conversation_members_insert_self" on public.conversation_members
for insert to authenticated with check (user_id = auth.uid());

create policy "messages_select_member" on public.messages
for select to authenticated
using (exists (select 1 from public.conversation_members m where m.conversation_id = messages.conversation_id and m.user_id = auth.uid()));

create policy "messages_insert_member" on public.messages
for insert to authenticated
with check (
  sender_id = auth.uid()
  and public.is_account_active()
  and exists (select 1 from public.conversation_members m where m.conversation_id = messages.conversation_id and m.user_id = auth.uid())
);

-- ===== notifications =====
create policy "notifications_select_own" on public.notifications
for select to authenticated using (user_id = auth.uid());
create policy "notifications_update_own_mark_read" on public.notifications
for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
-- INSERT policy YOK: yalnızca sunucu taraflı tetikleyiciler/Edge Functions (service_role).
