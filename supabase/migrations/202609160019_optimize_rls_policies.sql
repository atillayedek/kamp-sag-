-- get_advisors (performance) bulgusu: auth.uid()/auth.role() ve özel
-- fonksiyonlarımız (is_admin/is_moderator/current_university_id/is_account_active)
-- RLS politikalarında satır başına yeniden değerlendiriliyordu. Düzeltme:
-- her çağrı (select ...) ile sarmalanarak Postgres'in bunu InitPlan olarak
-- (sorgu başına bir kez) değerlendirmesi sağlanıyor (Supabase resmi önerisi).
-- Ayrıca "for all" politikaları SELECT için ayrı bir permissive politikayla
-- çakışıyordu (multiple_permissive_policies bulgusu) -> insert/update/delete'e
-- daraltıldı.

drop policy "profiles_select" on public.profiles;
create policy "profiles_select" on public.profiles
for select to authenticated
using (id = (select auth.uid()) or (account_status = 'ACTIVE' and verification_status = 'APPROVED'));

drop policy "profiles_update_own" on public.profiles;
create policy "profiles_update_own" on public.profiles
for update to authenticated
using (id = (select auth.uid()))
with check (id = (select auth.uid()));

drop policy "universities_select_active" on public.universities;
create policy "universities_select_active" on public.universities
for select to anon, authenticated
using (is_active or (select public.is_admin()));

drop policy "universities_write_admin" on public.universities;
create policy "universities_write_admin" on public.universities
for insert to authenticated with check ((select public.is_admin()));
create policy "universities_update_admin" on public.universities
for update to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));
create policy "universities_delete_admin" on public.universities
for delete to authenticated using ((select public.is_admin()));

drop policy "student_verifications_select" on public.student_verifications;
create policy "student_verifications_select" on public.student_verifications
for select to authenticated
using (user_id = (select auth.uid()) or (select public.is_moderator()));

drop policy "student_verifications_update_staff" on public.student_verifications;
create policy "student_verifications_update_staff" on public.student_verifications
for update to authenticated
using ((select public.is_moderator()))
with check ((select public.is_moderator()));

drop policy "communities_write_admin" on public.communities;
create policy "communities_insert_admin" on public.communities
for insert to authenticated with check ((select public.is_admin()));
create policy "communities_update_admin" on public.communities
for update to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));
create policy "communities_delete_admin" on public.communities
for delete to authenticated using ((select public.is_admin()));

drop policy "community_members_select" on public.community_members;
create policy "community_members_select" on public.community_members
for select to authenticated
using (user_id = (select auth.uid()) or (select public.is_moderator()));

drop policy "community_members_insert_own" on public.community_members;
create policy "community_members_insert_own" on public.community_members
for insert to authenticated
with check (
  user_id = (select auth.uid())
  and exists (
    select 1 from public.communities c
    where c.id = community_id
      and (c.type = 'GENERAL' or c.university_id = (select public.current_university_id()))
  )
);

drop policy "community_members_delete_own" on public.community_members;
create policy "community_members_delete_own" on public.community_members
for delete to authenticated using (user_id = (select auth.uid()));

drop policy "posts_select" on public.posts;
create policy "posts_select" on public.posts
for select to authenticated
using (
  status = 'PUBLISHED'
  and exists (
    select 1 from public.communities c
    where c.id = community_id
      and (c.type = 'GENERAL' or c.university_id = (select public.current_university_id()))
  )
);

drop policy "posts_insert_own" on public.posts;
create policy "posts_insert_own" on public.posts
for insert to authenticated
with check (
  author_id = (select auth.uid())
  and (select public.is_account_active())
  and exists (
    select 1 from public.communities c
    where c.id = community_id
      and (c.type = 'GENERAL' or c.university_id = (select public.current_university_id()))
  )
);

drop policy "posts_update_own" on public.posts;
create policy "posts_update_own" on public.posts
for update to authenticated
using (author_id = (select auth.uid()))
with check (author_id = (select auth.uid()));

drop policy "posts_delete_own_or_staff" on public.posts;
create policy "posts_delete_own_or_staff" on public.posts
for delete to authenticated
using (author_id = (select auth.uid()) or (select public.is_moderator()));

drop policy "comments_select" on public.comments;
create policy "comments_select" on public.comments
for select to authenticated
using (
  exists (
    select 1 from public.posts p
    join public.communities c on c.id = p.community_id
    where p.id = post_id
      and p.status = 'PUBLISHED'
      and (c.type = 'GENERAL' or c.university_id = (select public.current_university_id()))
  )
);

drop policy "comments_insert_own" on public.comments;
create policy "comments_insert_own" on public.comments
for insert to authenticated
with check (
  author_id = (select auth.uid())
  and (select public.is_account_active())
  and exists (select 1 from public.posts p where p.id = post_id and p.status = 'PUBLISHED')
);

drop policy "comments_update_own" on public.comments;
create policy "comments_update_own" on public.comments
for update to authenticated
using (author_id = (select auth.uid()))
with check (author_id = (select auth.uid()));

