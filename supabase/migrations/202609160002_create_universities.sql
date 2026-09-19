create table public.universities (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  short_name text not null,
  city text not null,
  logo_url text,
  domain text,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create unique index universities_short_name_idx on public.universities (short_name);
create index universities_city_idx on public.universities (city);
create index universities_is_active_idx on public.universities (is_active) where is_active;

alter table public.profiles
  add constraint profiles_university_id_fkey
  foreign key (university_id) references public.universities(id);

alter table public.universities enable row level security;
