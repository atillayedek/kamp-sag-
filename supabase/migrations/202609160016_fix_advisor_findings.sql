-- get_advisors (security) bulgusu: match_requirements target_university_id'yi
-- parametre olarak alıyordu -> istemci başka üniversitenin requirement
-- ID'lerini/benzerlik skorlarını sorgulayabilirdi (üniversite izolasyonu
-- ihlali). Düzeltme: university_id artık DAİMA çağıranın kendi profilinden
-- (current_university_id()) türetilir, dışarıdan parametre olarak kabul edilmez.
drop function if exists public.match_requirements(extensions.vector, uuid, int);

create or replace function public.match_requirements(
  query_embedding extensions.vector(1536),
  match_count int default 10
)
returns table (id uuid, similarity float)
language sql
stable
security definer
set search_path = public, extensions
as $$
  select r.id, 1 - (r.embedding <=> query_embedding) as similarity
  from public.requirements r
  where r.university_id = public.current_university_id()
    and r.embedding is not null
    and r.status = 'PUBLISHED'
  order by r.embedding <=> query_embedding
  limit match_count;
$$;

-- get_advisors (security) bulgusu: yalnızca tetikleyici olarak kullanılan
-- fonksiyonlar gereksiz yere /rest/v1/rpc/... üzerinden doğrudan çağrılabilir
-- durumdaydı. is_admin/is_moderator/is_account_active/current_university_id
-- KASITLI olarak çalıştırılabilir kalır (RLS politikaları bunları çağırır).
revoke execute on function public.handle_new_user() from anon, authenticated;
revoke execute on function public.protect_profile_privileged_fields() from anon, authenticated;
revoke execute on function public.sync_profile_verification_status() from anon, authenticated;
revoke execute on function public.notify_verification_status_change() from anon, authenticated;
