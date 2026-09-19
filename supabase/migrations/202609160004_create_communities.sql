-- İki kapsam: UNIVERSITY ("Kampüsüm") ve GENERAL ("Tüm Üniversiteler") — bkz. §15.
-- Kaynakta başka bir topluluk tipi (ör. serbest kulüpler) belirtilmedi, uydurulmadı.
create table public.communities (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  type text not null check (type in ('UNIVERSITY', 'GENERAL')),
  university_id uuid references public.universities(id),
  created_at timestamptz not null default now(),
  constraint communities_university_required_for_university_type
    check (type = 'GENERAL' or university_id is not null)
);

create unique index communities_one_per_university
  on public.communities (university_id)
  where type = 'UNIVERSITY';
create unique index communities_one_general
  on public.communities ((type))
  where type = 'GENERAL';

create table public.community_members (
  community_id uuid not null references public.communities(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'MEMBER' check (role in ('MEMBER', 'MODERATOR')),
  joined_at timestamptz not null default now(),
  primary key (community_id, user_id)
);

create index community_members_user_id_idx on public.community_members (user_id);

alter table public.communities enable row level security;
alter table public.community_members enable row level security;
