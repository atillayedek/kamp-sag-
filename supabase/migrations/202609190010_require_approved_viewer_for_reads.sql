-- ÖĞRENCİ DOĞRULAMASI OKUMAYI DA KAPSAMALI.
-- Önceki politikalar yalnızca gönderi/profil sahibinin durumuna bakıyordu: kayıt olup belge yüklemeyen (PENDING) veya
-- reddedilen bir hesap, seçtiği üniversitenin gönderilerini/ilanlarını, GENERAL akışı ve onaylı öğrencilerin profillerini
-- (ad, bölüm, üniversite) okuyabiliyordu. Doğrulamanın amacı "yalnızca gerçek öğrencilerin bir arada olması"dır;
-- bu yüzden okuyan kullanıcının kendisi de ACTIVE + APPROVED olmalı (`is_account_active()`).
-- Kullanıcı KENDİ profilini ve kendi doğrulama kaydını her durumda okuyabilir (kök durum makinesi buna dayanır).

alter policy profiles_select on public.profiles
  using (
    id = (select auth.uid())
    or (
      (select public.is_account_active())
      and account_status = 'ACTIVE'
      and verification_status = 'APPROVED'
    )
  );

alter policy posts_select on public.posts
  using (
    (select public.is_account_active())
    and status = 'PUBLISHED'
    and exists (
      select 1 from public.communities c
      where c.id = posts.community_id
        and (c.type = 'GENERAL' or c.university_id = (select public.current_university_id()))
    )
  );

alter policy comments_select on public.comments
  using (
    (select public.is_account_active())
    and exists (
      select 1
      from public.posts p
      join public.communities c on c.id = p.community_id
      where p.id = comments.post_id
        and p.status = 'PUBLISHED'
        and (c.type = 'GENERAL' or c.university_id = (select public.current_university_id()))
    )
  );

alter policy requirements_select_own_university on public.requirements
  using (
    (select public.is_account_active())
    and university_id = (select public.current_university_id())
  );

alter policy campus_events_select on public.campus_events
  using (
    (select public.is_account_active())
    and (university_id is null or university_id = (select public.current_university_id()))
  );

alter policy communities_select on public.communities using ((select public.is_account_active()));
alter policy badges_select on public.badges using ((select public.is_account_active()));
alter policy user_badges_select on public.user_badges using ((select public.is_account_active()));
alter policy leaderboard_entries_select on public.leaderboard_entries using ((select public.is_account_active()));

-- Yazma tarafında kalan iki boşluk: doğrulanmamış hesap etkinliğe katılım/şikâyet kaydı ekleyemez.
alter policy campus_event_attendees_insert_own on public.campus_event_attendees
  with check (user_id = (select auth.uid()) and (select public.is_account_active()));

alter policy reports_insert_own on public.reports
  with check (reporter_id = (select auth.uid()) and (select public.is_account_active()));
