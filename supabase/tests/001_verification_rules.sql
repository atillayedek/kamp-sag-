-- RLS / yetki testleri: öğrenci belgesi onayı ve profil koruma kuralları.
-- Çalıştırma: Supabase SQL editörü veya MCP execute_sql. Tümü tek transaction'da çalışır ve ROLLBACK ile biter
-- (kalıcı veri bırakmaz). Herhangi bir doğrulama başarısız olursa "FAIL: ..." istisnası fırlar.
begin;

insert into auth.users (id, email) values
  ('a1111111-1111-1111-1111-111111111111', 'ogrenci@rls-test.invalid'),
  ('a2222222-2222-2222-2222-222222222222', 'moderator@rls-test.invalid'),
  ('a3333333-3333-3333-3333-333333333333', 'baska@rls-test.invalid');

insert into public.universities (id, name, short_name, city) values
  ('00000000-0000-0000-0000-00000000f001', 'RLS Test Üniversitesi 1', 'RLS_T1', 'Test'),
  ('00000000-0000-0000-0000-00000000f002', 'RLS Test Üniversitesi 2', 'RLS_T2', 'Test');

update public.profiles
set university_id = '00000000-0000-0000-0000-00000000f001', department = 'Test Bölümü', full_name = 'Test Öğrenci'
where id = 'a1111111-1111-1111-1111-111111111111';

insert into public.student_verifications (id, user_id, document_path)
values ('00000000-0000-0000-0000-00000000e001', 'a1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111/document.pdf');

-- 1) Moderatör olmayan kullanıcı RPC'yi çalıştıramaz.
set local role authenticated;
set local request.jwt.claims = '{"sub":"a3333333-3333-3333-3333-333333333333","role":"authenticated","app_metadata":{}}';
do $$
declare v_denied boolean := false;
begin
  begin
    perform public.review_student_verification('00000000-0000-0000-0000-00000000e001', 'APPROVED', null);
  exception when insufficient_privilege then v_denied := true;
  end;
  if not v_denied then raise exception 'FAIL: moderatör olmayan kullanıcı belgeyi onaylayabildi'; end if;
end $$;

-- 2) Anonim çağrı reddedilir.
set local role anon;
do $$
declare v_denied boolean := false;
begin
  begin
    perform public.review_student_verification('00000000-0000-0000-0000-00000000e001', 'APPROVED', null);
  exception when insufficient_privilege then v_denied := true;
  end;
  if not v_denied then raise exception 'FAIL: anon RPC çalıştırabildi'; end if;
end $$;

-- 3) Moderatör: gerekçesiz red reddedilir, doğrudan UPDATE etkisizdir, onay atomik çalışır.
set local role authenticated;
set local request.jwt.claims = '{"sub":"a2222222-2222-2222-2222-222222222222","role":"authenticated","app_metadata":{"is_moderator":true}}';
do $$
declare v_failed boolean := false; v_rows int;
begin
  begin
    perform public.review_student_verification('00000000-0000-0000-0000-00000000e001', 'REJECTED', '   ');
  exception when invalid_parameter_value then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: gerekçesiz red kabul edildi'; end if;

  update public.student_verifications set status = 'APPROVED' where id = '00000000-0000-0000-0000-00000000e001';
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then raise exception 'FAIL: moderatör student_verifications''ı doğrudan güncelleyebildi'; end if;

  perform public.review_student_verification('00000000-0000-0000-0000-00000000e001', 'APPROVED', null);
end $$;

reset role;
do $$
declare v_row record;
begin
  select verification_status, account_status into v_row from public.profiles where id = 'a1111111-1111-1111-1111-111111111111';
  if v_row.verification_status <> 'APPROVED' or v_row.account_status <> 'ACTIVE' then
    raise exception 'FAIL: onay profile yansımadı (% / %)', v_row.verification_status, v_row.account_status;
  end if;
  if not exists (select 1 from public.student_verifications
                 where id = '00000000-0000-0000-0000-00000000e001'
                   and status = 'APPROVED' and reviewed_by = 'a2222222-2222-2222-2222-222222222222' and reviewed_at is not null) then
    raise exception 'FAIL: reviewed_by/reviewed_at yazılmadı';
  end if;
  if not exists (select 1 from public.notifications where user_id = 'a1111111-1111-1111-1111-111111111111') then
    raise exception 'FAIL: onay bildirimi oluşmadı';
  end if;
end $$;

-- 4) Sonuçlanmış başvuru tekrar değerlendirilemez.
set local role authenticated;
set local request.jwt.claims = '{"sub":"a2222222-2222-2222-2222-222222222222","role":"authenticated","app_metadata":{"is_moderator":true}}';
do $$
declare v_failed boolean := false;
begin
  begin
    perform public.review_student_verification('00000000-0000-0000-0000-00000000e001', 'REJECTED', 'sonradan red');
  exception when object_not_in_prerequisite_state then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: sonuçlanmış başvuru yeniden değerlendirildi'; end if;
end $$;

