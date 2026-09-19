-- RLS / yetki testleri: sohbet başlatma, üyelik izolasyonu, mesajlaşma, okundu bilgisi, bildirim, realtime konu yetkisi.
-- Tek transaction, ROLLBACK ile biter. Herhangi bir doğrulama başarısız olursa "FAIL: ..." istisnası fırlar.
begin;

insert into auth.users (id, email) values
  ('c1111111-1111-1111-1111-111111111111', 'bir@rls-test.invalid'),
  ('c2222222-2222-2222-2222-222222222222', 'iki@rls-test.invalid'),
  ('c3333333-3333-3333-3333-333333333333', 'uc@rls-test.invalid'),
  ('c4444444-4444-4444-4444-444444444444', 'dort@rls-test.invalid');

update public.profiles set account_status = 'ACTIVE', verification_status = 'APPROVED', full_name = 'Bir Kişi'
  where id in ('c1111111-1111-1111-1111-111111111111', 'c2222222-2222-2222-2222-222222222222', 'c3333333-3333-3333-3333-333333333333');
-- c4 onaysız (PENDING) kalır.

-- 1) Sohbet başlatma: kendisiyle, onaysız kullanıcıyla ve anonim olarak başlatılamaz.
set local role authenticated;
set local request.jwt.claims = '{"sub":"c1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
do $$
declare v_failed boolean;
begin
  v_failed := false;
  begin perform public.start_conversation('c1111111-1111-1111-1111-111111111111');
  exception when invalid_parameter_value then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: kullanıcı kendisiyle sohbet başlatabildi'; end if;

  v_failed := false;
  begin perform public.start_conversation('c4444444-4444-4444-4444-444444444444');
  exception when no_data_found then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: onaysız kullanıcıyla sohbet başlatılabildi'; end if;
end $$;

set local role anon;
do $$
declare v_failed boolean := false;
begin
  begin perform public.start_conversation('c2222222-2222-2222-2222-222222222222');
  exception when insufficient_privilege then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: anon sohbet başlatabildi'; end if;
end $$;

-- 2) Başlatma idempotent ve simetrik: aynı çift her zaman aynı sohbeti alır.
set local role authenticated;
set local request.jwt.claims = '{"sub":"c1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
create temp table t_conv (id uuid);
grant all on t_conv to authenticated;
insert into t_conv select public.start_conversation('c2222222-2222-2222-2222-222222222222');
do $$
declare v_again uuid; v_count int;
begin
  v_again := public.start_conversation('c2222222-2222-2222-2222-222222222222');
  if v_again <> (select id from t_conv) then raise exception 'FAIL: aynı çift için ikinci sohbet oluştu'; end if;
  select count(*) into v_count from public.conversation_members where conversation_id = v_again;
  if v_count <> 2 then raise exception 'FAIL: sohbet üye sayısı 2 değil: %', v_count; end if;
end $$;
set local request.jwt.claims = '{"sub":"c2222222-2222-2222-2222-222222222222","role":"authenticated","app_metadata":{}}';
do $$
begin
  if public.start_conversation('c1111111-1111-1111-1111-111111111111') <> (select id from t_conv) then
    raise exception 'FAIL: karşı taraf başlatınca farklı sohbet oluştu';
  end if;
end $$;

-- 3) Üye olmayan kullanıcı kendini ekleyemez, sohbeti/mesajları göremez.
set local request.jwt.claims = '{"sub":"c3333333-3333-3333-3333-333333333333","role":"authenticated","app_metadata":{}}';
do $$
declare v_failed boolean := false; v_conv uuid := (select id from t_conv);
begin
  begin
    insert into public.conversation_members (conversation_id, user_id) values (v_conv, 'c3333333-3333-3333-3333-333333333333');
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: dış kullanıcı kendini sohbete üye ekleyebildi'; end if;

  v_failed := false;
  begin
    insert into public.conversations (id) values (gen_random_uuid());
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: istemci doğrudan sohbet oluşturabildi'; end if;

  if (select count(*) from public.conversations where id = v_conv) <> 0 then raise exception 'FAIL: dış kullanıcı sohbeti görebildi'; end if;
  if public.is_chat_topic_member('chat:' || v_conv::text) then raise exception 'FAIL: dış kullanıcı realtime konusuna yetkili'; end if;
end $$;

