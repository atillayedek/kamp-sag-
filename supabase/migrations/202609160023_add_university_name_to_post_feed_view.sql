-- "Tüm Üniversiteler" akışında hangi üniversiteden geldiğini göstermek için
-- (bkz. §20 PostCard spesifikasyonu: "Üniversite" alanı). Yeni sütun SONA
-- eklendi (Postgres CREATE OR REPLACE VIEW sütun pozisyonu değişikliğine izin
-- vermez).
create or replace view public.post_feed_view
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
  (select count(*) from public.comments c where c.post_id = p.id) as comment_count,
  u.short_name as author_university_short_name
from public.posts p
join public.profiles pr on pr.id = p.author_id
left join public.universities u on u.id = pr.university_id;

grant select on public.post_feed_view to authenticated;
