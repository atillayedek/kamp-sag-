-- ⚠️ AÇIK NOKTA (kesinleşmiş karar DEĞİL — bkz. memory-bank/Memory_Bank.md):
-- Semantik eşleşme (%60 ağırlık, bkz. matching_config) bir embedding modeli
-- gerektirir. Anthropic Claude'un embedding endpoint'i YOKTUR; OpenAI ve
-- Gemini bu projede kesinlikle kullanılamaz (kullanıcı talimatı). Bu nedenle
-- `embedding` sütunu ve bu migration şema olarak HAZIR ama recompute-matches
-- Edge Function'ı bir embedding sağlayıcısı (ör. Anthropic'in resmi ortağı
-- Voyage AI, ya da başka bir onaylanmış seçenek) kullanıcı tarafından
-- onaylanana kadar semantic bileşeni 0 olarak hesaplayacaktır.

create extension if not exists vector with schema extensions;

alter table public.requirements add column embedding extensions.vector(1536);

create index requirements_embedding_idx on public.requirements
using hnsw (embedding extensions.vector_cosine_ops);

create or replace function public.match_requirements(
  query_embedding extensions.vector(1536),
  target_university_id uuid,
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
  where r.university_id = target_university_id
    and r.embedding is not null
    and r.status = 'PUBLISHED'
  order by r.embedding <=> query_embedding
  limit match_count;
$$;
