-- RLS / davranış testleri: abonelik planları ve kullanıcı hakları.
-- Kanıtlananlar: taslak planlar görünmez; doğrulanmamış hesap plan göremez; kullanıcı yalnızca KENDİ hakkını okur ve
-- ASLA yazamaz (kendine premium veremez); süresi dolan hak FREE sayılır; aynı satın alma jetonu iki hesaba bağlanamaz.
-- Tek transaction, ROLLBACK ile biter. Herhangi bir doğrulama başarısız olursa "FAIL: ..." istisnası fırlar.
begin;

insert into auth.users (id, email) values
  ('f1111111-1111-1111-1111-111111111111', 'onayli@sub-test.invalid'),
  ('f2222222-2222-2222-2222-222222222222', 'baska@sub-test.invalid'),
  ('f3333333-3333-3333-3333-333333333333', 'bekleyen@sub-test.invalid');

update public.profiles set account_status = 'ACTIVE', verification_status = 'APPROVED'
  where id in ('f1111111-1111-1111-1111-111111111111', 'f2222222-2222-2222-2222-222222222222');

-- 1) Onaylı kullanıcı YALNIZCA etkin planları görür (tohum: yalnızca FREE etkin).
set local role authenticated;
set local request.jwt.claims = '{"sub":"f1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
do $$
begin
  if (select count(*) from public.subscription_plans where id = 'FREE') <> 1 then raise exception 'FAIL: onaylı kullanıcı FREE planı göremedi'; end if;
  if (select count(*) from public.subscription_plans where not is_active) <> 0 then raise exception 'FAIL: taslak plan kullanıcıya göründü'; end if;
end $$;

-- 2) Doğrulanmamış hesap plan göremez.
set local request.jwt.claims = '{"sub":"f3333333-3333-3333-3333-333333333333","role":"authenticated","app_metadata":{}}';
do $$
begin
  if (select count(*) from public.subscription_plans) <> 0 then raise exception 'FAIL: doğrulanmamış hesap plan okuyabildi'; end if;
end $$;

-- 3) Hak yazma: hiçbir istemci (kendi adına bile) hak ekleyemez/güncelleyemez/silemez.
set local request.jwt.claims = '{"sub":"f1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
do $$
declare v_failed boolean := false;
begin
  begin
    insert into public.user_entitlements (user_id, plan_id, play_product_id, purchase_token, expires_at)
    values ('f1111111-1111-1111-1111-111111111111', 'PREMIUM', 'kampusagi_premium', 'sahte-jeton', now() + interval '30 days');
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: istemci kendine premium hakkı yazabildi'; end if;
end $$;

-- 4) Hak okuma ve süre dolumu (service role = postgres ile yazılır).
reset role;
insert into public.user_entitlements (user_id, plan_id, play_product_id, purchase_token, expires_at, auto_renewing)
values ('f1111111-1111-1111-1111-111111111111', 'PREMIUM', 'kampusagi_premium', 'jeton-1', now() + interval '30 days', true);

set local role authenticated;
set local request.jwt.claims = '{"sub":"f1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
do $$
declare v_failed boolean := false;
begin
  if public.my_plan() <> 'PREMIUM' then raise exception 'FAIL: süresi dolmamış premium hak plan olarak görünmedi: %', public.my_plan(); end if;
  if (select count(*) from public.user_entitlements) <> 1 then raise exception 'FAIL: kullanıcı kendi hakkını okuyamadı'; end if;

  update public.user_entitlements set plan_id = 'COMMUNITY_PRO', expires_at = now() + interval '3650 days'
    where user_id = 'f1111111-1111-1111-1111-111111111111';
  if found then raise exception 'FAIL: istemci kendi hakkını güncelleyebildi'; end if;

  delete from public.user_entitlements where user_id = 'f1111111-1111-1111-1111-111111111111';
  if found then raise exception 'FAIL: istemci kendi hakkını silebildi'; end if;
end $$;

-- Başka kullanıcı bu hakkı ne görür ne de kendi planı olarak kullanabilir.
set local request.jwt.claims = '{"sub":"f2222222-2222-2222-2222-222222222222","role":"authenticated","app_metadata":{}}';
do $$
begin
  if (select count(*) from public.user_entitlements) <> 0 then raise exception 'FAIL: başkasının hakkı okunabildi'; end if;
  if public.my_plan() <> 'FREE' then raise exception 'FAIL: hakkı olmayan kullanıcı FREE değil: %', public.my_plan(); end if;
end $$;

-- 5) Süresi dolan hak FREE sayılır.
reset role;
update public.user_entitlements set expires_at = now() - interval '1 minute' where user_id = 'f1111111-1111-1111-1111-111111111111';
set local role authenticated;
set local request.jwt.claims = '{"sub":"f1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
do $$
begin
  if public.my_plan() <> 'FREE' then raise exception 'FAIL: süresi dolan hak hâlâ premium sayıldı: %', public.my_plan(); end if;
end $$;

-- 6) Aynı satın alma jetonu ikinci bir hesaba bağlanamaz (jeton yeniden oynatma koruması).
reset role;
do $$
declare v_failed boolean := false;
begin
  begin
    insert into public.user_entitlements (user_id, plan_id, play_product_id, purchase_token, expires_at)
    values ('f2222222-2222-2222-2222-222222222222', 'PREMIUM', 'kampusagi_premium', 'jeton-1', now() + interval '30 days');
  exception when unique_violation then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: aynı satın alma jetonu iki hesaba bağlandı'; end if;
end $$;

-- 7) Şema kuralları: ücretsiz planın ürünü olmaz, ücretli planın ürünü olur; anon plan/hak göremez.
do $$
declare v_failed boolean := false;
begin
  begin
    update public.subscription_plans set play_product_id = 'x' where id = 'FREE';
  exception when check_violation then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: FREE plana Play ürünü bağlanabildi'; end if;

  v_failed := false;
  begin
    update public.subscription_plans set play_product_id = null where id = 'PREMIUM';
  exception when check_violation then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: ücretli plan ürünsüz bırakılabildi'; end if;
end $$;

set local role anon;
do $$
begin
  if (select count(*) from public.subscription_plans) <> 0 then raise exception 'FAIL: anon plan okuyabildi'; end if;
  if (select count(*) from public.user_entitlements) <> 0 then raise exception 'FAIL: anon hak okuyabildi'; end if;
end $$;

-- 8) Hesap silinince hak da gider.
reset role;
delete from auth.users where id = 'f1111111-1111-1111-1111-111111111111';
do $$
begin
  if exists (select 1 from public.user_entitlements where user_id = 'f1111111-1111-1111-1111-111111111111') then
    raise exception 'FAIL: silinen kullanıcının hakkı kaldı';
  end if;
end $$;

rollback;
