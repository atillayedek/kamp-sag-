-- RLS / davranış testleri: bildirim tercihleri (kendi satırı, başkasınınkine erişim yok) ve tercihe uyan yeni-mesaj bildirimi.
-- Tek transaction, ROLLBACK ile biter. Herhangi bir doğrulama başarısız olursa "FAIL: ..." istisnası fırlar.
begin;

insert into auth.users (id, email) values
  ('d1111111-1111-1111-1111-111111111111', 'bir@np-test.invalid'),
  ('d2222222-2222-2222-2222-222222222222', 'iki@np-test.invalid'),
  ('d3333333-3333-3333-3333-333333333333', 'uc@np-test.invalid');

update public.profiles set account_status = 'ACTIVE', verification_status = 'APPROVED', full_name = 'Deneme Kişi'
  where id in ('d1111111-1111-1111-1111-111111111111', 'd2222222-2222-2222-2222-222222222222', 'd3333333-3333-3333-3333-333333333333');

-- 1) Kullanıcı yalnızca KENDİ tercihini yazabilir/okuyabilir.
set local role authenticated;
set local request.jwt.claims = '{"sub":"d2222222-2222-2222-2222-222222222222","role":"authenticated","app_metadata":{}}';
insert into public.notification_preferences (user_id, new_message) values ('d2222222-2222-2222-2222-222222222222', false);

do $$
declare v_failed boolean := false;
begin
  begin
    insert into public.notification_preferences (user_id, new_message) values ('d3333333-3333-3333-3333-333333333333', false);
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: başkası adına tercih yazılabildi'; end if;
end $$;

set local request.jwt.claims = '{"sub":"d1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
do $$
begin
  if (select count(*) from public.notification_preferences) <> 0 then raise exception 'FAIL: başkasının tercihi okunabildi'; end if;
  -- Başkasının satırını güncelleme etkisiz (RLS filtreler): 0 satır etkilenir.
  update public.notification_preferences set new_message = true where user_id = 'd2222222-2222-2222-2222-222222222222';
  if found then raise exception 'FAIL: başkasının tercihi güncellenebildi'; end if;
end $$;

set local role anon;
do $$
declare v_failed boolean := false;
begin
  -- Tablo yetkisi anon'a açık olsa da politika yalnızca `authenticated` içindir: satır GÖRÜNMEZ ve yazılamaz.
  if (select count(*) from public.notification_preferences) <> 0 then raise exception 'FAIL: anon tercih satırı görebildi'; end if;
  begin
    insert into public.notification_preferences (user_id, new_message) values ('d3333333-3333-3333-3333-333333333333', false);
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: anon tercih yazabildi'; end if;
end $$;

-- 2) Yeni mesaj bildirimi tercihe uyar: d2 kapalı -> bildirim YOK; d3 (satır yok, varsayılan açık) -> bildirim VAR.
reset role;
insert into public.conversations (id) values ('d0000000-0000-0000-0000-00000000c0de');
insert into public.conversation_members (conversation_id, user_id) values
  ('d0000000-0000-0000-0000-00000000c0de', 'd1111111-1111-1111-1111-111111111111'),
  ('d0000000-0000-0000-0000-00000000c0de', 'd2222222-2222-2222-2222-222222222222'),
  ('d0000000-0000-0000-0000-00000000c0de', 'd3333333-3333-3333-3333-333333333333');
insert into public.messages (conversation_id, sender_id, body)
  values ('d0000000-0000-0000-0000-00000000c0de', 'd1111111-1111-1111-1111-111111111111', 'merhaba');

do $$
begin
  if exists (select 1 from public.notifications where user_id = 'd2222222-2222-2222-2222-222222222222' and type = 'NEW_MESSAGE') then
    raise exception 'FAIL: bildirimi kapatan kullanıcıya bildirim oluştu';
  end if;
  if not exists (select 1 from public.notifications where user_id = 'd3333333-3333-3333-3333-333333333333' and type = 'NEW_MESSAGE') then
    raise exception 'FAIL: tercihi olmayan (varsayılan açık) kullanıcıya bildirim oluşmadı';
  end if;
  if exists (select 1 from public.notifications where user_id = 'd1111111-1111-1111-1111-111111111111' and type = 'NEW_MESSAGE') then
    raise exception 'FAIL: gönderen kendi mesajı için bildirim aldı';
  end if;
end $$;

-- 3) Tercih tekrar açılınca (yeni okunmamış bildirim koşuluyla) bildirim yeniden oluşur.
update public.notification_preferences set new_message = true where user_id = 'd2222222-2222-2222-2222-222222222222';
insert into public.messages (conversation_id, sender_id, body)
  values ('d0000000-0000-0000-0000-00000000c0de', 'd1111111-1111-1111-1111-111111111111', 'tekrar');
do $$
begin
  if not exists (select 1 from public.notifications where user_id = 'd2222222-2222-2222-2222-222222222222' and type = 'NEW_MESSAGE') then
    raise exception 'FAIL: tercih açılınca bildirim oluşmadı';
  end if;
end $$;

-- 4) Hesap silinince tercih satırı da gider (ON DELETE CASCADE).
delete from auth.users where id = 'd2222222-2222-2222-2222-222222222222';
do $$
begin
  if exists (select 1 from public.notification_preferences where user_id = 'd2222222-2222-2222-2222-222222222222') then
    raise exception 'FAIL: silinen kullanıcının tercih satırı kaldı';
  end if;
end $$;

select 'OK: 004_notification_preferences' as result;
rollback;
