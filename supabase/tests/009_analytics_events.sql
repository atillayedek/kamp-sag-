-- RLS / RPC testleri: analitik olayları.
-- Kanıtlananlar: yalnızca izin verilen olay adı kaydedilir; istemci tabloya doğrudan erişemez (okuma/yazma/silme);
-- anon çağıramaz; dakikada 60'tan fazlası atılır (hata vermeden); hesap silinince olaylar da gider.
-- Tek transaction, ROLLBACK ile biter. Herhangi bir doğrulama başarısız olursa "FAIL: ..." istisnası fırlar.
begin;

insert into auth.users (id, email) values
  ('a0000001-4444-5555-6666-777777777701', 'bir@an-test.invalid'),
  ('a0000002-4444-5555-6666-777777777702', 'iki@an-test.invalid');

set local role authenticated;
set local request.jwt.claims = '{"sub":"a0000001-4444-5555-6666-777777777701","role":"authenticated","app_metadata":{}}';

-- 1) İzin verilen olay kaydedilir; geçersiz/boş ad reddedilir.
select public.track_event('POST_CREATED');
do $$
declare v_failed boolean;
begin
  v_failed := false;
  begin perform public.track_event('HACKED_EVENT');
  exception when invalid_parameter_value then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: izin verilmeyen olay adı kabul edildi'; end if;

  v_failed := false;
  begin perform public.track_event(null);
  exception when invalid_parameter_value then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: null olay adı kabul edildi'; end if;
end $$;

-- 2) İstemci tabloya doğrudan erişemez: okuma boş, yazma/silme reddedilir (politika yok).
do $$
declare v_failed boolean := false;
begin
  if (select count(*) from public.analytics_events) <> 0 then raise exception 'FAIL: istemci analitik olaylarını okuyabildi'; end if;
  begin
    insert into public.analytics_events (user_id, name) values ('a0000001-4444-5555-6666-777777777701', 'POST_CREATED');
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: istemci tabloya doğrudan yazabildi'; end if;
  delete from public.analytics_events;
  if found then raise exception 'FAIL: istemci olay silebildi'; end if;
end $$;

-- 3) Anon çağıramaz.
set local role anon;
do $$
declare v_failed boolean := false;
begin
  begin perform public.track_event('POST_CREATED');
  exception when insufficient_privilege then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: anon olay kaydedebildi'; end if;
end $$;

-- 4) Hız sınırı: aynı dakikada 60'tan fazlası sessizce atılır.
set local role authenticated;
set local request.jwt.claims = '{"sub":"a0000002-4444-5555-6666-777777777702","role":"authenticated","app_metadata":{}}';
do $$
begin
  for i in 1..75 loop
    perform public.track_event('MESSAGE_SENT');
  end loop;
end $$;
reset role;
do $$
begin
  if (select count(*) from public.analytics_events where user_id = 'a0000002-4444-5555-6666-777777777702') <> 60 then
    raise exception 'FAIL: hız sınırı 60 değil: %', (select count(*) from public.analytics_events where user_id = 'a0000002-4444-5555-6666-777777777702');
  end if;
  if (select count(*) from public.analytics_events where user_id = 'a0000001-4444-5555-6666-777777777701') <> 1 then
    raise exception 'FAIL: birinci kullanıcının tek olayı kaydedilmedi';
  end if;
end $$;

-- 5) Hesap silinince olaylar da silinir (CASCADE).
delete from auth.users where id = 'a0000002-4444-5555-6666-777777777702';
do $$
begin
  if exists (select 1 from public.analytics_events where user_id = 'a0000002-4444-5555-6666-777777777702') then
    raise exception 'FAIL: silinen kullanıcının analitik olayları kaldı';
  end if;
end $$;

rollback;
