-- post_likes/saved_posts INSERT yalnızca user_id = auth.uid() denetliyordu: onaysız (PENDING) bir hesap, başka
-- üniversitenin ya da kaldırılmış bir gönderinin UUID'sini bilerek beğeni atabilir ve (sayaç tetikleyicisi
-- sayesinde) sayacı şişirebilirdi. Artık: hesap aktif VE gönderi çağıranın RLS'inde görünür olmalı.
drop policy "post_likes_insert_own" on public.post_likes;
create policy "post_likes_insert_own" on public.post_likes
for insert to authenticated
with check (
  user_id = (select auth.uid())
  and (select public.is_account_active())
  and exists (select 1 from public.posts p where p.id = post_id)
);

drop policy "saved_posts_insert_own" on public.saved_posts;
create policy "saved_posts_insert_own" on public.saved_posts
for insert to authenticated
with check (
  user_id = (select auth.uid())
  and (select public.is_account_active())
  and exists (select 1 from public.posts p where p.id = post_id)
);
