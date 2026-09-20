-- Etkinlikler ekranı için sunucu tarafı.
--
-- 1) list_campus_events(): görüntüleyen kullanıcının görebileceği YAKLAŞAN etkinlikler + katılımcı sayısı + "ben katılıyor muyum".
--    Katılımcı satırları RLS gereği yalnızca sahibine görünür; sayıyı hesaplayabilmek için SECURITY DEFINER'dır ve
--    RLS'in yaptığı denetimleri KENDİSİ tekrar eder: yalnızca ACTIVE + APPROVED kullanıcı, yalnızca genel (university_id null)
--    veya kendi üniversitesinin etkinlikleri. Kimin katıldığı ASLA döndürülmez, yalnızca sayı.
-- 2) Katılım kaydı yalnızca kullanıcının GÖREBİLDİĞİ ve henüz bitmemiş bir etkinliğe eklenebilir
--    (önceden herhangi bir etkinlik kimliğine kayıt atılabiliyordu; başka üniversitenin etkinliğinin sayacını şişirirdi).

create or replace function public.list_campus_events(p_limit integer default 50)
returns table (
  id uuid,
  title text,
  description text,
  location text,
  starts_at timestamptz,
  ends_at timestamptz,
  university_id uuid,
  attendee_count bigint,
  is_joined boolean
)
language sql
stable
security definer
set search_path = public
as $$
  select e.id, e.title, e.description, e.location, e.starts_at, e.ends_at, e.university_id,
         (select count(*) from public.campus_event_attendees a where a.event_id = e.id),
         exists (select 1 from public.campus_event_attendees a where a.event_id = e.id and a.user_id = (select auth.uid()))
  from public.campus_events e
  where (select public.is_account_active())
    and (e.university_id is null or e.university_id = (select public.current_university_id()))
    and coalesce(e.ends_at, e.starts_at + interval '3 hours') > now()
  order by e.starts_at asc
  limit greatest(1, least(coalesce(p_limit, 50), 100));
$$;

revoke all on function public.list_campus_events(integer) from public, anon;
grant execute on function public.list_campus_events(integer) to authenticated;

alter policy campus_event_attendees_insert_own on public.campus_event_attendees
  with check (
    user_id = (select auth.uid())
    and (select public.is_account_active())
    -- İç sorgu campus_events RLS'ine tabidir: yalnızca kullanıcının görebildiği etkinlik geçerlidir.
    and exists (
      select 1 from public.campus_events e
      where e.id = campus_event_attendees.event_id
        and coalesce(e.ends_at, e.starts_at + interval '3 hours') > now()
    )
  );
