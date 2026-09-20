-- match_requirements (embedding benzerlik araması) için sertleştirme (Supabase güvenlik danışmanı bulgusu):
--  * `anon` çağıramaz.
--  * Çağıran ACTIVE + APPROVED olmalı (okuma yetkisi doğrulanmış öğrenciye aittir, bkz. 202609190010).
--  * match_count üst sınırı 50 (sınırsız satır dönüşü yok).
-- Şu an hiçbir istemci/Edge Function çağırmıyor (embedding sağlayıcısı yok, docs/BLOCKERS.md B3); açık kalması yerine güvenli tutulur.
create or replace function public.match_requirements(query_embedding extensions.vector, match_count integer default 10)
returns table (id uuid, similarity double precision)
language sql
stable
security definer
set search_path to 'public', 'extensions'
as $$
  select r.id, 1 - (r.embedding <=> query_embedding) as similarity
  from public.requirements r
  where (select public.is_account_active())
    and r.university_id = (select public.current_university_id())
    and r.embedding is not null
    and r.status = 'PUBLISHED'
  order by r.embedding <=> query_embedding
  limit greatest(1, least(coalesce(match_count, 10), 50));
$$;

revoke all on function public.match_requirements(extensions.vector, integer) from public, anon;
grant execute on function public.match_requirements(extensions.vector, integer) to authenticated;

-- Performans danışmanı: kapsayıcı indeksi olmayan yabancı anahtar.
create index user_entitlements_plan_id_idx on public.user_entitlements (plan_id);
