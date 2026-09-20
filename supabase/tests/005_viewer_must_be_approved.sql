-- RLS testleri: içerik OKUMA yetkisi doğrulanmış (ACTIVE + APPROVED) öğrenciye aittir.
-- PENDING / REJECTED hesap; aynı üniversiteyi seçmiş olsa bile gönderi, yorum, ilan, etkinlik, topluluk ve başkalarının
-- profillerini göremez; kendi profilini ve doğrulama kaydını görür. Onaylı öğrenci hepsini görmeye devam eder.
-- Tek transaction, ROLLBACK ile biter. Herhangi bir doğrulama başarısız olursa "FAIL: ..." istisnası fırlar.
begin;

insert into auth.users (id, email) values
  ('e1111111-1111-1111-1111-111111111111', 'onayli@rls-test.invalid'),
  ('e2222222-2222-2222-2222-222222222222', 'bekleyen@rls-test.invalid'),
  ('e3333333-3333-3333-3333-333333333333', 'reddedilen@rls-test.invalid'),
  ('e4444444-4444-4444-4444-444444444444', 'onayli2@rls-test.invalid');

insert into public.universities (id, name, short_name, city) values
  ('00000000-0000-0000-0000-00000000f101', 'RLS Okuma Test Üniversitesi', 'RLS_R1', 'Test');

-- Genel topluluk tektir (`communities_one_general`) ve canlıda zaten vardır; yalnızca üniversite topluluğu eklenir.
insert into public.communities (id, name, type, university_id) values
  ('00000000-0000-0000-0000-00000000d101', 'RLS R1 Kampüs', 'UNIVERSITY', '00000000-0000-0000-0000-00000000f101');

-- e1, e4: onaylı; e2: bekleyen (aynı üniversiteyi seçmiş); e3: reddedilmiş (aynı üniversite).
update public.profiles set university_id = '00000000-0000-0000-0000-00000000f101', account_status = 'ACTIVE',
  verification_status = 'APPROVED', full_name = 'Onaylı Öğrenci' where id in ('e1111111-1111-1111-1111-111111111111', 'e4444444-4444-4444-4444-444444444444');
update public.profiles set university_id = '00000000-0000-0000-0000-00000000f101', account_status = 'ACTIVE',
  verification_status = 'PENDING', full_name = 'Bekleyen' where id = 'e2222222-2222-2222-2222-222222222222';
update public.profiles set university_id = '00000000-0000-0000-0000-00000000f101', account_status = 'ACTIVE',
  verification_status = 'REJECTED', full_name = 'Reddedilen' where id = 'e3333333-3333-3333-3333-333333333333';

-- İçerik (postgres olarak): üniversite gönderisi, genel gönderi, yorum, ilan, etkinlik.
insert into public.posts (id, author_id, community_id, title, body, category) values
  ('00000000-0000-0000-0000-00000000b101', 'e1111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-00000000d101', 'Üni gönderi', 'gövde', 'ACADEMIC'),
  ('00000000-0000-0000-0000-00000000b102', 'e1111111-1111-1111-1111-111111111111', (select id from public.communities where type = 'GENERAL'), 'Genel gönderi', 'gövde', 'OTHER');
insert into public.comments (post_id, author_id, body) values
  ('00000000-0000-0000-0000-00000000b101', 'e1111111-1111-1111-1111-111111111111', 'yorum');
insert into public.requirements (id, author_id, university_id, raw_text, title, category, help_type, tags, skills, urgency, status) values
  ('00000000-0000-0000-0000-00000000a101', 'e1111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-00000000f101',
   'gizli ilan metni', 'İlan', 'OTHER', 'LOOKING_FOR_PEOPLE', '{}', '{}', 'NORMAL', 'PUBLISHED');
insert into public.campus_events (id, title, description, location, university_id, starts_at)
  values ('00000000-0000-0000-0000-00000000e101', 'Etkinlik', 'açıklama', 'Kampüs', '00000000-0000-0000-0000-00000000f101', now() + interval '1 day');

-- 1) Bekleyen VE reddedilen hesap: hiçbir öğrenci içeriğini okuyamaz; kendi profilini okur.
create temp table t_viewers (id uuid);
insert into t_viewers values ('e2222222-2222-2222-2222-222222222222'), ('e3333333-3333-3333-3333-333333333333');
grant all on t_viewers to authenticated;

