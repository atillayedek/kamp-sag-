-- Sorun: koruma auth.role() (JWT'deki rol) ile yapılıyordu. Moderatör onayı -> sync_profile_verification_status
-- (security definer) -> profiles UPDATE sırasında JWT hâlâ 'authenticated' olduğundan kendi koruma tetikleyicimize
-- takılıyor ve onay HİÇ çalışmıyordu (yalnızca service_role çalışırdı).
-- Çözüm: security INVOKER + current_user. Doğrudan istemci UPDATE'inde current_user = 'authenticated';
-- security definer fonksiyon içinden (sync tetikleyicisi, RPC) fonksiyon sahibi; Edge Function service_role'dür.
create or replace function public.protect_profile_privileged_fields()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  if current_user = 'authenticated' then
    if new.account_status is distinct from old.account_status
       or new.verification_status is distinct from old.verification_status
       or new.karma_score is distinct from old.karma_score then
      raise exception 'account_status, verification_status ve karma_score istemci tarafından değiştirilemez.';
    end if;

    if old.verification_status = 'APPROVED'
       and new.university_id is distinct from old.university_id then
      raise exception 'Onaylı hesapta üniversite değiştirilemez.';
    end if;
  end if;
  return new;
end;
$$;
