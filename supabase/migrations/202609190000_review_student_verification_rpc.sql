-- Öğrenci belgesi onayı: onay/red TEK atomik işlem. Moderatör artık student_verifications'a
-- doğrudan UPDATE yapamaz (document_path/user_id gibi sütunları değiştirebilirdi); tek yol bu RPC.
-- Aynı UPDATE, sync_profile_verification_status trigger'ını aynı transaction'da tetikler:
-- verification.status -> profiles.verification_status/account_status -> notifications (tek işlem).
create or replace function public.review_student_verification(
  p_verification_id uuid,
  p_decision text,
  p_reason text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_status text;
begin
  if not public.is_moderator() then
    raise exception 'Bu işlem için moderatör yetkisi gerekir.' using errcode = '42501';
  end if;

  if p_decision not in ('APPROVED', 'REJECTED') then
    raise exception 'Karar APPROVED veya REJECTED olmalıdır.' using errcode = '22023';
  end if;

  if p_decision = 'REJECTED' and coalesce(btrim(p_reason), '') = '' then
    raise exception 'Red gerekçesi zorunludur.' using errcode = '22023';
  end if;

  select status into v_status
  from public.student_verifications
  where id = p_verification_id
  for update;

  if not found then
    raise exception 'Başvuru bulunamadı.' using errcode = 'P0002';
  end if;

  if v_status <> 'PENDING' then
    raise exception 'Bu başvuru zaten sonuçlandırılmış.' using errcode = '55000';
  end if;

  update public.student_verifications
  set status = p_decision,
      rejection_reason = case when p_decision = 'REJECTED' then btrim(p_reason) else null end,
      reviewed_by = auth.uid(),
      reviewed_at = now()
  where id = p_verification_id;
end;
$$;

revoke all on function public.review_student_verification(uuid, text, text) from public, anon;
grant execute on function public.review_student_verification(uuid, text, text) to authenticated;

drop policy if exists "student_verifications_update_staff" on public.student_verifications;
