-- RLS denetiminde bulunan iki boşluk (satır sahibi kendi satırının KORUNMASI GEREKEN alanlarını değiştirebiliyordu):
--
-- 1) comments_update_own yalnızca `author_id = auth.uid()` denetliyordu: yazar yorumunun `post_id`'sini kendi GÖREBİLDİĞİ başka bir
--    gönderiye taşıyabilir ve `created_at`'i değiştirebilirdi (başka üniversitedeki gönderiye taşıma, yeni satırın SELECT politikasını
--    geçemediği için zaten engelliydi). `comment_count` tetikleyicisi yalnızca INSERT/DELETE dinlediği için taşıma sayaçları bozar.
--    Artık yalnızca `body` değişebilir.
-- 2) notifications_update_own_mark_read adı "okundu işaretle" derken tüm sütunlara izin veriyordu (başlık, tür, ilişkili kimlik).
--    Artık yalnızca `is_read` değişebilir.
--
-- Koruma yalnızca istemci rolü için (`current_user = 'authenticated'`) geçerlidir: sunucu tarafı (definer/service role) etkilenmez —
-- protect_post_fields ve protect_profile_privileged_fields ile aynı desen.

create or replace function public.protect_comment_fields()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user = 'authenticated' then
    if new.post_id is distinct from old.post_id
       or new.author_id is distinct from old.author_id
       or new.created_at is distinct from old.created_at then
      raise exception 'Yorumun gönderisi, yazarı ve oluşturulma zamanı değiştirilemez.';
    end if;
  end if;
  return new;
end;
$$;

create trigger protect_comment_fields_trigger
before update on public.comments
for each row execute function public.protect_comment_fields();

create or replace function public.protect_notification_fields()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user = 'authenticated' then
    if new.user_id is distinct from old.user_id
       or new.type is distinct from old.type
       or new.title is distinct from old.title
       or new.body is distinct from old.body
       or new.related_entity_type is distinct from old.related_entity_type
       or new.related_entity_id is distinct from old.related_entity_id
       or new.created_at is distinct from old.created_at then
      raise exception 'Bildirimde yalnızca okundu bilgisi değiştirilebilir.';
    end if;
  end if;
  return new;
end;
$$;

create trigger protect_notification_fields_trigger
before update on public.notifications
for each row execute function public.protect_notification_fields();
