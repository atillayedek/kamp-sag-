-- RLS / davranış testleri: etkinlik akışı (list_campus_events) ve katılım.
-- Kanıtlananlar: yalnızca ACTIVE+APPROVED kullanıcı görür; yalnızca genel + kendi üniversitesinin YAKLAŞAN etkinlikleri gelir;
-- sayı doğru, kimlik sızmaz; katılım yalnızca görülebilen ve bitmemiş etkinliğe eklenir; anon çağıramaz.
-- Tek transaction, ROLLBACK ile biter. Herhangi bir doğrulama başarısız olursa "FAIL: ..." istisnası fırlar.
begin;

insert into auth.users (id, email) values
  ('c0000001-2222-3333-4444-555555555501', 'a@ev-test.invalid'),
  ('c0000002-2222-3333-4444-555555555502', 'b@ev-test.invalid'),
  ('c0000003-2222-3333-4444-555555555503', 'diger-uni@ev-test.invalid'),
  ('c0000004-2222-3333-4444-555555555504', 'bekleyen@ev-test.invalid');

insert into public.universities (id, name, short_name, city) values
  ('00000000-0000-0000-0000-00000000f201', 'Etkinlik Test Üni 1', 'ET1', 'Test'),
  ('00000000-0000-0000-0000-00000000f202', 'Etkinlik Test Üni 2', 'ET2', 'Test');

update public.profiles set account_status = 'ACTIVE', verification_status = 'APPROVED', university_id = '00000000-0000-0000-0000-00000000f201'
  where id in ('c0000001-2222-3333-4444-555555555501', 'c0000002-2222-3333-4444-555555555502');
update public.profiles set account_status = 'ACTIVE', verification_status = 'APPROVED', university_id = '00000000-0000-0000-0000-00000000f202'
  where id = 'c0000003-2222-3333-4444-555555555503';
update public.profiles set university_id = '00000000-0000-0000-0000-00000000f201'
  where id = 'c0000004-2222-3333-4444-555555555504';

insert into public.campus_events (id, university_id, title, description, location, starts_at, ends_at) values
  ('00000000-0000-0000-0000-00000000e201', '00000000-0000-0000-0000-00000000f201', 'Üni 1 yaklaşan', 'd', 'Amfi', now() + interval '2 days', null),
  ('00000000-0000-0000-0000-00000000e202', '00000000-0000-0000-0000-00000000f202', 'Üni 2 yaklaşan', 'd', 'Amfi', now() + interval '1 day', null),
  ('00000000-0000-0000-0000-00000000e203', null, 'Genel yaklaşan', 'd', 'Online', now() + interval '3 days', null),
  ('00000000-0000-0000-0000-00000000e204', '00000000-0000-0000-0000-00000000f201', 'Üni 1 geçmiş', 'd', 'Amfi', now() - interval '2 days', now() - interval '1 day'),
  ('00000000-0000-0000-0000-00000000e205', '00000000-0000-0000-0000-00000000f201', 'Üni 1 sürüyor', 'd', 'Amfi', now() - interval '1 hour', now() + interval '1 hour');

-- 1) Akış: Üni 1 öğrencisi genel + kendi üniversitesinin yaklaşan/süren etkinliklerini başlangıç sırasıyla görür.
set local role authenticated;
set local request.jwt.claims = '{"sub":"c0000001-2222-3333-4444-555555555501","role":"authenticated","app_metadata":{}}';
do $$
declare v_ids uuid[];
begin
  select array_agg(id order by starts_at) into v_ids from public.list_campus_events();
  if v_ids is distinct from array[
      '00000000-0000-0000-0000-00000000e205',
      '00000000-0000-0000-0000-00000000e201',
      '00000000-0000-0000-0000-00000000e203']::uuid[] then
    raise exception 'FAIL: akış beklenen etkinlikleri/sıralamayı döndürmedi: %', v_ids;
  end if;
end $$;

-- 2) Katılım: kendi üniversitesinin etkinliğine katılır; başka üniversitenin / geçmiş etkinliğe katılamaz.
insert into public.campus_event_attendees (event_id, user_id)
  values ('00000000-0000-0000-0000-00000000e201', 'c0000001-2222-3333-4444-555555555501');
