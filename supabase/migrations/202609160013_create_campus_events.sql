-- NOT: `campus_event_attendees` kaynak tablo listesinde (§8) adı geçmiyordu
-- ama §29 "Katıl butonu" katılım kaydı gerektiriyor — bu makul/gerekli bir
-- ek olarak eklendi (uydurulan bir ÖZELLİK değil, var olan bir özelliğin
-- veri modeli karşılığı).
create table public.campus_events (
  id uuid primary key default gen_random_uuid(),
  university_id uuid references public.universities(id),
  title text not null,
  description text not null,
  location text not null,
  starts_at timestamptz not null,
  ends_at timestamptz,
  created_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);

create index campus_events_university_id_idx on public.campus_events (university_id, starts_at);

create table public.campus_event_attendees (
  event_id uuid not null references public.campus_events(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (event_id, user_id)
);

alter table public.campus_events enable row level security;
alter table public.campus_event_attendees enable row level security;

create policy "campus_events_select" on public.campus_events
for select to authenticated
using (university_id is null or university_id = public.current_university_id());

create policy "campus_events_write_staff" on public.campus_events
for all to authenticated
using (public.is_moderator())
with check (public.is_moderator());

create policy "campus_event_attendees_select_own" on public.campus_event_attendees
for select to authenticated using (user_id = auth.uid());

create policy "campus_event_attendees_insert_own" on public.campus_event_attendees
for insert to authenticated with check (user_id = auth.uid());

create policy "campus_event_attendees_delete_own" on public.campus_event_attendees
for delete to authenticated using (user_id = auth.uid());
