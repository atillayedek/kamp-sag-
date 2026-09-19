-- current_university_id() RLS'te üniversiteler arası izolasyonun temelidir. Onaylandıktan sonra
-- istemci profiles.university_id'yi değiştirebilseydi başka üniversitenin içeriğini görebilirdi.
create or replace function public.protect_profile_privileged_fields()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.role() = 'authenticated' then
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
