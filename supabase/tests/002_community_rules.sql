-- RLS / yetki testleri: topluluk gönderileri, beğeni/yorum sayaçları ve gönderi alanı koruması.
-- Tek transaction, ROLLBACK ile biter. Herhangi bir doğrulama başarısız olursa "FAIL: ..." istisnası fırlar.
begin;

insert into auth.users (id, email) values
  ('b1111111-1111-1111-1111-111111111111', 'yazar@rls-test.invalid'),
  ('b2222222-2222-2222-2222-222222222222', 'begenen@rls-test.invalid'),
  ('b3333333-3333-3333-3333-333333333333', 'baskauni@rls-test.invalid');

insert into public.universities (id, name, short_name, city) values
  ('00000000-0000-0000-0000-00000000f001', 'RLS Test Üniversitesi 1', 'RLS_T1', 'Test'),
  ('00000000-0000-0000-0000-00000000f002', 'RLS Test Üniversitesi 2', 'RLS_T2', 'Test');

insert into public.communities (id, name, type, university_id) values
  ('00000000-0000-0000-0000-00000000d001', 'RLS T1 Kampüs', 'UNIVERSITY', '00000000-0000-0000-0000-00000000f001'),
  ('00000000-0000-0000-0000-00000000d002', 'RLS T2 Kampüs', 'UNIVERSITY', '00000000-0000-0000-0000-00000000f002');

-- u1,u2: üniversite 1 (onaylı + aktif); u3: üniversite 2 (onaylı + aktif). Sunucu tarafı güncelleme (postgres).
update public.profiles set university_id = '00000000-0000-0000-0000-00000000f001', account_status = 'ACTIVE', verification_status = 'APPROVED'
  where id in ('b1111111-1111-1111-1111-111111111111', 'b2222222-2222-2222-2222-222222222222');
update public.profiles set university_id = '00000000-0000-0000-0000-00000000f002', account_status = 'ACTIVE', verification_status = 'APPROVED'
  where id = 'b3333333-3333-3333-3333-333333333333';

-- 1) Yazar kendi üniversitesinin topluluğuna yazabilir, başka üniversitenin topluluğuna yazamaz.
set local role authenticated;
set local request.jwt.claims = '{"sub":"b1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
insert into public.posts (id, author_id, community_id, title, body, category)
values ('00000000-0000-0000-0000-00000000b001', 'b1111111-1111-1111-1111-111111111111',
        '00000000-0000-0000-0000-00000000d001', 'Test başlığı', 'Test gövdesi', 'ACADEMIC');
do $$
declare v_failed boolean := false;
begin
  begin
    insert into public.posts (author_id, community_id, title, body, category)
    values ('b1111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-00000000d002', 'x', 'y', 'OTHER');
  exception when others then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: başka üniversite topluluğuna gönderi yazılabildi'; end if;

  v_failed := false;
  begin
    insert into public.posts (author_id, community_id, title, body, category)
    values ('b2222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-00000000d001', 'x', 'y', 'OTHER');
  exception when others then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: başkası adına gönderi yazılabildi'; end if;
end $$;

-- 2) Gönderi sayaçları ve alan koruması (yazar).
do $$
declare v_failed boolean; v_row record;
begin
  v_failed := false;
  begin
    update public.posts set like_count = 99 where id = '00000000-0000-0000-0000-00000000b001';
  exception when others then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: yazar like_count uydurabildi'; end if;

  v_failed := false;
  begin
    update public.posts set community_id = '00000000-0000-0000-0000-00000000d002' where id = '00000000-0000-0000-0000-00000000b001';
  exception when others then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: yazar gönderiyi başka topluluğa taşıyabildi'; end if;

  select count(*) as c into v_row from public.posts where id = '00000000-0000-0000-0000-00000000b001';
  if v_row.c <> 1 then raise exception 'FAIL: yazar kendi gönderisini göremedi'; end if;
end $$;

-- 3) Aynı üniversiteden ikinci kullanıcı beğenir ve yorum yapar; sayaç HERKESE doğru görünür (eski view hatası: 0/1).
set local request.jwt.claims = '{"sub":"b2222222-2222-2222-2222-222222222222","role":"authenticated","app_metadata":{}}';
insert into public.post_likes (post_id, user_id) values ('00000000-0000-0000-0000-00000000b001', 'b2222222-2222-2222-2222-222222222222');
insert into public.comments (id, post_id, author_id, body)
values ('00000000-0000-0000-0000-00000000cc01', '00000000-0000-0000-0000-00000000b001', 'b2222222-2222-2222-2222-222222222222', 'Yorum');
set local request.jwt.claims = '{"sub":"b1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
insert into public.post_likes (post_id, user_id) values ('00000000-0000-0000-0000-00000000b001', 'b1111111-1111-1111-1111-111111111111');