-- 5) Onaylı öğrenci: ayrıcalıklı alanlar ve üniversite değiştirilemez, bölüm değiştirilebilir.
set local request.jwt.claims = '{"sub":"a1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
do $$
declare v_failed boolean; v_rows int;
begin
  v_failed := false;
  begin
    update public.profiles set university_id = '00000000-0000-0000-0000-00000000f002' where id = 'a1111111-1111-1111-1111-111111111111';
  exception when others then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: onaylı hesap üniversiteyi değiştirebildi'; end if;

  v_failed := false;
  begin
    update public.profiles set account_status = 'SUSPENDED' where id = 'a1111111-1111-1111-1111-111111111111';
  exception when others then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: istemci account_status değiştirebildi'; end if;

  v_failed := false;
  begin
    update public.profiles set karma_score = 9999 where id = 'a1111111-1111-1111-1111-111111111111';
  exception when others then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: istemci karma_score değiştirebildi'; end if;

  update public.profiles set department = 'Yeni Bölüm' where id = 'a1111111-1111-1111-1111-111111111111';
  get diagnostics v_rows = row_count;
  if v_rows <> 1 then raise exception 'FAIL: kullanıcı kendi bölümünü güncelleyemedi'; end if;

  -- Başkasının profilini güncelleyemez (RLS satırı görmez -> 0 satır).
  update public.profiles set department = 'Hack' where id = 'a3333333-3333-3333-3333-333333333333';
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then raise exception 'FAIL: kullanıcı başkasının profilini güncelleyebildi'; end if;
end $$;

-- 6) Öğrenci kendi başvurusunu görür ama güncelleyemez; başka kullanıcı göremez.
do $$
declare v_count int; v_rows int;
begin
  select count(*) into v_count from public.student_verifications;
  if v_count <> 1 then raise exception 'FAIL: öğrenci beklenen sayıda başvuru gördü: %', v_count; end if;
  update public.student_verifications set status = 'APPROVED' where id = '00000000-0000-0000-0000-00000000e001';
  get diagnostics v_rows = row_count;
  if v_rows <> 0 then raise exception 'FAIL: öğrenci başvuru durumunu güncelleyebildi'; end if;
end $$;
set local request.jwt.claims = '{"sub":"a3333333-3333-3333-3333-333333333333","role":"authenticated","app_metadata":{}}';
do $$
declare v_count int;
begin
  select count(*) into v_count from public.student_verifications;
  if v_count <> 0 then raise exception 'FAIL: başka kullanıcı başvuruyu görebildi'; end if;
end $$;

-- 7) Üyesi kalmayan sohbet silinir; hesap silme kullanıcı verisini temizler.
reset role;
insert into public.conversations (id) values ('00000000-0000-0000-0000-00000000c001');
insert into public.conversation_members (conversation_id, user_id) values
  ('00000000-0000-0000-0000-00000000c001', 'a1111111-1111-1111-1111-111111111111'),
  ('00000000-0000-0000-0000-00000000c001', 'a3333333-3333-3333-3333-333333333333');
delete from auth.users where id = 'a1111111-1111-1111-1111-111111111111';
do $$
begin
  if not exists (select 1 from public.conversations where id = '00000000-0000-0000-0000-00000000c001') then
    raise exception 'FAIL: üyesi kalan sohbet silindi';
  end if;
end $$;
delete from auth.users where id = 'a3333333-3333-3333-3333-333333333333';
do $$
begin
  if exists (select 1 from public.conversations where id = '00000000-0000-0000-0000-00000000c001') then
    raise exception 'FAIL: üyesi kalmayan sohbet yetim kaldı';
  end if;
  if exists (select 1 from public.profiles where id = 'a1111111-1111-1111-1111-111111111111') then
    raise exception 'FAIL: hesap silinince profil kalıcı';
  end if;
  if exists (select 1 from public.student_verifications where user_id = 'a1111111-1111-1111-1111-111111111111') then
    raise exception 'FAIL: hesap silinince başvuru kalıcı';
  end if;
end $$;

-- 8) Moderatör hesabı silinince başvuru kayıtları korunur (reviewed_by null olur).
insert into auth.users (id, email) values ('a4444444-4444-4444-4444-444444444444', 'ogrenci2@rls-test.invalid');
insert into public.student_verifications (id, user_id, document_path, status, reviewed_by, reviewed_at)
values ('00000000-0000-0000-0000-00000000e002', 'a4444444-4444-4444-4444-444444444444', 'x/document.pdf', 'REJECTED',
        'a2222222-2222-2222-2222-222222222222', now());
delete from auth.users where id = 'a2222222-2222-2222-2222-222222222222';
do $$
begin
  if not exists (select 1 from public.student_verifications where id = '00000000-0000-0000-0000-00000000e002' and reviewed_by is null) then
    raise exception 'FAIL: moderatör silinince başvuru kaydı bozuldu';
  end if;
end $$;

select 'TÜM DOĞRULAMALAR GEÇTİ' as sonuc;
rollback;
