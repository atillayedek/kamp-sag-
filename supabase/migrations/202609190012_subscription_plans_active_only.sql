-- Taslak (is_active = false) planlar kullanıcıya hiç görünmez: özellik metni/ürün hazır olmadan vaat edilmez.
alter policy subscription_plans_select on public.subscription_plans
  using ((select public.is_account_active()) and is_active);