-- 4) Mesajlaşma: gönderen kimliği sahtelenemez, üyeler görür, dışarıdan görünmez, mesaj değiştirilemez.
set local request.jwt.claims = '{"sub":"c1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
do $$
declare v_failed boolean := false; v_conv uuid := (select id from t_conv); v_rows int;
begin
  begin
    insert into public.messages (conversation_id, sender_id, body) values (v_conv, 'c2222222-2222-2222-2222-222222222222', 'sahte');
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: gönderen kimliği sahtelenebildi'; end if;

  insert into public.messages (conversation_id, sender_id, body) values (v_conv, 'c1111111-1111-1111-1111-111111111111', 'Merhaba');
  insert into public.messages (conversation_id, sender_id, body) values (v_conv, 'c1111111-1111-1111-1111-111111111111', 'Nasılsın?');

  update public.messages set body = 'değişti' where conversation_id = v_conv;
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then raise exception 'FAIL: mesaj değiştirilebildi'; end if;
  delete from public.messages where conversation_id = v_conv;
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then raise exception 'FAIL: mesaj silinebildi'; end if;
  if not public.is_chat_topic_member('chat:' || v_conv::text) then raise exception 'FAIL: üye realtime konusuna yetkisiz'; end if;
  if public.is_chat_topic_member('chat:not-a-uuid') then raise exception 'FAIL: geçersiz konu yetkili'; end if;
end $$;

set local request.jwt.claims = '{"sub":"c3333333-3333-3333-3333-333333333333","role":"authenticated","app_metadata":{}}';
do $$
begin
  if (select count(*) from public.messages) <> 0 then raise exception 'FAIL: dış kullanıcı mesajları görebildi'; end if;
end $$;

-- 5) Bildirim: alıcıya TEK okunmamış NEW_MESSAGE bildirimi; gönderene yok.
reset role;
do $$
declare v_conv uuid := (select id from t_conv);
begin
  if (select count(*) from public.notifications where user_id = 'c2222222-2222-2222-2222-222222222222' and type = 'NEW_MESSAGE' and related_entity_id = v_conv) <> 1 then
    raise exception 'FAIL: alıcı için tek NEW_MESSAGE bildirimi bekleniyordu';
  end if;
  if exists (select 1 from public.notifications where user_id = 'c1111111-1111-1111-1111-111111111111' and type = 'NEW_MESSAGE') then
    raise exception 'FAIL: gönderene kendi mesajı için bildirim gitti';
  end if;
end $$;

-- 6) Sohbet listesi ve okundu bilgisi.
set local role authenticated;
set local request.jwt.claims = '{"sub":"c2222222-2222-2222-2222-222222222222","role":"authenticated","app_metadata":{}}';
do $$
declare v_row record; v_conv uuid := (select id from t_conv);
begin
  select * into v_row from public.list_my_conversations();
  if v_row.other_user_id <> 'c1111111-1111-1111-1111-111111111111' then raise exception 'FAIL: karşı taraf yanlış'; end if;
  if v_row.last_message_body <> 'Nasılsın?' then raise exception 'FAIL: son mesaj yanlış: %', v_row.last_message_body; end if;
  if v_row.unread_count <> 2 then raise exception 'FAIL: okunmamış sayısı yanlış: %', v_row.unread_count; end if;

  perform public.mark_conversation_read(v_conv);
  select * into v_row from public.list_my_conversations();
  if v_row.unread_count <> 0 then raise exception 'FAIL: okundu işaretlenince sayaç sıfırlanmadı: %', v_row.unread_count; end if;
end $$;

-- Gönderen tarafı: karşı tarafın okuduğu last_read_at görünür (okundu bilgisi için), kendi mesajları okunmamış sayılmaz.
set local request.jwt.claims = '{"sub":"c1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
do $$
declare v_conv uuid := (select id from t_conv); v_read timestamptz; v_last timestamptz;
begin
  select last_read_at into v_read from public.conversation_members where conversation_id = v_conv and user_id = 'c2222222-2222-2222-2222-222222222222';
  select max(created_at) into v_last from public.messages where conversation_id = v_conv;
  if v_read is null or v_read < v_last then raise exception 'FAIL: karşı tarafın okuma zamanı görünmüyor/eksik'; end if;
  if (select unread_count from public.list_my_conversations()) <> 0 then raise exception 'FAIL: kendi mesajları okunmamış sayıldı'; end if;
end $$;

-- 7) Başkasının okundu bilgisi değiştirilemez (istemci UPDATE politikası yok).
do $$
declare v_rows int; v_conv uuid := (select id from t_conv);
begin
  update public.conversation_members set last_read_at = null where conversation_id = v_conv and user_id = 'c2222222-2222-2222-2222-222222222222';
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then raise exception 'FAIL: başkasının last_read_at değeri değiştirilebildi'; end if;
end $$;

select 'TÜM DOĞRULAMALAR GEÇTİ' as sonuc;
rollback;