set local request.jwt.claims = '{"sub":"b2222222-2222-2222-2222-222222222222","role":"authenticated","app_metadata":{}}';
do $$
declare v_row record; v_failed boolean := false;
begin
  select like_count, comment_count into v_row from public.post_feed_view where id = '00000000-0000-0000-0000-00000000b001';
  if v_row.like_count <> 2 then raise exception 'FAIL: beğeni sayısı yanlış görünüyor: %', v_row.like_count; end if;
  if v_row.comment_count <> 1 then raise exception 'FAIL: yorum sayısı yanlış: %', v_row.comment_count; end if;

  -- Kullanıcı yalnızca KENDİ beğenisini görür (kim beğendi sızmaz).
  if (select count(*) from public.post_likes) <> 1 then raise exception 'FAIL: başkasının beğenisi görünüyor'; end if;

  -- Aynı beğeni iki kez atılamaz.
  begin
    insert into public.post_likes (post_id, user_id) values ('00000000-0000-0000-0000-00000000b001', 'b2222222-2222-2222-2222-222222222222');
  exception when unique_violation then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: aynı gönderi iki kez beğenilebildi'; end if;
end $$;

-- 4) Başka üniversitenin öğrencisi gönderiyi göremez, beğenemez, yorum yapamaz, kaydedemez.
set local request.jwt.claims = '{"sub":"b3333333-3333-3333-3333-333333333333","role":"authenticated","app_metadata":{}}';
do $$
declare v_failed boolean;
begin
  if (select count(*) from public.post_feed_view where id = '00000000-0000-0000-0000-00000000b001') <> 0 then
    raise exception 'FAIL: başka üniversitenin öğrencisi gönderiyi görebildi';
  end if;

  v_failed := false;
  begin
    insert into public.comments (post_id, author_id, body)
    values ('00000000-0000-0000-0000-00000000b001', 'b3333333-3333-3333-3333-333333333333', 'sızma');
  exception when others then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: başka üniversitenin öğrencisi yorum ekleyebildi'; end if;

  v_failed := false;
  begin
    insert into public.post_likes (post_id, user_id) values ('00000000-0000-0000-0000-00000000b001', 'b3333333-3333-3333-3333-333333333333');
  exception when others then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: başka üniversitenin öğrencisi beğeni atabildi'; end if;

  v_failed := false;
  begin
    insert into public.saved_posts (post_id, user_id) values ('00000000-0000-0000-0000-00000000b001', 'b3333333-3333-3333-3333-333333333333');
  exception when others then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: başka üniversitenin öğrencisi gönderiyi kaydedebildi'; end if;
end $$;

-- 4b) Onaysız (PENDING) hesap aynı üniversitede olsa bile beğenemez.
reset role;
update public.profiles set account_status = 'PENDING', verification_status = 'PENDING' where id = 'b3333333-3333-3333-3333-333333333333';
update public.profiles set university_id = '00000000-0000-0000-0000-00000000f001' where id = 'b3333333-3333-3333-3333-333333333333';
set local role authenticated;
set local request.jwt.claims = '{"sub":"b3333333-3333-3333-3333-333333333333","role":"authenticated","app_metadata":{}}';
do $$
declare v_failed boolean := false;
begin
  begin
    insert into public.post_likes (post_id, user_id) values ('00000000-0000-0000-0000-00000000b001', 'b3333333-3333-3333-3333-333333333333');
  exception when others then v_failed := true;
  end;
  if not v_failed then raise exception 'FAIL: onaysız hesap beğeni atabildi'; end if;
end $$;

-- 5) Yorum silinince sayaç düşer; sayaç 0'ın altına inmez.
set local request.jwt.claims = '{"sub":"b2222222-2222-2222-2222-222222222222","role":"authenticated","app_metadata":{}}';
delete from public.comments where id = '00000000-0000-0000-0000-00000000cc01';
delete from public.post_likes where post_id = '00000000-0000-0000-0000-00000000b001' and user_id = 'b2222222-2222-2222-2222-222222222222';
do $$
declare v_row record;
begin
  select like_count, comment_count into v_row from public.post_feed_view where id = '00000000-0000-0000-0000-00000000b001';
  if v_row.like_count <> 1 or v_row.comment_count <> 0 then
    raise exception 'FAIL: silme sonrası sayaçlar yanlış (% / %)', v_row.like_count, v_row.comment_count;
  end if;
end $$;

-- 6) Moderatör kaldırdığı gönderiyi yazar yeniden yayınlayamaz.
reset role;
update public.posts set status = 'REMOVED' where id = '00000000-0000-0000-0000-00000000b001';
set local role authenticated;
set local request.jwt.claims = '{"sub":"b1111111-1111-1111-1111-111111111111","role":"authenticated","app_metadata":{}}';
do $$
declare v_failed boolean := false;
begin
  begin
    update public.posts set status = 'PUBLISHED' where id = '00000000-0000-0000-0000-00000000b001';
  exception when others then v_failed := true;
  end;
  -- REMOVED satırı zaten posts_select ile görünmez (status = PUBLISHED); 0 satır etkilenmesi de kabul.
  if not v_failed and exists (select 1 from public.posts where id = '00000000-0000-0000-0000-00000000b001' and status = 'PUBLISHED') then
    raise exception 'FAIL: kaldırılmış gönderi yeniden yayınlandı';
  end if;
end $$;

reset role;
do $$
begin
  if not exists (select 1 from public.posts where id = '00000000-0000-0000-0000-00000000b001' and status = 'REMOVED') then
    raise exception 'FAIL: kaldırılmış gönderi PUBLISHED oldu';
  end if;
end $$;

select 'TÜM DOĞRULAMALAR GEÇTİ' as sonuc;
rollback;