do $$
declare v_failed boolean := false;
begin
  begin
    insert into public.campus_event_attendees (event_id, user_id)
    values ('00000000-0000-0000-0000-00000000e202', 'c0000001-2222-3333-4444-555555555501');
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: başka üniversitenin etkinliğine katılabildi'; end if;

  v_failed := false;
  begin
    insert into public.campus_event_attendees (event_id, user_id)
    values ('00000000-0000-0000-0000-00000000e204', 'c0000001-2222-3333-4444-555555555501');
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: geçmiş etkinliğe katılım kaydı eklenebildi'; end if;

  v_failed := false;
  begin
    insert into public.campus_event_attendees (event_id, user_id)
    values ('00000000-0000-0000-0000-00000000e203', 'c0000002-2222-3333-4444-555555555502');
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: başkası adına katılım eklenebildi'; end if;
end $$;

-- 3) İkinci öğrenci de katılır; sayı 2, herkes için doğru; is_joined kişiye göre.
set local request.jwt.claims = '{"sub":"c0000002-2222-3333-4444-555555555502","role":"authenticated","app_metadata":{}}';
insert into public.campus_event_attendees (event_id, user_id)
  values ('00000000-0000-0000-0000-00000000e201', 'c0000002-2222-3333-4444-555555555502');
do $$
declare v_row record;
begin
  select attendee_count, is_joined into v_row from public.list_campus_events() where id = '00000000-0000-0000-0000-00000000e201';
  if v_row.attendee_count <> 2 or not v_row.is_joined then raise exception 'FAIL: 2. öğrenci için sayı/is_joined yanlış: % / %', v_row.attendee_count, v_row.is_joined; end if;
  select attendee_count, is_joined into v_row from public.list_campus_events() where id = '00000000-0000-0000-0000-00000000e203';
  if v_row.attendee_count <> 0 or v_row.is_joined then raise exception 'FAIL: katılınmayan etkinlik yanlış görünüyor'; end if;
  -- Tablo RLS'i katılımcı satırlarını sızdırmaz: yalnızca kendi satırı.
  if (select count(*) from public.campus_event_attendees) <> 1 then raise exception 'FAIL: başkasının katılım satırı okunabildi'; end if;
end $$;

-- 4) Ayrılınca sayı düşer.
delete from public.campus_event_attendees where event_id = '00000000-0000-0000-0000-00000000e201' and user_id = 'c0000002-2222-3333-4444-555555555502';
do $$
declare v_row record;
begin
  select attendee_count, is_joined into v_row from public.list_campus_events() where id = '00000000-0000-0000-0000-00000000e201';
  if v_row.attendee_count <> 1 or v_row.is_joined then raise exception 'FAIL: ayrılınca sayı/is_joined güncellenmedi: % / %', v_row.attendee_count, v_row.is_joined; end if;
end $$;

-- 5) Başka üniversitenin öğrencisi Üni 1 etkinliğini görmez, kendisininkini + genel olanı görür.
set local request.jwt.claims = '{"sub":"c0000003-2222-3333-4444-555555555503","role":"authenticated","app_metadata":{}}';
do $$
declare v_ids uuid[];
begin
  select array_agg(id order by starts_at) into v_ids from public.list_campus_events();
  if v_ids is distinct from array['00000000-0000-0000-0000-00000000e202', '00000000-0000-0000-0000-00000000e203']::uuid[] then
    raise exception 'FAIL: diğer üniversite yanlış etkinlik listesi gördü: %', v_ids;
  end if;
end $$;

-- 6) Doğrulanmamış hesap ve anon: hiçbir şey görmez / çağıramaz.
set local request.jwt.claims = '{"sub":"c0000004-2222-3333-4444-555555555504","role":"authenticated","app_metadata":{}}';
do $$
begin
  if (select count(*) from public.list_campus_events()) <> 0 then raise exception 'FAIL: doğrulanmamış hesap etkinlik akışını gördü'; end if;
end $$;

set local role anon;
do $$
declare v_failed boolean := false;
begin
  begin perform * from public.list_campus_events();
  exception when insufficient_privilege then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: anon etkinlik akışını çağırabildi'; end if;
end $$;

-- 7) Limit sınırları: 0/negatif en az 1'e, çok büyük değer 100'e indirilir (hata vermez).
set local role authenticated;
set local request.jwt.claims = '{"sub":"c0000001-2222-3333-4444-555555555501","role":"authenticated","app_metadata":{}}';
do $$
begin
  if (select count(*) from public.list_campus_events(0)) <> 1 then raise exception 'FAIL: limit 0 en az 1 satır dönmedi'; end if;
  if (select count(*) from public.list_campus_events(1000000)) <> 3 then raise exception 'FAIL: büyük limit yanlış davrandı'; end if;
  if (select count(*) from public.list_campus_events(null)) <> 3 then raise exception 'FAIL: null limit varsayılana düşmedi'; end if;
end $$;

rollback;
