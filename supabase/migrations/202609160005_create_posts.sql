create table public.posts (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references public.profiles(id) on delete cascade,
  community_id uuid not null references public.communities(id),
  title text not null check (char_length(title) between 1 and 200),
  body text not null check (char_length(body) between 1 and 5000),
  category text not null,
  help_type text,
  tags text[] not null default '{}',
  image_urls text[] not null default '{}',
  status text not null default 'PUBLISHED' check (status in ('PUBLISHED', 'REMOVED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index posts_community_id_idx on public.posts (community_id, created_at desc);
create index posts_author_id_idx on public.posts (author_id);
create index posts_category_idx on public.posts (category);
create index posts_tags_idx on public.posts using gin (tags);

create trigger set_posts_updated_at
before update on public.posts
for each row execute function public.set_updated_at();

create table public.comments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts(id) on delete cascade,
  author_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 2000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index comments_post_id_idx on public.comments (post_id, created_at);

create trigger set_comments_updated_at
before update on public.comments
for each row execute function public.set_updated_at();

create table public.post_likes (
  post_id uuid not null references public.posts(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);

create table public.saved_posts (
  post_id uuid not null references public.posts(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);

alter table public.posts enable row level security;
alter table public.comments enable row level security;
alter table public.post_likes enable row level security;
alter table public.saved_posts enable row level security;
