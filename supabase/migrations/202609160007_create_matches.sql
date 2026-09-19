-- Ağırlıklar kullanıcının verdiği ÖRNEK değerlerdir (%60/15/10/5/5/5), NİHAİ
-- OLDUKLARI TEYİT EDİLMEDİ (bkz. project-goals.md §8, AI_Guidelines §24). Bu
-- yüzden sabit kod yerine tek satırlık, değiştirilebilir bir config tablosu.
create table public.matching_config (
  id boolean primary key default true check (id),
  semantic_weight numeric not null default 0.60,
  distance_weight numeric not null default 0.15,
  help_type_weight numeric not null default 0.10,
  profile_weight numeric not null default 0.05,
  trust_weight numeric not null default 0.05,
  activity_weight numeric not null default 0.05,
  updated_at timestamptz not null default now(),
  constraint matching_config_weights_sum_to_one check (
    abs(semantic_weight + distance_weight + help_type_weight
        + profile_weight + trust_weight + activity_weight - 1.0) < 0.001
  )
);

comment on table public.matching_config is 'ÖNEMLİ AÇIK NOKTA: semantic_weight (%60) pgvector embedding gerektirir; Anthropic Claude embedding endpoint''i SUNMAZ ve OpenAI/Gemini kesinlikle kullanılamaz (bkz. kullanıcı talimatı). Bir embedding sağlayıcısı (ör. Voyage AI) onaylanana kadar recompute-matches semantic bileşeni 0 olarak hesaplar. Bkz. memory-bank/Memory_Bank.md.';

insert into public.matching_config default values;

create trigger set_matching_config_updated_at
before update on public.matching_config
for each row execute function public.set_updated_at();

create table public.matches (
  id uuid primary key default gen_random_uuid(),
  requirement_id uuid not null references public.requirements(id) on delete cascade,
  matched_user_id uuid not null references public.profiles(id) on delete cascade,
  score numeric not null check (score between 0 and 100),
  score_breakdown jsonb not null default '{}'::jsonb,
  status text not null default 'SUGGESTED' check (status in ('SUGGESTED', 'ACCEPTED', 'DECLINED')),
  created_at timestamptz not null default now(),
  unique (requirement_id, matched_user_id)
);

comment on column public.matches.score is 'YALNIZCA backend (recompute-matches Edge Function, deterministic SQL) hesaplar. Claude bu değeri asla belirlemez.';

create index matches_requirement_id_idx on public.matches (requirement_id);
create index matches_matched_user_id_idx on public.matches (matched_user_id);

alter table public.matching_config enable row level security;
alter table public.matches enable row level security;