drop policy "comments_delete_own_or_staff" on public.comments;
create policy "comments_delete_own_or_staff" on public.comments
for delete to authenticated
using (author_id = (select auth.uid()) or (select public.is_moderator()));

drop policy "post_likes_select_own" on public.post_likes;
create policy "post_likes_select_own" on public.post_likes
for select to authenticated using (user_id = (select auth.uid()));
drop policy "post_likes_insert_own" on public.post_likes;
create policy "post_likes_insert_own" on public.post_likes
for insert to authenticated with check (user_id = (select auth.uid()));
drop policy "post_likes_delete_own" on public.post_likes;
create policy "post_likes_delete_own" on public.post_likes
for delete to authenticated using (user_id = (select auth.uid()));

drop policy "saved_posts_select_own" on public.saved_posts;
create policy "saved_posts_select_own" on public.saved_posts
for select to authenticated using (user_id = (select auth.uid()));
drop policy "saved_posts_insert_own" on public.saved_posts;
create policy "saved_posts_insert_own" on public.saved_posts
for insert to authenticated with check (user_id = (select auth.uid()));
drop policy "saved_posts_delete_own" on public.saved_posts;
create policy "saved_posts_delete_own" on public.saved_posts
for delete to authenticated using (user_id = (select auth.uid()));

drop policy "requirements_select_own_university" on public.requirements;
create policy "requirements_select_own_university" on public.requirements
for select to authenticated
using (university_id = (select public.current_university_id()));

drop policy "matching_config_update_admin" on public.matching_config;
create policy "matching_config_update_admin" on public.matching_config
for update to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));

drop policy "matches_select" on public.matches;
create policy "matches_select" on public.matches
for select to authenticated
using (
  matched_user_id = (select auth.uid())
  or exists (select 1 from public.requirements r where r.id = requirement_id and r.author_id = (select auth.uid()))
);

drop policy "conversations_select_member" on public.conversations;
create policy "conversations_select_member" on public.conversations
for select to authenticated
using (exists (select 1 from public.conversation_members m where m.conversation_id = id and m.user_id = (select auth.uid())));

drop policy "conversation_members_select" on public.conversation_members;
create policy "conversation_members_select" on public.conversation_members
for select to authenticated
using (exists (
  select 1 from public.conversation_members m2
  where m2.conversation_id = conversation_members.conversation_id and m2.user_id = (select auth.uid())
));

drop policy "conversation_members_insert_self" on public.conversation_members;
create policy "conversation_members_insert_self" on public.conversation_members
for insert to authenticated with check (user_id = (select auth.uid()));

drop policy "messages_select_member" on public.messages;
create policy "messages_select_member" on public.messages
for select to authenticated
using (exists (select 1 from public.conversation_members m where m.conversation_id = messages.conversation_id and m.user_id = (select auth.uid())));

drop policy "messages_insert_member" on public.messages;
create policy "messages_insert_member" on public.messages
for insert to authenticated
with check (
  sender_id = (select auth.uid())
  and (select public.is_account_active())
  and exists (select 1 from public.conversation_members m where m.conversation_id = messages.conversation_id and m.user_id = (select auth.uid()))
);

drop policy "notifications_select_own" on public.notifications;
create policy "notifications_select_own" on public.notifications
for select to authenticated using (user_id = (select auth.uid()));
drop policy "notifications_update_own_mark_read" on public.notifications;
create policy "notifications_update_own_mark_read" on public.notifications
for update to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy "campus_events_select" on public.campus_events;
create policy "campus_events_select" on public.campus_events
for select to authenticated
using (university_id is null or university_id = (select public.current_university_id()));

drop policy "campus_events_write_staff" on public.campus_events;
create policy "campus_events_insert_staff" on public.campus_events
for insert to authenticated with check ((select public.is_moderator()));
create policy "campus_events_update_staff" on public.campus_events
for update to authenticated using ((select public.is_moderator())) with check ((select public.is_moderator()));
create policy "campus_events_delete_staff" on public.campus_events
for delete to authenticated using ((select public.is_moderator()));

drop policy "campus_event_attendees_select_own" on public.campus_event_attendees;
create policy "campus_event_attendees_select_own" on public.campus_event_attendees
for select to authenticated using (user_id = (select auth.uid()));
drop policy "campus_event_attendees_insert_own" on public.campus_event_attendees;
create policy "campus_event_attendees_insert_own" on public.campus_event_attendees
for insert to authenticated with check (user_id = (select auth.uid()));
drop policy "campus_event_attendees_delete_own" on public.campus_event_attendees;
create policy "campus_event_attendees_delete_own" on public.campus_event_attendees
for delete to authenticated using (user_id = (select auth.uid()));

drop policy "reports_insert_own" on public.reports;
create policy "reports_insert_own" on public.reports
for insert to authenticated with check (reporter_id = (select auth.uid()));
drop policy "reports_select_own_or_staff" on public.reports;
create policy "reports_select_own_or_staff" on public.reports
for select to authenticated
using (reporter_id = (select auth.uid()) or (select public.is_moderator()));
drop policy "reports_update_staff" on public.reports;
create policy "reports_update_staff" on public.reports
for update to authenticated
using ((select public.is_moderator()))
with check ((select public.is_moderator()));
