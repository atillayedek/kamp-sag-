create table public.badges (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  title text not null,
  description text not null,
  icon text
);

create table public.user_badges (
  user_id uuid not null references auth.users(id) on delete cascade,
  badge_id uuid not null references public.badges(id) on delete cascade,
  awarded_at timestamptz not null default now(),
  primary key (user_id, badge_id)
);

create table public.leaderboard_entries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  university_id uuid references public.universities(id),
  period text not null check (period in ('ALL_TIME', 'MONTHLY', 'WEEKLY')),
  score integer not null default 0,
  rank integer,
  computed_at timestamptz not null default now(),
  unique (user_id, period)
);

create index leaderboard_entries_period_score_idx on public.leaderboard_entries (period, score desc);

alter table public.badges enable row level security;
alter table public.user_badges enable row level security;
alter table public.leaderboard_entries enable row level security;

create policy "badges_select" on public.badges for select to authenticated using (true);
create policy "user_badges_select" on public.user_badges for select to authenticated using (true);
create policy "leaderboard_entries_select" on public.leaderboard_entries for select to authenticated using (true);
-- Yazma policy'si YOK: karma/rozet/sıralama istemciden asla üretilmez (bkz. §27, §39).
-- Bu tablolar yalnızca service_role (planlanmış bir Edge Function / cron ile,
-- henüz yazılmadı) tarafından güncellenir.
