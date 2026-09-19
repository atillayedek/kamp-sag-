-- Varsayılan kullanıcı adı UUID'nin yalnızca ilk 8 hanesinden (32 bit) üretiliyordu: ~65 bin kullanıcıda
-- birthday çakışması kayıt akışını unique_violation ile bozardı. username kısıtı en çok 30 karakter
-- ('user_' + 25 onaltılık hane = 30) => 100 bit; pratikte çakışmaz.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, username, full_name)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'username', 'user_' || substr(replace(new.id::text, '-', ''), 1, 25)),
    coalesce(new.raw_user_meta_data ->> 'full_name', '')
  );
  return new;
end;
$$;
