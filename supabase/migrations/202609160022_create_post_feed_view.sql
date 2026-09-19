-- security_invoker = true ZORUNLU: bu olmadan view, view SAHİBİNİN (migration
-- rolü, RLS'den muaf) yetkileriyle çalışır ve TÜM üniversitelerin gönderilerini
-- sızdırır. security_invoker ile view, sorguyu yapan kullanıcının RLS'sine
-- tabi olur (posts_select politikası — üniversite izolasyonu korunur).
create view public.post_feed_view
with (security_invoker = true)
as
select
  p.id,
  p.author_id,
  p.community_id,
  p.title,
  p.body,
  p.category,
  p.help_type,
  p.tags,
  p.image_urls,
  p.status,
  p.created_at,
  p.updated_at,
  pr.username as author_username,
  pr.full_name as author_full_name,
  pr.avatar_url as author_avatar_url,
  pr.department as author_department,
  (select count(*) from public.post_likes pl where pl.post_id = p.id) as like_count,
  (select count(*) from public.comments c where c.post_id = p.id) as comment_count
from public.posts p
join public.profiles pr on pr.id = p.author_id;

grant select on public.post_feed_view to authenticated;
