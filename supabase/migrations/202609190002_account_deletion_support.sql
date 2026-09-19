-- Hesap silme (App Store/Play kuralı) auth.users satırını siler; kullanıcıya ait her şey CASCADE ile gider.
-- Moderatör/etkinlik oluşturan hesap silinince başkalarının kayıtları bozulmasın: set null.
alter table public.student_verifications drop constraint student_verifications_reviewed_by_fkey;
alter table public.student_verifications
  add constraint student_verifications_reviewed_by_fkey
  foreign key (reviewed_by) references auth.users(id) on delete set null;

alter table public.campus_events drop constraint campus_events_created_by_fkey;
alter table public.campus_events
  add constraint campus_events_created_by_fkey
  foreign key (created_by) references auth.users(id) on delete set null;

alter table public.reports drop constraint reports_reviewed_by_fkey;
alter table public.reports
  add constraint reports_reviewed_by_fkey
  foreign key (reviewed_by) references auth.users(id) on delete set null;

-- Üyesi kalmayan sohbet yetim kalmasın.
create or replace function public.delete_conversation_when_empty()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from public.conversations c
  where c.id = old.conversation_id
    and not exists (select 1 from public.conversation_members m where m.conversation_id = c.id);
  return null;
end;
$$;

revoke all on function public.delete_conversation_when_empty() from public, anon, authenticated;

create trigger delete_conversation_when_empty_trigger
after delete on public.conversation_members
for each row execute function public.delete_conversation_when_empty();
