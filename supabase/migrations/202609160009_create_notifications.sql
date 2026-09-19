create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  type text not null check (type in (
    'POST_COMMENT', 'POST_LIKE', 'NEW_MESSAGE', 'AI_MATCH_FOUND',
    'VERIFICATION_APPROVED', 'VERIFICATION_REJECTED', 'SYSTEM',
    'CAMPUS_EVENT', 'ADMIN_ANNOUNCEMENT'
  )),
  title text not null,
  body text not null,
  related_entity_type text,
  related_entity_id uuid,
  is_read boolean not null default false,
  created_at timestamptz not null default now()
);

create index notifications_user_id_idx on public.notifications (user_id, created_at desc);
create index notifications_user_unread_idx on public.notifications (user_id) where not is_read;

alter table public.notifications enable row level security;
alter publication supabase_realtime add table public.notifications;

-- verification onay/red olduğunda otomatik bildirim (sahte/sabit sayaç değil,
-- gerçek satır — bkz. §26, §39 "sabit okunmamış bildirim sayıları" yasağı).
create or replace function public.notify_verification_status_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.status = 'APPROVED' and (old is null or old.status is distinct from new.status) then
    insert into public.notifications (user_id, type, title, body)
    values (new.user_id, 'VERIFICATION_APPROVED', 'Öğrenci doğrulaman onaylandı', 'Artık KampüsAğı''nı tam olarak kullanabilirsin.');
  elsif new.status = 'REJECTED' and (old is null or old.status is distinct from new.status) then
    insert into public.notifications (user_id, type, title, body)
    values (new.user_id, 'VERIFICATION_REJECTED', 'Öğrenci doğrulaman reddedildi', coalesce(new.rejection_reason, 'Lütfen belgenizi kontrol edip tekrar yükleyin.'));
  end if;
  return new;
end;
$$;

create trigger notify_verification_status_change_trigger
after insert or update on public.student_verifications
for each row execute function public.notify_verification_status_change();
