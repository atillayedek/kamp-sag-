create table public.rate_limits (
  id text primary key, -- format: '{user_id}:{bucket}'
  count integer not null default 1,
  window_start timestamptz not null default now()
);

alter table public.rate_limits enable row level security;
-- Kasıtlı olarak HİÇBİR policy tanımlanmadı: authenticated/anon için
-- TAMAMEN KAPALI. Yalnızca service_role (Edge Functions) erişebilir
-- (service_role RLS'yi bypass eder) — bkz. §14 "İstemci rate limit kaydı yazamaz".
