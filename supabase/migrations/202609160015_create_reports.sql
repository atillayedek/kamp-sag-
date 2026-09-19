create table public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users(id) on delete cascade,
  target_type text not null check (target_type in ('POST', 'COMMENT', 'MESSAGE', 'USER')),
  target_id uuid not null,
  reason text not null check (char_length(reason) between 1 and 1000),
  status text not null default 'OPEN' check (status in ('OPEN', 'REVIEWED', 'DISMISSED')),
  reviewed_by uuid references auth.users(id),
  created_at timestamptz not null default now()
);

create index reports_status_idx on public.reports (status);

alter table public.reports enable row level security;

create policy "reports_insert_own" on public.reports
for insert to authenticated with check (reporter_id = auth.uid());

create policy "reports_select_own_or_staff" on public.reports
for select to authenticated
using (reporter_id = auth.uid() or public.is_moderator());

create policy "reports_update_staff" on public.reports
for update to authenticated
using (public.is_moderator())
with check (public.is_moderator());
