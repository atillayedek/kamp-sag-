-- Abonelik planları ve kullanıcı hakları (entitlement).
--
-- subscription_plans: hangi planların hangi özelliklerle gösterileceği VERİDİR (ürün sahibi yönetir); fiyat ve dönem
--   ASLA burada tutulmaz — Google Play Billing ürünlerinden (yerelleştirilmiş) okunur.
-- user_entitlements: yalnızca `verify-purchase` Edge Function'ı (service role) yazar. İstemci hiçbir koşulda hak yazamaz;
--   satın alma Google Play Developer API ile sunucuda doğrulanır. Süre dolumu (`expires_at`) sunucu saatiyle değerlendirilir.
--
-- Ücretli planlar (PREMIUM, COMMUNITY_PRO) `is_active = false` tohumlanır: özellik metinleri ve Play ürünleri ürün
-- sahibinden gelene kadar kullanıcıya vaat edilmez (bkz. docs/BLOCKERS.md B5).

create table public.subscription_plans (
  id text primary key check (id in ('FREE', 'PREMIUM', 'COMMUNITY_PRO')),
  title text not null,
  features text[] not null default '{}',
  play_product_id text unique,
  is_featured boolean not null default false,
  is_active boolean not null default false,
  sort_order integer not null default 0,
  constraint subscription_plans_product_iff_paid check ((id = 'FREE') = (play_product_id is null))
);

alter table public.subscription_plans enable row level security;

create policy subscription_plans_select on public.subscription_plans
  for select to authenticated using ((select public.is_account_active()));

insert into public.subscription_plans (id, title, features, play_product_id, is_featured, is_active, sort_order) values
  ('FREE', 'Free',
   array['Topluluklarda paylaşım yapma ve yorum yazma', 'İhtiyaç paylaşma ve eşleşmeleri görme', 'Eşleşmelerinle mesajlaşma'],
   null, false, true, 0),
  ('PREMIUM', 'Premium', '{}', 'kampusagi_premium', true, false, 1),
  ('COMMUNITY_PRO', 'Community Pro', '{}', 'kampusagi_community_pro', false, false, 2);

create table public.user_entitlements (
  user_id uuid primary key references auth.users(id) on delete cascade,
  plan_id text not null references public.subscription_plans(id),
  play_product_id text not null,
  purchase_token text not null unique,
  expires_at timestamptz not null,
  auto_renewing boolean not null default false,
  updated_at timestamptz not null default now()
);

alter table public.user_entitlements enable row level security;

-- Yalnızca kendi hakkını okur; yazma politikası YOK (service role RLS'yi atlar).
create policy user_entitlements_select_own on public.user_entitlements
  for select to authenticated using (user_id = (select auth.uid()));

-- Geçerli plan: süresi dolmamış hak varsa onun planı, yoksa FREE.
create or replace function public.my_plan()
returns text
language sql
stable
security invoker
set search_path = public
as $$
  select coalesce(
    (select e.plan_id from public.user_entitlements e where e.user_id = (select auth.uid()) and e.expires_at > now()),
    'FREE'
  );
$$;

revoke all on function public.my_plan() from public, anon;
grant execute on function public.my_plan() to authenticated;
