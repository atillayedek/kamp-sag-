-- "requirements" = Claude ile ayrıştırılan ihtiyaç ilanları (eski adıyla "needs").
create table public.requirements (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references public.profiles(id) on delete cascade,
  university_id uuid not null references public.universities(id),
  raw_text text not null check (char_length(raw_text) between 1 and 500),
  title text not null check (char_length(title) between 1 and 80),
  category text not null,
  help_type text not null,
  tags text[] not null default '{}',
  skills text[] not null default '{}',
  participant_count integer check (participant_count between 1 and 50),
  urgency text not null default 'NORMAL',
  starts_at timestamptz,
  status text not null default 'PUBLISHED' check (status in ('PUBLISHED', 'MATCHED', 'CLOSED')),
  ai_source text check (ai_source in ('claude', 'fallback')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on column public.requirements.university_id is 'İstemciden ASLA kabul edilmez; publish-need Edge Function''ı JWT''den çözülen kullanıcının kendi profilinden okuyup set eder.';
comment on column public.requirements.author_id is 'publish-need Edge Function''ı tarafından request.auth (JWT) üzerinden set edilir, istemci payload''ından değil.';

create index requirements_university_id_idx on public.requirements (university_id, created_at desc);
create index requirements_author_id_idx on public.requirements (author_id);

create trigger set_requirements_updated_at
before update on public.requirements
for each row execute function public.set_updated_at();

alter table public.requirements enable row level security;
-- Bilerek: authenticated rolü için INSERT/UPDATE policy'si YOK. Tüm yazma
-- publish-need Edge Function'ı (service_role) üzerinden yapılır — Claude/kullanıcı
-- taslağı doğrudan bu tabloya yazamaz (bkz. AI_Guidelines §4.5/§4.6 karşılığı).
