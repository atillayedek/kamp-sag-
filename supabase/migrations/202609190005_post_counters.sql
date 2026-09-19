-- Sorun: post_feed_view beğeni sayısını security_invoker ile post_likes'tan sayıyordu; post_likes RLS'i yalnızca
-- kullanıcının KENDİ beğenisini gösterdiği için sayaç her kullanıcıda 0/1 çıkıyordu.
-- Çözüm: posts üzerinde denormalize sayaçlar + security definer tetikleyiciler (satır kilidi yarışı önler).
alter table public.posts
  add column like_count integer not null default 0,
  add column comment_count integer not null default 0;

update public.posts p
set like_count = (select count(*) from public.post_likes pl where pl.post_id = p.id),
    comment_count = (select count(*) from public.comments c where c.post_id = p.id);

create or replace function public.sync_post_like_count()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op = 'INSERT' then
    update public.posts set like_count = like_count + 1 where id = new.post_id;
  else
    update public.posts set like_count = greatest(like_count - 1, 0) where id = old.post_id;
  end if;
  return null;
end;
$$;

create or replace function public.sync_post_comment_count()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op = 'INSERT' then
    update public.posts set comment_count = comment_count + 1 where id = new.post_id;
  else
    update public.posts set comment_count = greatest(comment_count - 1, 0) where id = old.post_id;
  end if;
  return null;
end;
$$;

revoke all on function public.sync_post_like_count() from public, anon, authenticated;
revoke all on function public.sync_post_comment_count() from public, anon, authenticated;

create trigger sync_post_like_count_trigger
after insert or delete on public.post_likes
for each row execute function public.sync_post_like_count();

create trigger sync_post_comment_count_trigger
after insert or delete on public.comments
for each row execute function public.sync_post_comment_count();

-- posts_update_own yazarın satırın TÜM sütunlarını değiştirmesine izin veriyordu: sayaçları uydurabilir,
-- moderatörün REMOVED yaptığı gönderiyi yeniden PUBLISHED yapabilirdi. İstemci doğrudan UPDATE'inde kilitle
-- (security invoker + current_user: bkz. 202609190004).
create or replace function public.protect_post_fields()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  if current_user = 'authenticated' then
    if new.like_count is distinct from old.like_count
       or new.comment_count is distinct from old.comment_count
       or new.author_id is distinct from old.author_id
       or new.community_id is distinct from old.community_id then
      raise exception 'like_count, comment_count, author_id ve community_id istemci tarafından değiştirilemez.';
    end if;
    if old.status = 'REMOVED' and new.status is distinct from old.status then
      raise exception 'Kaldırılmış gönderi yeniden yayınlanamaz.';
    end if;
  end if;
  return new;
end;
$$;

create trigger protect_post_fields_trigger
before update on public.posts
for each row execute function public.protect_post_fields();

-- Sütun tipleri korunur (CREATE OR REPLACE VIEW tip değiştirmeye izin vermez): sayaçlar bigint'e çevrilir.
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
  p.like_count::bigint as like_count,
  p.comment_count::bigint as comment_count,
  u.short_name as author_university_short_name
from public.posts p
join public.profiles pr on pr.id = p.author_id
left join public.universities u on u.id = pr.university_id;

grant select on public.post_feed_view to authenticated;