do $$
declare v_viewer uuid;
begin
  for v_viewer in select id from t_viewers loop
    perform set_config('role', 'authenticated', true);
    perform set_config('request.jwt.claims', json_build_object('sub', v_viewer, 'role', 'authenticated', 'app_metadata', '{}'::json)::text, true);

    if (select count(*) from public.posts) <> 0 then raise exception 'FAIL: doğrulanmamış hesap gönderi okuyabildi (%)', v_viewer; end if;
    if (select count(*) from public.post_feed_view) <> 0 then raise exception 'FAIL: doğrulanmamış hesap akış görünümünü okuyabildi (%)', v_viewer; end if;
    if (select count(*) from public.comments) <> 0 then raise exception 'FAIL: doğrulanmamış hesap yorum okuyabildi (%)', v_viewer; end if;
    if (select count(*) from public.requirements) <> 0 then raise exception 'FAIL: doğrulanmamış hesap ilan okuyabildi (%)', v_viewer; end if;
    if (select count(*) from public.campus_events) <> 0 then raise exception 'FAIL: doğrulanmamış hesap etkinlik okuyabildi (%)', v_viewer; end if;
    if (select count(*) from public.communities) <> 0 then raise exception 'FAIL: doğrulanmamış hesap topluluk okuyabildi (%)', v_viewer; end if;
    if (select count(*) from public.profiles where id <> v_viewer) <> 0 then raise exception 'FAIL: doğrulanmamış hesap başkasının profilini okuyabildi (%)', v_viewer; end if;
    if (select count(*) from public.profiles where id = v_viewer) <> 1 then raise exception 'FAIL: kullanıcı kendi profilini okuyamadı (%)', v_viewer; end if;

    perform set_config('role', 'postgres', true);
  end loop;
end $$;

-- 2) Doğrulanmamış hesap etkinliğe katılım/şikâyet kaydı ekleyemez.
set local role authenticated;
set local request.jwt.claims = '{"sub":"e2222222-2222-2222-2222-222222222222","role":"authenticated","app_metadata":{}}';
do $$
declare v_failed boolean := false;
begin
  begin
    insert into public.campus_event_attendees (event_id, user_id) values ('00000000-0000-0000-0000-00000000e101', 'e2222222-2222-2222-2222-222222222222');
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: doğrulanmamış hesap etkinliğe katılabildi'; end if;

  v_failed := false;
  begin
    insert into public.reports (reporter_id, target_type, target_id, reason)
    values ('e2222222-2222-2222-2222-222222222222', 'POST', '00000000-0000-0000-0000-00000000b101', 'test');
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: doğrulanmamış hesap şikâyet ekleyebildi'; end if;
end $$;

-- 3) Regresyon: onaylı öğrenci her şeyi görmeye devam eder.
set local request.jwt.claims = '{"sub":"e4444444-4444-4444-4444-444444444444","role":"authenticated","app_metadata":{}}';
do $$
begin
  if (select count(*) from public.posts) <> 2 then raise exception 'FAIL: onaylı öğrenci gönderileri göremedi'; end if;
  if (select count(*) from public.post_feed_view) <> 2 then raise exception 'FAIL: onaylı öğrenci akış görünümünü göremedi'; end if;
  if (select count(*) from public.comments) <> 1 then raise exception 'FAIL: onaylı öğrenci yorumu göremedi'; end if;
  if (select count(*) from public.requirements) <> 1 then raise exception 'FAIL: onaylı öğrenci ilanı göremedi'; end if;
  if (select count(*) from public.campus_events) <> 1 then raise exception 'FAIL: onaylı öğrenci etkinliği göremedi'; end if;
  if (select count(*) from public.communities where id = '00000000-0000-0000-0000-00000000d101' or type = 'GENERAL') <> 2 then
    raise exception 'FAIL: onaylı öğrenci toplulukları göremedi';
  end if;
  if (select count(*) from public.profiles where id = 'e1111111-1111-1111-1111-111111111111') <> 1 then
    raise exception 'FAIL: onaylı öğrenci başka onaylı öğrencinin profilini göremedi';
  end if;
  -- Bekleyen/reddedilen kullanıcıların profili onaylılara da görünmez (yalnızca ACTIVE + APPROVED görünür).
  if (select count(*) from public.profiles where id in ('e2222222-2222-2222-2222-222222222222', 'e3333333-3333-3333-3333-333333333333')) <> 0 then
    raise exception 'FAIL: doğrulanmamış kullanıcıların profili onaylılara göründü';
  end if;
end $$;

rollback;
