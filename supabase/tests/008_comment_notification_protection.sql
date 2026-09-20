-- RLS / tetikleyici testleri: yorum ve bildirim satırlarının sahibi tarafından değiştirilebilen alanları.
-- Kanıtlananlar: yorum yalnızca `body` değişebilir (gönderisi/yazarı/zamanı sabit; sayaçlar bozulmaz);
-- bildirimde yalnızca `is_read` değişebilir. Sunucu tarafı (postgres) etkilenmez.
-- Tek transaction, ROLLBACK ile biter. Herhangi bir doğrulama başarısız olursa "FAIL: ..." istisnası fırlar.
begin;

insert into auth.users (id, email) values
  ('d0000001-3333-4444-5555-666666666601', 'yazar@cp-test.invalid'),
  ('d0000002-3333-4444-5555-666666666602', 'diger@cp-test.invalid');

insert into public.universities (id, name, short_name, city) values
  ('00000000-0000-0000-0000-00000000f301', 'Koruma Test Üni 1', 'KT1', 'Test'),
  ('00000000-0000-0000-0000-00000000f302', 'Koruma Test Üni 2', 'KT2', 'Test');

insert into public.communities (id, name, type, university_id) values
  ('00000000-0000-0000-0000-00000000d301', 'KT1 Kampüs', 'UNIVERSITY', '00000000-0000-0000-0000-00000000f301'),
  ('00000000-0000-0000-0000-00000000d302', 'KT2 Kampüs', 'UNIVERSITY', '00000000-0000-0000-0000-00000000f302');

update public.profiles set account_status = 'ACTIVE', verification_status = 'APPROVED', university_id = '00000000-0000-0000-0000-00000000f301'
  where id = 'd0000001-3333-4444-5555-666666666601';
update public.profiles set account_status = 'ACTIVE', verification_status = 'APPROVED', university_id = '00000000-0000-0000-0000-00000000f302'
  where id = 'd0000002-3333-4444-5555-666666666602';

insert into public.posts (id, author_id, community_id, title, body, category) values
  ('00000000-0000-0000-0000-00000000b301', 'd0000001-3333-4444-5555-666666666601', '00000000-0000-0000-0000-00000000d301', 'P1', 'g', 'OTHER'),
  ('00000000-0000-0000-0000-00000000b302', 'd0000002-3333-4444-5555-666666666602', '00000000-0000-0000-0000-00000000d302', 'P2', 'g', 'OTHER'),
  ('00000000-0000-0000-0000-00000000b303', 'd0000001-3333-4444-5555-666666666601', '00000000-0000-0000-0000-00000000d301', 'P3', 'g', 'OTHER');
insert into public.comments (id, post_id, author_id, body)
  values ('00000000-0000-0000-0000-00000000cc31', '00000000-0000-0000-0000-00000000b301', 'd0000001-3333-4444-5555-666666666601', 'ilk');
insert into public.notifications (id, user_id, type, title, body, related_entity_type, related_entity_id)
  values ('00000000-0000-0000-0000-00000000a301', 'd0000001-3333-4444-5555-666666666601', 'SYSTEM', 'Başlık', 'Gövde', 'conversation', '00000000-0000-0000-0000-00000000c301');

set local role authenticated;
set local request.jwt.claims = '{"sub":"d0000001-3333-4444-5555-666666666601","role":"authenticated","app_metadata":{}}';

-- 1) Yorum: gövde değişir; gönderi/yazar/zaman değişemez.
do $$
declare v_failed boolean;
begin
  update public.comments set body = 'düzenlendi' where id = '00000000-0000-0000-0000-00000000cc31';
  if not found then raise exception 'FAIL: yazar kendi yorumunun gövdesini düzenleyemedi'; end if;

  -- Kullanıcının GÖREBİLDİĞİ başka bir gönderiye taşıma (RLS izin verirdi; yalnızca tetikleyici engeller).
  v_failed := false;
  begin
    update public.comments set post_id = '00000000-0000-0000-0000-00000000b303' where id = '00000000-0000-0000-0000-00000000cc31';
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: yorum görünen başka bir gönderiye taşınabildi (sayaçlar bozulur)'; end if;

  -- Başka üniversitedeki gönderiye taşıma.
  v_failed := false;
  begin
    update public.comments set post_id = '00000000-0000-0000-0000-00000000b302' where id = '00000000-0000-0000-0000-00000000cc31';
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: yorum başka üniversitenin gönderisine taşınabildi'; end if;

  v_failed := false;
  begin
    update public.comments set author_id = 'd0000002-3333-4444-5555-666666666602' where id = '00000000-0000-0000-0000-00000000cc31';
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: yorumun yazarı değiştirilebildi'; end if;

  v_failed := false;
  begin
    update public.comments set created_at = now() - interval '1 year' where id = '00000000-0000-0000-0000-00000000cc31';
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: yorumun oluşturulma zamanı değiştirilebildi'; end if;

  if (select body from public.comments where id = '00000000-0000-0000-0000-00000000cc31') <> 'düzenlendi' then
    raise exception 'FAIL: yorum gövdesi beklenmedik değerde';
  end if;
  if (select comment_count from public.posts where id = '00000000-0000-0000-0000-00000000b301') <> 1 then
    raise exception 'FAIL: P1 yorum sayacı bozuldu';
  end if;
end $$;

-- 2) Bildirim: yalnızca is_read.
do $$
declare v_failed boolean;
begin
  update public.notifications set is_read = true where id = '00000000-0000-0000-0000-00000000a301';
  if not found then raise exception 'FAIL: kullanıcı bildirimini okundu işaretleyemedi'; end if;
  if not (select is_read from public.notifications where id = '00000000-0000-0000-0000-00000000a301') then
    raise exception 'FAIL: is_read yazılmadı';
  end if;

  v_failed := false;
  begin update public.notifications set title = 'Sahte' where id = '00000000-0000-0000-0000-00000000a301';
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: bildirim başlığı değiştirilebildi'; end if;

  v_failed := false;
  begin update public.notifications set related_entity_id = '00000000-0000-0000-0000-00000000c302' where id = '00000000-0000-0000-0000-00000000a301';
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: bildirimin ilişkili kimliği değiştirilebildi'; end if;

  v_failed := false;
  begin update public.notifications set user_id = 'd0000002-3333-4444-5555-666666666602' where id = '00000000-0000-0000-0000-00000000a301';
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: bildirim başka kullanıcıya devredilebildi'; end if;

  v_failed := false;
  begin update public.notifications set type = 'ADMIN_ANNOUNCEMENT' where id = '00000000-0000-0000-0000-00000000a301';
  exception when others then v_failed := true; end;
  if not v_failed then raise exception 'FAIL: bildirim türü değiştirilebildi'; end if;
end $$;

-- 3) Sunucu tarafı (postgres) etkilenmez: sistem bildirim satırlarını güncelleyebilir.
reset role;
update public.notifications set body = 'Sunucu güncelledi' where id = '00000000-0000-0000-0000-00000000a301';
do $$
begin
  if (select body from public.notifications where id = '00000000-0000-0000-0000-00000000a301') <> 'Sunucu güncelledi' then
    raise exception 'FAIL: sunucu tarafı bildirimi güncelleyemedi';
  end if;
end $$;

rollback;
